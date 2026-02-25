package com.project.pooket.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.project.pooket.core.nightlight.LocalNightLightConfig
import com.project.pooket.core.nightlight.NightLightOverlay

@Composable
fun NightLightDialog(
    onDismissRequest: () -> Unit,
    width: Dp? = null,
    content: @Composable (() -> Unit),
) {
    val nightLightConfig = LocalNightLightConfig.current

    val configuration = LocalConfiguration.current
    val maxDialogHeight = configuration.screenHeightDp.dp * 0.85f

    Dialog(
        onDismissRequest = onDismissRequest,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .heightIn(max = maxDialogHeight)
                .wrapContentHeight()
                .then(
                    if (width != null) Modifier.width(width)
                    else Modifier.wrapContentWidth()
                )
        ) {
            Box {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(all = 20.dp),
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