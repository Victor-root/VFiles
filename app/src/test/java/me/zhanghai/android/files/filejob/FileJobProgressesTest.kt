/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FileJobProgressesTest {
    @Test
    fun followsTheRunningJobs() {
        FileJobProgresses.update(1, "Copying", null, 0, 0, true)
        FileJobProgresses.update(2, "Deleting", "a.txt", 4, 1, false)
        assertEquals(
            listOf(
                FileJobProgress(1, "Copying", null, null),
                FileJobProgress(2, "Deleting", "a.txt", 0.25f)
            ),
            FileJobProgresses.flow.value
        )
        FileJobProgresses.update(1, "Copying", "b.txt", 2, 2, false)
        assertEquals(
            FileJobProgress(1, "Copying", "b.txt", 1f), FileJobProgresses.flow.value.first()
        )
        FileJobProgresses.remove(1)
        assertEquals(listOf(2), FileJobProgresses.flow.value.map { it.id })
        val progresses = FileJobProgresses.flow.value
        FileJobProgresses.remove(3)
        assertSame(progresses, FileJobProgresses.flow.value)
        FileJobProgresses.remove(2)
        assertEquals(emptyList<FileJobProgress>(), FileJobProgresses.flow.value)
    }
}
