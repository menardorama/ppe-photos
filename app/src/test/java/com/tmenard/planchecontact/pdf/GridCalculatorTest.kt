package com.tmenard.planchecontact.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class GridCalculatorTest {

    private val portrait = SheetSpec(landscape = false, columns = 5, title = "Test")

    @Test
    fun `portrait 5 colonnes`() {
        val l = GridCalculator.computeLayout(portrait, 47)
        assertEquals(99.056f, l.cellWidthPt, 0.01f)   // (595.28-68-4*8)/5
        assertEquals(66.03f, l.cellHeightPt, 0.01f)  // cellW * 2/3
        assertEquals(7, l.rowsPerPage)                // floor(717.89 / 90.037)
        assertEquals(35, l.photosPerPage)
        assertEquals(2, l.pageCount)
    }

    @Test
    fun `paysage 6 colonnes`() {
        val l = GridCalculator.computeLayout(SheetSpec(landscape = true, columns = 6), 60)
        assertEquals(122.31f, l.cellWidthPt, 0.01f)   // (841.89-68-5*8)/6
        assertEquals(4, l.rowsPerPage)
        assertEquals(24, l.photosPerPage)
        assertEquals(3, l.pageCount)
    }

    @Test
    fun `zero photo = une page`() {
        assertEquals(1, GridCalculator.computeLayout(portrait, 0).pageCount)
    }

    @Test
    fun `taille cible miniature en pixels`() {
        val l = GridCalculator.computeLayout(portrait, 10)
        assertEquals(412, GridCalculator.thumbnailTargetPx(l)) // 99.056/72*300
    }
}
