/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import androidx.annotation.StringRes
import me.zhanghai.android.files.R
import me.zhanghai.android.libarchive.Archive

// The ordinal is persisted, so only add new types at the end.
enum class ArchiveType(
    val extension: String,
    @get:StringRes val labelRes: Int,
    val format: Int,
    val filter: Int,
    val isPasswordSupported: Boolean
) {
    ZIP(
        "zip", R.string.file_create_archive_type_zip, Archive.FORMAT_ZIP, Archive.FILTER_NONE, true
    ),
    TAR_XZ(
        "tar.xz", R.string.file_create_archive_type_tar_xz, Archive.FORMAT_TAR, Archive.FILTER_XZ,
        false
    ),
    SEVEN_Z(
        "7z", R.string.file_create_archive_type_7z, Archive.FORMAT_7ZIP, Archive.FILTER_NONE, false
    )
}
