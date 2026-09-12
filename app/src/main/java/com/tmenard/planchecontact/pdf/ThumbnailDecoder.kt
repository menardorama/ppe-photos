package com.tmenard.planchecontact.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size

class ThumbnailDecoder(private val context: Context) {

    /** Décode une miniature échantillonnée (jamais la pleine résolution). null si illisible. */
    fun decode(uri: Uri, targetPx: Int): Bitmap? = try {
        context.contentResolver.loadThumbnail(uri, Size(targetPx, targetPx), null)
    } catch (e: Exception) {
        null
    }
}
