/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.theme.custom

import android.app.WallpaperManager
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Primary color extracted from the system wallpaper (ARGB), or null if unavailable.
 *
 * Read straight from the wallpaper's own extracted colors via [WallpaperManager.getWallpaperColors],
 * NOT the system "dynamic" / Material You accent (`@android:color/system_accent*`). On some OEM
 * skins (e.g. ColorOS) that system accent is a fixed value that does not follow the wallpaper, so
 * reading the wallpaper colors directly is what keeps the "wallpaper" theme option matching the
 * actual wallpaper. Only the extracted colors are read, never the wallpaper image, so no permission
 * is required. Available since API 27 (O_MR1).
 *
 * A live wallpaper may not tell its colors, like the image ColorOS shows behind an effect of glass,
 * whose colors ColorOS keeps on its own instead.
 */
@ColorInt
fun Context.wallpaperAccentColor(): Int? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
        return null
    }
    return runCatching {
        WallpaperManager.getInstance(this)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?.primaryColor
            ?.toArgb()
    }.getOrNull() ?: colorOsWallpaperSeedColor()
}

// The color ColorOS derives from the wallpaper, as "<color>&<…>&<…>".
@ColorInt
private fun Context.colorOsWallpaperSeedColor(): Int? {
    val value = runCatching {
        Settings.System.getString(contentResolver, COLOR_OS_WALLPAPER_SEED_COLOR)
    }.getOrNull()
    return value?.substringBefore('&')?.toIntOrNull()
}

private const val COLOR_OS_WALLPAPER_SEED_COLOR = "wall_paper_seed_color"

/**
 * Counts the changes of the colors of the wallpaper while [isEnabled], so that what's derived from
 * them is computed again, since the system may not tell otherwise.
 */
@Composable
fun rememberWallpaperColorsVersion(isEnabled: Boolean): Int {
    var version by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    DisposableEffect(context, isEnabled) {
        if (!isEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            return@DisposableEffect onDispose {}
        }
        val handler = Handler(Looper.getMainLooper())
        val wallpaperManager = WallpaperManager.getInstance(context)
        val listener = WallpaperManager.OnColorsChangedListener { _, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                ++version
            }
        }
        wallpaperManager.addOnColorsChangedListener(listener, handler)
        // And the color ColorOS keeps for a live wallpaper not telling its colors.
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                ++version
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(COLOR_OS_WALLPAPER_SEED_COLOR), false, observer
        )
        onDispose {
            wallpaperManager.removeOnColorsChangedListener(listener)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return version
}
