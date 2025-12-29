package com.project.pooket.core.utils

import com.project.pooket.data.local.annotation.PdfRect
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

class TextLocationStripper(
    private val targetStart: Int,
    private val targetEnd: Int
) : PDFTextStripper() {

    val highlightedRects = mutableListOf<PdfRect>()
    private var currentIndex = 0

    override fun writeString(string: String?, textPositions: MutableList<TextPosition>?) {
        textPositions?.forEach { pos ->
            if (currentIndex in targetStart until targetEnd) {
                // PDFBox coordinates: (x, y) is bottom-left
                highlightedRects.add(
                    PdfRect(
                        left = pos.xDirAdj,
                        top = pos.yDirAdj - pos.heightDir,
                        right = pos.xDirAdj + pos.widthDirAdj,
                        bottom = pos.yDirAdj
                    )
                )
            }
            currentIndex++
        }
        // Handle newline character index gap
        currentIndex++
    }
}