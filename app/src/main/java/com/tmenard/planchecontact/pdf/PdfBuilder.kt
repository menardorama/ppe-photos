package com.tmenard.planchecontact.pdf

import java.io.ByteArrayOutputStream
import java.util.Locale

data class JpegImage(val bytes: ByteArray, val widthPx: Int, val heightPx: Int)

class PdfBuilder(private val pageWidthPt: Float, private val pageHeightPt: Float) {

    private class PageData {
        val content = StringBuilder()
        var imageCount = 0
        var firstImageIndex = 0
        var contentObjPos = -1
        var pageObjPos = -1
        var contentsRefPos = -1
    }

    private class PatchableOut : ByteArrayOutputStream() {
        fun patch(pos: Int, bytes: ByteArray) {
            System.arraycopy(bytes, 0, buf, pos, bytes.size)
        }
    }

    private val sink = PatchableOut()
    private val offsets = HashMap<Int, Int>()
    private val pages = mutableListOf<PageData>()
    private var current: PageData? = null
    private var totalImages = 0
    private var built = false

    init {
        sink.write(HEADER)
    }

    fun startPage() {
        current?.let(::finalizePage)
        current = PageData().also {
            it.firstImageIndex = totalImages
            pages.add(it)
        }
    }

    fun drawImage(image: JpegImage, xPt: Float, yPt: Float, wPt: Float, hPt: Float) {
        val page = requireCurrent()
        val objNum = 3 + totalImages
        totalImages++
        page.imageCount++
        offsets[objNum] = sink.size()
        sink.write("$objNum 0 obj\n".toByteArray(ISO_8859_1))
        sink.write(
            ("<< /Type /XObject /Subtype /Image /Width ${image.widthPx} /Height ${image.heightPx}" +
                " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode" +
                " /Length ${image.bytes.size} >>").toByteArray(ISO_8859_1)
        )
        sink.write("\nstream\n".toByteArray(ISO_8859_1))
        sink.write(image.bytes)
        sink.write("\nendstream\nendobj\n".toByteArray(ISO_8859_1))
        page.content
            .append("q\n")
            .append(num(wPt)).append(" 0 0 ").append(num(hPt)).append(' ')
            .append(num(xPt)).append(' ').append(num(yPt)).append(" cm\n")
            .append("/Im${page.imageCount - 1} Do\n")
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
        current?.let {
            finalizePage(it)
            current = null
        }
    }

    fun build(): ByteArray {
        check(!built) { "build() ne doit être appelé qu'une seule fois" }
        built = true
        current?.let {
            finalizePage(it)
            current = null
        }
        val objectCount = 2 + totalImages + 2 * pages.size
        val size = objectCount + 1
        pages.forEachIndexed { p, page ->
            val pageNum = 3 + totalImages + 2 * p
            val contentNum = pageNum + 1
            offsets[pageNum] = page.pageObjPos
            offsets[contentNum] = page.contentObjPos
            patch(page.contentObjPos, "$contentNum 0 obj")
            patch(page.pageObjPos, "$pageNum 0 obj")
            patch(page.contentsRefPos, "$contentNum 0 R")
        }

        offsets[1] = sink.size()
        sink.write("1 0 obj\n".toByteArray(ISO_8859_1))
        sink.write("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".toByteArray(ISO_8859_1))

        offsets[2] = sink.size()
        val kids = (0 until pages.size).joinToString(" ") { "${3 + totalImages + 2 * it} 0 R" }
        sink.write(
            ("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>\nendobj\n")
                .toByteArray(ISO_8859_1)
        )

        val xrefOffset = sink.size()
        sink.write("xref\n0 $size\n".toByteArray(ISO_8859_1))
        sink.write("0000000000 65535 f \n".toByteArray(ISO_8859_1))
        for (n in 1..objectCount) {
            val off = offsets[n] ?: throw IllegalStateException("objet $n sans offset")
            sink.write(
                String.format(Locale.US, "%010d 00000 n \n", off).toByteArray(ISO_8859_1)
            )
        }
        sink.write(
            "trailer\n<< /Size $size /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n"
                .toByteArray(ISO_8859_1)
        )
        return sink.toByteArray()
    }

    private fun finalizePage(page: PageData) {
        if (page.contentObjPos >= 0) return
        val contentBytes = page.content.toString().toByteArray(ISO_8859_1)
        page.contentObjPos = sink.size()
        sink.write(SLOT)
        sink.write("\n<< /Length ${contentBytes.size} >>\nstream\n".toByteArray(ISO_8859_1))
        sink.write(contentBytes)
        sink.write("\nendstream\nendobj\n".toByteArray(ISO_8859_1))

        page.pageObjPos = sink.size()
        sink.write(SLOT)
        sink.write(
            ("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${num(pageWidthPt)} ${num(pageHeightPt)}]" +
                " /Contents ").toByteArray(ISO_8859_1)
        )
        page.contentsRefPos = sink.size()
        sink.write(SLOT)
        val xobjects = if (page.imageCount == 0) {
            ""
        } else {
            (0 until page.imageCount).joinToString(" ") { k -> "/Im$k ${3 + page.firstImageIndex + k} 0 R" }
        }
        val resources = if (page.imageCount == 0) "<< >>" else "<< /XObject << $xobjects >> >>"
        sink.write(" /Resources $resources >>\nendobj\n".toByteArray(ISO_8859_1))
    }

    private fun requireCurrent(): PageData =
        current ?: throw IllegalStateException("startPage() doit être appelé avant de dessiner")

    private fun patch(pos: Int, text: String) {
        val bytes = text.toByteArray(ISO_8859_1)
        check(bytes.size <= SLOT_WIDTH) { "numéro d'objet trop grand pour le slot" }
        val padded = ByteArray(SLOT_WIDTH) { ' '.code.toByte() }
        bytes.copyInto(padded)
        sink.patch(pos, padded)
    }

    private fun num(v: Float): String {
        val s = String.format(Locale.US, "%.2f", v)
        return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
    }

    private companion object {
        const val SLOT_WIDTH = 12
        val ISO_8859_1 = Charsets.ISO_8859_1
        val HEADER = "%PDF-1.4\n%âãÏÓ\n".toByteArray(ISO_8859_1)
        val SLOT = ByteArray(SLOT_WIDTH) { ' '.code.toByte() }
    }
}
