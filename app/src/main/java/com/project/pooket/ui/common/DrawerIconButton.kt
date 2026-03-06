package com.project.pooket.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun DrawerIconButton(
    onClick : () -> Unit
){
    IconButton(onClick) {
        Icon(
            Icons.Filled.CatchingPokemon,
            "Menu",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}