/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
class CharsetDetectionTest {
    @Test
    fun detectsUtf8() {
        assertEquals(StandardCharsets.UTF_8, "Déjà vu".toByteArray().detectCharset())
        assertEquals(StandardCharsets.UTF_8, ByteArray(0).detectCharset())
    }

    @Test
    fun detectsUtf16FromByteOrderMark() {
        val text = "Déjà vu, ça coûte 10 euros"
        val littleEndian = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            text.toByteArray(StandardCharsets.UTF_16LE)
        assertEquals(StandardCharsets.UTF_16, littleEndian.detectCharset())
        assertEquals(text, String(littleEndian, littleEndian.detectCharset()))
        assertEquals(
            StandardCharsets.UTF_16, text.toByteArray(StandardCharsets.UTF_16).detectCharset()
        )
    }

    @Test
    fun guessesOtherEncodings() {
        val text = "Déjà vu : où est passé le garçon ? Ça coûte très cher.\n".repeat(20)
        val bytes = text.toByteArray(StandardCharsets.ISO_8859_1)
        assertEquals(text, String(bytes, bytes.detectCharset()))
        val japanese = "日本語のテキストファイルです。\n".repeat(20)
        val shiftJisBytes = japanese.toByteArray(Charset.forName("Shift_JIS"))
        assertEquals(japanese, String(shiftJisBytes, shiftJisBytes.detectCharset()))
    }
}
