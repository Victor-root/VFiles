/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.music

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.WorkerThread
import androidx.core.database.getStringOrNull

/** The songs indexed by the media store, on all the volumes. */
object MediaStoreMusic {
    val audioUri: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    /** All the songs, but not ringtones and the like. */
    @Suppress("DEPRECATION")
    @WorkerThread
    fun loadTracks(context: Context): List<MusicTrack> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            // The path is needed to share, open and show the files like the rest of the app.
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        val cursor = context.contentResolver.query(
            audioUri, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null
        ) ?: return emptyList()
        return cursor.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val displayNameColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val trackColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateModifiedColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            buildList(it.count) {
                while (it.moveToNext()) {
                    val data = it.getStringOrNull(dataColumn) ?: continue
                    val id = it.getLong(idColumn)
                    add(
                        MusicTrack(
                            id = id,
                            uri = ContentUris.withAppendedId(audioUri, id),
                            data = data,
                            title = it.getStringOrNull(titleColumn)?.takeIf { it.isNotBlank() }
                                ?: it.getStringOrNull(displayNameColumn)
                                ?: data.substringAfterLast('/'),
                            artist = it.getStringOrNull(artistColumn).takeIfKnown(),
                            artistId = it.getLong(artistIdColumn),
                            album = it.getStringOrNull(albumColumn).takeIfKnown(),
                            albumId = it.getLong(albumIdColumn),
                            // Like 2003 for the 3rd song of the 2nd disc.
                            trackNumber = it.getInt(trackColumn),
                            durationMillis = it.getLong(durationColumn),
                            // Seconds.
                            dateModified = it.getLong(dateModifiedColumn) * 1000
                        )
                    )
                }
            }
        }
    }

    // The media store fills what it doesn't know with "<unknown>".
    private fun String?.takeIfKnown(): String? =
        takeIf { !it.isNullOrBlank() && it != MediaStore.UNKNOWN_STRING }
}
