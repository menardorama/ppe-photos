package com.tmenard.planchecontact.pdf

import kotlin.math.ceil
import kotlin.math.floor

data class SheetSpec(
    val landscape: Boolean = false,
    val columns: Int = 5,
    val title: String = ""
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
    const val MARGIN_PT = 34f      // ~12 mm
    const val HEADER_PT = 40f
    const val FOOTER_PT = 24f
    const val GAP_PT = 8f
    const val CAPTION_PT = 16f
    const val CELL_ASPECT = 2f / 3f  // cellule 3:2
    const val PRINT_DPI = 300

    fun computeLayout(spec: SheetSpec, photoCount: Int): SheetLayout {
        val contentW = spec.pageWidthPt - 2 * MARGIN_PT
        val contentH = spec.pageHeightPt - 2 * MARGIN_PT - HEADER_PT - FOOTER_PT
        val cellW = (contentW - (spec.columns - 1) * GAP_PT) / spec.columns
        val cellH = cellW * CELL_ASPECT
        val rows = floor((contentH + GAP_PT) / (cellH + CAPTION_PT + GAP_PT)).toInt()
        val perPage = spec.columns * rows
        val pages = if (photoCount == 0) 1 else ceil(photoCount.toDouble() / perPage).toInt()
        return SheetLayout(spec.columns, rows, perPage, cellW, cellH, pages)
    }

    fun thumbnailTargetPx(layout: SheetLayout): Int =
        (layout.cellWidthPt / 72f * PRINT_DPI).toInt()
}
