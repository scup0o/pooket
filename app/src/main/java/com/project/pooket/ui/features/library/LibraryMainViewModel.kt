package com.project.pooket.ui.features.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.pooket.core.navigation.AppRoute
import com.project.pooket.core.navigation.NavigationManager
import com.project.pooket.data.local.book.BookLocalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryMainViewModel @Inject constructor(
    private val navManager : NavigationManager,
    private val bookRepository: BookLocalRepository
) : ViewModel(){

    val books = bookRepository.allBooks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // 2. Continue Reading Section
    val recentBook = bookRepository.recentBook.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    init {
        // 3. Auto-Scan on Load
        viewModelScope.launch {
            bookRepository.refreshAllLibrary()
        }
    }

    fun onFolderSelected(uri: Uri?) {
        if (uri != null) {
            viewModelScope.launch { bookRepository.scanDirectory(uri) }
        }
    }
    fun onBookPressed(uri: String){
        val route = AppRoute.Reader.createRoute(uri)
        navManager.navigate(route)
    }
}