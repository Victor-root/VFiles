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

/**
 * An in-memory media store with a few photos and videos, which the media store of Robolectric
 * doesn't have. Their content is a plain colored image, for photos and for the frames of videos.
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
        val isVideo = uri.pathSegments.contains("video")
        val columns = projection ?: emptyArray()
        return MatrixCursor(columns).apply {
            for (medium in media.filter { it.isVideo == isVideo }) {
                addRow(columns.map { medium.getColumn(it) })
            }
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val medium = media.first { it.id == uri.lastPathSegment!!.toLong() }
        return ParcelFileDescriptor.open(medium.file, ParcelFileDescriptor.MODE_READ_ONLY)
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

    companion object {
        private const val IMAGE_SIZE = 64
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        private val COLORS = intArrayOf(
            Color.rgb(0xE5, 0x73, 0x73), Color.rgb(0x64, 0xB5, 0xF6), Color.rgb(0x81, 0xC7, 0x84),
            Color.rgb(0xFF, 0xB7, 0x4D), Color.rgb(0xBA, 0x68, 0xC8), Color.rgb(0x4D, 0xB6, 0xAC)
        )

        private val media = mutableListOf<TestMedium>()

        /**
         * Installs the provider with photos taken today, yesterday and a while ago in a "Camera"
         * album, screenshots, and a video.
         */
        fun install(directory: File) {
            media.clear()
            val now = System.currentTimeMillis()
            var id = 1L
            fun add(name: String, album: String, daysAgo: Int, isVideo: Boolean = false) {
                val file = File(directory, "$id.png")
                createBitmap(IMAGE_SIZE, IMAGE_SIZE).apply {
                    eraseColor(COLORS[(id % COLORS.size).toInt()])
                    file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                media += TestMedium(
                    id, name, album, now - daysAgo * DAY_MILLIS - id * 1000, isVideo, file
                )
                ++id
            }
            repeat(5) { add("IMG_today_$it.png", "Camera", 0) }
            add("VID_today.mp4", "Camera", 0, isVideo = true)
            repeat(3) { add("IMG_yesterday_$it.png", "Camera", 1) }
            repeat(4) { add("Screenshot_$it.png", "Screenshots", 40) }
            Robolectric.buildContentProvider(TestMediaStoreProvider::class.java)
                .create(MediaStore.AUTHORITY)
        }
    }
}
