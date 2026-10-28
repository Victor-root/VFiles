/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.music

import android.net.Uri
import androidx.compose.runtime.Immutable
import java8.nio.file.Path
import java8.nio.file.Paths

/** A song indexed by the media store. */
@Immutable
data class MusicTrack(
    val id: Long,
    val uri: Uri,
    val data: String,
    val title: String,
    /** Null when unknown. */
    val artist: String?,
    val artistId: Long,
    /** Null when unknown. */
    val album: String?,
    val albumId: Long,
    /** Its number in its album, 0 when unknown. */
    val trackNumber: Int,
    val durationMillis: Long,
    /** When it was last modified, in milliseconds since the epoch. */
    val dateModified: Long
) {
    val path: Path
        get() = Paths.get(data)
}

/** The songs of an album, in their order. */
@Immutable
data class MusicAlbum(
    val id: Long,
    val name: String,
    /** Null when unknown or when it's various artists. */
    val artist: String?,
    val tracks: List<MusicTrack>
) {
    val cover: MusicTrack
        get() = tracks.first()
}

/** The albums and songs of an artist. */
@Immutable
data class MusicArtist(
    val id: Long,
    val name: String,
    val albums: List<MusicAlbum>
) {
    val tracks: List<MusicTrack>
        get() = albums.flatMap { it.tracks }
}
