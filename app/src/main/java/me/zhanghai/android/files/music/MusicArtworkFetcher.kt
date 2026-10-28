/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.music

import android.content.ContentUris
import android.content.Context
import android.net.Uri
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
import me.zhanghai.android.files.util.runWithCancellationSignal
import okio.buffer
import okio.source

class MusicTrackKeyer : Keyer<MusicTrack> {
    override fun key(data: MusicTrack, options: Options): String =
        "${data.uri}:${data.dateModified}"
}

/** The cover of a song, kept by the media store, which fails when the song has none. */
class MusicArtworkFetcher(
    private val data: MusicTrack,
    private val options: Options,
    private val context: Context
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
        val albumArtUri = ContentUris.withAppendedId(ALBUM_ART_URI, data.albumId)
        val inputStream = checkNotNull(contentResolver.openInputStream(albumArtUri)) {
            "Cannot open $albumArtUri"
        }
        return SourceFetchResult(
            ImageSource(inputStream.source().buffer(), options.fileSystem), null, DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<MusicTrack> {
        override fun create(data: MusicTrack, options: Options, imageLoader: ImageLoader): Fetcher =
            MusicArtworkFetcher(data, options, context)
    }

    companion object {
        // @see android.provider.MediaStore.ThumbnailConstants.MINI_SIZE
        private const val DEFAULT_SIZE = 512

        // The covers of albums before Android 10, which has no public constant for them.
        private val ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")
    }
}
