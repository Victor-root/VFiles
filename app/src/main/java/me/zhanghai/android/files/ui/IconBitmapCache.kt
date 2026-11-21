/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

/**
 * Icons drawn once per size and shared by the items of a list, which would otherwise each draw
 * their own copy of them and upload it to the GPU while scrolling.
 */
class IconBitmapCache(private val context: Context) {
    private val bitmaps = mutableMapOf<Pair<Int, Int>, ImageBitmap>()

    fun get(@DrawableRes iconRes: Int, sizePx: Int): ImageBitmap =
        bitmaps.getOrPut(iconRes to sizePx) {
            ContextCompat.getDrawable(context, iconRes)!!.toBitmap(sizePx, sizePx).asImageBitmap()
        }
}

val LocalIconBitmapCache = staticCompositionLocalOf<IconBitmapCache?> { null }

/** The icon from [LocalIconBitmapCache] at [size] if there's one, or else from the resources. */
@Composable
fun cachedIconPainter(@DrawableRes iconRes: Int, size: Dp): Painter {
    val cache = LocalIconBitmapCache.current ?: return painterResource(iconRes)
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    return remember(cache, iconRes, sizePx) { BitmapPainter(cache.get(iconRes, sizePx)) }
}
