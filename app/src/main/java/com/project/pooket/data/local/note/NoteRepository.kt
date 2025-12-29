package com.project.pooket.data.local.note

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao
) {
    fun getNotes(bookUri: String) = noteDao.getNotesForBook(bookUri)
    suspend fun addNote(note: NoteEntity) = noteDao.insertNote(note)
    suspend fun deleteNote(note: NoteEntity) = noteDao.deleteNote(note)
}