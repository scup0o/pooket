package com.project.pooket.data.local.book

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books")
    fun getAllBooks(): Flow<List<BookEntity>>
    @Query("SELECT * FROM books WHERE uri = :uri LIMIT 1")
    suspend fun getBook(uri: String): BookEntity?
    @Query("SELECT * FROM books WHERE lastReadTime > 0 ORDER BY lastReadTime DESC LIMIT 1")
    fun getRecentBook(): Flow<BookEntity?>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Query("UPDATE books SET coverImagePath = :coverPath WHERE uri = :uri")
    suspend fun updateCover(uri: String, coverPath: String)

    @Query("UPDATE books SET lastPage = :page, lastReadTime = :time WHERE uri = :uri")
    suspend fun updateProgress(uri: String, page: Int, time: Long)

    @Query("UPDATE books SET totalPages = :total WHERE uri = :uri AND totalPages = 0")
    suspend fun initTotalPages(uri: String, total: Int)

    @Update
    suspend fun updateBookInfo(book : BookEntity)
}