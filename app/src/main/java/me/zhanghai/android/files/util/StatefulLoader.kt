/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/**
 * Loads a value in the background with [loadValue] into [flow], keeping the previous value while
 * it loads again. Loading again cancels the previous loading, by interrupting it since [loadValue]
 * usually blocks on I/O, and the result of a cancelled loading is never kept.
 */
class StatefulLoader<T>(
    private val coroutineScope: CoroutineScope,
    private val loadValue: () -> T
) {
    private val _flow = MutableStateFlow<Stateful<T>>(Loading(null))
    val flow: StateFlow<Stateful<T>>
        get() = _flow

    val value: Stateful<T>
        get() = _flow.value

    /** The number of collectors of [flow], like the UI while it's visible. */
    val subscriptionCount: StateFlow<Int>
        get() = _flow.subscriptionCount

    private var job: Job? = null

    fun load() {
        job?.cancel()
        _flow.value = Loading(_flow.value.value)
        job = coroutineScope.launch {
            _flow.value = try {
                Success(runInterruptible(Dispatchers.IO) { loadValue() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Failure(_flow.value.value, e)
            }
        }
    }

    /** Sets a value already loaded elsewhere, cancelling any loading. */
    fun setValue(value: T) {
        job?.cancel()
        job = null
        _flow.value = Success(value)
    }
}
