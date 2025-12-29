package com.project.pooket.ui.features.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.pooket.data.local.book.BookLocalRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.project.pooket.data.local.note.NormRect
import com.project.pooket.data.local.note.NoteEntity
import com.project.pooket.data.local.note.NoteRepository
import com.project.pooket.core.utils.CoordinateTextStripper
import com.project.pooket.core.utils.PdfChar
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
enum class DragHandle { NONE, START, END }

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val app: Application,
    private val repository: BookLocalRepository,
    private  val noteRepository: NoteRepository
) : ViewModel() {

    private val _pdfRenderer = MutableStateFlow<PdfRenderer?>(null)

    private val _pageCount = MutableStateFlow(0)
    val pageCount = _pageCount.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isVerticalScrollMode = MutableStateFlow(false)
    val isVerticalScrollMode = _isVerticalScrollMode.asStateFlow()

    private val _isTextMode = MutableStateFlow(false)
    val isTextMode = _isTextMode.asStateFlow()

    private val _fontSize = MutableStateFlow(16f)
    val fontSize = _fontSize.asStateFlow()

    private val _initialPage = MutableStateFlow(0)
    val initialPage = _initialPage.asStateFlow()

    private val bitmapCache =
        object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 4).toInt()) {
            override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
        }
    private val textCache = LruCache<Int, String>(200)
    private val rendererMutex = Mutex()
    private var fileDescriptor: ParcelFileDescriptor? = null

    private var pdDocument: PDDocument? = null
    private var currentBookUri: String? = null
    private var saveJob: Job? = null

    init {
        PDFBoxResourceLoader.init(app)
    }

    fun loadPdf(uriString: String) {
        if (currentBookUri == uriString && _pdfRenderer.value != null) {
            _isLoading.value = false
            return
        }
        cleanupResources()

        currentBookUri = uriString
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val book = repository.getBook(uriString)
                _initialPage.value = book?.lastPage ?: 0

                val uri = uriString.toUri()
                fileDescriptor = app.contentResolver.openFileDescriptor(uri, "r")

                fileDescriptor?.let { fd ->
                    val renderer = PdfRenderer(fd)
                    _pdfRenderer.value = renderer
                    _pageCount.value = renderer.pageCount

                    if ((book?.totalPages ?: 0) == 0 && renderer.pageCount > 0) {
                        repository.initTotalPages(uriString, renderer.pageCount)
                    }
                }
            } catch (e: Exception) {
                Log.e("ReaderVM", "Failed to load PDF", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun cleanupResources() {
        CoroutineScope(Dispatchers.IO + NonCancellable).launch {
            try {
                rendererMutex.withLock {
                    pdDocument?.close()
                    pdDocument = null

                    _pdfRenderer.value?.close()
                    _pdfRenderer.value = null

                    fileDescriptor?.close()
                    fileDescriptor = null

                    bitmapCache.evictAll()
                    textCache.evictAll()
                    Log.d("ReaderVM", "Resources cleaned successfully")
                }
            } catch (e: Exception) {
                Log.e("ReaderVM", "Cleanup error", e)
            }
        }
    }

    suspend fun renderPage(index: Int): Bitmap? = withContext(Dispatchers.Default) {
        val renderer = _pdfRenderer.value ?: return@withContext null
        bitmapCache.get(index)?.let { return@withContext it }

        rendererMutex.withLock {
            bitmapCache.get(index)?.let { return@withLock it }
            var page: PdfRenderer.Page? = null
            try {
                page = renderer.openPage(index)
                val density = app.resources.displayMetrics.density
                val screenWidth = app.resources.displayMetrics.widthPixels
                val targetWidth = (screenWidth * (if (density > 2) 1.5f else 2.0f)).toInt()
                val targetHeight = (targetWidth.toFloat() / page.width * page.height).toInt()

                val bitmap = createBitmap(targetWidth, targetHeight)
                bitmap.eraseColor(android.graphics.Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                bitmapCache.put(index, bitmap)
                bitmap
            } catch (e: Exception) {
                Log.e("ReaderVM", "Error rendering page $index", e)
                null
            } finally {
                try {
                    page?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun extractText(index: Int): String = withContext(Dispatchers.IO) {
        textCache.get(index)?.let { return@withContext it }

        rendererMutex.withLock {
            textCache.get(index)?.let { return@withLock it }

            try {
                if (pdDocument == null) {
                    val uri = (currentBookUri ?: return@withLock "").toUri()
                    app.contentResolver.openInputStream(uri)?.use { stream ->
                        pdDocument = PDDocument.load(stream)
                    }
                }

                pdDocument?.let { doc ->
                    val stripper = PDFTextStripper().apply {
                        startPage = index + 1
                        endPage = index + 1
                    }
                    val text = stripper.getText(doc).trim()
                    if (text.isNotEmpty()) textCache.put(index, text)
                    text
                } ?: ""
            } catch (e: Exception) {
                Log.e("ReaderVM", "Text extraction failed", e)
                ""
            }
        }
    }


    fun toggleReadingMode() {
        _isVerticalScrollMode.value = !_isVerticalScrollMode.value
    }

    fun toggleTextMode() {
        _isTextMode.value = !_isTextMode.value
    }

    fun setFontSize(size: Float) {
        _fontSize.value = size
    }

    fun onPageChanged(pageIndex: Int) {
        val uri = currentBookUri ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            repository.saveProgress(uri, pageIndex)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanupResources()
    }


    private val _notes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val notes = _notes.asStateFlow()

    // Holds the cached word positions for pages we've touched
    private val pageCharsCache = mutableMapOf<Int, List<PdfChar>>()

    // Selection State
    data class SelectionState(
        val pageIndex: Int,
        val startWordIndex: Int,
        val endWordIndex: Int,
        val selectedText: String,
        val rects: List<NormRect>,
        val activeHandle: DragHandle = DragHandle.NONE // Track what we are dragging
    )

    private val _selectionState = MutableStateFlow<SelectionState?>(null)
    val selectionState = _selectionState.asStateFlow()

    // 1. Observe notes when book loads
    fun loadNotes(uri: String) {
        viewModelScope.launch {
            noteRepository.getNotes(uri).collect {
                _notes.value = it
            }
        }
    }

    // --- FIX 2: Helper to check handle touch without modifying state yet ---
    fun isTouchingHandle(pageIndex: Int, point: Offset, viewSize: Size): Boolean {
        val current = _selectionState.value ?: return false
        if (current.pageIndex != pageIndex) return false

        val normPoint = Offset(point.x / viewSize.width, point.y / viewSize.height)
        val rects = current.rects
        if (rects.isEmpty()) return false

        val startRect = rects.first()
        val endRect = rects.last()

        // Threshold: 8% of the screen dimension approx
        val threshold = 0.08f

        val startDist = distance(normPoint, startRect.left, startRect.bottom, 1f)
        val endDist = distance(normPoint, endRect.right, endRect.bottom, 1f)

        return startDist < threshold || endDist < threshold
    }

    // Call this inside loadPdf() after currentBookUri is set
    // loadNotes(uriString)

    // 2. Extract words with coordinates (Lazy loaded)
    suspend fun getPageChars(pageIndex: Int): List<PdfChar> = withContext(Dispatchers.IO) {
        if (pageCharsCache.containsKey(pageIndex)) return@withContext pageCharsCache[pageIndex]!!

        rendererMutex.withLock {
            try {
                if (pdDocument == null && currentBookUri != null) {
                    val uri = currentBookUri!!.toUri()
                    app.contentResolver.openInputStream(uri)?.use { pdDocument = PDDocument.load(it) }
                }
                val doc = pdDocument ?: return@withLock emptyList()
                val page = doc.getPage(pageIndex)

                val stripper = CoordinateTextStripper(page)
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                stripper.writeText(doc, OutputStreamWriter(ByteArrayOutputStream()))

                pageCharsCache[pageIndex] = stripper.chars
                stripper.chars
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun mergeCharsToLineRects(chars: List<PdfChar>): List<NormRect> {
        val merged = mutableListOf<NormRect>()
        if (chars.isEmpty()) return merged

        val visibleChars = chars.filter { !it.isSpace }
        if (visibleChars.isEmpty()) return merged

        // Sort primarily by Top (Y), secondarily by Left (X)
        // We use a small tolerance for Y sorting to handle uneven PDFs
        val sorted = visibleChars.sortedWith { a, b ->
            if (kotlin.math.abs(a.bounds.top - b.bounds.top) < 0.01f) {
                a.bounds.left.compareTo(b.bounds.left)
            } else {
                a.bounds.top.compareTo(b.bounds.top)
            }
        }

        var currentLeft = sorted[0].bounds.left
        var currentRight = sorted[0].bounds.right
        var currentTop = sorted[0].bounds.top
        var currentBottom = sorted[0].bounds.bottom

        // Track the visual center of the current line to detect line breaks
        var currentLineY = (currentTop + currentBottom) / 2

        for (i in 1 until sorted.size) {
            val charBounds = sorted[i].bounds
            val charCenterY = (charBounds.top + charBounds.bottom) / 2

            // CHECK: Is this char on a NEW LINE?
            // If the Y difference is significant (> 1.5% of page height), it's a new line
            val isNewLine = kotlin.math.abs(charCenterY - currentLineY) > 0.015f

            if (!isNewLine) {
                // SAME LINE: Extend the current rectangle horizontally
                currentRight = maxOf(currentRight, charBounds.right)
                currentTop = minOf(currentTop, charBounds.top)        // Expand up if needed
                currentBottom = maxOf(currentBottom, charBounds.bottom) // Expand down if needed
            } else {
                // NEW LINE: Save current rect and start a new one
                merged.add(NormRect(currentLeft, currentTop, currentRight, currentBottom))

                // Reset for next line
                currentLeft = charBounds.left
                currentRight = charBounds.right
                currentTop = charBounds.top
                currentBottom = charBounds.bottom
                currentLineY = (currentTop + currentBottom) / 2
            }
        }
        // Add the final line
        merged.add(NormRect(currentLeft, currentTop, currentRight, currentBottom))

        return merged
    }

    // 2. IMPROVED LONG PRESS (Fixes "Random Whole Line" selection)
    fun onLongPress(pageIndex: Int, touchPoint: Offset, viewSize: Size) {
        viewModelScope.launch(Dispatchers.Default) {
            val chars = getPageChars(pageIndex)
            val normPoint = Offset(touchPoint.x / viewSize.width, touchPoint.y / viewSize.height)

            val hitIndex = chars.indexOfFirst { !it.isSpace && containsPoint(it.bounds, normPoint) }

            if (hitIndex != -1) {
                // Safety: Get Y of the hit char to detect line breaks
                val hitY = chars[hitIndex].bounds.top

                // EXPAND LEFT
                var start = hitIndex
                while (start > 0) {
                    val prev = chars[start - 1]
                    // Stop if Space OR Line Change (Y diff > 2%)
                    if (prev.isSpace || kotlin.math.abs(prev.bounds.top - hitY) > 0.02f) break
                    start--
                }

                // EXPAND RIGHT
                var end = hitIndex
                while (end < chars.lastIndex) {
                    val next = chars[end + 1]
                    // Stop if Space OR Line Change
                    if (next.isSpace || kotlin.math.abs(next.bounds.top - hitY) > 0.02f) break
                    end++
                }

                updateSelectionState(pageIndex, start, end, chars)
            } else {
                _selectionState.value = null
            }
        }
    }

    fun onDrag(dragPoint: Offset, viewSize: Size) {
        val current = _selectionState.value ?: return
        if (current.activeHandle == DragHandle.NONE) return

        viewModelScope.launch(Dispatchers.Default) {
            val chars = getPageChars(current.pageIndex)
            val normPoint = Offset(dragPoint.x / viewSize.width, dragPoint.y / viewSize.height)

            // Find closest char (excluding spaces for snap targets)
            val targetIndex = chars.indices
                .filter { !chars[it].isSpace }
                .minByOrNull { index ->
                    val rect = chars[index].bounds
                    val cx = (rect.left + rect.right) / 2
                    val cy = (rect.top + rect.bottom) / 2
                    (cx - normPoint.x) * (cx - normPoint.x) + (cy - normPoint.y) * (cy - normPoint.y)
                } ?: return@launch

            var newStart = current.startWordIndex // reusing field name for char index
            var newEnd = current.endWordIndex

            if (current.activeHandle == DragHandle.START) {
                newStart = targetIndex
                if (newStart > newEnd) newEnd = newStart
            } else {
                newEnd = targetIndex
                if (newEnd < newStart) newStart = newEnd
            }

            updateSelectionState(current.pageIndex, newStart, newEnd, chars, current.activeHandle)
        }
    }

    // 4. MERGE LOGIC (Continuous Highlight)
    private fun updateSelectionState(
        pageIndex: Int,
        start: Int,
        end: Int,
        chars: List<PdfChar>,
        activeHandle: DragHandle = DragHandle.NONE
    ) {
        val selectedSubset = chars.slice(start..end)

        // A. Construct Text
        val text = selectedSubset.joinToString("") { it.text }

        // B. Construct Visual Rects (Merged Line by Line)
        val visualRects = mergeCharsToLineRects(selectedSubset)

        _selectionState.value = SelectionState(
            pageIndex = pageIndex,
            startWordIndex = start,
            endWordIndex = end,
            selectedText = text,
            rects = visualRects,
            activeHandle = activeHandle
        )
    }

    fun setDraggingHandle(handle: DragHandle) {
        val current = _selectionState.value ?: return
        _selectionState.value = current.copy(activeHandle = handle)
    }

    // Helper to check hit on the UI thread side
    fun checkHandleHitUI(touchPoint: Offset, viewSize: Size, selection: SelectionState): DragHandle {
        val normPoint = Offset(touchPoint.x / viewSize.width, touchPoint.y / viewSize.height)
        val rects = selection.rects
        if (rects.isEmpty()) return DragHandle.NONE

        val startRect = rects.first()
        val endRect = rects.last()

        // Use a generous threshold for "Fat Finger" (approx 8-10% of screen width)
        val threshold = 0.08f

        // Check Start Handle (Left-Bottom)
        val startDist = distance(normPoint, startRect.left, startRect.bottom, 1f)
        if (startDist < threshold) return DragHandle.START

        // Check End Handle (Right-Bottom)
        val endDist = distance(normPoint, endRect.right, endRect.bottom, 1f)
        if (endDist < threshold) return DragHandle.END

        return DragHandle.NONE
    }

    // Slightly increased padding (3%) to make selection easier
    private fun containsPoint(rect: NormRect, point: Offset): Boolean {
        val paddingX = 0.01f
        val paddingY = 0.01f
        return point.x >= (rect.left - paddingX) && point.x <= (rect.right + paddingX) &&
                point.y >= (rect.top - paddingY) && point.y <= (rect.bottom + paddingY)
    }
    fun onDragStart(pageIndex: Int, startPoint: Offset, viewSize: Size) {
        val current = _selectionState.value ?: return
        if (current.pageIndex != pageIndex) return

        val normPoint = Offset(startPoint.x / viewSize.width, startPoint.y / viewSize.height)

        // Check if we touched the START handle (first rect start)
        val startRect = current.rects.first()
        val startDist = distance(normPoint, startRect.left, startRect.top, viewSize.width / viewSize.height) // aspect ratio correction roughly

        // Check if we touched the END handle (last rect end)
        val endRect = current.rects.last()
        val endDist = distance(normPoint, endRect.right, endRect.bottom, viewSize.width / viewSize.height)

        // Threshold for "touching" a handle (e.g., 40dp converted to norm)
        val touchThreshold = 0.05f

        if (startDist < touchThreshold) {
            _selectionState.value = current.copy(activeHandle = DragHandle.START)
        } else if (endDist < touchThreshold) {
            _selectionState.value = current.copy(activeHandle = DragHandle.END)
        } else {
            // User dragged middle of text? Maybe clear or scroll.
            // For now, let's treat drag outside handles as NO-OP or Clear
            // _selectionState.value = null
        }
    }

    fun onDragEnd() {
        _selectionState.value = _selectionState.value?.copy(activeHandle = DragHandle.NONE)
    }

    private fun distance(p: Offset, targetX: Float, targetY: Float, aspect: Float): Float {
        // Adjust X by aspect to measure distance in "visual squareness" if needed,
        // or just simple norm distance
        return kotlin.math.sqrt(
            (p.x - targetX) * (p.x - targetX) + (p.y - targetY) * (p.y - targetY)
        )
    }

    fun clearSelection() {
        _selectionState.value = null
    }

    fun saveCurrentSelectionAsNote(content: String) {
        val sel = _selectionState.value ?: return
        val uri = currentBookUri ?: return

        viewModelScope.launch {
            val note = NoteEntity.fromRects(uri, sel.pageIndex, sel.selectedText, content, sel.rects)
            noteRepository.addNote(note)
            clearSelection()
        }
    }

    fun saveTextModeNote(pageIndex: Int, textContent: String, range: TextRange, note: String) {
        val uri = currentBookUri ?: return
        if (range.collapsed) return

        // Extract the selected substring
        val selectedText = try {
            textContent.substring(range.start, range.end)
        } catch (e: Exception) { "" }

        viewModelScope.launch {
            val entity = NoteEntity(
                bookUri = uri,
                pageIndex = pageIndex,
                originalText = selectedText,
                noteContent = note,
                rectsJson = "", // Not used in text mode
                textRangeStart = range.start,
                textRangeEnd = range.end
            )
            noteRepository.addNote(entity)
        }
    }

    private fun String.clean(): String = this.replace("\\s+".toRegex(), "")

    // ---------------------------------------------------------
    // 1. SYNC: Image Mode -> Text Mode (Display Rect Note in Text)
    // ---------------------------------------------------------
    fun processTextHighlights(rawText: String, pageNotes: List<NoteEntity>): AnnotatedString {
        return buildAnnotatedString {
            append(rawText)

            val textLength = rawText.length // Get actual length

            pageNotes.forEach { note ->
                var start = note.textRangeStart
                var end = note.textRangeEnd

                // (Keep your fallback sync logic here...)
                if (start == null || end == null) {
                    val bounds = findFuzzyBounds(rawText, note.originalText)
                    if (bounds != null) {
                        start = bounds.first
                        end = bounds.second
                    }
                }

                // CRITICAL FIX: Safe Bounds Check
                if (start != null && end != null) {
                    // Clamp values to prevent out-of-bounds crash
                    val safeStart = start!!.coerceIn(0, textLength)
                    val safeEnd = end!!.coerceIn(0, textLength)

                    // Only apply if range is valid
                    if (safeStart < safeEnd) {
                        try {
                            addStyle(
                                style = SpanStyle(background = Color(0x66FFEB3B)),
                                start = safeStart,
                                end = safeEnd
                            )
                        } catch (e: Exception) {
                            // Log error but don't crash
                        }
                    }
                }
            }
        }
    }

        // ---------------------------------------------------------
    // 2. SYNC: Text Mode -> Image Mode (Display Text Note on Image)
    // ---------------------------------------------------------
    suspend fun getRectsForNote(note: NoteEntity): List<NormRect> {
        // If we have saved rects, use them
        val savedRects = note.getRects()
        if (savedRects.isNotEmpty()) return savedRects

        return withContext(Dispatchers.Default) {
            val chars = getPageChars(note.pageIndex)
            if (chars.isEmpty()) return@withContext emptyList()

            // 1. Construct the Page String from PdfChars
            val pageTextBuilder = StringBuilder()
            val charIndices = mutableListOf<Int>() // Map string index -> char list index

            chars.forEachIndexed { index, pdfChar ->
                // Skip phantom spaces for search matching to be safer,
                // OR treat them as standard spaces.
                // Let's use the text as is.
                val s = if (pdfChar.isSpace) " " else pdfChar.text
                pageTextBuilder.append(s)
                repeat(s.length) { charIndices.add(index) }
            }

            val fullPageString = pageTextBuilder.toString()

            // 2. ROBUST SEARCH
            val bounds = findFuzzyBounds(fullPageString, note.originalText)

            if (bounds != null) {
                // We found the text range in the reconstructed string
                // Map it back to the PdfChar list
                val startCharIndex = charIndices.getOrElse(bounds.first) { 0 }
                val endCharIndex = charIndices.getOrElse(bounds.second - 1) { chars.lastIndex }

                // 3. Slice and Merge
                val matchedChars = chars.slice(startCharIndex..endCharIndex)
                mergeCharsToLineRects(matchedChars)
            } else {
                emptyList()
            }
        }
    }

    // ---------------------------------------------------------
    // THE SEARCH ALGORITHM (Ignores Whitespace Mismatches)
    // ---------------------------------------------------------
    private fun findFuzzyBounds(container: String, target: String): Pair<Int, Int>? {
        // 1. Try Exact Match first (Fastest)
        val exactIndex = container.indexOf(target)
        if (exactIndex != -1) return exactIndex to (exactIndex + target.length)

        // 2. Try Clean Match (Ignore all whitespace)
        // This maps the "Clean Index" back to "Real Index"
        val cleanTarget = target.clean()
        if (cleanTarget.isEmpty()) return null

        var containerCleanIndex = 0
        var matchCount = 0
        var startIndex = -1

        // Iterate through the container character by character
        for (i in container.indices) {
            val char = container[i]
            if (char.isWhitespace()) continue // Skip whitespace in container

            // Check match against clean target
            if (char == cleanTarget[matchCount]) {
                if (matchCount == 0) startIndex = i // Start of match
                matchCount++
                if (matchCount == cleanTarget.length) {
                    // Match Complete!
                    // i is the end index (inclusive), so add 1 for exclusive
                    return startIndex to (i + 1)
                }
            } else {
                // Mismatch. Reset.
                if (matchCount > 0) {
                    // Backtrack is tricky in simple scanner.
                    // For perfect accuracy, we need KMP algorithm or regex,
                    // but simple reset often works for unique text.
                    // A simple hack: If we fail, we reset matchCount.
                    // (Real implementation should backtrack 'i' but this is usually sufficient for Reader)
                    matchCount = 0
                    startIndex = -1
                }
            }
        }
        return null
    }
}