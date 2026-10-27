/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.theme.custom

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import me.zhanghai.android.files.R
import me.zhanghai.android.files.colorpicker.ColorPickerDialog
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.Preference
import me.zhanghai.android.files.util.observeAsStateCompat

@Composable
fun ThemeColorPreference() {
    val context = LocalContext.current
    val title = stringResource(R.string.settings_theme_color_title)
    val themeColor by Settings.THEME_COLOR.observeAsStateCompat()
    var isDialogShown by rememberSaveable { mutableStateOf(false) }
    Preference(
        title = title,
        summary = stringResource(R.string.settings_theme_color_summary),
        icon = painterResource(R.drawable.palette_icon_white_24dp),
        onClick = { isDialogShown = true }
    )
    if (isDialogShown) {
        // The dynamic (wallpaper) color first, and only on Android 12+ where it makes sense. This
        // order is independent of the persisted ordinals.
        val themeColors = remember {
            buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(ThemeColor.DYNAMIC)
                }
                addAll(ThemeColor.entries.filter { it != ThemeColor.DYNAMIC })
            }
        }
        val colors = remember(context) { themeColors.map { it.displayColor(context) } }
        val defaultThemeColor =
            ThemeColor.entries[stringResource(R.string.pref_default_value_theme_color).toInt()]
        ColorPickerDialog(
            title = title,
            colors = colors,
            hasLeadingDynamicColor = themeColors.first() == ThemeColor.DYNAMIC,
            selectedIndex = themeColors.indexOf(themeColor),
            defaultIndex = themeColors.indexOf(defaultThemeColor),
            onColorSelected = { Settings.THEME_COLOR.putValue(themeColors[it]) },
            onDismissRequest = { isDialogShown = false }
        )
    }
}

// The wallpaper color is the real one instead of the system accent, which doesn't follow the
// wallpaper on some OEM skins.
private fun ThemeColor.displayColor(context: Context): Color =
    Color(
        (if (this == ThemeColor.DYNAMIC) context.wallpaperAccentColor() else null)
            ?: ContextCompat.getColor(context, resourceId)
    )
