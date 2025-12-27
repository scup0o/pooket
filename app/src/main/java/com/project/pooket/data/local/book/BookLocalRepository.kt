package com.project.pooket.data.local.book

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.example.pooket.core.utils.BookCoverExtractor
import com.project.pooket.data.local.book.BookDao
import com.project.pooket.data.local.book.BookEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookLocalRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val coverExtractor: BookCoverExtractor,
    private val libraryPrefs: LibraryPreferences
) {

    // --- DATA STREAMS ---
    // Sorted by Title (default)
    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()

    // The single most recently read book (for the "Continue Reading" card)
    val recentBook: Flow<BookEntity?> = bookDao.getRecentBook()

    // --- ACTIONS ---

    /**
     * 1. AUTO-REFRESH: Called when App Starts.
     * Iterates through all folders the user previously added and rescans them.
     */
    suspend fun refreshAllLibrary() = withContext(Dispatchers.IO) {
        val folders = libraryPrefs.scannedFolders.first()

        folders.forEach { uriString ->
            try {
                val uri = Uri.parse(uriString)
                // Security Check: Do we still have permission to read this folder?
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

    /**
     * 2. ADD NEW FOLDER: Called when user clicks "+" and picks a folder.
     */
    suspend fun scanDirectory(treeUri: Uri) = withContext(Dispatchers.IO) {
        // Save to Preferences so we remember it next time
        libraryPrefs.addFolder(treeUri.toString())

        // Run the scan
        performScan(treeUri)
    }

    /**
     * 3. FAST SCAN ENGINE:
     * Uses 'DocumentsContract' query (instead of File.listFiles) for 20x speed on SD cards.
     */
    private suspend fun performScan(treeUri: Uri) {
        // A. Persist Permissions (Crucial)
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        } catch (e: Exception) {
            // Permission might already persist, ignore error
        }

        val rawBooks = mutableListOf<BookEntity>()
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

        // Columns we need to fetch
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        try {
            // B. Query the File System (Database style)
            context.contentResolver.query(
                childrenUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val mimeType = cursor.getString(mimeCol) ?: ""
                    val documentId = cursor.getString(idCol)
                    val size = cursor.getLong(sizeCol)

                    // C. Filter for PDF / EPUB
                    val isPdf = mimeType == "application/pdf" || name.endsWith(".pdf", true)
                    val isEpub = name.endsWith(".epub", true)

                    if (isPdf || isEpub) {
                        // Reconstruct the file URI
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

                        rawBooks.add(
                            BookEntity(
                                uri = fileUri.toString(),
                                title = name.substringBeforeLast("."), // Remove extension
                                format = if (isPdf) "PDF" else "EPUB",
                                coverImagePath = null, // Set null initially (Fast)
                                size = size
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // D. INSERT TEXT DATA (Instant UI Update)
        // User sees the list immediately with placeholder icons
        if (rawBooks.isNotEmpty()) {
            bookDao.insertBooks(rawBooks)
        }

        // E. EXTRACT COVERS (Background Process)
        // Update the books one by one as covers are generated
        rawBooks.forEach { book ->
            // Only extract if we suspect we can (e.g. PDF)
            // And logic inside extractor checks if cache already exists
            val coverPath = coverExtractor.extractCover(context, Uri.parse(book.uri), book.title)

            if (coverPath != null) {
                bookDao.updateCover(book.uri, coverPath)
            }
        }
    }

    // --- READING PROGRESS ---

    suspend fun saveProgress(bookUri: String, page: Int, totalPages: Int) {
        val currentTime = System.currentTimeMillis()
        bookDao.updateProgress(bookUri, page, currentTime)
    }

    // For initializing the Total Pages count when opening a book for the first time
    suspend fun initTotalPages(bookUri: String, totalPages: Int) {
        bookDao.initTotalPages(bookUri, totalPages)
    }

    suspend fun getBook(bookUri: String): BookEntity? {
        return bookDao.getBook(bookUri)
    }
}