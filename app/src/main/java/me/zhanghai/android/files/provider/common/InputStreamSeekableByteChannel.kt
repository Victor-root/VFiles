/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import java8.nio.channels.SeekableByteChannel
import me.zhanghai.android.files.util.closeSafe
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ClosedChannelException
import java.nio.channels.NonWritableChannelException
import java.nio.channels.ReadableByteChannel

/**
 * Reads what can only be read from its start, opening it again to seek backwards.
 */
class InputStreamSeekableByteChannel(
    private val size: Long,
    private val openInputStream: () -> InputStream
) : SeekableByteChannel {
    private var inputStream: InputStream? = null
    private var channel: ReadableByteChannel? = null
    private var streamPosition = 0L
    private var position = 0L
    private var isOpen = true

    @Throws(IOException::class)
    override fun read(destination: ByteBuffer): Int {
        ensureOpen()
        if (position >= size) {
            return -1
        }
        val readSize = seekStream().read(destination)
        if (readSize > 0) {
            position += readSize.toLong()
            streamPosition = position
        }
        return readSize
    }

    @Throws(IOException::class)
    private fun seekStream(): ReadableByteChannel {
        if (channel != null && streamPosition > position) {
            closeStream()
        }
        val channel = channel ?: openStream()
        val skipSize = position - streamPosition
        if (skipSize > 0) {
            skipStream(skipSize)
            streamPosition = position
        }
        return channel
    }

    @Throws(IOException::class)
    private fun openStream(): ReadableByteChannel {
        val inputStream = openInputStream()
        this.inputStream = inputStream
        streamPosition = 0
        return Channels.newChannel(inputStream).also { channel = it }
    }

    @Throws(IOException::class)
    private fun skipStream(size: Long) {
        val buffer = ByteArray(minOf(size, DEFAULT_BUFFER_SIZE.toLong()).toInt())
        var remainingSize = size
        while (remainingSize > 0) {
            val readSize = inputStream!!.read(
                buffer, 0, minOf(remainingSize, buffer.size.toLong()).toInt()
            )
            if (readSize == -1) {
                throw EOFException()
            }
            remainingSize -= readSize.toLong()
        }
    }

    private fun closeStream() {
        channel = null
        inputStream?.closeSafe()
        inputStream = null
    }

    override fun write(source: ByteBuffer): Int = throw NonWritableChannelException()

    @Throws(IOException::class)
    override fun position(): Long {
        ensureOpen()
        return position
    }

    @Throws(IOException::class)
    override fun position(newPosition: Long): SeekableByteChannel {
        require(newPosition >= 0) { "Negative position: $newPosition" }
        ensureOpen()
        position = newPosition
        return this
    }

    @Throws(IOException::class)
    override fun size(): Long {
        ensureOpen()
        return size
    }

    override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()

    override fun isOpen(): Boolean = isOpen

    override fun close() {
        isOpen = false
        closeStream()
    }

    @Throws(ClosedChannelException::class)
    private fun ensureOpen() {
        if (!isOpen) {
            throw ClosedChannelException()
        }
    }
}
