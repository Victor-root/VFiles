/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import androidx.compose.runtime.collectAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import java8.nio.file.Path
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.navigation.NavigationRootMap
import me.zhanghai.android.files.ui.Preference
import me.zhanghai.android.files.ui.SwitchPreference

@Composable
fun SettingSwitchPreference(
    setting: Setting<Boolean>,
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    icon: Painter? = null
) {
    val checked by setting.flow.collectAsState()
    SwitchPreference(
        title = title,
        checked = checked,
        onCheckedChange = { setting.putValue(it) },
        summary = summary,
        enabled = enabled,
        icon = icon
    )
}

/** A folder, picked in the file list. */
@Composable
fun PathPreference(
    setting: Setting<Path>,
    title: String,
    enabled: Boolean = true,
    icon: Painter? = null
) {
    val context = LocalContext.current
    val path by setting.flow.collectAsState()
    val navigationRoots by NavigationRootMap.flow.collectAsState()
    val launcher = rememberLauncherForActivityResult(FileListActivity.OpenDirectoryContract()) {
        if (it != null) {
            setting.putValue(it)
        }
    }
    Preference(
        title = title,
        summary = navigationRoots[path]?.getName(context) ?: path.toUserFriendlyString(),
        enabled = enabled,
        icon = icon,
        onClick = { launcher.launch(path) }
    )
}
