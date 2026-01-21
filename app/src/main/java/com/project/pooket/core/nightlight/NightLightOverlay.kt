package com.project.pooket.core.nightlight

import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toDrawable

@Composable
fun NightLightOverlay(
    isEnabled: Boolean,
    warmth: Float,
    dimming: Float,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    if (!isEnabled) return
    // < android 9 (API 28) or older
    val useLegacyRendering = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    if (useLegacyRendering) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                FrameLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    val warmthView = View(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    }
                    addView(warmthView)

                    val dimmingView = View(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                    }
                    addView(dimmingView)
                }
            },
            update = { parent ->
                val warmthView = parent.getChildAt(0)
                val dimmingView = parent.getChildAt(1)

                val r = (255 - (warmth * 95)).toInt()
                val g = (60 + ((1.0f - warmth) * 160)).toInt()
                val b = 0
                val alpha = 0.1f + (warmth * 0.45f)
                val alphaInt = (alpha * 255).toInt()
                warmthView.setBackgroundColor(AndroidColor.argb(alphaInt, r, g, b))

                val baseDimming = if (warmth > 0.5f) (warmth - 0.5f) * 0.3f else 0f
                val totalDimming = (dimming + baseDimming).coerceAtMost(0.8f)
                if (totalDimming > 0f) {
                    val dimAlpha = (totalDimming * 255).toInt()
                    dimmingView.setBackgroundColor(AndroidColor.argb(dimAlpha, 0, 0, 0))
                } else {
                    dimmingView.setBackgroundColor(AndroidColor.TRANSPARENT)
                }
            }
        )
    } else {
        Canvas(modifier = modifier) {
            //warmth
            val coldColor = Color(1.0f, 0.95f, 0.9f)
            val deepColor = Color(1.0f, 0.6f, 0.2f)
            val filterColor = lerp(coldColor, deepColor, warmth)
            drawRect(
                color = filterColor,
                blendMode = BlendMode.Multiply
            )

            //dimming
            if (dimming > 0f) {
                drawRect(
                    color = Color.Black.copy(alpha = dimming * 0.8f),
                    blendMode = BlendMode.Darken
                )
            }
        }
    }
}