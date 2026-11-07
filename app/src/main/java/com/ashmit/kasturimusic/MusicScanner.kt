package com.ashmit.kasturimusic

import android.content.Context
import android.provider.MediaStore

object MusicScanner {
    fun scan(ctx: Context): List<Song> {
        val out = ArrayList<Song>()
        val a = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        val sel = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " +
                MediaStore.Audio.Media.DURATION + " >= 15000"
        try {
            ctx.contentResolver.query(a, proj, sel, null, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    val artist = c.getString(iArtist) ?: ""
                    val album = c.getString(iAlbum) ?: ""
                    out.add(
                        Song(
                            c.getLong(iId),
                            c.getString(iTitle) ?: "Unknown",
                            if (artist.isBlank() || artist == "<unknown>") "Unknown artist" else artist,
                            if (album.isBlank() || album == "<unknown>") "Unknown album" else album,
                            c.getLong(iAlbumId),
                            c.getLong(iDur),
                            c.getString(iData) ?: ""
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // permission ya MediaStore error: khali list wapas
        }
        return out
    }
}
