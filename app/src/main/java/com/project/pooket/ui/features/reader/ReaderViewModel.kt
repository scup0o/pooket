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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import kotlin.math.abs
import kotlin.math.sqrt

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
        clearAllSelection() // FIX: Clear when changing scroll mode
    }

    fun toggleTextMode() {
        _isTextMode.value = !_isTextMode.value
        clearAllSelection() // FIX: Clear when switching Image/Text
    }

    fun onPageChanged(pageIndex: Int) {
        // FIX: Clear selection when changing pages so toolbar doesn't persist
        // (Optional: if you want selection to persist across page swipe, remove this)
        clearAllSelection()

        val uri = currentBookUri ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            repository.saveProgress(uri, pageIndex)
        }
    }

    fun setFontSize(size: Float) {
        _fontSize.value = size
    }

    override fun onCleared() {
        super.onCleared()
        cleanupResources()
    }


    private val _notes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val notes = _notes.asStateFlow()

    private val pageCharsCache = mutableMapOf<Int, List<PdfChar>>()

    // --- Image Mode Selection State ---
    data class SelectionState(
        val pageIndex: Int,
        val startWordIndex: Int,
        val endWordIndex: Int,
        val selectedText: String,
        val rects: List<NormRect>,
        val activeHandle: DragHandle = DragHandle.NONE
    )
    private val _selectionState = MutableStateFlow<SelectionState?>(null)
    val selectionState = _selectionState.asStateFlow()

    // --- Text Mode Selection State ---
    data class TextSelectionState(
        val pageIndex: Int,
        val text: String,
        val range: TextRange
    )
    private val _textSelection = MutableStateFlow<TextSelectionState?>(null)
    val textSelection = _textSelection.asStateFlow()


    fun loadNotes(uri: String) {
        viewModelScope.launch {
            noteRepository.getNotes(uri).collect {
                _notes.value = it
            }
        }
    }

    // ----------------------------------------------------------------------
    // 1. UNIFIED SAVE FUNCTION
    // ----------------------------------------------------------------------
    fun saveNote(noteContent: String) {
        val uri = currentBookUri ?: return

        viewModelScope.launch {
            var noteEntity: NoteEntity? = null

            if (_isTextMode.value) {
                // TEXT MODE LOGIC
                // We map the selected text -> Image Mode Rects
                val sel = _textSelection.value
                if (sel != null && !sel.range.collapsed) {

                    // A. Use Image Mode logic to find visual coordinates for this text
                    val rects = findRectsForText(sel.pageIndex, sel.text)

                    // B. Save with BOTH:
                    //    1. Rects (for Image Mode display)
                    //    2. TextRange (for fast Text Mode display)
                    noteEntity = NoteEntity.fromRects(
                        bookUri = uri,
                        rects = rects,
                        page = sel.pageIndex,
                        text = sel.text,
                        note = noteContent
                    ).copy(
                        textRangeStart = sel.range.start,
                        textRangeEnd = sel.range.end
                    )
                }
            } else {
                // IMAGE MODE LOGIC
                // We already have the rects in the selection state
                val sel = _selectionState.value
                if (sel != null) {
                    noteEntity = NoteEntity.fromRects(
                        bookUri = uri,
                        page = sel.pageIndex,
                        text = sel.selectedText,
                        note= noteContent,
                        rects = sel.rects
                    )
                }
            }

            if (noteEntity != null) {
                noteRepository.addNote(noteEntity)
                clearAllSelection()
            }
        }
    }


    // ----------------------------------------------------------------------
    // 2. CORE COORDINATE LOGIC (Reusable for Syncing)
    // ----------------------------------------------------------------------

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

    /**
     * UNIFIED SEARCH: Takes any text string and finds the Rects in the PDF.
     * Used by:
     * 1. Displaying notes in Image Mode
     * 2. Converting Text Mode Selection -> Image Mode Note
     */
    suspend fun findRectsForText(pageIndex: Int, text: String): List<NormRect> = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext emptyList()

        val chars = getPageChars(pageIndex)
        if (chars.isEmpty()) return@withContext emptyList()

        // Remove whitespaces to find ink-to-ink match
        val cleanTarget = text.replace("\\s+".toRegex(), "")
        if (cleanTarget.isEmpty()) return@withContext emptyList()

        var matchIndex = 0
        var startCharIndex = -1

        for (i in chars.indices) {
            val pdfChar = chars[i]
            if (pdfChar.isSpace || pdfChar.text.isBlank()) continue

            if (pdfChar.text.equals(cleanTarget[matchIndex].toString(), ignoreCase = true)) {
                if (matchIndex == 0) startCharIndex = i
                matchIndex++

                if (matchIndex == cleanTarget.length) {
                    // Match found: Merge chars into lines
                    val matchedChars = chars.slice(startCharIndex..i)
                    return@withContext mergeCharsToLineRects(matchedChars)
                }
            } else {
                if (matchIndex > 0) {
                    // Reset on mismatch
                    matchIndex = 0
                    startCharIndex = -1
                }
            }
        }
        emptyList()
    }

    // Helper to allow NoteEntity to call the search without duplicating logic
    suspend fun getRectsForNote(note: NoteEntity): List<NormRect> {
        val savedRects = note.getRects()
        if (savedRects.isNotEmpty()) return savedRects // Use cached if available
        return findRectsForText(note.pageIndex, note.originalText)
    }

    // ----------------------------------------------------------------------
    // UI HELPERS (Image Mode)
    // ----------------------------------------------------------------------

    fun onLongPress(pageIndex: Int, touchPoint: Offset, viewSize: Size) {
        viewModelScope.launch(Dispatchers.Default) {
            val chars = getPageChars(pageIndex)
            val normPoint = Offset(touchPoint.x / viewSize.width, touchPoint.y / viewSize.height)
            val hitIndex = chars.indexOfFirst { !it.isSpace && containsPoint(it.bounds, normPoint) }

            if (hitIndex != -1) {
                val hitY = chars[hitIndex].bounds.top
                var start = hitIndex
                while (start > 0) {
                    val prev = chars[start - 1]
                    if (prev.isSpace || abs(prev.bounds.top - hitY) > 0.02f) break
                    start--
                }
                var end = hitIndex
                while (end < chars.lastIndex) {
                    val next = chars[end + 1]
                    if (next.isSpace || abs(next.bounds.top - hitY) > 0.02f) break
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

            val targetIndex = chars.indices
                .filter { !chars[it].isSpace }
                .minByOrNull { index ->
                    val rect = chars[index].bounds
                    val cx = (rect.left + rect.right) / 2
                    val cy = (rect.top + rect.bottom) / 2
                    (cx - normPoint.x) * (cx - normPoint.x) + (cy - normPoint.y) * (cy - normPoint.y)
                } ?: return@launch

            var newStart = current.startWordIndex
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

    private fun updateSelectionState(
        pageIndex: Int,
        start: Int,
        end: Int,
        chars: List<PdfChar>,
        activeHandle: DragHandle = DragHandle.NONE
    ) {
        val selectedSubset = chars.slice(start..end)
        val text = selectedSubset.joinToString("") { it.text }
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

    // ... (Helper methods for UI: isTouchingHandle, checkHandleHitUI, onDragStart, onDragEnd, distance, etc - No logic change, just kept for UI functionality) ...

    fun onDragStart(pageIndex: Int, startPoint: Offset, viewSize: Size) {
        val current = _selectionState.value ?: return
        if (current.pageIndex != pageIndex) return
        val normPoint = Offset(startPoint.x / viewSize.width, startPoint.y / viewSize.height)
        val startRect = current.rects.first()
        val startDist = distance(normPoint, startRect.left, startRect.top, 1f)
        val endRect = current.rects.last()
        val endDist = distance(normPoint, endRect.right, endRect.bottom, 1f)
        val touchThreshold = 0.05f

        if (startDist < touchThreshold) _selectionState.value = current.copy(activeHandle = DragHandle.START)
        else if (endDist < touchThreshold) _selectionState.value = current.copy(activeHandle = DragHandle.END)
    }

    fun onDragEnd() { _selectionState.value = _selectionState.value?.copy(activeHandle = DragHandle.NONE) }
    private fun distance(p: Offset, targetX: Float, targetY: Float, aspect: Float): Float {
        return sqrt((p.x - targetX) * (p.x - targetX) + (p.y - targetY) * (p.y - targetY))
    }
    private fun containsPoint(rect: NormRect, point: Offset): Boolean {
        val pad = 0.01f
        return point.x >= (rect.left - pad) && point.x <= (rect.right + pad) &&
                point.y >= (rect.top - pad) && point.y <= (rect.bottom + pad)
    }

    private fun mergeCharsToLineRects(chars: List<PdfChar>): List<NormRect> {
        val merged = mutableListOf<NormRect>()
        if (chars.isEmpty()) return merged
        val visibleChars = chars.filter { !it.isSpace }
        if (visibleChars.isEmpty()) return merged

        val sorted = visibleChars.sortedWith { a, b ->
            if (abs(a.bounds.top - b.bounds.top) < 0.01f) a.bounds.left.compareTo(b.bounds.left)
            else a.bounds.top.compareTo(b.bounds.top)
        }

        var currentLeft = sorted[0].bounds.left
        var currentRight = sorted[0].bounds.right
        var currentTop = sorted[0].bounds.top
        var currentBottom = sorted[0].bounds.bottom
        var currentLineY = (currentTop + currentBottom) / 2

        for (i in 1 until sorted.size) {
            val charBounds = sorted[i].bounds
            val charCenterY = (charBounds.top + charBounds.bottom) / 2
            val isNewLine = abs(charCenterY - currentLineY) > 0.015f
            val isHugeGap = abs(charBounds.left - currentRight) > 0.8f

            if (!isNewLine && !isHugeGap) {
                currentRight = maxOf(currentRight, charBounds.right)
                currentTop = minOf(currentTop, charBounds.top)
                currentBottom = maxOf(currentBottom, charBounds.bottom)
            } else {
                merged.add(NormRect(currentLeft, currentTop, currentRight, currentBottom))
                currentLeft = charBounds.left
                currentRight = charBounds.right
                currentTop = charBounds.top
                currentBottom = charBounds.bottom
                currentLineY = (currentTop + currentBottom) / 2
            }
        }
        merged.add(NormRect(currentLeft, currentTop, currentRight, currentBottom))
        return merged
    }

    // ----------------------------------------------------------------------
    // UI HELPERS (Text Mode)
    // ----------------------------------------------------------------------

    fun setTextSelection(page: Int, text: String, range: TextRange) {
        _textSelection.value = TextSelectionState(page, text, range)
    }

    fun processTextHighlights(rawText: String, pageNotes: List<NoteEntity>): AnnotatedString {
        return buildAnnotatedString {
            append(rawText)
            val textLength = rawText.length
            pageNotes.forEach { note ->
                var start = note.textRangeStart
                var end = note.textRangeEnd

                // If saved via Image Mode (no text indices), find them dynamically
                if (start == null || end == null) {
                    val bounds = findFuzzyBounds(rawText, note.originalText)
                    if (bounds != null) {
                        start = bounds.first
                        end = bounds.second
                    }
                }

                if (start != null && end != null) {
                    val safeStart = start!!.coerceIn(0, textLength)
                    val safeEnd = end!!.coerceIn(0, textLength)
                    if (safeStart < safeEnd) {
                        try {
                            addStyle(SpanStyle(background = Color(0x66FFEB3B)), safeStart, safeEnd)
                        } catch (e: Exception) { }
                    }
                }
            }
        }
    }

    private fun findFuzzyBounds(container: String, target: String): Pair<Int, Int>? {
        val exactIndex = container.indexOf(target)
        if (exactIndex != -1) return exactIndex to (exactIndex + target.length)

        val cleanTarget = target.replace("\\s+".toRegex(), "")
        if (cleanTarget.isEmpty()) return null

        var containerCleanIndex = 0
        var matchCount = 0
        var startIndex = -1

        for (i in container.indices) {
            val char = container[i]
            if (char.isWhitespace()) continue
            if (char == cleanTarget[matchCount]) {
                if (matchCount == 0) startIndex = i
                matchCount++
                if (matchCount == cleanTarget.length) return startIndex to (i + 1)
            } else {
                if (matchCount > 0) {
                    matchCount = 0
                    startIndex = -1
                }
            }
        }
        return null
    }

    fun clearAllSelection() {
        _selectionState.value = null
        _textSelection.value = null
    }

    // Unified selection text for UI consumption (Toolbars, etc)
    val currentSelectionText = combine(
        _selectionState,
        _textSelection,
        _isTextMode
    ) { img, txt, isText ->
        if (isText) txt?.text else img?.selectedText
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)



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

    fun clearSelection() {
        _selectionState.value = null
    }
}