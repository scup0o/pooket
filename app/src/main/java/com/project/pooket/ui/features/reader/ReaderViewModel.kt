package com.project.pooket.ui.features.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
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
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val app: Application,
    private val repository: BookLocalRepository
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
}