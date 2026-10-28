/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.test

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.graphics.createBitmap
import org.robolectric.Robolectric
import java.io.File
import java.io.FileNotFoundException

/**
 * An in-memory media store with a few photos, videos and songs, which the media store of
 * Robolectric doesn't have. Their content is a plain colored image, for photos, for the frames of
 * videos and for the covers of songs.
 */
class TestMediaStoreProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val columns = projection ?: emptyArray()
        if (uri.pathSegments.contains("audio")) {
            return MatrixCursor(columns).apply {
                for (song in songs) {
                    addRow(columns.map { song.getColumn(it) })
                }
            }
        }
        val isVideo = uri.pathSegments.contains("video")
        return MatrixCursor(columns).apply {
            for (medium in media.filter { it.isVideo == isVideo }) {
                addRow(columns.map { medium.getColumn(it) })
            }
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = uri.lastPathSegment!!.toLong()
        val file = media.find { it.id == id }?.file
            ?: songs.first { it.id == id }.coverFile
            ?: throw FileNotFoundException("No cover for $uri")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private class TestMedium(
        val id: Long,
        val name: String,
        val album: String,
        val dateTaken: Long,
        val isVideo: Boolean,
        val file: File
    ) {
        fun getColumn(column: String): Any? =
            when (column) {
                MediaStore.MediaColumns._ID -> id
                @Suppress("DEPRECATION")
                MediaStore.MediaColumns.DATA -> "/storage/emulated/0/$album/$name"
                MediaStore.MediaColumns.DISPLAY_NAME -> name
                MediaStore.MediaColumns.MIME_TYPE -> if (isVideo) "video/mp4" else "image/png"
                MediaStore.Images.ImageColumns.DATE_TAKEN -> dateTaken
                MediaStore.MediaColumns.DATE_MODIFIED -> dateTaken / 1000
                MediaStore.MediaColumns.WIDTH -> IMAGE_SIZE
                MediaStore.MediaColumns.HEIGHT -> IMAGE_SIZE
                MediaStore.MediaColumns.SIZE -> file.length()
                MediaStore.Images.ImageColumns.BUCKET_ID -> album.hashCode().toLong()
                MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME -> album
                MediaStore.Video.VideoColumns.DURATION -> if (isVideo) 83_000L else null
                else -> null
            }
    }

    private class TestSong(
        val id: Long,
        val title: String,
        val artist: String?,
        val album: String,
        val trackNumber: Int,
        val coverFile: File?
    ) {
        @Suppress("DEPRECATION")
        fun getColumn(column: String): Any? =
            when (column) {
                MediaStore.Audio.Media._ID -> id
                MediaStore.Audio.Media.DATA -> "/storage/emulated/0/Music/$album/$title.mp3"
                MediaStore.Audio.Media.TITLE -> title
                MediaStore.Audio.Media.DISPLAY_NAME -> "$title.mp3"
                MediaStore.Audio.Media.ARTIST -> artist ?: MediaStore.UNKNOWN_STRING
                MediaStore.Audio.Media.ARTIST_ID -> artist.hashCode().toLong()
                MediaStore.Audio.Media.ALBUM -> album
                MediaStore.Audio.Media.ALBUM_ID -> album.hashCode().toLong()
                MediaStore.Audio.Media.TRACK -> trackNumber
                MediaStore.Audio.Media.DURATION -> 180_000L + id * 1000
                MediaStore.Audio.Media.DATE_MODIFIED -> 0L
                else -> null
            }
    }

    companion object {
        private const val IMAGE_SIZE = 64
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        private val COLORS = intArrayOf(
            Color.rgb(0xE5, 0x73, 0x73), Color.rgb(0x64, 0xB5, 0xF6), Color.rgb(0x81, 0xC7, 0x84),
            Color.rgb(0xFF, 0xB7, 0x4D), Color.rgb(0xBA, 0x68, 0xC8), Color.rgb(0x4D, 0xB6, 0xAC)
        )

        private val media = mutableListOf<TestMedium>()

        private val songs = mutableListOf<TestSong>()

        /**
         * Installs the provider with photos taken today, yesterday and a while ago in a "Camera"
         * album, screenshots, and a video.
         */
        fun install(directory: File) {
            media.clear()
            songs.clear()
            val now = System.currentTimeMillis()
            var id = 1L
            fun createImage(): File {
                val file = File(directory, "$id.png")
                createBitmap(IMAGE_SIZE, IMAGE_SIZE).apply {
                    eraseColor(COLORS[(id % COLORS.size).toInt()])
                    file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                return file
            }
            fun add(name: String, album: String, daysAgo: Int, isVideo: Boolean = false) {
                media += TestMedium(
                    id, name, album, now - daysAgo * DAY_MILLIS - id * 1000, isVideo,
                    createImage()
                )
                ++id
            }
            // The songs of an album share its cover.
            fun addSong(title: String, artist: String?, album: String, trackNumber: Int) {
                val coverFile = songs.find { it.album == album }?.coverFile
                    ?: createImage().takeIf { artist != null }
                songs += TestSong(id, title, artist, album, trackNumber, coverFile)
                ++id
            }
            repeat(5) { add("IMG_today_$it.png", "Camera", 0) }
            add("VID_today.mp4", "Camera", 0, isVideo = true)
            repeat(3) { add("IMG_yesterday_$it.png", "Camera", 1) }
            repeat(4) { add("Screenshot_$it.png", "Screenshots", 40) }
            addSong("Morning Light", "Nova", "Blue Sky", 2)
            addSong("Blue Sky", "Nova", "Blue Sky", 1)
            addSong("Falling Rain", "Nova", "Blue Sky", 3)
            addSong("City Lights", "Echo", "Night Drive", 1)
            addSong("Lost Tape", null, "Recordings", 0)
            Robolectric.buildContentProvider(TestMediaStoreProvider::class.java)
                .create(MediaStore.AUTHORITY)
        }
    }
}
