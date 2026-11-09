package com.ashmit.kasturimusic

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import java.io.File

/**
 * source: 0 = phone ka local gaana, 1 = online stream (Jamendo), 2 = download kiya hua gaana (app ke andar file).
 * Online/download gaano ki id negative hoti hai taaki local ids se na takraye.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val path: String,
    val source: Int = 0,
    val streamUrl: String? = null,
    val artUrl: String? = null,
    val downloadUrl: String? = null
) {
    val uri: Uri
        get() = when (source) {
            1 -> Uri.parse(streamUrl ?: "")
            2 -> Uri.fromFile(File(path))
            else -> ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        }

    val folder: String
        get() = path.substringBeforeLast('/', "")
}
