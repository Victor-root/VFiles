/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.theme.custom

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import me.zhanghai.android.files.BuildConfig

/**
 * Primary color extracted from the system wallpaper (ARGB), or null if unavailable.
 *
 * Read straight from the wallpaper's own extracted colors via [WallpaperManager.getWallpaperColors],
 * NOT the system "dynamic" / Material You accent (`@android:color/system_accent*`). On some OEM
 * skins (e.g. ColorOS) that system accent is a fixed value that does not follow the wallpaper, so
 * reading the wallpaper colors directly is what keeps the "wallpaper" theme option matching the
 * actual wallpaper. Only the extracted colors are read, never the wallpaper image, so no permission
 * is required. Available since API 27 (O_MR1).
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
    }.getOrNull()
}

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
        val wallpaperManager = WallpaperManager.getInstance(context)
        val listener = WallpaperManager.OnColorsChangedListener { _, which ->
            if (BuildConfig.BUILD_TYPE == "debug") {
                Log.d("DynamicColorDebug", "Wallpaper colors changed: which=$which")
            }
            if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                ++version
            }
        }
        wallpaperManager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { wallpaperManager.removeOnColorsChangedListener(listener) }
    }
    return version
}
