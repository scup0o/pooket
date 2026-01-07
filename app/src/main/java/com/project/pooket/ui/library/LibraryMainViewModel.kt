package com.project.pooket.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.pooket.core.navigation.AppRoute
import com.project.pooket.core.navigation.NavigationManager
import com.project.pooket.data.local.book.BookEntity
import com.project.pooket.data.local.book.BookLocalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class BookSortOption(val label: String) {
    NAME_ASC("Name: A-Z"),
    NAME_DESC("Name: Z-A"),
    DATE_NEWEST("Recently Added"),
    DATE_OLDEST("Oldest Added")
}

enum class BookCompletedFilter(val label: String) {
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    NOT_STARTED("Not Started")
}

@HiltViewModel
class LibraryMainViewModel @Inject constructor(
    private val navManager: NavigationManager,
    private val bookRepository: BookLocalRepository
) : ViewModel() {

    private val _rawBooks = bookRepository.allBooks

    private val _sortOption = MutableStateFlow(BookSortOption.DATE_NEWEST)
    val sortOption = _sortOption.asStateFlow()

    private val _activeFilters = MutableStateFlow(emptySet<BookCompletedFilter>())
    val activeFilters = _activeFilters.asStateFlow()

    val books = combine(_rawBooks, _sortOption, _activeFilters) { list, sort, filters ->
        val filteredList = if (filters.isEmpty()) {
            list
        } else {
            list.filter { book -> doesBookMatchFilter(book, filters) }
        }

        when (sort) {
            BookSortOption.NAME_ASC -> filteredList.sortedBy { it.title.lowercase() }
            BookSortOption.NAME_DESC -> filteredList.sortedByDescending { it.title.lowercase() }
            BookSortOption.DATE_OLDEST -> filteredList
            BookSortOption.DATE_NEWEST -> filteredList.reversed()
        }
    }.stateIn(
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
//
//    private val _isLoading = MutableStateFlow(true)
//    val isLoading = _isLoading.asStateFlow()

    private val _isGridMode = MutableStateFlow(true)
    val isGridMode = _isGridMode.asStateFlow()

    init {
        viewModelScope.launch {
            delay(100)
            if (books.value.isEmpty()) {
                onRefresh()
            } else {
//                _isLoading.value = false
            }
        }
    }

    private fun doesBookMatchFilter(book: BookEntity, filters: Set<BookCompletedFilter>): Boolean {
        if (filters.contains(BookCompletedFilter.COMPLETED)) {
            if (book.isCompleted == true) return true
        }
        if (filters.contains(BookCompletedFilter.NOT_STARTED)) {
            if (book.isCompleted != true && book.lastPage == 0 && book.lastReadTime == 0L) return true
        }
        if (filters.contains(BookCompletedFilter.IN_PROGRESS)) {
            if (book.isCompleted != true && (book.lastPage > 0 || book.lastReadTime > 0)) return true
        }

        return false
    }

    fun onRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
//            _isLoading.value = true
            withContext(Dispatchers.IO) {
                try {
                    bookRepository.refreshAllLibrary()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
//            _isLoading.value = false
            _isRefreshing.value = false
        }
    }

    fun onFolderSelected(uri: Uri?) {
        if (uri != null) {
            viewModelScope.launch { bookRepository.scanDirectory(uri) }
        }
    }

    fun onBookPressed(uri: String, title: String) {
        val route = AppRoute.Reader.createRoute(uri, title)
        navManager.navigate(route)
    }

    fun onChangeViewMode() {
        _isGridMode.value = !_isGridMode.value
    }

    fun applySortAndFilters(newSort: BookSortOption, newFilters: Set<BookCompletedFilter>) {
        _sortOption.value = newSort
        _activeFilters.value = newFilters
    }
}