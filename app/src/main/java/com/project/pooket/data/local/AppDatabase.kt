package com.project.pooket.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.project.pooket.data.local.book.BookDao
import com.project.pooket.data.local.book.BookEntity
import com.project.pooket.data.local.note.NoteDao
import com.project.pooket.data.local.note.NoteEntity

@Database(entities = [BookEntity::class, NoteEntity::class], version=1)
abstract class AppDatabase: RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun noteDao(): NoteDao
}