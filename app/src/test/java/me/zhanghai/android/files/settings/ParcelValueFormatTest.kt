/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ParcelValueFormatTest {
    private fun bytesOf(vararg ints: Int): ByteArray =
        ByteBuffer.allocate(ints.size * Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            .apply { ints.forEach { putInt(it) } }.array()

    @Test
    fun recognizesParcelableBeforeTiramisu() {
        // The type, then the length of the name of the class instead of the length of the value.
        assertTrue(isParcelValueBeforeTiramisu(bytesOf(PARCELABLE, 3, 1, 2, 3)))
    }

    @Test
    fun recognizesListBeforeTiramisu() {
        // The type, then the size of the list, then its elements.
        assertTrue(isParcelValueBeforeTiramisu(bytesOf(LIST, 1, PARCELABLE, 3, 1, 2, 3)))
        assertTrue(isParcelValueBeforeTiramisu(bytesOf(LIST, 0)))
    }

    @Test
    fun recognizesCurrentFormat() {
        assertFalse(isParcelValueBeforeTiramisu(bytesOf(PARCELABLE, 12, 1, 2, 3)))
        assertFalse(isParcelValueBeforeTiramisu(bytesOf(LIST, 4, 0)))
    }

    @Test
    fun ignoresOtherValues() {
        assertFalse(isParcelValueBeforeTiramisu(ByteArray(0)))
        assertFalse(isParcelValueBeforeTiramisu(bytesOf(0, 0, 0)))
    }

    private companion object {
        const val PARCELABLE = 4
        const val LIST = 11
    }
}
