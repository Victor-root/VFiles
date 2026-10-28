/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import me.zhanghai.android.files.provider.common.observe
import me.zhanghai.android.files.util.closeSafe
import java.io.IOException

private const val THROTTLE_INTERVAL_MILLIS = 1000L

/**
 * The changes of this path, at most once per second, or none when it can't be observed. Observing
 * it, which may block, happens in the background.
 */
fun Path.observeChanges(): Flow<Unit> =
    callbackFlow {
        val pathObservable = try {
            observe(THROTTLE_INTERVAL_MILLIS)
        } catch (e: UnsupportedOperationException) {
            // Ignored.
            null
        } catch (e: IOException) {
            // Ignored.
            e.printStackTrace()
            null
        }
        if (pathObservable == null) {
            close()
            return@callbackFlow
        }
        pathObservable.addObserver { trySend(Unit) }
        awaitClose { pathObservable.closeSafe() }
    }
        .conflate()
        .flowOn(Dispatchers.IO)
