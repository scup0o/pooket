package com.project.pooket.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sf.jazzlib.ZipFile
import nl.siegmann.epublib.epub.EpubReader
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookCoverExtractor @Inject constructor() {

    suspend fun extractCover(context: Context, fileUri: Uri): String? = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "book_covers")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val coverFile = File(cacheDir, "${fileUri.toString().hashCode()}.jpg")
        if (coverFile.exists()) return@withContext coverFile.absolutePath

        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(fileUri) ?: ""

            if (mimeType == "application/pdf" || fileUri.toString().endsWith(".pdf", true)) {
                return@withContext extractPdfCover(context, fileUri, coverFile)
            } else {
                return@withContext extractEpubCover(context, fileUri, coverFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun extractPdfCover(context: Context, uri: Uri, outputFile: File): String? {
        return context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                val page = renderer.openPage(0)
                val targetWidth = 300
                val aspectRatio = page.width.toFloat() / page.height.toFloat()
                val targetHeight = (targetWidth / aspectRatio).toInt()

                val bitmap = createBitmap(targetWidth, targetHeight)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                saveAndRecycle(bitmap, outputFile)

                page.close()
                renderer.close()
                outputFile.absolutePath
            } else {
                renderer.close()
                null
            }
        }
    }

    private fun extractEpubCover(context: Context, uri: Uri, outputFile: File): String? {
        val tempFile = File(context.cacheDir, "temp_cover_extract.epub")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            val zipFile = ZipFile(tempFile)
            val book = EpubReader().readEpubLazy(zipFile, "UTF-8")
            val coverImage = book.coverImage

            if (coverImage != null) {
                val data = coverImage.data

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, options)

                options.inSampleSize = calculateInSampleSize(options, 300, 450)
                options.inJustDecodeBounds = false

                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options)
                if (bitmap != null) {
                    saveAndRecycle(bitmap, outputFile)
                    return outputFile.absolutePath
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
        return null
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun saveAndRecycle(bitmap: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
        } finally {
            bitmap.recycle()
        }
    }
}