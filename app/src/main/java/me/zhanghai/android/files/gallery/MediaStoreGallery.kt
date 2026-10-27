/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.gallery

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.WorkerThread
import androidx.core.database.getStringOrNull
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asMimeTypeOrNull

/** The photos and videos indexed by the media store, on all the volumes. */
object MediaStoreGallery {
    val imagesUri: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    val videosUri: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    /** All the photos and videos, newest first. */
    @WorkerThread
    fun loadMedia(context: Context): List<GalleryMedia> =
        (query(context, isVideo = false) + query(context, isVideo = true))
            .sortedByDescending { it.dateTaken }

    @Suppress("DEPRECATION")
    private fun query(context: Context, isVideo: Boolean): List<GalleryMedia> {
        val contentUri = if (isVideo) videosUri else imagesUri
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            // The path is needed to share, open and show the files like the rest of the app.
            add(MediaStore.MediaColumns.DATA)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.Images.ImageColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.Images.ImageColumns.BUCKET_ID)
            add(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
            if (isVideo) {
                add(MediaStore.Video.VideoColumns.DURATION)
            }
        }.toTypedArray()
        val cursor = context.contentResolver.query(
            contentUri, projection, null, null,
            "${MediaStore.Images.ImageColumns.DATE_TAKEN} DESC"
        ) ?: return emptyList()
        return cursor.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val displayNameColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeTypeColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateTakenColumn =
                it.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_TAKEN)
            val dateModifiedColumn =
                it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val widthColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_ID)
            val albumNameColumn =
                it.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
            val durationColumn = if (isVideo) {
                it.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
            } else {
                -1
            }
            val defaultMimeType = if (isVideo) MimeType.VIDEO_ANY else MimeType.IMAGE_ANY
            buildList(it.count) {
                while (it.moveToNext()) {
                    val data = it.getStringOrNull(dataColumn) ?: continue
                    val id = it.getLong(idColumn)
                    // Seconds, unlike the date taken.
                    val dateModified = it.getLong(dateModifiedColumn) * 1000
                    add(
                        GalleryMedia(
                            id = id,
                            uri = ContentUris.withAppendedId(contentUri, id),
                            data = data,
                            displayName = it.getStringOrNull(displayNameColumn)
                                ?: data.substringAfterLast('/'),
                            mimeType = it.getStringOrNull(mimeTypeColumn)?.asMimeTypeOrNull()
                                ?: defaultMimeType,
                            isVideo = isVideo,
                            dateTaken = it.getLong(dateTakenColumn).takeIf { it > 0 }
                                ?: dateModified,
                            dateModified = dateModified,
                            durationMillis = if (durationColumn != -1) {
                                it.getLong(durationColumn)
                            } else {
                                0
                            },
                            width = it.getInt(widthColumn),
                            height = it.getInt(heightColumn),
                            size = it.getLong(sizeColumn),
                            albumId = it.getLong(albumIdColumn),
                            albumName = it.getStringOrNull(albumNameColumn)
                        )
                    )
                }
            }
        }
    }
}
