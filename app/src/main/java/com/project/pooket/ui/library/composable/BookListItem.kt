package com.project.pooket.ui.library.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.project.pooket.data.local.book.BookEntity
import java.io.File


@Composable
fun BookListItem(book: BookEntity, onClick: () -> Unit) {

    Column(
        modifier = Modifier.width(110.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .height(160.dp)
                .fillMaxWidth(),
        ) {
            Row {
                Box {
                    BookCover(
                        coverPath = book.coverImagePath,
                        title = book.title,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (book.isCompleted == true) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(color = Color.LightGray.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint = Color.White,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                }
                Row {
                    Box() {
                        Column {
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                            )
                            Text(
                                text = book.author ?:"Unknown author",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth(),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Text(
                                text = book.description ?: "No description",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth(),
                                color = MaterialTheme.colorScheme.outlineVariant

                            )
                        }
                        if (book.totalPages > 0) {
                            val progress = remember(book.lastPage, book.totalPages) {
                                (book.lastPage.toFloat() / (book.totalPages - 1))
                            }
                            if (progress > 0f) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(4.dp),
                                    trackColor = Color.Transparent,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    "${progress.toInt()}%"
                                )
                            }
                        }


                    }
                    if (book.isFavorite)
                        Icon(
                            Icons.Rounded.Favorite,
                            null,
                            tint = Color.Red
                        )
                }

            }



        }


    }
}
