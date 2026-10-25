/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import android.graphics.Color as AndroidColor

@JvmInline
value class Color(@param:ColorInt val value: Int)

val Color.alpha: Int
    @IntRange(from = 0, to = 255)
    get() = AndroidColor.alpha(value)

val Color.red: Int
    @IntRange(from = 0, to = 255)
    get() = AndroidColor.red(value)

val Color.green: Int
    @IntRange(from = 0, to = 255)
    get() = AndroidColor.green(value)

val Color.blue: Int
    @IntRange(from = 0, to = 255)
    get() = AndroidColor.blue(value)
