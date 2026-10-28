/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.gallery

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.app.applicationScope

/**
 * The photos and videos of the device, shared by the gallery and its viewer, and read again when
 * the media store changes while they are shown. Null until read for the first time.
 */
object Gallery {
    // A burst of changes, like while copying many photos, is read once it's over.
    private const val RELOAD_DELAY_MILLIS = 500L

    private val _flow = MutableStateFlow<GalleryState?>(null)
    val flow: StateFlow<GalleryState?> = _flow.asStateFlow()

    val value: GalleryState?
        get() = _flow.value

    private var loadJob: Job? = null

    private var isObservingChanges = false

    private var isChangedWhileInactive = false

    private val isActive: Boolean
        get() = _flow.subscriptionCount.value > 0

    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (isActive) {
                loadValue(RELOAD_DELAY_MILLIS)
            } else {
                isChangedWhileInactive = true
            }
        }
    }

    init {
        applicationScope.launch {
            _flow.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .filter { it }
                .collect { onActive() }
        }
    }

    private fun onActive() {
        if (!isObservingChanges) {
            val contentResolver = application.contentResolver
            for (uri in listOf(MediaStoreGallery.imagesUri, MediaStoreGallery.videosUri)) {
                contentResolver.registerContentObserver(uri, true, contentObserver)
            }
            isObservingChanges = true
            loadValue()
        } else if (isChangedWhileInactive) {
            isChangedWhileInactive = false
            loadValue()
        }
    }

    /** Reads them again, like once the access to them is given. */
    fun reload() {
        if (isObservingChanges) {
            loadValue()
        }
    }

    private fun loadValue(delayMillis: Long = 0) {
        loadJob?.cancel()
        loadJob = applicationScope.launch {
            delay(delayMillis)
            _flow.value = runInterruptible(Dispatchers.IO) {
                try {
                    GalleryState(MediaStoreGallery.loadMedia(application))
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    GalleryState(emptyList(), isAccessDenied = true)
                }
            }
        }
    }
}

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
