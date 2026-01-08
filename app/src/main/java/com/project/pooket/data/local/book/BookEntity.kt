package com.project.pooket.data.local.book

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity("books")
data class BookEntity(
    @PrimaryKey val uri: String,
    val title : String,
    val author: String?=null,
    val genre: String?=null,
    val description: String? = null,
    val coverImagePath: String?,
    val isCompleted: Boolean=false,
    val isFavorite: Boolean = false,
    
    val format: String,
    val size: Long =0,
    val lastPage: Int =0,
    val totalPages: Int = 0,
    val lastReadTime: Long =0,
) {
}