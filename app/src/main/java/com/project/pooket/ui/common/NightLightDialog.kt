package com.project.pooket.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.project.pooket.core.nightlight.LocalNightLightConfig
import com.project.pooket.core.nightlight.NightLightOverlay

@Composable
fun NightLightDialog(
    onDismissRequest: () -> Unit,
    content: @Composable (() -> Unit)
) {
    val nightLightConfig = LocalNightLightConfig.current
    Dialog(
        onDismissRequest = onDismissRequest,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight()
        ) {
            Box {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
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
}