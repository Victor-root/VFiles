/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.theme.custom

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.annotation.ColorInt

/**
 * Primary color extracted from the system wallpaper (ARGB), or null if unavailable.
 *
 * Read straight from the wallpaper's own extracted colors via [WallpaperManager.getWallpaperColors]
 * — NOT the system "dynamic" / Material You accent (`@android:color/system_accent*`). On some OEM
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
