package com.project.pooket.ui.library.composable

import android.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.project.pooket.ui.common.NightLightBottomModal
import com.project.pooket.ui.library.BookCompletedFilter
import com.project.pooket.ui.library.BookSortOption
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Signpost
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.SelectableChipColors
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.project.pooket.ui.common.ModalTitle


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

    val onRefresh: () -> Unit = {
        tempSortOption = BookSortOption.DEFAULT
        tempFilters = emptySet<BookCompletedFilter>()

        onApply(tempSortOption, tempFilters)
    }

    NightLightBottomModal(
        onDismiss = onDismiss,
    ) {
        ModalTitle(
            titleText = "Filter & Sort",
            rightAction = {

                Icon(
                    Icons.Rounded.Replay,
                    contentDescription = "Refresh",
                    tint=MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onRefresh)
                        .size(25.dp)
                )
            }
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Filter books by..."
            )
            TextButton(
                onClick = { showSortingPopUp = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(25),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically){
                        Icon(
                            Icons.Rounded.Signpost, null
                        )
                        Text("Sort by", color = MaterialTheme.colorScheme.onSurface)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
        }

        Column() {
            Text("Book status")
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(BookCompletedFilter.entries) { filter ->
                    val isSelected = tempFilters.contains(filter)
                    FilterChip(
                        colors = FilterChipDefaults.filterChipColors(),
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
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                )
                            }
                        } else null
                    )
                }
            }
        }


        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
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