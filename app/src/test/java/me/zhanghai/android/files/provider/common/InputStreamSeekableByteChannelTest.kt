/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.NonWritableChannelException

class InputStreamSeekableByteChannelTest {
    private val content = ByteArray(100_000) { it.toByte() }
    private var openCount = 0
    private val channel = InputStreamSeekableByteChannel(content.size.toLong()) {
        openCount++
        ByteArrayInputStream(content)
    }

    private fun readAt(position: Long, size: Int): ByteArray {
        channel.position(position)
        val buffer = ByteBuffer.allocate(size)
        while (buffer.hasRemaining() && channel.read(buffer) != -1) {
        }
        return buffer.array().copyOf(buffer.position())
    }

    private fun expected(position: Int, size: Int) =
        content.copyOfRange(position, position + size).toList()

    @Test
    fun readsInOrderWithoutOpeningAgain() {
        assertEquals(expected(0, 10), readAt(0, 10).toList())
        assertEquals(expected(10, 10), readAt(10, 10).toList())
        assertEquals(1, openCount)
    }

    @Test
    fun seeksForward() {
        assertEquals(expected(50_000, 10), readAt(50_000, 10).toList())
        assertEquals(expected(70_000, 10), readAt(70_000, 10).toList())
        assertEquals(1, openCount)
    }

    @Test
    fun seeksBackwardByOpeningAgain() {
        assertEquals(expected(50_000, 10), readAt(50_000, 10).toList())
        assertEquals(expected(5, 10), readAt(5, 10).toList())
        assertEquals(2, openCount)
    }

    @Test
    fun endsAtSize() {
        assertEquals(expected(99_990, 10), readAt(99_990, 100).toList())
        channel.position(200_000)
        assertEquals(-1, channel.read(ByteBuffer.allocate(1)))
    }

    @Test
    fun isReadOnly() {
        assertThrows(NonWritableChannelException::class.java) {
            channel.write(ByteBuffer.allocate(1))
        }
    }

    @Test
    fun failsOnceClosed() {
        channel.close()
        assertThrows(ClosedChannelException::class.java) {
            channel.read(ByteBuffer.allocate(1))
        }
    }
}
