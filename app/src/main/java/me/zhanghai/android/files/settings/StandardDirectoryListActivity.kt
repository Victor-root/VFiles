/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.ComposeActivity
import me.zhanghai.android.files.navigation.StandardDirectoriesLiveData
import me.zhanghai.android.files.navigation.StandardDirectory
import me.zhanghai.android.files.navigation.getExternalStorageDirectory
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.SwitchPreference
import me.zhanghai.android.files.util.observeAsStateCompat
import me.zhanghai.android.files.util.valueCompat

class StandardDirectoryListActivity : ComposeActivity() {
    @Composable
    override fun Content() {
        StandardDirectoryListScreen()
    }
}

@Composable
private fun StandardDirectoryListScreen() {
    val context = LocalContext.current
    val standardDirectories by StandardDirectoriesLiveData.observeAsStateCompat()
    AppScaffold(title = stringResource(R.string.settings_standard_directory_list_title)) {
        contentPadding ->
        LazyColumn(contentPadding = contentPadding) {
            items(standardDirectories, key = { it.key }) {
                SwitchPreference(
                    title = it.getTitle(context),
                    checked = it.isEnabled,
                    onCheckedChange = { isEnabled -> setStandardDirectoryEnabled(it, isEnabled) },
                    summary = getExternalStorageDirectory(it.relativePath),
                    icon = painterResource(it.iconRes)
                )
            }
        }
    }
}

private fun setStandardDirectoryEnabled(standardDirectory: StandardDirectory, isEnabled: Boolean) {
    val settingsList = Settings.STANDARD_DIRECTORY_SETTINGS.valueCompat.toMutableList()
    val index = settingsList.indexOfFirst { it.id == standardDirectory.key }
    if (index != -1) {
        settingsList[index] = settingsList[index].copy(isEnabled = isEnabled)
    } else {
        settingsList += standardDirectory.toSettings().copy(isEnabled = isEnabled)
    }
    Settings.STANDARD_DIRECTORY_SETTINGS.putValue(settingsList)
}
