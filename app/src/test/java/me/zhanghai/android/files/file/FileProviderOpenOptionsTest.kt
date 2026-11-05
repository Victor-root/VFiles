/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.os.ParcelFileDescriptor
import java8.nio.file.StandardOpenOption
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FileProviderOpenOptionsTest {
    private fun openOptions(mode: String) = ParcelFileDescriptor.parseMode(mode).toOpenOptions()

    @Test
    fun readsWithoutWriting() {
        assertEquals(setOf(StandardOpenOption.READ), openOptions("r"))
    }

    @Test
    fun replacesFileOnPlainWrite() {
        val expected = setOf(
            StandardOpenOption.WRITE, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
        assertEquals(expected, openOptions("w"))
        assertEquals(expected, openOptions("wt"))
    }

    @Test
    fun keepsContentOnReadAndWrite() {
        assertEquals(
            setOf(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE),
            openOptions("rw")
        )
    }

    @Test
    fun truncatesOnReadWriteTruncate() {
        assertEquals(
            setOf(
                StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            ),
            openOptions("rwt")
        )
    }

    @Test
    fun appendsWithoutTruncating() {
        assertEquals(
            setOf(
                StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND
            ),
            openOptions("wa")
        )
    }
}
