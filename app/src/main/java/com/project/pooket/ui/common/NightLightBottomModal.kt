package com.project.pooket.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.project.pooket.core.nightlight.LocalNightLightConfig
import com.project.pooket.core.nightlight.NightLightOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightLightBottomModal(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val nightLightConfig = LocalNightLightConfig.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(BottomSheetDefaults.windowInsets)
                    .padding(horizontal = 16.dp)
                    .padding(top = 30.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                content()
            }
            NightLightOverlay(
                modifier = Modifier.matchParentSize(),
                isEnabled = nightLightConfig.isEnabled,
                warmth = nightLightConfig.warmth,
                dimming = nightLightConfig.dimming
            )
        }
    }

}