package com.tmenard.planchecontact.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

class ContactSheetPdfWriter(
    private val spec: SheetSpec,
    private val layout: SheetLayout
) {
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 0.8f
        color = Color.GRAY
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
    }
    private val gap = GridCalculator.GAP_MM * GridCalculator.MM_TO_PT

    fun write(
        photoCount: Int,
        thumbnail: (index: Int) -> Bitmap?,
        onProgress: (done: Int, total: Int) -> Unit
    ): ByteArray {
        val doc = PdfDocument()
        val gridW = layout.columns * layout.cellWidthPt + (layout.columns - 1) * gap
        val gridH = layout.rowsPerPage * layout.cellHeightPt + (layout.rowsPerPage - 1) * gap
        val startX = (spec.pageWidthPt - gridW) / 2f
        val startY = (spec.pageHeightPt - gridH) / 2f
        for (page in 0 until layout.pageCount) {
            val info = PdfDocument.PageInfo.Builder(
                spec.pageWidthPt.toInt(), spec.pageHeightPt.toInt(), page + 1
            ).create()
            val sheet = doc.startPage(info)
            val canvas = sheet.canvas
            for (i in 0 until layout.photosPerPage) {
                val index = page * layout.photosPerPage + i
                if (index >= photoCount) break
                drawCell(canvas, startX, startY, i, index, thumbnail(index))
                onProgress(index + 1, photoCount)
            }
            doc.finishPage(sheet)
        }
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private fun drawCell(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        slot: Int,
        index: Int,
        bitmap: Bitmap?
    ) {
        val col = slot % layout.columns
        val row = slot / layout.columns
        val x = startX + col * (layout.cellWidthPt + gap)
        val y = startY + row * (layout.cellHeightPt + gap)
        val cell = RectF(x, y, x + layout.cellWidthPt, y + layout.cellHeightPt)
        val bmp = bitmap
        if (bmp != null) {
            val rotate = (bmp.height > bmp.width) != (layout.cellHeightPt > layout.cellWidthPt)
            val scale = if (rotate) {
                maxOf(layout.cellHeightPt / bmp.width, layout.cellWidthPt / bmp.height)
            } else {
                maxOf(layout.cellWidthPt / bmp.width, layout.cellHeightPt / bmp.height)
            }
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val cx = cell.centerX()
            val cy = cell.centerY()
            canvas.save()
            canvas.clipRect(cell)
            if (rotate) canvas.rotate(90f, cx, cy)
            canvas.drawBitmap(bmp, null,
                RectF(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f), bitmapPaint)
            canvas.restore()
        }
        canvas.drawRect(cell, cutPaint)
    }
}
