package com.project.pooket.data.local.book

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.project.pooket.core.utils.BookCoverExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

@Singleton
class BookLocalRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val coverExtractor: BookCoverExtractor,
    private val libraryPrefs: LibraryPreferences
) {

    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()
    val recentBook: Flow<BookEntity?> = bookDao.getRecentBook()

    suspend fun refreshAllLibrary() = withContext(Dispatchers.IO) {
        val folders = libraryPrefs.scannedFolders.first()

        folders.forEach { uriString ->
            try {
                val uri = uriString.toUri()
                val hasPermission = context.contentResolver.persistedUriPermissions.any {
                    it.uri == uri && it.isReadPermission
                }

                if (hasPermission) {
                    performScan(uri)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun scanDirectory(treeUri: Uri) = withContext(Dispatchers.IO) {
        libraryPrefs.addFolder(treeUri.toString())
        performScan(treeUri)
    }

    private suspend fun performScan(treeUri: Uri) {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(treeUri, takeFlags) }

        val scannedBooks = mutableListOf<BookEntity>()
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        val existingBooks = bookDao.getAllBooksOnce()

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val mimeType = cursor.getString(mimeCol) ?: ""
                val isPdf = mimeType == "application/pdf" || name.endsWith(".pdf", true)
                val isEpub = name.endsWith(".epub", true)

                if (isPdf || isEpub) {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idCol))
                    scannedBooks.add(
                        BookEntity(
                            uri = fileUri.toString(),
                            title = name.substringBeforeLast("."),
                            format = if (isPdf) "PDF" else "EPUB",
                            coverImagePath = null,
                            size = cursor.getLong(sizeCol)
                        )
                    )
                }
            }
        }

        val scannedUris = scannedBooks.map { it.uri }.toSet()
        val toDelete = existingBooks.filter { it.uri.contains(treeUri.toString()) && it.uri !in scannedUris }
        if (toDelete.isNotEmpty()) {
            bookDao.deleteBooks(toDelete)
        }

        if (scannedBooks.isNotEmpty()) {
            bookDao.insertBooks(scannedBooks)
        }

        val booksNeedingCover = scannedBooks.filter { book ->
            val existing = existingBooks.find { it.uri == book.uri }
            existing?.coverImagePath == null
        }

        booksNeedingCover.chunked(3).forEach { chunk ->
            coroutineScope {
                chunk.map { book ->
                    async {
                        val path = coverExtractor.extractCover(context, book.uri.toUri())
                        if (path != null) {
                            bookDao.updateCover(book.uri, path)
                        }
                    }
                }.awaitAll()
            }
        }
    }

    suspend fun saveProgress(bookUri: String, page: Int) {
        val currentTime = System.currentTimeMillis()
        bookDao.updateProgress(bookUri, page, currentTime)
    }

    suspend fun setCompletedState(completedState: Boolean, bookUri: String){
        bookDao.setCompleteState(isCompleted = completedState, bookUri)
    }

    suspend fun initTotalPages(bookUri: String, totalPages: Int) {
        bookDao.initTotalPages(bookUri, totalPages)
    }

    suspend fun getBook(bookUri: String): BookEntity? {
        return bookDao.getBook(bookUri)
    }
}