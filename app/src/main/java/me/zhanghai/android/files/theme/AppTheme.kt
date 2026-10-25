/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.theme.night.isNightModeActive
import me.zhanghai.android.files.util.observeAsStateCompat
import android.graphics.Color as AndroidColor

@Immutable
class AppColors(
    val isDark: Boolean,
    val appBarContainer: Color,
    val onAppBar: Color
)

val LocalAppColors = staticCompositionLocalOf<AppColors> { error("No AppColors provided") }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val nightMode by Settings.NIGHT_MODE.observeAsStateCompat()
    val themeColor by Settings.THEME_COLOR.observeAsStateCompat()
    val isBlackNightMode by Settings.BLACK_NIGHT_MODE.observeAsStateCompat()
    val isDark = nightMode.isNightModeActive()
    val context = LocalContext.current
    val colorScheme = remember(context, themeColor, isDark, isBlackNightMode) {
        appColorScheme(context, themeColor, isDark, isBlackNightMode)
    }
    val appColors = remember(colorScheme, isDark) { colorScheme.toAppColors(isDark) }
    SystemBarsEffect(appColors)
    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

// The app bar takes the theme color in light mode, and the surface color in dark mode.
private fun ColorScheme.toAppColors(isDark: Boolean): AppColors =
    if (isDark) {
        AppColors(isDark, surface, onSurface)
    } else {
        AppColors(isDark, primary, onPrimary)
    }

// Status bar icons follow the app bar drawn behind them, navigation bar icons follow the content.
@Composable
private fun SystemBarsEffect(appColors: AppColors) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val isAppBarDark = appColors.appBarContainer.luminance() < 0.5f
    DisposableEffect(activity, isAppBarDark, appColors.isDark) {
        activity.enableEdgeToEdge(
            SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) {
                isAppBarDark
            },
            SystemBarStyle.auto(LIGHT_NAVIGATION_BAR_SCRIM, DARK_NAVIGATION_BAR_SCRIM) {
                appColors.isDark
            }
        )
        onDispose {}
    }
}

// The scrims enableEdgeToEdge() uses by default for the three-button navigation bar.
private val LIGHT_NAVIGATION_BAR_SCRIM = AndroidColor.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val DARK_NAVIGATION_BAR_SCRIM = AndroidColor.argb(0x80, 0x1B, 0x1B, 0x1B)
