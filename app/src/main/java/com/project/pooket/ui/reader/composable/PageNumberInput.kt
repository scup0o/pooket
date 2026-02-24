package com.project.pooket.ui.reader.composable

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirlineStops
import androidx.compose.material.icons.rounded.ScubaDiving
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.project.pooket.ui.common.NightLightDialog

@Composable
fun PageNumberInput(
    onInputPageNumber: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var pageNumber by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    NightLightDialog(
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            OutlinedTextField(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(25),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                value = pageNumber,
                onValueChange = {
                    isError = false
                    if (it.all { char -> char.isDigit() }) {
                        pageNumber = it
                    }
                },
                placeholder = { Text("Dive to page number...") },
                isError = isError,
                supportingText = { if (isError) Text("Page number must > 0") },

                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = {
                    FilledIconButton(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(end=5.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        onClick = {
                            val num = pageNumber.toIntOrNull() ?: 0
                            if (num <= 0) {
                                isError = true
                            } else {
                                onInputPageNumber(num - 1)
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.ScubaDiving, null)


                    }
                }
            )


        }
    }
}