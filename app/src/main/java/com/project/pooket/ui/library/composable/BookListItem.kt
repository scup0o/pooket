package com.project.pooket.ui.library.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.project.pooket.data.local.book.BookEntity
import java.io.File


@Composable
fun BookListItem(book: BookEntity, onClick: () -> Unit, height: Dp = 130.dp, width: Dp = 130.dp ) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val y = size.height + 40 - strokeWidth / 2

                drawLine(
                    color = Color.LightGray,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidth
                )
            },
        horizontalArrangement = Arrangement.spacedBy(15.dp)
    ) {

        Box(modifier = Modifier.weight(1f).height(height)) {
            Column() {
                Text(
                    text = book.author ?: "Unknown author",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                )

                Text(
                    text = book.description ?: "No description",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.outline

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
                }
            }


        }
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(shape = RoundedCornerShape(10))
        ) {
            BookCover(
                coverPath = book.coverImagePath,
                title = book.title,
                modifier = Modifier
                    .width(width)
                    .height(height)
            )
            if (book.isCompleted) {
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
            if (book.isFavorite)
                Icon(
                    Icons.Rounded.Favorite,
                    null,
                    tint = Color(0xB2B91621),
                    modifier = Modifier
                        .padding(5.dp)
                        .size(20.dp)
                        .align(Alignment.TopEnd)
                )

        }


    }


}
