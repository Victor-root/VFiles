/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.gallery

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.app.backgroundExecutor
import java.util.concurrent.Future

/**
 * The photos and videos of the device, shared by the gallery and its viewer, and read again when
 * the media store changes. Null until read for the first time.
 */
object GalleryLiveData : LiveData<GalleryState>() {
    // A burst of changes, like while copying many photos, is read once it's over.
    private const val RELOAD_DELAY_MILLIS = 500L

    private val handler = Handler(Looper.getMainLooper())

    private val loadValueRunnable = Runnable { loadValue() }

    private var future: Future<Unit>? = null

    private var isObservingChanges = false

    private var isChangedWhileInactive = false

    private val contentObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            if (hasActiveObservers()) {
                handler.removeCallbacks(loadValueRunnable)
                handler.postDelayed(loadValueRunnable, RELOAD_DELAY_MILLIS)
            } else {
                isChangedWhileInactive = true
            }
        }
    }

    override fun onActive() {
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

    private fun loadValue() {
        future?.cancel(true)
        future = backgroundExecutor.submit<Unit> {
            val value = try {
                GalleryState(MediaStoreGallery.loadMedia(application))
            } catch (e: SecurityException) {
                e.printStackTrace()
                GalleryState(emptyList(), isAccessDenied = true)
            }
            postValue(value)
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
