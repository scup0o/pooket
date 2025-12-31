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
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

enum class DragHandle { NONE, START, END }
private val WHITESPACE_REGEX = "\\s+".toRegex()

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val app: Application,
    private val repository: BookLocalRepository,
    private val noteRepository: NoteRepository
) : ViewModel() {
    //services & jobs
    private val _pdfRenderer = MutableStateFlow<PdfRenderer?>(null)
    private val renderMutex = Mutex()
    private val pdfBoxMutex = Mutex()
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdDocument: PDDocument? = null
    private var saveJob: Job? = null

    //ui universal state
    private var currentBookUri: String? = null
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

    // note state
    private val _notes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val notes = _notes.asStateFlow()

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
    val currentSelectionText = combine(_selectionState, _textSelection, _isTextMode) { img, txt, isText ->
        if (isText) txt?.text else img?.selectedText
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    // cache
    private val bitmapCache = object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 4).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }
    private val textCache = LruCache<Int, String>(100)
    private val pageCharsCache = LruCache<Int, List<PdfChar>>(50)

    init {
        PDFBoxResourceLoader.init(app)
        viewModelScope.launch(Dispatchers.IO){
            trimBookCache()
        }
    }

    //clean up
    override fun onCleared() {
        super.onCleared()
        cleanupResources()
    }

    private fun trimBookCache() {
        val cacheDir = app.cacheDir
        val maxCacheSize = 100 * 1024 * 1024L //100 MB limit
        val maxFileCount = 5 // max 5 recent books

        val bookFiles = cacheDir.listFiles { _, name ->
            name.startsWith("cached_book_") && name.endsWith(".pdf")
        } ?: return

        val sortedFiles = bookFiles.sortedBy { it.lastModified() }.toMutableList()

        var currentTotalSize = sortedFiles.sumOf { it.length() }

        while (sortedFiles.isNotEmpty() && (currentTotalSize > maxCacheSize || sortedFiles.size > maxFileCount)) {
            val fileToDelete = sortedFiles.removeAt(0)
            if (fileToDelete.exists()) {
                val size = fileToDelete.length()
                if (fileToDelete.delete()) {
                    currentTotalSize -= size
                    Log.d("ReaderVM", "Cleaned up old cache file: ${fileToDelete.name}")
                }
            }
        }
    }

    private fun cleanupResources() {
        CoroutineScope(Dispatchers.IO + NonCancellable).launch {
            renderMutex.withLock {
                try {
                    _pdfRenderer.value?.close()
                    _pdfRenderer.value = null
                    fileDescriptor?.close()
                    fileDescriptor = null
                    bitmapCache.evictAll()
                } catch (e: Exception) { Log.e("ReaderVM", "Renderer cleanup error", e) }
            }

            pdfBoxMutex.withLock {
                try {
                    pdDocument?.close()
                    pdDocument = null
                    textCache.evictAll()
                    pageCharsCache.evictAll()// Ensure this is the LruCache version
                } catch (e: Exception) { Log.e("ReaderVM", "PDFBox cleanup error", e) }
            }
        }
    }

    //load + render pdf
    private fun getTempFile(uri: String): File {
        val filename = "cached_book_${uri.hashCode()}.pdf"
        return File(app.cacheDir, filename)
    }

    private fun ensurePdDocumentLoaded(): PDDocument? {
        if (pdDocument == null && currentBookUri != null) {
            try {
                val tempFile = getTempFile(currentBookUri!!)

                if (!tempFile.exists()) {
                    val uri = currentBookUri!!.toUri()
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        val tmpWriteFile = File(tempFile.parent, "${tempFile.name}.tmp")
                        tmpWriteFile.outputStream().use { output -> input.copyTo(output) }
                        tmpWriteFile.renameTo(tempFile)
                    }
                } else {
                    tempFile.setLastModified(System.currentTimeMillis())
                }

                pdDocument = PDDocument.load(tempFile, MemoryUsageSetting.setupTempFileOnly())
            } catch (e: Exception) {
                Log.e("ReaderVM", "Failed to load PDDocument", e)
            }
        }
        return pdDocument
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

    suspend fun renderPage(index: Int): Bitmap? = withContext(Dispatchers.Default) {
        val renderer = _pdfRenderer.value ?: return@withContext null
        bitmapCache.get(index)?.let { return@withContext it }

        renderMutex.withLock {
            bitmapCache.get(index)?.let { return@withLock it }

            var page: PdfRenderer.Page? = null
            try {
                page = renderer.openPage(index)

                val displayMetrics = app.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
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

        pdfBoxMutex.withLock {
            yield()
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

    //ui control
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
            delay(1000)
            repository.saveProgress(uri, pageIndex)
        }
    }

    fun setFontSize(size: Float) { _fontSize.value = size }

    // note
    fun loadNotes(uri: String) {
        viewModelScope.launch {
            noteRepository.getNotes(uri).collect { _notes.value = it }
        }
    }

    fun saveNote(noteContent: String) {
        val uri = currentBookUri ?: return
        viewModelScope.launch {
            val noteEntity = if (_isTextMode.value) {
                val sel = _textSelection.value
                if (sel != null && !sel.range.collapsed) {
                    val rects = findRectsForText(sel.pageIndex, sel.text)
                    NoteEntity(
                        bookUri = uri,
                        pageIndex = sel.pageIndex,
                        originalText = sel.text,
                        noteContent = noteContent,
                        rects = rects,
                        textRangeStart = sel.range.start,
                        textRangeEnd = sel.range.end
                    )
                } else null
            } else {
                val sel = _selectionState.value
                if (sel != null) {
                    NoteEntity(
                        bookUri = uri,
                        pageIndex = sel.pageIndex,
                        originalText = sel.selectedText,
                        noteContent = noteContent,
                        rects = sel.rects
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
        pageCharsCache.get(pageIndex)?.let { return@withContext it }
        pdfBoxMutex.withLock {
            pageCharsCache.get(pageIndex)?.let { return@withLock it }

            try {
                ensurePdDocumentLoaded()
                val doc = pdDocument ?: return@withLock emptyList()
                if (pageIndex < 0 || pageIndex >= doc.numberOfPages) return@withLock emptyList()
                val page = doc.getPage(pageIndex)

                val stripper = CoordinateTextStripper(page)
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1

                // dummy write to trigger parsing
                stripper.writeText(doc, OutputStreamWriter(ByteArrayOutputStream()))

                val chars = stripper.chars
                pageCharsCache.put(pageIndex, chars)

                chars
            } catch (e: Exception) {
                Log.e("ReaderVM", "Error parsing chars for page $pageIndex", e)
                emptyList()
            }
        }
    }

    suspend fun getRectsForNote(note: NoteEntity): List<NormRect> {
        if (note.rects.isNotEmpty()) return note.rects
        return findRectsForText(note.pageIndex, note.originalText)
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

            var charInternalIndex = 0
            var matchedSoFar = 0

            while (charInternalIndex < pdfChar.text.length && matchIndex < cleanTarget.length) {
                if (pdfChar.text[charInternalIndex].equals(cleanTarget[matchIndex], ignoreCase = true)) {
                    if (matchIndex == 0) startCharIndex = i
                    matchIndex++
                    charInternalIndex++
                    matchedSoFar++
                } else {
                    break
                }
            }

            if (matchedSoFar == pdfChar.text.length) {
                if (matchIndex == cleanTarget.length) {
                    val matchedChars = chars.slice(startCharIndex..i)
                    return@withContext mergeCharsToLineRects(matchedChars)
                }
            } else {
                if (matchIndex > 0) {
                    matchIndex = 0
                    startCharIndex = -1
                }
            }
        }
        emptyList()
    }


    // ----------------------------------------------------------------------
    // GESTURE & SELECTION LOGIC
    // ----------------------------------------------------------------------

    fun onLongPress(pageIndex: Int, touchPoint: Offset, viewSize: Size) {
        viewModelScope.launch(Dispatchers.Default) {
            val chars = getPageChars(pageIndex)
            val normPoint = Offset(touchPoint.x / viewSize.width, touchPoint.y / viewSize.height)

            val hitIndex = chars.indexOfFirst { !it.isSpace && containsPoint(it.bounds, normPoint) }

            if (hitIndex != -1) {
                val hitY = chars[hitIndex].bounds.top
                var start = hitIndex
                var end = hitIndex

                while (start > 0) {
                    val prev = chars[start - 1]
                    if (prev.isSpace || abs(prev.bounds.top - hitY) > 0.02f) break
                    start--
                }
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

    fun setDraggingHandle(handle: DragHandle) {
        _selectionState.value = _selectionState.value?.copy(activeHandle = handle)
    }

    fun checkHandleHitUI(touchPoint: Offset, viewSize: Size, selection: SelectionState): DragHandle {
        val normPoint = Offset(touchPoint.x / viewSize.width, touchPoint.y / viewSize.height)
        val rects = selection.rects
        if (rects.isEmpty()) return DragHandle.NONE

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

    fun onDragEnd() { setDraggingHandle(DragHandle.NONE) }


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

                if (start == null || end == null) {
                    val bounds = findFuzzyBounds(rawText, note.originalText)
                    if (bounds != null) {
                        start = bounds.first
                        end = bounds.second
                    }
                }

                if (start != null && end != null) {
                    val safeStart = start.coerceIn(0, textLength)
                    val safeEnd = end.coerceIn(0, textLength)
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
        val pad = 0.01f
        return point.x >= (rect.left - pad) && point.x <= (rect.right + pad) &&
                point.y >= (rect.top - pad) && point.y <= (rect.bottom + pad)
    }
}