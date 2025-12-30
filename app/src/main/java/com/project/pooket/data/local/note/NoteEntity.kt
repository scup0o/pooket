package com.project.pooket.data.local.note

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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
    indices = [Index(value = ["bookUri"])]
)

@TypeConverters(NoteConverters::class)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookUri: String,
    val pageIndex: Int,
    val originalText: String,
    val noteContent: String,
    val timestamp: Long = System.currentTimeMillis(),
    val colorHex: Long = 0xFFFFFF00,

    @ColumnInfo(name = "rectsJson")
    val rects: List<NormRect> = emptyList(),
    val textRangeStart: Int? = null,
    val textRangeEnd: Int? = null
)

@Serializable
data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

class NoteConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromRectsList(rects: List<NormRect>): String {
        return json.encodeToString(rects)
    }

    @TypeConverter
    fun toRectsList(data: String): List<NormRect> {
        return try {
            if (data.isBlank()) emptyList() else json.decodeFromString(data)
        } catch (e: Exception) {
            emptyList()
        }
    }
}