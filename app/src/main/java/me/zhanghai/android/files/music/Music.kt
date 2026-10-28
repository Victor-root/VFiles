/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.music

import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.util.MediaStoreLibrary
import java.text.Collator

/** The songs of the device, shared by the music screens. */
object Music : MediaStoreLibrary<MusicState>(
    uris = { listOf(MediaStoreMusic.audioUri) },
    load = { MusicState(MediaStoreMusic.loadTracks(application)) },
    onAccessDenied = { MusicState(emptyList(), isAccessDenied = true) }
)

class MusicState(tracks: List<MusicTrack>, val isAccessDenied: Boolean = false) {
    private val collator = Collator.getInstance()

    /** By title, like the music players of phones. */
    val tracks: List<MusicTrack> = tracks.sortedWith(compareBy(collator) { it.title })

    /** By name, with their songs in their order. */
    val albums: List<MusicAlbum> by lazy {
        tracks.groupBy { it.albumId }
            .map { (albumId, albumTracks) ->
                val sortedTracks = albumTracks.sortedWith(
                    compareBy<MusicTrack> { it.trackNumber }.thenBy(collator) { it.title }
                )
                MusicAlbum(
                    id = albumId,
                    name = sortedTracks.first().album
                        ?: sortedTracks.first().data.substringBeforeLast('/')
                            .substringAfterLast('/'),
                    // Various artists are left out.
                    artist = sortedTracks.mapTo(mutableSetOf()) { it.artist }.singleOrNull(),
                    tracks = sortedTracks
                )
            }
            .sortedWith(compareBy(collator) { it.name })
    }

    /** By name, with their albums by name. Songs of unknown artists are left out. */
    val artists: List<MusicArtist> by lazy {
        albums.flatMap { album -> album.tracks.map { album to it } }
            .filter { it.second.artist != null }
            .groupBy { it.second.artistId }
            .map { (artistId, pairs) ->
                MusicArtist(
                    id = artistId,
                    name = pairs.first().second.artist!!,
                    albums = pairs.groupBy({ it.first }, { it.second })
                        .map { (album, albumTracks) -> album.copy(tracks = albumTracks) }
                )
            }
            .sortedWith(compareBy(collator) { it.name })
    }

    fun getAlbum(albumId: Long): MusicAlbum? = albums.find { it.id == albumId }

    fun getArtist(artistId: Long): MusicArtist? = artists.find { it.id == artistId }
}
