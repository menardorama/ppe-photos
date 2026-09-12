package com.tmenard.planchecontact

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class PdfCompressionInstrumentedTest {

    private fun noisyBitmap(w: Int, h: Int, config: Bitmap.Config): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, config)
        val canvas = Canvas(bmp)
        val rnd = Random(42)
        for (y in 0 until h step 4) {
            for (x in 0 until w step 4) {
                canvas.drawRect(
                    x.toFloat(), y.toFloat(), (x + 4).toFloat(), (y + 4).toFloat(),
                    Paint().apply { color = Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)) }
                )
            }
        }
        return bmp
    }

    private fun writePdf(bmp: Bitmap): ByteArray {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        page.canvas.drawBitmap(bmp, null, android.graphics.RectF(0f, 0f, 595f, 842f), null)
        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private fun report(label: String, bytes: ByteArray) {
        val dct = bytes.toString(Charsets.ISO_8859_1).split("DCTDecode").size - 1
        val flate = bytes.toString(Charsets.ISO_8859_1).split("FlateDecode").size - 1
        android.util.Log.i("PdfCompressTest",
            "$label: ${bytes.size / 1024} Ko, DCT=$dct, Flate=$flate")
    }

    @Test
    fun compareEncodings() {
        val argb = noisyBitmap(540, 1200, Bitmap.Config.ARGB_8888)
        report("ARGB_8888", writePdf(argb))

        val opaque = noisyBitmap(540, 1200, Bitmap.Config.ARGB_8888)
        opaque.setHasAlpha(false)
        report("ARGB opaque", writePdf(opaque))

        report("RGB_565", writePdf(noisyBitmap(540, 1200, Bitmap.Config.RGB_565)))

        val jpegRoundtrip = ByteArrayOutputStream().also {
            noisyBitmap(540, 1200, Bitmap.Config.ARGB_8888).compress(
                Bitmap.CompressFormat.JPEG, 85, it)
        }.toByteArray()
        val fromJpeg = BitmapFactory.decodeByteArray(jpegRoundtrip, 0, jpegRoundtrip.size)
        report("JPEG roundtrip", writePdf(fromJpeg))
    }
}
