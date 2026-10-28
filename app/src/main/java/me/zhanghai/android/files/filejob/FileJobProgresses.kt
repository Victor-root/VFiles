/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A snapshot of one in-flight file job, mirrored from its progress notification. */
data class FileJobProgress(
    val id: Int,
    val title: String,
    val detail: String?,
    // null means indeterminate (e.g. while scanning).
    val fraction: Float?
)

/**
 * The file jobs currently running, so the UI can show in-app progress alongside the notification.
 * Updated (off the main thread) from [postNotification] and cleared when a job ends.
 */
object FileJobProgresses {
    private val progresses = LinkedHashMap<Int, FileJobProgress>()

    private val _flow = MutableStateFlow<List<FileJobProgress>>(emptyList())
    val flow: StateFlow<List<FileJobProgress>> = _flow.asStateFlow()

    fun update(
        id: Int,
        title: CharSequence,
        detail: CharSequence?,
        max: Int,
        progress: Int,
        indeterminate: Boolean
    ) {
        val fraction = if (indeterminate || max <= 0) {
            null
        } else {
            (progress.toFloat() / max).coerceIn(0f, 1f)
        }
        synchronized(progresses) {
            progresses[id] = FileJobProgress(id, title.toString(), detail?.toString(), fraction)
            _flow.value = progresses.values.toList()
        }
    }

    fun remove(id: Int) {
        synchronized(progresses) {
            if (progresses.remove(id) != null) {
                _flow.value = progresses.values.toList()
            }
        }
    }
}
