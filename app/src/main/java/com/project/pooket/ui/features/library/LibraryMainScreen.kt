package com.project.pooket.ui.features.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.project.pooket.R
import com.project.pooket.data.local.book.BookEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryMainScreen(
    onOpenDrawer: () -> Unit,
    viewModel: LibraryMainViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val recentBook by viewModel.recentBook.collectAsStateWithLifecycle() // Observe Recent

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> viewModel.onFolderSelected(uri) }

    val onBookClick: (String) -> Unit = remember { { uri -> viewModel.onBookPressed(uri) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(painterResource(R.drawable.menu), "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { launcher.launch(null) }) {
                        Icon(Icons.Default.Add, "Add Folder")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {

            if (books.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No books found. Click + to scan.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {

                    // 1. CONTINUE READING SECTION (Full Width)
                    if (recentBook != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ContinueReadingCard(
                                book = recentBook!!,
                                onClick = { onBookClick(recentBook!!.uri) }
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "All Books",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                    }

                    // 2. ALL BOOKS GRID
                    items(items = books, key = { it.uri }) { book ->
                        BookItem(
                            book = book,
                            onClick = { onBookClick(book.uri) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueReadingCard(book: BookEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().height(140.dp)
    ) {
        Row(Modifier.fillMaxSize()) {
            // Cover Image
            if (book.coverImagePath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(book.coverImagePath))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(90.dp).fillMaxHeight()
                )
            } else {
                Box(Modifier.width(90.dp).fillMaxHeight().padding(8.dp), contentAlignment = Alignment.Center) {
                    Text(book.title.take(1), style = MaterialTheme.typography.headlineLarge)
                }
            }

            // Info Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Continue Reading",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))

                // Progress Bar
                if (book.totalPages > 0) {
                    val progress = book.lastPage.toFloat() / book.totalPages.toFloat()
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${(progress * 100).toInt()}% Completed",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Play Icon
            Box(Modifier.fillMaxHeight().padding(end = 16.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null)
            }
        }
    }
}

@Composable
fun BookItem(book: BookEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(4.dp),
            modifier = Modifier
                .height(150.dp)
                .fillMaxWidth()
                .clickable { onClick() },
            ) {
            if (book.coverImagePath!= null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(book.coverImagePath))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(book.title.take(1), style = MaterialTheme.typography.displayMedium)
                }
            }
            if (book.totalPages > 0) {
                val progress = book.lastPage.toFloat() / book.totalPages.toFloat()
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 4.dp),
            overflow = TextOverflow.Ellipsis
        )
    }
}