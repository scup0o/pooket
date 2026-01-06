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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.project.pooket.ui.library.composable.BookGridItem
import com.project.pooket.ui.library.composable.BookListItem
import com.project.pooket.ui.library.composable.ContinueReadingCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryMainScreen(
    onOpenDrawer: () -> Unit,
    viewModel: LibraryMainViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val recentBook by viewModel.recentBook.collectAsStateWithLifecycle()

    val isRefresing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isGridMode by viewModel.isGridMode.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> viewModel.onFolderSelected(uri) }

    val onBookClick: (String, String) -> Unit =
        remember { { uri, title -> viewModel.onBookPressed(uri, title) } }

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
                            if (isGridMode) Icons.Default.Dashboard else Icons.AutoMirrored.Filled.ViewList,
                            null
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            null
                        )
                    }
                    IconButton(onClick = { launcher.launch(null) }) {
                        Icon(
                            Icons.Default.Add,
                            "Add Folder",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefresing, onRefresh = viewModel::onRefresh, modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            } else {
                if (books.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No books found. Click + to scan.")
                    }


                } else {
                    if (isGridMode){
                        LazyVerticalGrid(
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
                    }
                    else{
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item{
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

        }

    }
}
