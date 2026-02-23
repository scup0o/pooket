package com.project.pooket.ui.reader.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirlineStops
import androidx.compose.material.icons.rounded.ScubaDiving
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
                value = pageNumber,
                onValueChange = {
                    isError = false
                    if (it.all { char -> char.isDigit() }) {
                        pageNumber = it
                    }
                },
                placeholder = { Text("Enter page number...") },
                isError = isError,
                supportingText = { if (isError) Text("Page number must > 0") },

                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = {
                    IconButton(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        onClick = {
                            if (pageNumber.toInt() == 0) {
                                isError = true
                            }
                            else{
                                onInputPageNumber(pageNumber.toInt()-1)
                            }
                        }) {
                        Icon(Icons.Rounded.ScubaDiving, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                )

        }
    }
}