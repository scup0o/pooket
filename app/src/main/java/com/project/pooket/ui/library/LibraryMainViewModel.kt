package com.project.pooket.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.pooket.core.navigation.AppRoute
import com.project.pooket.core.navigation.NavigationManager
import com.project.pooket.data.local.book.BookLocalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
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
    val recentBook = bookRepository.recentBook.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch {
            if (books.value.isEmpty()) bookRepository.refreshAllLibrary()
        }
    }

    fun onRefresh(){
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                viewModelScope.launch {
                    bookRepository.refreshAllLibrary()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun onFolderSelected(uri: Uri?) {
        if (uri != null) {
            viewModelScope.launch { bookRepository.scanDirectory(uri) }
        }
    }
    fun onBookPressed(uri: String, title: String){
        val route = AppRoute.Reader.createRoute(uri, title)
        navManager.navigate(route)
    }
}