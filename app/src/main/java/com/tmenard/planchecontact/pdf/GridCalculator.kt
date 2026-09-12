package com.tmenard.planchecontact.pdf

import kotlin.math.ceil
import kotlin.math.floor

data class SheetSpec(
    val landscape: Boolean = false,
    val format: Int = 6
) {
    val pageWidthPt: Float get() = if (landscape) 841.89f else 595.28f
    val pageHeightPt: Float get() = if (landscape) 595.28f else 841.89f
}

data class SheetLayout(
    val columns: Int,
    val rowsPerPage: Int,
    val photosPerPage: Int,
    val cellWidthPt: Float,
    val cellHeightPt: Float,
    val pageCount: Int
)

object GridCalculator {
    const val MARGIN_MM = 0f
    const val GAP_MM = 0f
    const val MM_TO_PT = 72f / 25.4f
    const val PRINT_DPI = 300

    private val FORMAT_MM = mapOf(
        4 to (210 to 297),
        5 to (148 to 210),
        6 to (105 to 148),
        7 to (74 to 105),
        8 to (52 to 74),
        9 to (37 to 52),
        10 to (26 to 37)
    )

    fun computeLayout(spec: SheetSpec, photoCount: Int): SheetLayout {
        require(spec.format in FORMAT_MM.keys) { "format doit être entre 4 et 10" }
        require(photoCount >= 0) { "photoCount doit être >= 0" }
        val pageW = spec.pageWidthPt
        val pageH = spec.pageHeightPt
        if (spec.format == 4) {
            return SheetLayout(1, 1, 1, pageW, pageH, if (photoCount == 0) 1 else photoCount)
        }
        val margin = MARGIN_MM * MM_TO_PT
        val gap = GAP_MM * MM_TO_PT
        val (wMm, hMm) = FORMAT_MM.getValue(spec.format)
        val portraitW = wMm * MM_TO_PT
        val portraitH = hMm * MM_TO_PT
        fun fitAcross(cellW: Float) = floor((pageW - 2 * margin + gap) / (cellW + gap)).toInt()
        fun fitDown(cellH: Float) = floor((pageH - 2 * margin + gap) / (cellH + gap)).toInt()
        val pAcross = fitAcross(portraitW)
        val pDown = fitDown(portraitH)
        val lAcross = fitAcross(portraitH)
        val lDown = fitDown(portraitW)
        val portraitTotal = pAcross * pDown
        val landscapeTotal = lAcross * lDown
        val cols: Int
        val rows: Int
        val cellW: Float
        val cellH: Float
        if (portraitTotal >= landscapeTotal) {
            cols = pAcross; rows = pDown; cellW = portraitW; cellH = portraitH
        } else {
            cols = lAcross; rows = lDown; cellW = portraitH; cellH = portraitW
        }
        val perPage = cols * rows
        val pages = if (photoCount == 0) 1 else ceil(photoCount.toDouble() / perPage).toInt()
        return SheetLayout(cols, rows, perPage, cellW, cellH, pages)
    }

    fun thumbnailTargetPx(layout: SheetLayout): Int =
        (maxOf(layout.cellWidthPt, layout.cellHeightPt) / 72f * PRINT_DPI).toInt()
}
