package com.project.pooket.ui.library.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.project.pooket.ui.common.NightLightBottomModal
import com.project.pooket.ui.library.BookCompletedFilter
import com.project.pooket.ui.library.BookSortOption
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSortSheet(
    onDismiss: () -> Unit,
    onApply: (BookSortOption, Set<BookCompletedFilter>) -> Unit,
    currentSortOption: BookSortOption,
    currentFilters: Set<BookCompletedFilter>,
) {
    var showSortingPopUp by remember { mutableStateOf(false) }
    var tempSortOption by remember { mutableStateOf(currentSortOption) }
    var tempFilters by remember { mutableStateOf(currentFilters) }

    NightLightBottomModal(
        onDismiss = onDismiss
    ) {
        Text(
            "Filter books by..."
        )

        Button(
            onClick = { showSortingPopUp = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sort by")
                Row {
                    Text(
                        tempSortOption.label
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
        Text("Book status")
        LazyRow(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            items(BookCompletedFilter.entries) { filter ->
                val isSelected = tempFilters.contains(filter)

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        tempFilters = if (isSelected) {
                            tempFilters - filter
                        } else {
                            tempFilters + filter
                        }
                    },
                    label = { Text(filter.label) },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                            )
                        }
                    } else null
                )
            }
        }
        Row {
            Button(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    onApply(tempSortOption, tempFilters)
                    onDismiss()
                }
            ) { Text("Start filtering") }
        }

    }
    if (showSortingPopUp) {
        SortOptionPopUp(
            currentSortOption = tempSortOption,
            onDismiss = { showSortingPopUp = false },
            onSelect = { newSort ->
                tempSortOption = newSort
                showSortingPopUp = false
            }
        )
    }
}

@Composable
fun SortOptionPopUp(
    currentSortOption: BookSortOption,
    onDismiss: () -> Unit,
    onSelect: (BookSortOption) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort Books By") },
        text = {
            Column(Modifier.selectableGroup()) {
                BookSortOption.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (option == currentSortOption),
                                onClick = { onSelect(option) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (option == currentSortOption),
                            onClick = null
                        )
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}