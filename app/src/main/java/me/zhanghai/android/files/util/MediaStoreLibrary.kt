/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.database.ContentObserver
import android.net.Uri
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
 * What [load] reads from the media store at [uris], shared by the screens showing it, and read again
 * when the media store changes while they are shown. Null until read for the first time, and
 * [onAccessDenied] without the access to the media.
 */
abstract class MediaStoreLibrary<T : Any>(
    private val uris: () -> List<Uri>,
    private val load: () -> T,
    private val onAccessDenied: () -> T
) {
    private val _flow = MutableStateFlow<T?>(null)
    val flow: StateFlow<T?> = _flow.asStateFlow()

    val value: T?
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
            for (uri in uris()) {
                contentResolver.registerContentObserver(uri, true, contentObserver)
            }
            isObservingChanges = true
            loadValue()
        } else if (isChangedWhileInactive) {
            isChangedWhileInactive = false
            loadValue()
        }
    }

    /** Reads it again, like once the access to the media is given. */
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
                    load()
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    onAccessDenied()
                }
            }
        }
    }

    companion object {
        // A burst of changes, like while copying many files, is read once it's over.
        private const val RELOAD_DELAY_MILLIS = 500L
    }
}
