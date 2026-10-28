/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.gallery

import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.util.MediaStoreLibrary

/** The photos and videos of the device, shared by the gallery and its viewer. */
object Gallery : MediaStoreLibrary<GalleryState>(
    uris = { listOf(MediaStoreGallery.imagesUri, MediaStoreGallery.videosUri) },
    load = { GalleryState(MediaStoreGallery.loadMedia(application)) },
    onAccessDenied = { GalleryState(emptyList(), isAccessDenied = true) }
)

class GalleryState(
    /** Newest first. */
    val media: List<GalleryMedia>,
    val isAccessDenied: Boolean = false
) {
    /** The folders with photos or videos, the one with the newest first. */
    val albums: List<GalleryAlbum> by lazy {
        media.groupBy { it.albumId }
            .map { (albumId, albumMedia) ->
                val cover = albumMedia.first()
                GalleryAlbum(
                    albumId,
                    cover.albumName ?: cover.data.substringBeforeLast('/').substringAfterLast('/'),
                    albumMedia
                )
            }
    }

    fun getAlbum(albumId: Long): GalleryAlbum? = albums.find { it.id == albumId }
}
