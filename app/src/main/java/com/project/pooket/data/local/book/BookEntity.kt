package com.project.pooket.data.local.book

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity("books")
data class BookEntity(
    @PrimaryKey val uri: String,
    val title : String,
    val format: String,
    val coverImagePath: String?,
    val size: Long =0,

    val lastPage: Int =0,
    val totalPages: Int = 0,
    val lastReadTime: Long =0,
    val isCompleted: Boolean?=false,
) {
}