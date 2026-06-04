/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import androidx.lifecycle.MutableLiveData

/** A snapshot of one in-flight file job, mirrored from its progress notification. */
data class FileJobProgress(
    val id: Int,
    val title: String,
    val detail: String?,
    // null means indeterminate (e.g. while scanning).
    val fraction: Float?
)

/**
 * Live list of the file jobs currently running, so the UI can show in-app progress alongside the
 * notification. Updated (off the main thread) from [postNotification] and cleared when a job ends.
 */
object FileJobProgressLiveData : MutableLiveData<List<FileJobProgress>>(emptyList()) {
    private val progresses = LinkedHashMap<Int, FileJobProgress>()

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
            postValue(progresses.values.toList())
        }
    }

    fun remove(id: Int) {
        synchronized(progresses) {
            if (progresses.remove(id) != null) {
                postValue(progresses.values.toList())
            }
        }
    }
}
