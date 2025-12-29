package com.project.pooket.data.local.annotation

import androidx.room.*
import com.project.pooket.data.local.annotation.AnnotationEntity
import com.project.pooket.data.local.annotation.PdfRect
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromRectList(value: List<PdfRect>): String = Json.encodeToString(value)

    @TypeConverter
    fun toRectList(value: String): List<PdfRect> = Json.decodeFromString(value)
}

@Dao
interface AnnotationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity)

    @Query("SELECT * FROM annotations WHERE bookUri = :bookUri")
    fun getAnnotationsForBook(bookUri: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE bookUri = :bookUri AND pageIndex = :pageIndex")
    suspend fun getAnnotationsByPage(bookUri: String, pageIndex: Int): List<AnnotationEntity>

    @Delete
    suspend fun deleteAnnotation(annotation: AnnotationEntity)
}