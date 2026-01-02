package com.project.pooket.ui.reader.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun ReaderControls(
    visible: Boolean,
    isVertical: Boolean,
    isTextMode: Boolean,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    onToggleMode: () -> Unit,
    onToggleTextMode: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallFloatingActionButton(
                onClick = onPrevPage,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.Default.ChevronLeft, "Prev")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = onToggleMode) {
                    Icon(
                        if (isVertical) Icons.Default.SwapVert else Icons.Default.ViewCarousel,
                        "Mode"
                    )
                }
                FloatingActionButton(
                    onClick = onToggleLock,
                    containerColor = if (isLocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen, "Lock")
                }
                FloatingActionButton(
                    onClick = onToggleTextMode,
                    containerColor = if (isTextMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(if (isTextMode) Icons.Default.Image else Icons.Default.TextFields, "Text")
                }
            }
            SmallFloatingActionButton(
                onClick = onNextPage,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.Default.ChevronRight, "Next")
            }
        }
    }
}


@Composable
fun FontSizeControl(visible: Boolean, fontSize: Float, onFontSizeChange: (Float) -> Unit) {
    AnimatedVisibility(
        visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it }) {
        Surface(
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Font Size: ${fontSize.toInt()}sp",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(value = fontSize, onValueChange = onFontSizeChange, valueRange = 12f..40f)
            }
        }
    }
}