package com.tmenard.planchecontact.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GridCalculatorTest {

    private fun spec(format: Int, landscape: Boolean = false) =
        SheetSpec(landscape = landscape, format = format)

    @Test
    fun `A4 pleine page`() {
        val l = GridCalculator.computeLayout(spec(4), 1)
        assertEquals(1, l.columns)
        assertEquals(1, l.rowsPerPage)
        assertEquals(1, l.photosPerPage)
        assertEquals(595.28f, l.cellWidthPt, 0.01f)
        assertEquals(841.89f, l.cellHeightPt, 0.01f)
        assertEquals(3, GridCalculator.computeLayout(spec(4), 3).pageCount)
    }

    @Test
    fun `A5 - 2 vignettes 210x148 par page`() {
        val l = GridCalculator.computeLayout(spec(5), 3)
        assertEquals(1, l.columns)
        assertEquals(2, l.rowsPerPage)
        assertEquals(2, l.photosPerPage)
        assertEquals(595.28f, l.cellWidthPt, 0.05f)
        assertEquals(419.53f, l.cellHeightPt, 0.05f)
        assertEquals(2, l.pageCount)
    }

    @Test
    fun `A6 - 4 vignettes 105x148 par page`() {
        val l = GridCalculator.computeLayout(spec(6), 3)
        assertEquals(2, l.columns)
        assertEquals(2, l.rowsPerPage)
        assertEquals(4, l.photosPerPage)
        assertEquals(297.64f, l.cellWidthPt, 0.05f)
        assertEquals(419.53f, l.cellHeightPt, 0.05f)
        assertEquals(1, l.pageCount)
    }

    @Test
    fun `A7 - 8 vignettes 105x74 par page`() {
        val l = GridCalculator.computeLayout(spec(7), 10)
        assertEquals(2, l.columns)
        assertEquals(4, l.rowsPerPage)
        assertEquals(8, l.photosPerPage)
        assertEquals(297.64f, l.cellWidthPt, 0.05f)
        assertEquals(209.76f, l.cellHeightPt, 0.05f)
        assertEquals(2, l.pageCount)
    }

    @Test
    fun `A8 - 16 vignettes 52x74 par page`() {
        val l = GridCalculator.computeLayout(spec(8), 20)
        assertEquals(4, l.columns)
        assertEquals(4, l.rowsPerPage)
        assertEquals(16, l.photosPerPage)
        assertEquals(147.40f, l.cellWidthPt, 0.05f)
        assertEquals(209.76f, l.cellHeightPt, 0.05f)
        assertEquals(2, l.pageCount)
    }

    @Test
    fun `A9 - 32 vignettes 52x37 par page`() {
        val l = GridCalculator.computeLayout(spec(9), 100)
        assertEquals(4, l.columns)
        assertEquals(8, l.rowsPerPage)
        assertEquals(32, l.photosPerPage)
        assertEquals(147.40f, l.cellWidthPt, 0.05f)
        assertEquals(104.88f, l.cellHeightPt, 0.05f)
        assertEquals(4, l.pageCount)
    }

    @Test
    fun `A10 - 64 vignettes 26x37 par page`() {
        val l = GridCalculator.computeLayout(spec(10), 100)
        assertEquals(8, l.columns)
        assertEquals(8, l.rowsPerPage)
        assertEquals(64, l.photosPerPage)
        assertEquals(73.70f, l.cellWidthPt, 0.05f)
        assertEquals(104.88f, l.cellHeightPt, 0.05f)
        assertEquals(2, l.pageCount)
    }

    @Test
    fun `page paysage - A6 - 4 vignettes 148x105 par page`() {
        val l = GridCalculator.computeLayout(spec(6, landscape = true), 3)
        assertEquals(2, l.columns)
        assertEquals(2, l.rowsPerPage)
        assertEquals(4, l.photosPerPage)
        assertEquals(419.53f, l.cellWidthPt, 0.05f)
        assertEquals(297.64f, l.cellHeightPt, 0.05f)
    }

    @Test
    fun `zero photo = une page`() {
        assertEquals(1, GridCalculator.computeLayout(spec(6), 0).pageCount)
    }

    @Test
    fun `formats hors domaine rejetes`() {
        assertThrows(IllegalArgumentException::class.java) {
            GridCalculator.computeLayout(spec(3), 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GridCalculator.computeLayout(spec(11), 1)
        }
    }

    @Test
    fun `taille cible miniature A4 en pixels`() {
        val l = GridCalculator.computeLayout(spec(4), 1)
        assertEquals(2338, GridCalculator.thumbnailTargetPx(l))
    }
}
