/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.theme

import android.content.Context
import android.os.Build
import androidx.annotation.ColorInt
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.content.ContextCompat
import com.materialkolor.dynamiccolor.DynamicScheme
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeFidelity
import me.zhanghai.android.files.theme.custom.ThemeColor
import me.zhanghai.android.files.theme.custom.wallpaperAccentColor
import me.zhanghai.android.files.theme.night.withNightMode

fun appColorScheme(
    context: Context,
    themeColor: ThemeColor,
    isDark: Boolean,
    isBlack: Boolean
): ColorScheme {
    // Theme colors have a lighter variant for night mode, which follows the app setting.
    val nightModeContext = context.withNightMode(isDark)
    val colorScheme = if (themeColor == ThemeColor.DYNAMIC) {
        dynamicColorScheme(nightModeContext, isDark)
    } else {
        themeColorScheme(ContextCompat.getColor(nightModeContext, themeColor.resourceId), isDark)
    }
    val neutralColorScheme = colorScheme.withNeutralText(isDark)
    return if (isDark && isBlack) neutralColorScheme.toBlack() else neutralColorScheme
}

// Text is plain black or white, and grey when secondary, instead of tinted with the theme color.
private fun ColorScheme.withNeutralText(isDark: Boolean): ColorScheme =
    if (isDark) {
        copy(
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = NEUTRAL_SECONDARY_TEXT_DARK
        )
    } else {
        copy(
            onBackground = Color.Black,
            onSurface = Color.Black,
            onSurfaceVariant = NEUTRAL_SECONDARY_TEXT_LIGHT
        )
    }

private val NEUTRAL_SECONDARY_TEXT_LIGHT = Color(0xFF5F6368)
private val NEUTRAL_SECONDARY_TEXT_DARK = Color(0xFFBDC1C6)

// OEM skins (e.g. ColorOS) may report a fixed system accent that ignores the wallpaper, so the
// scheme is seeded from the real wallpaper color when it can be read, like the system does.
private fun dynamicColorScheme(context: Context, isDark: Boolean): ColorScheme {
    val wallpaperColor = context.wallpaperAccentColor()
    return when {
        wallpaperColor != null ->
            SchemeContent(Hct.fromInt(wallpaperColor), isDark, 0.0).toColorScheme()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> themeColorScheme(
            ContextCompat.getColor(context, ThemeColor.COLOR_PRIMARY.resourceId), isDark
        )
    }
}

// The chosen color is used as is for the primary color, like the app has always done, and the
// rest of the scheme is derived from it.
private fun themeColorScheme(@ColorInt color: Int, isDark: Boolean): ColorScheme {
    val colorScheme = SchemeFidelity(Hct.fromInt(color), isDark, 0.0).toColorScheme()
    val primary = Color(color)
    val onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White
    return colorScheme.copy(primary = primary, onPrimary = onPrimary)
}

private fun DynamicScheme.toColorScheme(): ColorScheme {
    val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
    return colorScheme.copy(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        inversePrimary = Color(inversePrimary),
        secondary = Color(secondary),
        onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer),
        onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary),
        onTertiary = Color(onTertiary),
        tertiaryContainer = Color(tertiaryContainer),
        onTertiaryContainer = Color(onTertiaryContainer),
        background = Color(background),
        onBackground = Color(onBackground),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant),
        onSurfaceVariant = Color(onSurfaceVariant),
        surfaceTint = Color(surfaceTint),
        inverseSurface = Color(inverseSurface),
        inverseOnSurface = Color(inverseOnSurface),
        error = Color(error),
        onError = Color(onError),
        errorContainer = Color(errorContainer),
        onErrorContainer = Color(onErrorContainer),
        outline = Color(outline),
        outlineVariant = Color(outlineVariant),
        scrim = Color(scrim),
        surfaceBright = Color(surfaceBright),
        surfaceContainer = Color(surfaceContainer),
        surfaceContainerHigh = Color(surfaceContainerHigh),
        surfaceContainerHighest = Color(surfaceContainerHighest),
        surfaceContainerLow = Color(surfaceContainerLow),
        surfaceContainerLowest = Color(surfaceContainerLowest),
        surfaceDim = Color(surfaceDim)
    )
}

// True black for the background and the surfaces drawn directly on it, while elevated surfaces
// like dialogs and menus keep their tone to stay distinguishable.
private fun ColorScheme.toBlack(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black
    )
