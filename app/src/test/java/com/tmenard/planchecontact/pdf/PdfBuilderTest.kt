package com.tmenard.planchecontact.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets.ISO_8859_1
import java.util.Locale

class PdfBuilderTest {

    private val originalLocale = Locale.getDefault()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.FRANCE)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    private val bufferedImageClass = Class.forName("java.awt.image.BufferedImage")

    /**
     * Petit JPEG en dégradé, généré par le vrai ImageIO du JDK hôte.
     * Réflexion obligatoire : la compilation des tests unitaires AGP se fait
     * contre android.jar, qui ne contient pas java.awt/javax.imageio ; le
     * JDK complet est en revanche disponible à l'exécution des tests.
     */
    private fun jpegBytes(w: Int, h: Int): ByteArray {
        val image = bufferedImageClass
            .getConstructor(Int::class.java, Int::class.java, Int::class.java)
            .newInstance(w, h, 1) // 1 = BufferedImage.TYPE_INT_RGB
        val setRGB = bufferedImageClass.getMethod(
            "setRGB", Int::class.java, Int::class.java, Int::class.java
        )
        for (y in 0 until h) {
            for (x in 0 until w) {
                val r = x * 255 / maxOf(1, w - 1)
                val g = y * 255 / maxOf(1, h - 1)
                setRGB.invoke(image, x, y, (r shl 16) or (g shl 8) or 128)
            }
        }
        val write = Class.forName("javax.imageio.ImageIO").getMethod(
            "write", Class.forName("java.awt.image.RenderedImage"),
            String::class.java, OutputStream::class.java
        )
        val out = ByteArrayOutputStream()
        write.invoke(null, image, "jpg", out)
        return out.toByteArray()
    }

    private fun img(w: Int, h: Int) = JpegImage(jpegBytes(w, h), w, h)

    /** Redécode un flux JPEG avec ImageIO (réflexion, cf. jpegBytes) et renvoie (largeur, hauteur). */
    private fun readJpeg(bytes: ByteArray): Pair<Int, Int> {
        val decoded = Class.forName("javax.imageio.ImageIO")
            .getMethod("read", java.io.InputStream::class.java)
            .invoke(null, ByteArrayInputStream(bytes))
            ?: throw AssertionError("le flux JPEG embarqué est illisible par ImageIO")
        val w = bufferedImageClass.getMethod("getWidth").invoke(decoded) as Int
        val h = bufferedImageClass.getMethod("getHeight").invoke(decoded) as Int
        return w to h
    }

    private fun build(actions: PdfBuilder.() -> Unit): ByteArray {
        val b = PdfBuilder(595.28f, 841.89f)
        b.actions()
        return b.build()
    }

    private data class XrefInfo(val xrefOffset: Int, val size: Int, val offsets: List<Int>)

    private fun xrefRows(pdf: ByteArray): XrefInfo {
        val s = String(pdf, ISO_8859_1)
        val sx = s.lastIndexOf("startxref\n")
        assertTrue("startxref manquant", sx >= 0)
        val after = sx + "startxref\n".length
        val lineEnd = s.indexOf('\n', after)
        val xrefOffset = s.substring(after, lineEnd).trim().toInt()
        assertTrue(
            "le mot-clé xref doit être à l'offset indiqué par startxref",
            s.substring(xrefOffset, xrefOffset + 4) == "xref"
        )
        val sizeStart = s.indexOf('\n', xrefOffset) + 1
        val sizeEnd = s.indexOf('\n', sizeStart)
        val size = s.substring(sizeStart, sizeEnd).trim().split(" ")[1].toInt()
        val rowsStart = sizeEnd + 1
        val offsets = (0 until size).map { i ->
            val row = String(pdf.copyOfRange(rowsStart + i * 20, rowsStart + i * 20 + 20), ISO_8859_1)
            if (i == 0) {
                assertEquals("0000000000 65535 f \n", row)
                -1
            } else {
                assertEquals("chaque ligne xref fait exactement 20 octets", 20, row.length)
                row.substring(0, 10).trim().toInt()
            }
        }
        return XrefInfo(xrefOffset, size, offsets)
    }

    private fun assertXrefCoherent(pdf: ByteArray) {
        val (_, size, offsets) = xrefRows(pdf)
        val s = String(pdf, ISO_8859_1)
        for (obj in 1 until size) {
            val off = offsets[obj]
            assertEquals(
                "la ligne xref de l'objet $obj doit pointer exactement sur « $obj 0 obj »",
                "$obj 0 obj",
                s.substring(off, off + "$obj 0 obj".length)
            )
        }
    }

    private fun objectString(pdf: ByteArray, info: XrefInfo, n: Int): String {
        val start = info.offsets[n]
        val end = if (n + 1 < info.offsets.size && info.offsets[n + 1] > start) {
            info.offsets[n + 1]
        } else {
            info.xrefOffset
        }
        return String(pdf, start, end - start, ISO_8859_1)
    }

    private fun payloadOf(obj: String): ByteArray {
        val start = obj.indexOf("stream\n") + "stream\n".length
        val end = obj.lastIndexOf("\nendstream")
        assertTrue("objet sans stream valide", start >= "stream\n".length && end > start)
        return obj.substring(start, end).toByteArray(ISO_8859_1)
    }

    @Test
    fun `en-tete et fin de fichier`() {
        val pdf = build {
            startPage()
            finishPage()
        }
        val s = String(pdf, ISO_8859_1)
        assertTrue(s.startsWith("%PDF-1.4\n%âãÏÓ\n"))
        assertTrue(s.endsWith("%%EOF\n"))
        assertEquals(0xE2.toByte(), pdf[10])
        assertEquals(0xE3.toByte(), pdf[11])
        assertEquals(0xCF.toByte(), pdf[12])
        assertEquals(0xD3.toByte(), pdf[13])
    }

    @Test
    fun `xref pointe sur chaque objet`() {
        val pdf = build {
            startPage()
            drawImage(img(8, 6), 0f, 0f, 100f, 100f)
            finishPage()
        }
        assertXrefCoherent(pdf)
        val info = xrefRows(pdf)
        assertEquals(6, info.size) // 5 objets (catalogue, pages, image, page, contenu) + entrée libre
    }

    @Test
    fun `images en DCTDecode avec dimensions et longueur exactes`() {
        val j1 = img(8, 6)
        val j2 = img(4, 4)
        val pdf = build {
            startPage()
            drawImage(j1, 0f, 0f, 100f, 100f)
            drawImage(j2, 100f, 0f, 50f, 50f)
            finishPage()
        }
        val (xrefOffset, size, offsets) = xrefRows(pdf)
        assertEquals(7, size) // 6 objets (2 + 2 images + 1 page + 1 contenu) + entrée libre
        val s = String(pdf, ISO_8859_1)
        assertEquals(2, s.split("/Subtype /Image").size - 1)
        assertEquals(2, s.split("/Filter /DCTDecode").size - 1)
        val obj3 = objectString(pdf, XrefInfo(xrefOffset, size, offsets), 3)
        val obj4 = objectString(pdf, XrefInfo(xrefOffset, size, offsets), 4)
        assertTrue(obj3.contains("/Width 8"))
        assertTrue(obj3.contains("/Height 6"))
        assertTrue(obj4.contains("/Width 4"))
        assertTrue(obj4.contains("/Height 4"))
        val payload3 = payloadOf(obj3)
        assertEquals(Regex("/Length (\\d+)").find(obj3)!!.groupValues[1].toInt(), payload3.size)
        assertTrue(payload3.contentEquals(j1.bytes))
        val decoded = readJpeg(payload3)
        assertEquals(8, decoded.first)
        assertEquals(6, decoded.second)
    }

    @Test
    fun `contenu des pages - placement et y-flip`() {
        val pdf = build {
            startPage()
            drawImage(img(8, 6), 0f, 841.89f - 200f, 300f, 200f)
            finishPage()
        }
        val (xrefOffset, _, offsets) = xrefRows(pdf)
        // T=1 : page = 4, contenu = 5
        val content = String(payloadOf(objectString(pdf, XrefInfo(xrefOffset, 0, offsets), 5)), ISO_8859_1)
        assertEquals("q\n300 0 0 200 0 641.89 cm\n/Im0 Do\nQ\n", content)
    }

    @Test
    fun `traits de decoupe en pointilles`() {
        val pdf = build {
            startPage()
            drawDashedRect(10f, 20f, 100f, 50f)
            drawDashedRect(10f, 80f, 100f, 50f)
            finishPage()
        }
        val (xrefOffset, _, offsets) = xrefRows(pdf)
        // T=0 : page = 3, contenu = 4
        val content = String(payloadOf(objectString(pdf, XrefInfo(xrefOffset, 0, offsets), 4)), ISO_8859_1)
        assertTrue(content.contains("0.8 w"))
        assertTrue(content.contains("[4 3] 0 d"))
        assertTrue(content.contains("0.5 G"))
        assertTrue(content.contains("10 20 100 50 re"))
        assertTrue(content.contains("10 80 100 50 re"))
        assertEquals(2, content.split("\nS\n").size - 1)
    }

    @Test
    fun `pages multiples et ressources locales`() {
        val pdf = build {
            startPage()
            drawImage(img(8, 6), 0f, 0f, 100f, 100f)
            drawImage(img(6, 8), 100f, 0f, 100f, 100f)
            finishPage()
            startPage()
            drawImage(img(4, 4), 0f, 0f, 50f, 50f)
            finishPage()
        }
        val (xrefOffset, size, offsets) = xrefRows(pdf)
        assertEquals(10, size) // 9 objets : 2 + 3 images + 2 × (page + contenu) + entrée libre
        val s = String(pdf, ISO_8859_1)
        assertTrue(s.contains("/Kids [6 0 R 8 0 R] /Count 2"))
        assertTrue(s.contains("/Size 10"))
        val info = XrefInfo(xrefOffset, size, offsets)
        val page1 = objectString(pdf, info, 6)
        val page2 = objectString(pdf, info, 8)
        assertTrue(page1.contains("/Im0 3 0 R"))
        assertTrue(page1.contains("/Im1 4 0 R"))
        assertTrue(page2.contains("/Im0 5 0 R"))
        assertFalse(page2.contains("/Im1"))
        val c1 = String(payloadOf(objectString(pdf, info, 7)), ISO_8859_1)
        val c2 = String(payloadOf(objectString(pdf, info, 9)), ISO_8859_1)
        assertTrue(c1.contains("/Im0 Do"))
        assertTrue(c1.contains("/Im1 Do"))
        assertTrue(c2.contains("/Im0 Do"))
        assertFalse(c2.contains("/Im1 Do"))
        assertTrue(objectString(pdf, info, 3).contains("/Width 8"))
        assertTrue(objectString(pdf, info, 4).contains("/Width 6"))
        assertTrue(objectString(pdf, info, 5).contains("/Width 4"))
    }

    @Test
    fun `page sans image`() {
        val pdf = build {
            startPage()
            drawDashedRect(0f, 0f, 595.28f, 841.89f)
            finishPage()
        }
        assertXrefCoherent(pdf)
        val (xrefOffset, size, offsets) = xrefRows(pdf)
        assertEquals(5, size) // 4 objets + entrée libre
        val s = String(pdf, ISO_8859_1)
        assertFalse(s.contains("/Subtype /Image"))
        assertFalse(s.contains("/XObject"))
        val page = objectString(pdf, XrefInfo(xrefOffset, size, offsets), 3)
        assertTrue(page.contains("/Resources << >>"))
    }

    @Test
    fun `nombres independants de la locale`() {
        // @Before a imposé la locale FRANCE (séparateur décimal « , ») : les nombres
        // du PDF doivent néanmoins utiliser le point.
        val pdf = build {
            startPage()
            drawImage(img(8, 6), 0f, 419.53f, 100f, 200f)
            finishPage()
        }
        val (xrefOffset, _, offsets) = xrefRows(pdf)
        val content = String(payloadOf(objectString(pdf, XrefInfo(xrefOffset, 0, offsets), 5)), ISO_8859_1)
        assertTrue("la valeur 419.53 doit apparaître avec un point", content.contains("419.53"))
        assertFalse(content.contains("419,53"))
    }
}
