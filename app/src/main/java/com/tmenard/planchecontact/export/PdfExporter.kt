package com.tmenard.planchecontact.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    fun fileName(): String = "planche-contact-" +
        SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.FRANCE).format(Date()) + ".pdf"

    fun saveToDownloads(context: Context, bytes: ByteArray, displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/PlancheContact")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Impossible de créer le fichier PDF")
        (resolver.openOutputStream(uri) as OutputStream).use { it.write(bytes) }
        return uri
    }
}
