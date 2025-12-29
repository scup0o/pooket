package com.project.pooket.data.local.note

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookUri: String,
    val pageIndex: Int,
    val originalText: String,
    val noteContent: String,
    val timestamp: Long = System.currentTimeMillis(),
    val colorHex: Long = 0xFFFFFF00, // Yellow default
    val rectsJson: String // Stored as JSON string of normalized coordinates
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

// Normalized Rectangle (0.0 to 1.0) relative to page width/height
@Serializable
data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)