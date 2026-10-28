/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.music

import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/** The controller of the music playing in the background, null while connecting to it. */
@Composable
fun rememberMusicController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val sessionToken =
            SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            {
                controller = runCatching { future.get() }
                    .onFailure { it.printStackTrace() }
                    .getOrNull()
            },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
    }
    return controller
}

/** What [player] is playing, read again when it changes. */
@Composable
fun rememberCurrentMediaItem(player: Player): CurrentMediaItem {
    var current by remember(player) { mutableStateOf(player.toCurrentMediaItem()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_MEDIA_METADATA_CHANGED,
                        Player.EVENT_TIMELINE_CHANGED
                    )
                ) {
                    current = player.toCurrentMediaItem()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return current
}

/** The song playing, null when nothing is. */
class CurrentMediaItem(val mediaItem: MediaItem?, val metadata: MediaMetadata) {
    val title: String?
        get() = metadata.title?.toString() ?: metadata.displayTitle?.toString()

    val artist: String?
        get() = metadata.artist?.toString()

    /**
     * Its cover for Coil, read from the file once it's playing, or else from the media store for
     * its songs.
     */
    val cover: Any? by lazy {
        metadata.artworkData ?: metadata.artworkUri
            ?: mediaItem?.mediaId?.toLongOrNull()?.let { trackId ->
                Music.value?.tracks?.find { it.id == trackId }
            }
    }
}

private fun Player.toCurrentMediaItem(): CurrentMediaItem =
    CurrentMediaItem(currentMediaItem, mediaMetadata)

/** Plays [tracks] from the one at [startIndex], in a random order with [shuffle]. */
fun Player.playTracks(tracks: List<MusicTrack>, startIndex: Int = 0, shuffle: Boolean = false) {
    playItems(
        tracks.map {
            MusicItem(it.id.toString(), it.uri, it.title, it.title, it.artist, it.album)
        },
        startIndex, shuffle
    )
}

/**
 * A song to play, from the media store with its ID, or from a file whose name is shown until its
 * [title] and [artist] are read from it.
 */
class MusicItem(
    val mediaId: String,
    val uri: Uri,
    val displayTitle: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null
)

/** Plays [items] from the one at [startIndex], in a random order with [shuffle]. */
fun Player.playItems(items: List<MusicItem>, startIndex: Int = 0, shuffle: Boolean = false) {
    val mediaItems = items.map {
        MediaItem.Builder()
            .setMediaId(it.mediaId)
            .setUri(it.uri)
            // The URI is only passed to the service through its request metadata.
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(it.uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setDisplayTitle(it.displayTitle)
                    .setTitle(it.title)
                    .setArtist(it.artist)
                    .setAlbumTitle(it.album)
                    .build()
            )
            .build()
    }
    shuffleModeEnabled = shuffle
    setMediaItems(mediaItems, startIndex, 0)
    prepare()
    play()
}
