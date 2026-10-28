/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.audio

import android.media.MediaMetadataRetriever
import android.os.Build
import java8.nio.file.Path
import me.zhanghai.android.files.compat.METADATA_KEY_SAMPLERATE
import me.zhanghai.android.files.compat.use
import me.zhanghai.android.files.fileproperties.extractMetadataNotBlank
import me.zhanghai.android.files.util.setDataSource
import java.time.Duration

/** Loads the audio information of a file, blocking on I/O. */
fun loadAudioInfo(path: Path): AudioInfo {
    return MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(path)
        val title = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_TITLE
        )
        val artist = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_ARTIST
        )
        val album = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_ALBUM
        )
        val albumArtist = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST
        )
        val composer = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_COMPOSER
        )
        val discNumber = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER
        )
        val trackNumber = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER
        )
        val year = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_YEAR
        )
        val genre = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_GENRE
        )
        val duration = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLongOrNull()?.let { Duration.ofMillis(it) }
        val bitRate = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_BITRATE
        )?.toIntOrNull()
        val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            retriever.extractMetadataNotBlank(
                MediaMetadataRetriever::class.METADATA_KEY_SAMPLERATE
            )?.toIntOrNull()
        } else {
            null
        }
        AudioInfo(
            title, artist, album, albumArtist, composer, discNumber, trackNumber, year,
            genre, duration, bitRate, sampleRate
        )
    }
}
