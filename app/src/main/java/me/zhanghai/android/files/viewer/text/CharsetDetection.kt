/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import org.mozilla.universalchardet.UniversalDetector
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** The encoding of this text: the one of its byte order mark, or else UTF-8 if valid, or a guess. */
internal fun ByteArray.detectCharset(): Charset =
    when {
        hasUtf16ByteOrderMark() -> StandardCharsets.UTF_16
        isValidUtf8() -> StandardCharsets.UTF_8
        else -> guessCharset() ?: StandardCharsets.UTF_8
    }

private fun ByteArray.hasUtf16ByteOrderMark(): Boolean {
    if (size < 2) {
        return false
    }
    val first = this[0].toInt() and 0xFF
    val second = this[1].toInt() and 0xFF
    return first == 0xFF && second == 0xFE || first == 0xFE && second == 0xFF
}

private fun ByteArray.isValidUtf8(): Boolean =
    try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(this))
        true
    } catch (e: CharacterCodingException) {
        false
    }

private fun ByteArray.guessCharset(): Charset? {
    val detector = UniversalDetector()
    detector.handleData(this)
    detector.dataEnd()
    val name = detector.detectedCharset ?: return null
    return runCatching { Charset.forName(name) }.getOrNull()
}
