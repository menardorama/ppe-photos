package com.tmenard.planchecontact.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GridCalculatorTest {

    private fun spec(format: Int, landscape: Boolean = false) =
        SheetSpec(landscape = landscape, format = format)

    @Test
    fun `A4 pleine page sans marge`() {
        val l = GridCalculator.computeLayout(spec(4), 1)
        assertEquals(1, l.columns)
        assertEquals(1, l.rowsPerPage)
        assertEquals(1, l.photosPerPage)
        assertEquals(595.28f, l.cellWidthPt, 0.01f)
        assertEquals(841.89f, l.cellHeightPt, 0.01f)
        assertEquals(3, GridCalculator.computeLayout(spec(4), 3).pageCount)
    }

    @Test
    fun `A6 - 2 vignettes 148x105 par page portrait`() {
        val l = GridCalculator.computeLayout(spec(6), 3)
        assertEquals(1, l.columns)
        assertEquals(2, l.rowsPerPage)
        assertEquals(2, l.photosPerPage)
        assertEquals(419.53f, l.cellWidthPt, 0.05f)
        assertEquals(297.64f, l.cellHeightPt, 0.05f)
        assertEquals(2, l.pageCount)
    }

    @Test
    fun `A7 - 4 vignettes 74x105 par page portrait`() {
        val l = GridCalculator.computeLayout(spec(7), 10)
        assertEquals(2, l.columns)
        assertEquals(2, l.rowsPerPage)
        assertEquals(4, l.photosPerPage)
        assertEquals(209.76f, l.cellWidthPt, 0.05f)
        assertEquals(297.64f, l.cellHeightPt, 0.05f)
        assertEquals(3, l.pageCount)
    }

    @Test
    fun `A9 - 25 vignettes 37x52 par page portrait`() {
        val l = GridCalculator.computeLayout(spec(9), 100)
        assertEquals(5, l.columns)
        assertEquals(5, l.rowsPerPage)
        assertEquals(25, l.photosPerPage)
        assertEquals(104.88f, l.cellWidthPt, 0.05f)
        assertEquals(147.40f, l.cellHeightPt, 0.05f)
        assertEquals(4, l.pageCount)
    }

    @Test
    fun `A10 - 50 vignettes 37x26 par page portrait`() {
        val l = GridCalculator.computeLayout(spec(10), 100)
        assertEquals(5, l.columns)
        assertEquals(10, l.rowsPerPage)
        assertEquals(50, l.photosPerPage)
        assertEquals(104.88f, l.cellWidthPt, 0.05f)
        assertEquals(73.70f, l.cellHeightPt, 0.05f)
        assertEquals(2, l.pageCount)
    }

    @Test
    fun `page paysage - A6 devient 2 vignettes portrait`() {
        val l = GridCalculator.computeLayout(spec(6, landscape = true), 3)
        assertEquals(2, l.columns)
        assertEquals(1, l.rowsPerPage)
        assertEquals(2, l.photosPerPage)
        assertEquals(297.64f, l.cellWidthPt, 0.05f)
        assertEquals(419.53f, l.cellHeightPt, 0.05f)
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
        assertEquals(3507, GridCalculator.thumbnailTargetPx(l))
    }
}
