/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.checksum

import java8.nio.file.Path
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.util.toHexString

/** Loads the checksum information of a file, blocking on I/O. */
fun loadChecksumInfo(path: Path): ChecksumInfo {
    val messageDigests =
        ChecksumInfo.Algorithm.entries.associateWith { it.createMessageDigest() }
    path.newInputStream().use { inputStream ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val readSize = inputStream.read(buffer)
            if (readSize == -1) {
                break
            }
            messageDigests.values.forEach { it.update(buffer, 0, readSize) }
        }
    }
    return ChecksumInfo(
        messageDigests.mapValues { it.value.digest().toHexString() }
    )
}
