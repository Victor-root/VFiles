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
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.materialkolor.dynamiccolor.DynamicScheme
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeFidelity
import me.zhanghai.android.files.theme.custom.ThemeColor
import me.zhanghai.android.files.theme.custom.wallpaperAccentColor
import me.zhanghai.android.files.theme.night.withNightMode
import kotlin.math.abs
import kotlin.math.min

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
    val neutralColorScheme = colorScheme.withNeutralColors(isDark)
    return if (isDark && isBlack) neutralColorScheme.toBlack() else neutralColorScheme
}

// Backgrounds, surfaces and text are white, black and greys, instead of tinted with the theme
// color, which is kept for the app bar and the controls.
private fun ColorScheme.withNeutralColors(isDark: Boolean): ColorScheme =
    if (isDark) {
        copy(
            background = NEUTRAL_DARK_SURFACE,
            onBackground = Color.White,
            surface = NEUTRAL_DARK_SURFACE,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF3C4043),
            onSurfaceVariant = Color(0xFFBDC1C6),
            surfaceTint = Color.Transparent,
            inverseSurface = Color(0xFFE8EAED),
            inverseOnSurface = Color(0xFF202124),
            outline = Color(0xFF9AA0A6),
            outlineVariant = Color(0xFF3C4043),
            surfaceBright = Color(0xFF3C4043),
            surfaceDim = NEUTRAL_DARK_SURFACE,
            surfaceContainerLowest = Color(0xFF0E0E0F),
            surfaceContainerLow = Color(0xFF1B1B1C),
            surfaceContainer = Color(0xFF202124),
            surfaceContainerHigh = Color(0xFF2A2B2D),
            surfaceContainerHighest = Color(0xFF35363A)
        )
    } else {
        copy(
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFE8EAED),
            onSurfaceVariant = Color(0xFF5F6368),
            surfaceTint = Color.Transparent,
            inverseSurface = Color(0xFF303134),
            inverseOnSurface = Color(0xFFF1F3F4),
            outline = Color(0xFF80868B),
            outlineVariant = Color(0xFFDADCE0),
            surfaceBright = Color.White,
            surfaceDim = Color(0xFFDADCE0),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF8F9FA),
            surfaceContainer = Color(0xFFF1F3F4),
            surfaceContainerHigh = Color(0xFFECEDEF),
            surfaceContainerHighest = Color(0xFFE8EAED)
        )
    }

private val NEUTRAL_DARK_SURFACE = Color(0xFF121212)

/**
 * The scheme of the system, unless it doesn't follow the wallpaper, like on ColorOS whose dynamic
 * colors can stay on an older wallpaper. The colors of the wallpaper itself, which the system
 * derives its scheme from, tell it: when their hue is way off the one of the system, the scheme is
 * derived from the wallpaper instead, like the system does. Before Android 12, there's only the
 * wallpaper to follow.
 */
private fun dynamicColorScheme(context: Context, isDark: Boolean): ColorScheme {
    val wallpaperColor = context.wallpaperAccentColor()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val systemColorScheme =
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (wallpaperColor == null ||
            !isHueMismatched(systemColorScheme.primary.toArgb(), wallpaperColor)) {
            return systemColorScheme
        }
    }
    return if (wallpaperColor != null) {
        SchemeContent(Hct.fromInt(wallpaperColor), isDark, 0.0).toColorScheme()
    } else {
        themeColorScheme(
            ContextCompat.getColor(context, ThemeColor.COLOR_PRIMARY.resourceId), isDark
        )
    }
}

// Further apart than the system would ever bring colors derived from the same wallpaper, for colors
// colorful enough for their hue to mean anything.
private fun isHueMismatched(@ColorInt color1: Int, @ColorInt color2: Int): Boolean {
    val hsv1 = FloatArray(3).also { android.graphics.Color.colorToHSV(color1, it) }
    val hsv2 = FloatArray(3).also { android.graphics.Color.colorToHSV(color2, it) }
    if (hsv1[1] <= HUE_SATURATION_MIN || hsv2[1] <= HUE_SATURATION_MIN) {
        return false
    }
    val hueDistance = abs(hsv1[0] - hsv2[0]).let { min(it, 360 - it) }
    return hueDistance > HUE_DISTANCE_MAX
}

private const val HUE_SATURATION_MIN = 0.15f
private const val HUE_DISTANCE_MAX = 60f

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
