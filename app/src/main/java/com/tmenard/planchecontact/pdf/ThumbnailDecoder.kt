package com.tmenard.planchecontact.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size

class ThumbnailDecoder(private val context: Context) {

    /** Décode une miniature échantillonnée (jamais la pleine résolution). null si illisible. */
    fun decode(uri: Uri, targetPx: Int): Bitmap? = try {
        val bmp = context.contentResolver.loadThumbnail(uri, Size(targetPx, targetPx), null)
        if (bmp.config == Bitmap.Config.HARDWARE) {
            bmp.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bmp
        }
    } catch (e: Exception) {
        null
    }
}
