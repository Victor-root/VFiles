/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.theme.custom

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import me.zhanghai.android.files.R
import me.zhanghai.android.files.colorpicker.ColorPickerDialog
import me.zhanghai.android.files.settings.SettingSwitchPreference
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.ConfirmationDialog
import me.zhanghai.android.files.ui.Preference
import me.zhanghai.android.files.util.isTelevision

/** Launchers on TV show a banner instead of the icon. */
@Composable
fun LauncherIconPreferences() {
    val context = LocalContext.current
    if (remember(context) { context.isTelevision }) {
        return
    }
    val activity = LocalActivity.current
    val coroutineScope = rememberCoroutineScope()
    var colorToConfirm by rememberSaveable { mutableStateOf<ThemeColor?>(null) }
    // Picking the icon already in place would close the app for nothing.
    val offerColor = { color: ThemeColor ->
        if (color != LauncherIcon.currentColor) {
            colorToConfirm = color
        }
    }

    val isThemeApplied by Settings.APPLY_THEME_COLOR_TO_LAUNCHER_ICON.flow.collectAsState()
    val themeColor by Settings.THEME_COLOR.flow.collectAsState()
    val themeToApply = themeColor.takeIf { isThemeApplied }
    // Only offered when it changes, not every time this screen opens after declining.
    var offeredTheme by rememberSaveable { mutableStateOf(themeToApply) }
    LaunchedEffect(themeToApply) {
        if (themeToApply != offeredTheme) {
            offeredTheme = themeToApply
            offerColor(
                if (themeToApply != null) {
                    LauncherIcon.nearestColor(themeToApply.displayColor(context).toArgb())
                } else {
                    ThemeColor.COLOR_PRIMARY
                }
            )
        }
    }

    SettingSwitchPreference(
        Settings.APPLY_THEME_COLOR_TO_LAUNCHER_ICON,
        stringResource(R.string.settings_apply_theme_color_to_launcher_icon_title),
        icon = painterResource(R.drawable.apps_icon_white_24dp)
    )
    if (!isThemeApplied) {
        val title = stringResource(R.string.settings_launcher_icon_color_title)
        var isPickerShown by rememberSaveable { mutableStateOf(false) }
        Preference(
            title = title,
            summary = stringResource(R.string.settings_launcher_icon_color_summary),
            icon = painterResource(R.drawable.palette_icon_white_24dp),
            onClick = { isPickerShown = true }
        )
        if (isPickerShown) {
            val colors = remember(context) {
                LauncherIcon.colors.map { Color(ContextCompat.getColor(context, it.resourceId)) }
            }
            ColorPickerDialog(
                title = title,
                colors = colors,
                hasLeadingDynamicColor = false,
                selectedIndex = LauncherIcon.colors.indexOf(LauncherIcon.currentColor),
                defaultIndex = LauncherIcon.colors.indexOf(ThemeColor.COLOR_PRIMARY),
                onColorSelected = { offerColor(LauncherIcon.colors[it]) },
                onDismissRequest = { isPickerShown = false }
            )
        }
    }

    colorToConfirm?.let { color ->
        ConfirmationDialog(
            text = stringResource(R.string.settings_launcher_icon_change_message),
            onConfirm = {
                coroutineScope.launch {
                    LauncherIcon.apply(color)
                    activity?.finishAffinity()
                }
            },
            onDismissRequest = { colorToConfirm = null },
            title = stringResource(R.string.settings_launcher_icon_change_title),
            confirmText = stringResource(R.string.settings_launcher_icon_change_confirm)
        )
    }
}
