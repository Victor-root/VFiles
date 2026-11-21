/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.video

import android.media.MediaMetadataRetriever
import android.util.Size
import java8.nio.file.Path
import me.zhanghai.android.files.compat.use
import me.zhanghai.android.files.fileproperties.date
import me.zhanghai.android.files.fileproperties.extractMetadataNotBlank
import me.zhanghai.android.files.fileproperties.location
import me.zhanghai.android.files.util.setDataSource
import java.time.Duration

/** Loads the video information of a file, blocking on I/O. */
fun loadVideoInfo(path: Path): VideoInfo {
    return MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(path)
        val title = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_TITLE
        )
        val width = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
        )?.toIntOrNull()
        val height = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
        )?.toIntOrNull()
        val dimensions = if (width != null && height != null) {
            Size(width, height)
        } else {
            null
        }
        val duration = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLongOrNull()?.let { Duration.ofMillis(it) }
        val date = retriever.date
        val location = retriever.location
        val bitRate = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_BITRATE
        )?.toLongOrNull()
        VideoInfo(title, dimensions, duration, date, location, bitRate)
    }
}
