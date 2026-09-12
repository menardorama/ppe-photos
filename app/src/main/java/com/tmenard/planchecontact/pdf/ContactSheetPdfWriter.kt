package com.tmenard.planchecontact.pdf

import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.ByteArrayOutputStream

class ContactSheetPdfWriter(
    private val spec: SheetSpec,
    private val layout: SheetLayout
) {
    private val gap = GridCalculator.GAP_MM * GridCalculator.MM_TO_PT

    fun write(
        photoCount: Int,
        thumbnail: (index: Int) -> Bitmap?,
        onProgress: (done: Int, total: Int) -> Unit
    ): ByteArray {
        val builder = PdfBuilder(spec.pageWidthPt, spec.pageHeightPt)
        val gridW = layout.columns * layout.cellWidthPt + (layout.columns - 1) * gap
        val gridH = layout.rowsPerPage * layout.cellHeightPt + (layout.rowsPerPage - 1) * gap
        val startX = (spec.pageWidthPt - gridW) / 2f
        val startY = (spec.pageHeightPt - gridH) / 2f
        for (page in 0 until layout.pageCount) {
            builder.startPage()
            for (i in 0 until layout.photosPerPage) {
                val index = page * layout.photosPerPage + i
                if (index >= photoCount) break
                val col = i % layout.columns
                val row = i / layout.columns
                val x = startX + col * (layout.cellWidthPt + gap)
                val yTop = startY + row * (layout.cellHeightPt + gap)
                val yBottomLeft = spec.pageHeightPt - yTop - layout.cellHeightPt
                thumbnail(index)?.let { bmp ->
                    builder.drawImage(
                        prepareJpeg(bmp), x, yBottomLeft, layout.cellWidthPt, layout.cellHeightPt
                    )
                }
                builder.drawDashedRect(x, yBottomLeft, layout.cellWidthPt, layout.cellHeightPt)
                onProgress(index + 1, photoCount)
            }
            builder.finishPage()
        }
        return builder.build()
    }

    private fun prepareJpeg(bmp: Bitmap): JpegImage {
        var src = bmp
        val rotate = (src.height > src.width) != (layout.cellHeightPt > layout.cellWidthPt)
        if (rotate) {
            val matrix = Matrix().apply { postRotate(90f) }
            src = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, false)
        }
        val aspect = layout.cellWidthPt / layout.cellHeightPt
        val srcAspect = src.width.toFloat() / src.height
        var cropW = src.width
        var cropH = src.height
        if (srcAspect > aspect) {
            cropW = (src.height * aspect).toInt().coerceAtMost(src.width)
        } else {
            cropH = (src.width / aspect).toInt().coerceAtMost(src.height)
        }
        val cropped = if (cropW == src.width && cropH == src.height) {
            src
        } else {
            Bitmap.createBitmap(
                src, (src.width - cropW) / 2, (src.height - cropH) / 2, cropW, cropH
            )
        }
        val out = ByteArrayOutputStream()
        cropped.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return JpegImage(out.toByteArray(), cropped.width, cropped.height)
    }
}
