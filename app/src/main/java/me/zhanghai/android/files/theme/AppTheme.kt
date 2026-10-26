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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.theme.night.isNightModeActive
import me.zhanghai.android.files.theme.night.withNightMode
import me.zhanghai.android.files.util.observeAsStateCompat
import android.graphics.Color as AndroidColor

@Immutable
class AppColors(
    val isDark: Boolean,
    val appBarContainer: Color,
    val onAppBar: Color
)

val LocalAppColors = staticCompositionLocalOf<AppColors> { error("No AppColors provided") }

// What is drawn behind the status bar when it isn't the app bar, like an open drawer.
private val LocalStatusBarBackground =
    staticCompositionLocalOf<MutableState<Color?>> { error("No status bar background provided") }

/** Makes the status bar icons follow [color] instead of the app bar while this is composed. */
@Composable
fun StatusBarBackgroundEffect(color: Color) {
    val statusBarBackground = LocalStatusBarBackground.current
    DisposableEffect(statusBarBackground, color) {
        statusBarBackground.value = color
        onDispose { statusBarBackground.value = null }
    }
}

/** [hasDarkSystemBars] is for screens that always draw dark content behind the system bars. */
@Composable
fun AppTheme(hasDarkSystemBars: Boolean = false, content: @Composable () -> Unit) {
    val nightMode by Settings.NIGHT_MODE.observeAsStateCompat()
    val themeColor by Settings.THEME_COLOR.observeAsStateCompat()
    val isBlackNightMode by Settings.BLACK_NIGHT_MODE.observeAsStateCompat()
    val isDark = nightMode.isNightModeActive()
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val context = remember(baseContext, baseConfiguration, isDark) {
        baseContext.withNightMode(isDark)
    }
    val colorScheme = remember(context, themeColor, isDark, isBlackNightMode) {
        appColorScheme(context, themeColor, isDark, isBlackNightMode)
    }
    val appColors = remember(colorScheme, isDark) { colorScheme.toAppColors(isDark) }
    val statusBarBackground = remember { mutableStateOf<Color?>(null) }
    SystemBarsEffect(
        statusBarBackground.value ?: appColors.appBarContainer, appColors.isDark,
        hasDarkSystemBars
    )
    CompositionLocalProvider(
        LocalContext provides context,
        LocalConfiguration provides context.resources.configuration,
        LocalAppColors provides appColors,
        LocalStatusBarBackground provides statusBarBackground
    ) {
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

// Status bar icons follow what is drawn behind them, navigation bar icons follow the content.
@Composable
private fun SystemBarsEffect(
    statusBarBackground: Color,
    isDark: Boolean,
    hasDarkSystemBars: Boolean
) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val isStatusBarDark = hasDarkSystemBars || statusBarBackground.luminance() < 0.5f
    val isNavigationBarDark = hasDarkSystemBars || isDark
    DisposableEffect(activity, isStatusBarDark, isNavigationBarDark) {
        activity.enableEdgeToEdge(
            SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) {
                isStatusBarDark
            },
            // An explicit style, unlike auto(), keeps the system from adding its own scrim to the
            // three-button navigation bar, so it's fully transparent like before.
            if (isNavigationBarDark) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                // Android 8.0 can't draw dark navigation bar icons, so they need a dark scrim.
                SystemBarStyle.light(AndroidColor.TRANSPARENT, DARK_NAVIGATION_BAR_SCRIM)
            }
        )
        onDispose {}
    }
}

// The scrim enableEdgeToEdge() uses by default for the three-button navigation bar.
private val DARK_NAVIGATION_BAR_SCRIM = AndroidColor.argb(0x80, 0x1B, 0x1B, 0x1B)
