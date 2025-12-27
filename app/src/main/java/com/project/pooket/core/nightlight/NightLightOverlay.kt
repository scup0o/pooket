//package com.example.pooket.core.nightlight
//
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.lerp
//
//@Composable
//fun NightLightOverlay(
//    isEnabled: Boolean,
//    warmth: Float,
//    dimming: Float
//) {
//    if (!isEnabled) return
//
//    val coldColor = Color(0xFFFFF9E5).copy(alpha = 0.0f)
//    val deepColor = Color(0xFFFF8F00).copy(alpha = 0.35f)
//
//    val warmthFilter = lerp(coldColor, deepColor, warmth)
//
//    val dimmingFilter = Color.Black.copy(alpha = dimming * 0.7f)
//
//    Box(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(warmthFilter)
//    ) {
//        if (dimming > 0f) {
//            Box(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .background(dimmingFilter)
//            )
//        }
//    }
//}

package com.project.pooket.core.nightlight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

@Composable
fun NightLightOverlay(
    isEnabled : Boolean,
    warmth : Float,
    dimming: Float
){
    if (!isEnabled) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        //warmth
        val coldColor = Color(1.0f, 0.95f, 0.9f)
        val deepColor = Color(1.0f, 0.6f, 0.2f)
        val filterColor = lerp(coldColor, deepColor, warmth)
        drawRect(
            color = filterColor,
            blendMode = BlendMode.Multiply
        )

        //dimming
        if (dimming>0f){
            drawRect(
                color = Color.Black.copy(alpha = dimming*0.8f),
                blendMode = BlendMode.Darken
            )
        }
    }
}