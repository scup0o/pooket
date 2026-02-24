package com.project.pooket.ui.reader.composable

import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CopyAll
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionControlBar(onCopy: () -> Unit, onNote: () -> Unit, onClose: () -> Unit) {
    TopAppBar(title = { Text("") }, navigationIcon = {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
    }, actions = {
        IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, "Copy") }
        VerticalDivider(
            modifier = Modifier
                .height(24.dp)
                .padding(horizontal = 8.dp)
                .border(
                    width = 1.dp,
                    shape = RoundedCornerShape(100),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
        )
        IconButton(onClick = onNote) { Icon(Icons.Rounded.EditNote, "Note") }
    })
}