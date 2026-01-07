package com.project.pooket.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.project.pooket.ui.library.composable.BookGridItem
import com.project.pooket.ui.library.composable.BookListItem
import com.project.pooket.ui.library.composable.ContinueReadingCard
import com.project.pooket.ui.library.composable.FilterSortSheet
import com.project.pooket.ui.library.composable.FolderManagementDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryMainScreen(
    onOpenDrawer: () -> Unit,
    viewModel: LibraryMainViewModel = hiltViewModel()
) {
    //data-state
    val books by viewModel.books.collectAsStateWithLifecycle()
    val recentBook by viewModel.recentBook.collectAsStateWithLifecycle()
    val scannedFolders by viewModel.scannedFolders.collectAsStateWithLifecycle()

    //ui-state
    val isRefresing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isGridMode by viewModel.isGridMode.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val activeFilters by viewModel.activeFilters.collectAsStateWithLifecycle()
    var showFolderDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    //services & controller
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> viewModel.onFolderSelected(uri) }

    val onBookClick: (String, String) -> Unit =
        remember { { uri, title -> viewModel.onBookPressed(uri, title) } }

    LaunchedEffect(sortOption, activeFilters) {
        gridState.scrollToItem(0)
        listState.scrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            Icons.Filled.CatchingPokemon,
                            "Menu",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::onChangeViewMode) {
                        Icon(
                            if (isGridMode) Icons.Rounded.Dashboard
                            else Icons.AutoMirrored.Filled.ViewList,
                            null,
                            tint = MaterialTheme.colorScheme.primary

                        )
                    }
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Sort,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showFolderDialog = true}) {
                        Icon(
                            Icons.Rounded.LibraryAdd,
                            "Add Folder",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefresing,
            onRefresh = viewModel::onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            if (books.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No books found. Click + to scan.")
                }


            } else {
                if (isGridMode) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            bottom = if (recentBook != null) 140.dp else 16.dp
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = books,
                            key = { it.uri }
                        ) { book ->
                            BookGridItem(
                                book = book,
                                onClick = { onBookClick(book.uri, book.title) }
                            )
                        }
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        item {
                            Text("ListMode")
                        }
                        items(
                            items = books,
                            key = { it.uri }
                        ) { book ->
                            BookListItem(
                                book = book,
                                onClick = { onBookClick(book.uri, book.title) }
                            )
                        }
                    }
                }
            }

            recentBook?.let {
                if (it.isCompleted == false) {
                    ContinueReadingCard(
                        book = recentBook!!,
                        onClick = { onBookClick(recentBook!!.uri, recentBook!!.title) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 5.dp)
                    )
                }
            }


        }
        if (showFilterSheet) {
            FilterSortSheet(
                onDismiss = { showFilterSheet = false },
                onApply = { newSort, newFilters ->
                    viewModel.applySortAndFilters(newSort, newFilters)
                },
                currentSortOption = sortOption,
                currentFilters = activeFilters
            )
        }

        if (showFolderDialog) {
            FolderManagementDialog(
                folders = scannedFolders,
                onDismiss = { showFolderDialog = false },
                onAddFolder = {
                    showFolderDialog = false
                    launcher.launch(null)
                },
                onRemoveFolder = { uri ->
                    viewModel.onRemoveFolder(uri)
                }
            )
        }
    }
}
