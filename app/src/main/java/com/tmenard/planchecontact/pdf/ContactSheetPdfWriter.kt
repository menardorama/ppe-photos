package com.tmenard.planchecontact.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

class ContactSheetPdfWriter(
    private val spec: SheetSpec,
    private val layout: SheetLayout
) {
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val emptyPaint = Paint().apply { color = Color.LTGRAY }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 0.8f; color = Color.GRAY; style = Paint.Style.STROKE
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f; color = Color.DKGRAY; textAlign = Paint.Align.CENTER
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f; isFakeBoldText = true; color = Color.BLACK
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f; color = Color.DKGRAY; textAlign = Paint.Align.RIGHT
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f; color = Color.GRAY; textAlign = Paint.Align.CENTER
    }

    fun write(
        photoCount: Int,
        dateText: String,
        thumbnail: (index: Int) -> Bitmap?,
        onProgress: (done: Int, total: Int) -> Unit
    ): ByteArray {
        val doc = PdfDocument()
        for (page in 0 until layout.pageCount) {
            val info = PdfDocument.PageInfo.Builder(
                spec.pageWidthPt.toInt(), spec.pageHeightPt.toInt(), page + 1
            ).create()
            val sheet = doc.startPage(info)
            val canvas = sheet.canvas
            drawHeader(canvas, dateText)
            drawFooter(canvas, page)
            for (i in 0 until layout.photosPerPage) {
                val index = page * layout.photosPerPage + i
                if (index >= photoCount) break
                drawCell(canvas, i, index, thumbnail(index))
                onProgress(index + 1, photoCount)
            }
            doc.finishPage(sheet)
        }
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private fun drawHeader(canvas: Canvas, dateText: String) {
        val title = spec.title.ifBlank { "Planche contact" }
        canvas.drawText(title, GridCalculator.MARGIN_PT,
            GridCalculator.MARGIN_PT + 20f, titlePaint)
        canvas.drawText(dateText, spec.pageWidthPt - GridCalculator.MARGIN_PT,
            GridCalculator.MARGIN_PT + 16f, datePaint)
    }

    private fun drawFooter(canvas: Canvas, page: Int) {
        canvas.drawText("Page ${page + 1} / ${layout.pageCount}",
            spec.pageWidthPt / 2f,
            spec.pageHeightPt - GridCalculator.MARGIN_PT - 9f, footerPaint)
    }

    private fun drawCell(canvas: Canvas, slot: Int, index: Int, bitmap: Bitmap?) {
        val col = slot % layout.columns
        val row = slot / layout.columns
        val x = GridCalculator.MARGIN_PT + col * (layout.cellWidthPt + GridCalculator.GAP_PT)
        val y = GridCalculator.MARGIN_PT + GridCalculator.HEADER_PT +
            row * (layout.cellHeightPt + GridCalculator.CAPTION_PT + GridCalculator.GAP_PT)
        val cell = RectF(x, y, x + layout.cellWidthPt, y + layout.cellHeightPt)
        canvas.drawRect(cell, borderPaint)
        val bmp = bitmap
        if (bmp != null) {
            val scale = minOf(cell.width() / bmp.width, cell.height() / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val dx = cell.left + (cell.width() - dw) / 2f
            val dy = cell.top + (cell.height() - dh) / 2f
            canvas.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), bitmapPaint)
        } else {
            canvas.drawRect(cell, emptyPaint)
        }
        canvas.drawText("${index + 1}", cell.centerX(), cell.bottom + 11f, captionPaint)
    }
}
