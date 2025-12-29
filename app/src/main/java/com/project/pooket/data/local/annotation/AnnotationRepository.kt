package com.project.pooket.data.local.book

import com.project.pooket.data.local.annotation.AnnotationDao
import com.project.pooket.data.local.annotation.AnnotationEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class AnnotationRepository @Inject constructor(
    private val annotationDao: AnnotationDao
) {
    fun getAnnotations(bookUri: String): Flow<List<AnnotationEntity>> =
        annotationDao.getAnnotationsForBook(bookUri)

    suspend fun addAnnotation(annotation: AnnotationEntity) =
        annotationDao.insertAnnotation(annotation)

    suspend fun deleteAnnotation(annotation: AnnotationEntity) =
        annotationDao.deleteAnnotation(annotation)
}