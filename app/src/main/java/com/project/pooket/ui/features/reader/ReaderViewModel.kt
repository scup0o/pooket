package com.project.pooket.ui.features.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
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

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val app: Application,
    private val repository: BookLocalRepository
) : ViewModel() {

    private val _pdfRenderer = MutableStateFlow<PdfRenderer?>(null)
    val pdfRenderer = _pdfRenderer.asStateFlow()

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

    private val bitmapCache = LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt())
    private val textCache = LruCache<Int, String>(100)

    private val rendererMutex = Mutex()
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var currentBookUri: String? = null
    private var saveJob: Job? = null

    init {
        // Correct initialization for Emulator/Android environment
        PDFBoxResourceLoader.init(app)
    }

    fun loadPdf(uriString: String) {
        if (currentBookUri == uriString && _pdfRenderer.value != null) {
            _isLoading.value = false
            return
        }

        currentBookUri = uriString
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Load book data from DB first
                val book = repository.getBook(uriString)
                _initialPage.value = book?.lastPage ?: 0

                // 2. Open File Descriptor
                val uri = Uri.parse(uriString)
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
                e.printStackTrace()
            } finally {
                // Ensure loading is set to false even if load fails
                _isLoading.value = false
            }
        }
    }

    suspend fun renderPage(index: Int): Bitmap? = withContext(Dispatchers.Default) {
        val renderer = _pdfRenderer.value ?: return@withContext null
        bitmapCache.get(index)?.let { return@withContext it }

        rendererMutex.withLock {
            bitmapCache.get(index)?.let { return@withLock it }
            try {
                val page = renderer.openPage(index)
                // Emulator usually has more RAM, so 1.8f is safe for high resolution
                val scale = 1.8f
                val screenWidth = app.resources.displayMetrics.widthPixels
                val bWidth = (screenWidth * scale).toInt()
                val bHeight = (bWidth.toFloat() / page.width * page.height).toInt()

                val bitmap = Bitmap.createBitmap(bWidth, bHeight, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmapCache.put(index, bitmap)
                bitmap
            } catch (e: Exception) { null }
        }
    }

    suspend fun extractText(index: Int): String = withContext(Dispatchers.IO) {
        textCache.get(index)?.let { return@withContext it }
        try {
            val uri = Uri.parse(currentBookUri ?: return@withContext "")
            app.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { doc ->
                    val stripper = PDFTextStripper().apply {
                        startPage = index + 1
                        endPage = index + 1
                    }
                    val text = stripper.getText(doc).trim()
                    if (text.isNotEmpty()) textCache.put(index, text)
                    text
                }
            } ?: ""
        } catch (e: Exception) { "" }
    }

    fun toggleReadingMode() { _isVerticalScrollMode.value = !_isVerticalScrollMode.value }
    fun toggleTextMode() { _isTextMode.value = !_isTextMode.value }
    fun setFontSize(size: Float) { _fontSize.value = size }

    fun onPageChanged(pageIndex: Int) {
        val uri = currentBookUri ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            repository.saveProgress(uri, pageIndex, _pageCount.value)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            _pdfRenderer.value?.close()
            fileDescriptor?.close()
            bitmapCache.evictAll()
            textCache.evictAll()
        } catch (e: Exception) { }
    }
}