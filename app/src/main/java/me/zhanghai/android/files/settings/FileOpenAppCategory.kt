/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import androidx.annotation.StringRes
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.isAudio
import me.zhanghai.android.files.file.isImage
import me.zhanghai.android.files.file.isPdf
import me.zhanghai.android.files.file.isVideo

/**
 * A broad file category for which the user can pick a default app to open with, à la Windows'
 * "Default apps". [queryMimeType] is the representative MIME used to list candidate apps.
 */
enum class FileOpenAppCategory(
    @StringRes val titleRes: Int,
    val queryMimeType: String,
    val matches: (MimeType) -> Boolean
) {
    IMAGE(R.string.settings_default_apps_image, "image/*", { it.isImage }),
    AUDIO(R.string.settings_default_apps_audio, "audio/*", { it.isAudio }),
    VIDEO(R.string.settings_default_apps_video, "video/*", { it.isVideo }),
    PDF(R.string.settings_default_apps_pdf, "application/pdf", { it.isPdf }),
    TEXT(R.string.settings_default_apps_text, "text/*", { it.value.startsWith("text/") });

    companion object {
        fun forMimeType(mimeType: MimeType): FileOpenAppCategory? =
            entries.firstOrNull { it.matches(mimeType) }
    }
}
