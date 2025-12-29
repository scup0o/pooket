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

class CoordinateTextStripper(private val page: PDPage) : PDFTextStripper() {
    val chars = mutableListOf<PdfChar>()

    init {
        sortByPosition = true
        startPage = 1
        endPage = 1
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>?) {
        if (textPositions.isNullOrEmpty()) return

        val cropBox = page.cropBox
        val rotation = page.rotation

        val pageWidth: Float
        val pageHeight: Float

        if (rotation == 90 || rotation == 270) {
            pageWidth = cropBox.height
            pageHeight = cropBox.width
        } else {
            pageWidth = cropBox.width
            pageHeight = cropBox.height
        }

        for (i in textPositions.indices) {
            val pos = textPositions[i]
            val charStr = pos.unicode

            // 1. CHECK FOR EXPLICIT SPACE
            // If the PDF actually has a " " character, mark it as space!
            if (charStr == " " || charStr.isBlank()) {
                // It's a space. We add it so string construction is correct,
                // but we mark isSpace=true so selection stops here.
                val normRect = normalize(pos.x, pos.y, pos.width, pos.height, pageWidth, pageHeight)
                chars.add(PdfChar(" ", normRect, isSpace = true))
                continue // Skip gap check for this char
            }

            // 2. CHECK FOR PHANTOM SPACE (GAP DETECTION)
            // Only if the current char is NOT a space, check if there was a gap before it
            if (i > 0) {
                val prevPos = textPositions[i - 1]
                val isSameLine = abs(pos.y - prevPos.y) < (pos.height * 0.5f)

                if (isSameLine) {
                    val endOfPrev = prevPos.x + prevPos.width
                    val gap = pos.x - endOfPrev

                    // Threshold: 10% of char width OR 10% of char height (safer)
                    // If gap is significant, insert a phantom space
                    if (gap > (pos.width * 0.1f)) {
                        val spaceRect = normalize(endOfPrev, prevPos.y, gap, prevPos.height, pageWidth, pageHeight)
                        chars.add(PdfChar(" ", spaceRect, isSpace = true))
                    }
                }
            }

            // 3. NORMAL CHARACTER
            val normRect = normalize(pos.x, pos.y, pos.width, pos.height, pageWidth, pageHeight)
            chars.add(PdfChar(charStr, normRect, isSpace = false))
        }
    }

    private fun normalize(x: Float, y: Float, w: Float, h: Float, pageWidth: Float, pageHeight: Float): NormRect {
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