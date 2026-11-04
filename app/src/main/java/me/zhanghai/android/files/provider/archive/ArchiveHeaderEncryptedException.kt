/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive

import java8.nio.file.FileSystemException

/** The names of the files in an archive are encrypted too, which can't be read. */
class ArchiveHeaderEncryptedException(file: String?, reason: String?) :
    FileSystemException(file, null, reason)
