/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.storage.StorageVolume
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.app.storageManager
import me.zhanghai.android.files.compat.pathCompat
import me.zhanghai.android.files.compat.registerReceiverCompat

/** The storage volumes of the device, updated as they are mounted and unmounted. */
object StorageVolumeList {
    // Not a state flow, which would drop a volume changing state only, since volumes are equal when
    // their paths are.
    private val _flow = MutableSharedFlow<List<StorageVolume>>(
        replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val flow: SharedFlow<List<StorageVolume>>
        get() = _flow

    val value: List<StorageVolume>
        get() = _flow.replayCache.single()

    init {
        loadValue()
        application.registerReceiverCompat(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    loadValue()
                }
            }, IntentFilter().apply {
                // @see android.os.storage.VolumeInfo#sEnvironmentToBroadcast
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_CHECKING)
                addAction(Intent.ACTION_MEDIA_MOUNTED)
                addAction(Intent.ACTION_MEDIA_EJECT)
                addAction(Intent.ACTION_MEDIA_UNMOUNTABLE)
                addAction(Intent.ACTION_MEDIA_REMOVED)
                addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
                // The "file" data scheme is required to receive these broadcasts.
                // @see https://stackoverflow.com/a/7143298
                addDataScheme(ContentResolver.SCHEME_FILE)
            }, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun loadValue() {
        _flow.tryEmit(storageManager.storageVolumes)
    }

    /**
     * Re-query the mounted volumes on demand (e.g. when the app is resumed). Modern Android does not
     * always send the "file"-scheme media broadcasts for USB drives, so a drive plugged in while the
     * app was in the background would otherwise never show up until a restart. Only emits when the
     * set of volumes actually changed, to avoid needless navigation-drawer rebuilds.
     */
    fun refresh() {
        val volumes = storageManager.storageVolumes
        if (value.map { it.pathCompat } != volumes.map { it.pathCompat }) {
            _flow.tryEmit(volumes)
        }
    }
}
