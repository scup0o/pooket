package com.project.pooket.ui.reader.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.project.pooket.ui.common.NightLightDialog
import kotlinx.coroutines.delay

@Composable
fun NoteContentDialog(content: String, onDismiss: () -> Unit) {
    NightLightDialog(
        onDismissRequest = onDismiss
    ) {
        TextField(
            value = content,
            onValueChange = {},
            readOnly = true,
            maxLines = 10,
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
            )
            )
    }
}


@Composable
fun NoteInputDialog(initialText: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

    NightLightDialog(
        onDismissRequest = onDismiss
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.focusRequester(focusRequester),
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.outline,
                focusedPlaceholderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            placeholder = {
                Text("Jot down your note")
            },
            maxLines = 10,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Absolute.Right
        ){
            TextButton(onClick = onDismiss) {
                Text("Discard")
            }
            TextButton(
                onClick = { onConfirm(text)
                }) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Jot down")
                    Icon(
                        Icons.Rounded.Brush, null,
                        modifier = Modifier.size(18.dp)
                    )
                }}
            }
    }
}