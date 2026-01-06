package com.project.pooket.ui.library.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontStyle
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
            .height(90.dp)
    ) {
        Box(){
            Row(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                ) {
                    Text(
                        text = "Continue Reading:",
                        style = MaterialTheme.typography.titleMedium.copy(

                        )
                    )
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )



                }
                BookCover(
                    coverPath = book.coverImagePath,
                    title = book.title,
                    modifier = Modifier.aspectRatio(0.7f)
                        .fillMaxHeight(),
                )
            }

            val progress = remember(book.lastPage, book.totalPages) {
                if (book.totalPages > 0) (book.lastPage.toFloat() / (book.totalPages - 1))
                else 0f
            }

            if (progress > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Round,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .align(Alignment.BottomCenter)
                )
            }
        }

    }
}
