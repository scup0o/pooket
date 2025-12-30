package com.project.pooket.data.local.note

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import com.project.pooket.data.local.book.BookEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["uri"],
            childColumns = ["bookUri"],
            onDelete = CASCADE
        )
    ],
    indices = [Index(value = ["bookUri"])])

data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookUri: String,
    val pageIndex: Int,
    val originalText: String,
    val noteContent: String,
    val timestamp: Long = System.currentTimeMillis(),
    val colorHex: Long = 0xFFFFFF00,
    val rectsJson: String,

    val textRangeStart: Int? = null,
    val textRangeEnd: Int? = null
) {
    fun getRects(): List<NormRect> {
        return try {
            Json.decodeFromString(rectsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        fun fromRects(bookUri: String, page: Int, text: String, note: String, rects: List<NormRect>): NoteEntity {
            return NoteEntity(
                bookUri = bookUri,
                pageIndex = page,
                originalText = text,
                noteContent = note,
                rectsJson = Json.encodeToString(rects)
            )
        }
    }
}

@Serializable
data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)