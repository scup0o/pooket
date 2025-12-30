package com.project.pooket.core.utils

import com.project.pooket.data.local.note.NormRect
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlin.math.abs

data class PdfChar(
    val text: String,
    val bounds: NormRect,
    val isSpace: Boolean = false
)

class CoordinateTextStripper(page: PDPage) : PDFTextStripper() {
    val chars = ArrayList<PdfChar>(1000)
    private val pageWidth: Float
    private val pageHeight: Float

    init {
        sortByPosition = true
        startPage = 1
        endPage = 1

        val cropBox = page.cropBox
        val rotation = page.rotation

        if (rotation == 90 || rotation == 270) {
            pageWidth = cropBox.height
            pageHeight = cropBox.width
        } else {
            pageWidth = cropBox.width
            pageHeight = cropBox.height
        }
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>?) {
        if (textPositions.isNullOrEmpty()) return

        val size = textPositions.size

        for (i in 0 until size) {
            val pos = textPositions[i]
            val charStr = pos.unicode

            if (charStr.isBlank()) {
                chars.add(
                    PdfChar(
                        text = " ",
                        bounds = normalize(pos.x, pos.y, pos.width, pos.height),
                        isSpace = true
                    )
                )
                continue
            }

            if (i > 0) {
                val prevPos = textPositions[i - 1]

                // are they on the visual same line?
                if (abs(pos.y - prevPos.y) < (pos.height * 0.5f)) {
                    val endOfPrev = prevPos.x + prevPos.width
                    val gap = pos.x - endOfPrev

                    // threshold: >10% of character width implies a visual space
                    if (gap > (pos.width * 0.1f)) {
                        chars.add(
                            PdfChar(
                                text = " ",
                                bounds = normalize(endOfPrev, prevPos.y, gap, prevPos.height),
                                isSpace = true
                            )
                        )
                    }
                }
            }

            chars.add(
                PdfChar(
                    text = charStr,
                    bounds = normalize(pos.x, pos.y, pos.width, pos.height),
                    isSpace = false
                )
            )
        }
    }

    private inline fun normalize(x: Float, y: Float, w: Float, h: Float): NormRect {
        val topRaw = y - h
        val bottomRaw = y + (h * 0.2f)

        return NormRect(
            left = (x / pageWidth).coerceIn(0f, 1f),
            top = (topRaw / pageHeight).coerceIn(0f, 1f),
            right = ((x + w) / pageWidth).coerceIn(0f, 1f),
            bottom = (bottomRaw / pageHeight).coerceIn(0f, 1f)
        )
    }
}