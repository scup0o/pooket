package com.project.pooket.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.graphics.createBitmap
import java.io.FileOutputStream

@Singleton
class BookCoverExtractor @Inject constructor() {
    suspend fun extractCover(context: Context, fileUri: Uri): String? = withContext(
        Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "book_covers")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val coverFile = File(cacheDir, "${fileUri.toString().hashCode()}.jpg")
        if (coverFile.exists()) return@withContext coverFile.absolutePath

        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(fileUri)

            if (mimeType == "application/pdf") {
                contentResolver.openFileDescriptor(fileUri, "r")?.use { pfd ->
                    val renderer = PdfRenderer(pfd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val targetWidth = 300

                        // calculate aspect ratio so image doesn't stretch
                        val aspectRatio = page.width.toFloat() / page.height.toFloat()
                        val targetHeight = (targetWidth / aspectRatio).toInt()

                        //create a small bitmap to saves RAM
                        val bitmap = createBitmap(targetWidth, targetHeight)
                        bitmap.eraseColor(android.graphics.Color.WHITE)

                        //render the huge PDF page into the tiny Bitmap
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        page.close()
                        renderer.close()

                        //compress to JPEG with 70% quality to saves disk space
                        FileOutputStream(coverFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                        }

                        //recycle bitmap to free RAM immediately
                        bitmap.recycle()

                        return@withContext coverFile.absolutePath
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }}