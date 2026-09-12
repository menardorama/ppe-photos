package com.tmenard.planchecontact.pdf

import java.io.ByteArrayOutputStream
import java.util.Locale

data class JpegImage(val bytes: ByteArray, val widthPx: Int, val heightPx: Int)

class PdfBuilder(private val pageWidthPt: Float, private val pageHeightPt: Float) {

    private class PageData {
        val images = mutableListOf<JpegImage>()
        val content = StringBuilder()
    }

    private val pages = mutableListOf<PageData>()
    private var current: PageData? = null

    fun startPage() {
        current = PageData().also { pages.add(it) }
    }

    fun drawImage(image: JpegImage, xPt: Float, yPt: Float, wPt: Float, hPt: Float) {
        val page = requireCurrent()
        val k = page.images.size
        page.images.add(image)
        page.content
            .append("q\n")
            .append(num(wPt)).append(" 0 0 ").append(num(hPt)).append(' ')
            .append(num(xPt)).append(' ').append(num(yPt)).append(" cm\n")
            .append("/Im").append(k).append(" Do\n")
            .append("Q\n")
    }

    fun drawDashedRect(xPt: Float, yPt: Float, wPt: Float, hPt: Float) {
        val page = requireCurrent()
        page.content
            .append("0.8 w\n[4 3] 0 d\n0.5 G\n")
            .append(num(xPt)).append(' ').append(num(yPt)).append(' ')
            .append(num(wPt)).append(' ').append(num(hPt)).append(" re\nS\n")
    }

    fun finishPage() {
        current = null
    }

    fun build(): ByteArray {
        val totalImages = pages.sumOf { it.images.size }
        val objectCount = 2 + totalImages + 2 * pages.size
        val size = objectCount + 1
        val out = ByteArrayOutputStream()
        val offsets = IntArray(size)

        out.write(HEADER)

        fun writePlainObject(n: Int, body: String) {
            offsets[n] = out.size()
            out.write("$n 0 obj\n".toByteArray(ISO_8859_1))
            out.write(body.toByteArray(ISO_8859_1))
            out.write("\nendobj\n".toByteArray(ISO_8859_1))
        }

        fun writeStreamObject(n: Int, dict: String, payload: ByteArray) {
            offsets[n] = out.size()
            out.write("$n 0 obj\n".toByteArray(ISO_8859_1))
            out.write(dict.toByteArray(ISO_8859_1))
            out.write("\nstream\n".toByteArray(ISO_8859_1))
            out.write(payload)
            out.write("\nendstream\nendobj\n".toByteArray(ISO_8859_1))
        }

        writePlainObject(1, "<< /Type /Catalog /Pages 2 0 R >>")

        val kids = (0 until pages.size).joinToString(" ") { "${3 + totalImages + 2 * it} 0 R" }
        writePlainObject(2, "<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>")

        var imageIndex = 0
        pages.forEach { page ->
            page.images.forEach { image ->
                writeStreamObject(
                    3 + imageIndex,
                    "<< /Type /XObject /Subtype /Image /Width ${image.widthPx} /Height ${image.heightPx}" +
                        " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode" +
                        " /Length ${image.bytes.size} >>",
                    image.bytes
                )
                imageIndex++
            }
        }

        var imageCursor = 0
        pages.forEachIndexed { p, page ->
            val pageObj = 3 + totalImages + 2 * p
            val contentObj = pageObj + 1
            val xobjects = if (page.images.isEmpty()) {
                ""
            } else {
                (0 until page.images.size).joinToString(" ") { k -> "/Im$k ${3 + imageCursor + k} 0 R" }
            }
            val resources = if (page.images.isEmpty()) "<< >>" else "<< /XObject << $xobjects >> >>"
            writePlainObject(
                pageObj,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${num(pageWidthPt)} ${num(pageHeightPt)}]" +
                    " /Contents $contentObj 0 R /Resources $resources >>"
            )
            val contentBytes = page.content.toString().toByteArray(ISO_8859_1)
            writeStreamObject(contentObj, "<< /Length ${contentBytes.size} >>", contentBytes)
            imageCursor += page.images.size
        }

        val xrefOffset = out.size()
        out.write("xref\n0 $size\n".toByteArray(ISO_8859_1))
        out.write("0000000000 65535 f \n".toByteArray(ISO_8859_1))
        for (n in 1..objectCount) {
            out.write(
                String.format(Locale.US, "%010d 00000 n \n", offsets[n]).toByteArray(ISO_8859_1)
            )
        }
        out.write(
            "trailer\n<< /Size $size /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n"
                .toByteArray(ISO_8859_1)
        )
        return out.toByteArray()
    }

    private fun requireCurrent(): PageData =
        current ?: throw IllegalStateException("startPage() doit être appelé avant de dessiner")

    private fun num(v: Float): String {
        val s = String.format(Locale.US, "%.2f", v)
        return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
    }

    private companion object {
        val ISO_8859_1 = Charsets.ISO_8859_1
        val HEADER = "%PDF-1.4\n%âãÏÓ\n".toByteArray(ISO_8859_1)
    }
}
