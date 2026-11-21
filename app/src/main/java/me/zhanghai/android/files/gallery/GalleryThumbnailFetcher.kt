/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.gallery

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Size
import androidx.core.graphics.drawable.toDrawable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import me.zhanghai.android.files.coil.VideoFrameFetcher
import me.zhanghai.android.files.util.runWithCancellationSignal
import okio.buffer
import okio.source

class GalleryMediaKeyer : Keyer<GalleryMedia> {
    override fun key(data: GalleryMedia, options: Options): String =
        "${data.uri}:${data.dateModified}"
}

/**
 * The thumbnail of a photo or a video, from the thumbnails kept by the media store when possible,
 * which is much faster than reading the file each time.
 */
class GalleryThumbnailFetcher(
    private val data: GalleryMedia,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val context: Context,
    private val videoFrameFetcherFactory: VideoFrameFetcher.Factory<GalleryMedia>
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val contentResolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val width = options.size.width.pxOrElse { DEFAULT_SIZE }
            val height = options.size.height.pxOrElse { DEFAULT_SIZE }
            // Failures are returned rather than thrown, which would crash the thread of the signal.
            val thumbnail = runWithCancellationSignal { signal ->
                runCatching {
                    contentResolver.loadThumbnail(data.uri, Size(width, height), signal)
                }
            }.getOrThrow()
            return ImageFetchResult(
                thumbnail.toDrawable(context.resources).asImage(), true, DataSource.DISK
            )
        }
        if (data.isVideo) {
            return videoFrameFetcherFactory.create(data, options, imageLoader).fetch()
        }
        val inputStream = checkNotNull(contentResolver.openInputStream(data.uri)) {
            "Cannot open ${data.uri}"
        }
        return SourceFetchResult(
            ImageSource(inputStream.source().buffer(), options.fileSystem), data.mimeType.value,
            DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<GalleryMedia> {
        private val videoFrameFetcherFactory = object : VideoFrameFetcher.Factory<GalleryMedia>() {
            override fun MediaMetadataRetriever.setDataSource(data: GalleryMedia) {
                setDataSource(context, data.uri)
            }
        }

        override fun create(
            data: GalleryMedia,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher =
            GalleryThumbnailFetcher(data, options, imageLoader, context, videoFrameFetcherFactory)
    }

    companion object {
        // @see android.provider.MediaStore.ThumbnailConstants.MINI_SIZE
        private const val DEFAULT_SIZE = 512
    }
}
