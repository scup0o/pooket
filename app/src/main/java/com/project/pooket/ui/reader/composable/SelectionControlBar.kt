package com.project.pooket.ui.reader.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.dp


@Composable
fun SelectionControlBar(onCopy: () -> Unit, onNote: () -> Unit, onClose: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Text Selected",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Copy") }
                IconButton(onClick = onNote) { Icon(Icons.Default.Edit, "Note") }
                VerticalDivider(modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 8.dp))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
            }
        }
    }
}

class CustomTextToolbar(
    private val onShowMenu: (Rect) -> Unit,
    private val onHideMenu: () -> Unit,
    private val onCopy: () -> Unit
) : TextToolbar {
    override val status get() = if (shown) TextToolbarStatus.Shown else TextToolbarStatus.Hidden
    private var shown = false
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        shown = true; onShowMenu(rect)
    }

    override fun hide() {
        shown = false; onHideMenu()
    }
}