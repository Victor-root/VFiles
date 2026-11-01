/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import androidx.compose.runtime.staticCompositionLocalOf
import java8.nio.file.Path
import me.zhanghai.android.files.file.MimeType

/**
 * Picks files for another app, from the screens opened above the file list, like the gallery.
 * Only the files whose type [accepts] can be picked, several of them if [allowsMultiple].
 */
class FilePicker(
    val accepts: (MimeType) -> Boolean,
    val allowsMultiple: Boolean,
    val pick: (List<Path>) -> Unit
)

/** The picker when files are picked for another app, or else null. */
val LocalFilePicker = staticCompositionLocalOf<FilePicker?> { null }
