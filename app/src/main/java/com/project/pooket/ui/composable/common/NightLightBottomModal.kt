package com.project.pooket.ui.composable.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.project.pooket.core.nightlight.LocalNightLightConfig
import com.project.pooket.core.nightlight.NightLightOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightLightBottomModal(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit){
    val nightLightConfig = LocalNightLightConfig.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
    ){
        Box(
            modifier = Modifier.fillMaxWidth()
        ){
            Column(Modifier.fillMaxWidth()) {
                BottomSheetDefaults.DragHandle(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                content()
            }
            NightLightOverlay(
                isEnabled = nightLightConfig.isEnabled,
                warmth = nightLightConfig.warmth,
                dimming = nightLightConfig.dimming
            )
        }
    }

}