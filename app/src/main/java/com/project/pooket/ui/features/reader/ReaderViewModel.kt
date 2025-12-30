package com.project.pooket.ui.features.reader

import android.app.Application
import android.graphics.Bitmap
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
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.pooket.core.utils.CoordinateTextStripper
import com.project.pooket.core.utils.PdfChar
import com.project.pooket.data.local.book.BookLocalRepository
import com.project.pooket.data.local.note.NormRect
import com.project.pooket.data.local.note.NoteEntity
import com.project.pooket.data.local.note.NoteRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

enum class DragHandle { NONE, START, END }

// Pre-compile Regex for performance (used frequently in search/highlight)
private val WHITESPACE_REGEX = "\\s+".toRegex()

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val app: Application,
    private val repository: BookLocalRepository,
    private val noteRepository: NoteRepository
) : ViewModel() {

    // --- State Flows ---
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

    private val _notes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val notes = _notes.asStateFlow()

    // --- Caches & Resources ---
    // Cache ~1/4th of available memory for Bitmaps
    private val bitmapCache = object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 4).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }
    // Lightweight cache for raw text and coordinate-mapped characters
    private val textCache = LruCache<Int, String>(100)
    private val pageCharsCache = mutableMapOf<Int, List<PdfChar>>()

    private val rendererMutex = Mutex()
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdDocument: PDDocument? = null // Heavy object, lazy loaded
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
                val fd = app.contentResolver.openFileDescriptor(uri, "r")
                fileDescriptor = fd

                if (fd != null) {
                    val renderer = PdfRenderer(fd)
                    _pdfRenderer.value = renderer
                    _pageCount.value = renderer.pageCount

                    if ((book?.totalPages ?: 0) == 0 && renderer.pageCount > 0) {
                        repository.initTotalPages(uriString, renderer.pageCount)
                    }
                }
                loadNotes(uriString)
            } catch (e: Exception) {
                Log.e("ReaderVM", "Failed to load PDF", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun cleanupResources() {
        // Launch on IO + NonCancellable to ensure cleanup finishes even if VM dies
        CoroutineScope(Dispatchers.IO + NonCancellable).launch {
            rendererMutex.withLock {
                try {
                    pdDocument?.close()
                    pdDocument = null
                    _pdfRenderer.value?.close()
                    _pdfRenderer.value = null
                    fileDescriptor?.close()
                    fileDescriptor = null
                    bitmapCache.evictAll()
                    textCache.evictAll()
                    pageCharsCache.clear()
                } catch (e: Exception) {
                    Log.e("ReaderVM", "Cleanup error", e)
                }
            }
        }
    }

    // ----------------------------------------------------------------------
    // RENDERING & TEXT EXTRACTION
    // ----------------------------------------------------------------------

    suspend fun renderPage(index: Int): Bitmap? = withContext(Dispatchers.Default) {
        val renderer = _pdfRenderer.value ?: return@withContext null
        bitmapCache.get(index)?.let { return@withContext it }

        rendererMutex.withLock {
            // Double check cache after acquiring lock
            bitmapCache.get(index)?.let { return@withLock it }

            var page: PdfRenderer.Page? = null
            try {
                page = renderer.openPage(index)

                // Calculate optimal bitmap size based on screen density
                val displayMetrics = app.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                // Cap density multiplier to avoid OOM on very high res screens
                val densityMult = if (displayMetrics.density > 2) 1.5f else 2.0f
                val targetWidth = (screenWidth * densityMult).toInt()
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
                page?.close()
            }
        }
    }

    suspend fun extractText(index: Int): String = withContext(Dispatchers.IO) {
        textCache.get(index)?.let { return@withContext it }

        rendererMutex.withLock {
            textCache.get(index)?.let { return@withLock it }
            ensurePdDocumentLoaded() ?: return@withLock ""

            try {
                val doc = pdDocument ?: return@withLock ""
                val stripper = PDFTextStripper().apply {
                    startPage = index + 1
                    endPage = index + 1
                }
                val text = stripper.getText(doc).trim()
                if (text.isNotEmpty()) textCache.put(index, text)
                text
            } catch (e: Exception) {
                Log.e("ReaderVM", "Text extraction failed", e)
                ""
            }
        }
    }

    // Helper to lazy load the heavy PDFBox document safely
    private fun ensurePdDocumentLoaded(): PDDocument? {
        if (pdDocument == null && currentBookUri != null) {
            try {
                val uri = currentBookUri!!.toUri()
                app.contentResolver.openInputStream(uri)?.use { stream ->
                    pdDocument = PDDocument.load(stream)
                }
            } catch (e: Exception) {
                Log.e("ReaderVM", "Failed to load PDDocument", e)
            }
        }
        return pdDocument
    }

    // ----------------------------------------------------------------------
    // UI CONTROLS & SELECTION STATE
    // ----------------------------------------------------------------------

    fun toggleReadingMode() {
        _isVerticalScrollMode.value = !_isVerticalScrollMode.value
        clearAllSelection()
    }

    fun toggleTextMode() {
        _isTextMode.value = !_isTextMode.value
        clearAllSelection()
    }

    fun onPageChanged(pageIndex: Int) {
        clearAllSelection()
        val uri = currentBookUri ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            repository.saveProgress(uri, pageIndex)
        }
    }

    fun setFontSize(size: Float) { _fontSize.value = size }

    override fun onCleared() {
        super.onCleared()
        cleanupResources()
    }

    // --- Selection States ---
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

    data class TextSelectionState(
        val pageIndex: Int,
        val text: String,
        val range: TextRange
    )
    private val _textSelection = MutableStateFlow<TextSelectionState?>(null)
    val textSelection = _textSelection.asStateFlow()

    // Unified selection text for UI consumption
    val currentSelectionText = combine(_selectionState, _textSelection, _isTextMode) { img, txt, isText ->
        if (isText) txt?.text else img?.selectedText
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)


    // ----------------------------------------------------------------------
    // NOTE LOGIC
    // ----------------------------------------------------------------------

    fun loadNotes(uri: String) {
        viewModelScope.launch {
            noteRepository.getNotes(uri).collect { _notes.value = it }
        }
    }

    fun saveNote(noteContent: String) {
        val uri = currentBookUri ?: return
        viewModelScope.launch {
            val noteEntity = if (_isTextMode.value) {
                // TEXT MODE: Calculate rects from text for syncing
                val sel = _textSelection.value
                if (sel != null && !sel.range.collapsed) {
                    val rects = findRectsForText(sel.pageIndex, sel.text)
                    NoteEntity(
                        bookUri = uri,
                        pageIndex = sel.pageIndex,
                        originalText = sel.text,
                        noteContent = noteContent,
                        rects = rects, // List passed directly
                        textRangeStart = sel.range.start,
                        textRangeEnd = sel.range.end
                    )
                } else null
            } else {
                // IMAGE MODE: Use existing selection rects
                val sel = _selectionState.value
                if (sel != null) {
                    NoteEntity(
                        bookUri = uri,
                        pageIndex = sel.pageIndex,
                        originalText = sel.selectedText,
                        noteContent = noteContent,
                        rects = sel.rects // List passed directly
                    )
                } else null
            }

            if (noteEntity != null) {
                noteRepository.addNote(noteEntity)
                clearAllSelection()
            }
        }
    }

    // ----------------------------------------------------------------------
    // COORDINATE MAPPING & SEARCH ALGORITHMS
    // ----------------------------------------------------------------------

    suspend fun getPageChars(pageIndex: Int): List<PdfChar> = withContext(Dispatchers.IO) {
        // Fast return from cache
        if (pageCharsCache.containsKey(pageIndex)) return@withContext pageCharsCache[pageIndex]!!

        rendererMutex.withLock {
            // Check cache again inside lock
            if (pageCharsCache.containsKey(pageIndex)) return@withLock pageCharsCache[pageIndex]!!

            try {
                ensurePdDocumentLoaded()
                val doc = pdDocument ?: return@withLock emptyList()
                val page = doc.getPage(pageIndex)

                // Optimized Stripper
                val stripper = CoordinateTextStripper(page)
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1

                // Write to dummy stream to trigger parsing
                stripper.writeText(doc, OutputStreamWriter(ByteArrayOutputStream()))

                pageCharsCache[pageIndex] = stripper.chars
                stripper.chars
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun findRectsForText(pageIndex: Int, text: String): List<NormRect> = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext emptyList()
        val chars = getPageChars(pageIndex)
        if (chars.isEmpty()) return@withContext emptyList()

        val cleanTarget = text.replace(WHITESPACE_REGEX, "")
        if (cleanTarget.isEmpty()) return@withContext emptyList()

        var matchIndex = 0
        var startCharIndex = -1

        for (i in chars.indices) {
            val pdfChar = chars[i]
            if (pdfChar.isSpace || pdfChar.text.isBlank()) continue

            // FIX: Robust matching for ligatures/clusters (e.g. "fi" vs "f")
            // Iterate through characters inside the PdfChar string
            var charInternalIndex = 0
            var matchedSoFar = 0

            // Check if this PdfChar matches the *current* expectation in target
            while (charInternalIndex < pdfChar.text.length && matchIndex < cleanTarget.length) {
                if (pdfChar.text[charInternalIndex].equals(cleanTarget[matchIndex], ignoreCase = true)) {
                    // Match found for this char!
                    if (matchIndex == 0) startCharIndex = i
                    matchIndex++
                    charInternalIndex++
                    matchedSoFar++
                } else {
                    // Mismatch within this PdfChar
                    break
                }
            }

            if (matchedSoFar == pdfChar.text.length) {
                // We consumed the whole PdfChar successfully
                if (matchIndex == cleanTarget.length) {
                    val matchedChars = chars.slice(startCharIndex..i)
                    return@withContext mergeCharsToLineRects(matchedChars)
                }
            } else {
                // Partial match failed or mismatch
                if (matchIndex > 0) {
                    // Reset and try to restart match from current index?
                    // Simple reset logic:
                    matchIndex = 0
                    startCharIndex = -1
                }
            }
        }
        emptyList()
    }

    // Allows NoteItem to lazy-load rects if they were somehow missing (fallback),
    // otherwise returns the stored list.
    suspend fun getRectsForNote(note: NoteEntity): List<NormRect> {
        if (note.rects.isNotEmpty()) return note.rects
        return findRectsForText(note.pageIndex, note.originalText)
    }

    // ----------------------------------------------------------------------
    // GESTURE & SELECTION LOGIC
    // ----------------------------------------------------------------------

    fun onLongPress(pageIndex: Int, touchPoint: Offset, viewSize: Size) {
        viewModelScope.launch(Dispatchers.Default) {
            val chars = getPageChars(pageIndex)
            val normPoint = Offset(touchPoint.x / viewSize.width, touchPoint.y / viewSize.height)

            // Find char under finger
            val hitIndex = chars.indexOfFirst { !it.isSpace && containsPoint(it.bounds, normPoint) }

            if (hitIndex != -1) {
                // Intelligent Word Selection (Expand to word boundaries)
                val hitY = chars[hitIndex].bounds.top
                var start = hitIndex
                var end = hitIndex

                // Expand Left
                while (start > 0) {
                    val prev = chars[start - 1]
                    // Stop at space or line break (Y diff > 2%)
                    if (prev.isSpace || abs(prev.bounds.top - hitY) > 0.02f) break
                    start--
                }
                // Expand Right
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

            // Find closest non-space character to the drag point
            val targetIndex = chars.indices
                .filter { !chars[it].isSpace }
                .minByOrNull { index ->
                    val rect = chars[index].bounds
                    val cx = (rect.left + rect.right) / 2
                    val cy = (rect.top + rect.bottom) / 2
                    // Euclidean distance squared is sufficient for sorting
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

    fun setDraggingHandle(handle: DragHandle) {
        _selectionState.value = _selectionState.value?.copy(activeHandle = handle)
    }

    fun checkHandleHitUI(touchPoint: Offset, viewSize: Size, selection: SelectionState): DragHandle {
        val normPoint = Offset(touchPoint.x / viewSize.width, touchPoint.y / viewSize.height)
        val rects = selection.rects
        if (rects.isEmpty()) return DragHandle.NONE

        // Hit detection threshold (approx 8% of screen dimension)
        val threshold = 0.08f

        if (distance(normPoint, rects.first().left, rects.first().bottom) < threshold) return DragHandle.START
        if (distance(normPoint, rects.last().right, rects.last().bottom) < threshold) return DragHandle.END

        return DragHandle.NONE
    }

    fun clearAllSelection() {
        _selectionState.value = null
        _textSelection.value = null
    }

    fun setTextSelection(page: Int, text: String, range: TextRange) {
        _textSelection.value = TextSelectionState(page, text, range)
    }

    fun clearSelection() { _selectionState.value = null }
    fun onDragEnd() { setDraggingHandle(DragHandle.NONE) }
    fun onDragStart(pageIndex: Int, startPoint: Offset, viewSize: Size) { /* Logic handled in UI via checkHandleHitUI for better response */ }


    // ----------------------------------------------------------------------
    // TEXT HIGHLIGHT SYNC LOGIC
    // ----------------------------------------------------------------------

    suspend fun processTextHighlights(rawText: String, pageNotes: List<NoteEntity>): AnnotatedString = withContext(Dispatchers.Default) {
        buildAnnotatedString {
            append(rawText)
            val textLength = rawText.length
            pageNotes.forEach { note ->
                var start = note.textRangeStart
                var end = note.textRangeEnd

                // Fallback for older notes or notes made in Image mode
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
                        addStyle(SpanStyle(background = Color(0x66FFEB3B)), safeStart, safeEnd)
                    }
                }
            }
        }
    }

    private fun findFuzzyBounds(container: String, target: String): Pair<Int, Int>? {
        val exactIndex = container.indexOf(target)
        if (exactIndex != -1) return exactIndex to (exactIndex + target.length)

        val cleanTarget = target.replace(WHITESPACE_REGEX, "")
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

    // ----------------------------------------------------------------------
    // UTILITIES
    // ----------------------------------------------------------------------

    private fun mergeCharsToLineRects(chars: List<PdfChar>): List<NormRect> {
        if (chars.isEmpty()) return emptyList()
        val visibleChars = chars.filter { !it.isSpace }
        if (visibleChars.isEmpty()) return emptyList()

        val sorted = visibleChars.sortedWith { a, b ->
            if (abs(a.bounds.top - b.bounds.top) < 0.01f) a.bounds.left.compareTo(b.bounds.left)
            else a.bounds.top.compareTo(b.bounds.top)
        }

        val merged = mutableListOf<NormRect>()
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

    private fun distance(p: Offset, x: Float, y: Float): Float {
        return sqrt((p.x - x) * (p.x - x) + (p.y - y) * (p.y - y))
    }

    private fun containsPoint(rect: NormRect, point: Offset): Boolean {
        // 1% padding for easier touch
        val pad = 0.01f
        return point.x >= (rect.left - pad) && point.x <= (rect.right + pad) &&
                point.y >= (rect.top - pad) && point.y <= (rect.bottom + pad)
    }
}