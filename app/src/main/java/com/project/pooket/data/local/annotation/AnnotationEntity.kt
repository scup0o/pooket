package com.project.pooket.data.local.annotation

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.project.pooket.data.local.book.BookEntity
import kotlinx.serialization.Serializable

@Serializable
data class PdfRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

enum class AnnotationType {
    HIGHLIGHT, NOTE, BOOKMARK
}

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["uri"],
            childColumns = ["bookUri"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookUri"])]
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookUri: String,
    val pageIndex: Int,
    val type: AnnotationType,

    // Text positioning
    val startOffset: Int,
    val endOffset: Int,
    val selectedText: String,

    // Image positioning (JSON list of PdfRect)
    val rectsJson: String,

    val noteContent: String? = null,
    val color: Int = 0x55FFFF00.toInt(), // Semi-transparent yellow
    val createdAt: Long = System.currentTimeMillis()
)