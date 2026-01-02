package com.project.pooket.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
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
    val recentBook by viewModel.recentBook.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> viewModel.onFolderSelected(uri) }

    val onBookClick: (String, String) -> Unit = remember { { uri, title -> viewModel.onBookPressed(uri,title) } }

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

            if (books.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text("No books found. Click + to scan.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                ) {

                    if (recentBook != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ContinueReadingCard(
                                book = recentBook!!,
                                onClick = { onBookClick(recentBook!!.uri, recentBook!!.title) }
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

                    items(items = books, key = { it.uri }) { book ->
                        BookItem(
                            book = book,
                            onClick = { onBookClick(book.uri, book.title) }
                        )
                    }
                }
            }

    }
}

@Composable
fun BookCover(coverPath: String?, title: String, modifier: Modifier = Modifier) {
    if (coverPath != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(coverPath))
                .crossfade(true)
                .diskCacheKey(coverPath)
                .memoryCacheKey(coverPath)
                .build(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(title.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun ContinueReadingCard(book: BookEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().height(120.dp)
    ) {
        Row(Modifier.fillMaxSize()) {
            BookCover(
                coverPath = book.coverImagePath,
                title = book.title,
                modifier = Modifier.width(80.dp).fillMaxHeight()
            )
            Column(
                modifier = Modifier.weight(1f).padding(12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)

                val progress = remember(book.lastPage, book.totalPages) {
                    if (book.totalPages > 0) book.lastPage.toFloat() / book.totalPages else 0f
                }

                if (progress > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    )
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                }
            }
            Icon(Icons.Default.PlayArrow, null, Modifier.align(Alignment.CenterVertically).padding(16.dp))
        }
    }
}

@Composable
fun BookItem(book: BookEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(120.dp).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(160.dp).fillMaxWidth(),
        ) {
            Box {
                BookCover(coverPath = book.coverImagePath, title = book.title, modifier = Modifier.fillMaxSize())
                if (book.totalPages > 0) {
                    val progress = remember(book.lastPage, book.totalPages) {
                        book.lastPage.toFloat() / book.totalPages
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                        trackColor = Color.Transparent
                    )
                }
            }
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}