/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.gallery

import android.net.Uri
import androidx.compose.runtime.Immutable
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.file.MimeType

/** A photo or a video indexed by the media store. */
@Immutable
data class GalleryMedia(
    val id: Long,
    val uri: Uri,
    val data: String,
    val displayName: String,
    val mimeType: MimeType,
    val isVideo: Boolean,
    /** When it was taken, or else last modified, in milliseconds since the epoch. */
    val dateTaken: Long,
    /** When it was last modified, in milliseconds since the epoch. */
    val dateModified: Long,
    val durationMillis: Long,
    val width: Int,
    val height: Int,
    val size: Long,
    val albumId: Long,
    val albumName: String?
) {
    val path: Path
        get() = Paths.get(data)
}

/** The photos and videos of a folder, newest first. */
@Immutable
data class GalleryAlbum(
    val id: Long,
    val name: String,
    val media: List<GalleryMedia>
) {
    val cover: GalleryMedia
        get() = media.first()
}

/** Like 1:23, or 1:02:03 from an hour, like the galleries of phones. */
fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    val hours = seconds / 3600
    val minutes = seconds / 60 % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds % 60)
    } else {
        "%d:%02d".format(minutes, seconds % 60)
    }
}
