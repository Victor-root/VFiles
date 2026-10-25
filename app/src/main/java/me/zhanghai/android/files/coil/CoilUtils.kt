/*
 * Copyright (c) 2022 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.coil

import android.graphics.Bitmap
import coil3.decode.DataSource
import coil3.size.Dimension
import coil3.size.Scale
import coil3.size.Size
import coil3.size.isOriginal
import coil3.size.pxOrElse
import java8.nio.file.Path
import me.zhanghai.android.files.filelist.isRemotePath

val Bitmap.Config.isHardware: Boolean
    get() = this == Bitmap.Config.HARDWARE

fun Bitmap.Config.toSoftware(): Bitmap.Config = if (isHardware) Bitmap.Config.ARGB_8888 else this

val Path.dataSource: DataSource
    get() = if (isRemotePath) DataSource.NETWORK else DataSource.DISK

inline fun Size.widthPx(scale: Scale, original: () -> Int): Int =
    if (isOriginal) original() else width.toPx(scale)

inline fun Size.heightPx(scale: Scale, original: () -> Int): Int =
    if (isOriginal) original() else height.toPx(scale)

fun Dimension.toPx(scale: Scale) =
    pxOrElse {
        when (scale) {
            Scale.FILL -> Int.MIN_VALUE
            Scale.FIT -> Int.MAX_VALUE
        }
    }
