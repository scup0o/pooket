package com.project.pooket.ui.library.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.project.pooket.data.local.book.BookEntity


@Composable
fun ContinueReadingCard(book: BookEntity, onClick: () -> Unit, modifier: Modifier) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        Row(Modifier.fillMaxSize()) {
            BookCover(
                coverPath = book.coverImagePath,
                title = book.title,
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight()
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val progress = remember(book.lastPage, book.totalPages) {
                    if (book.totalPages > 0) (book.lastPage.toFloat() / (book.totalPages - 1))
                    else 0f
                }

                if (progress > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Icon(
                Icons.Default.PlayArrow,
                null,
                Modifier
                    .align(Alignment.CenterVertically)
                    .padding(16.dp)
            )
        }
    }
}
