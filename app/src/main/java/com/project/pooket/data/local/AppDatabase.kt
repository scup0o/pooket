package com.project.pooket.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.project.pooket.data.local.book.BookDao
import com.project.pooket.data.local.book.BookEntity

@Database(entities = [BookEntity::class], version=1)
abstract class AppDatabase: RoomDatabase() {
    abstract fun bookDao(): BookDao
}