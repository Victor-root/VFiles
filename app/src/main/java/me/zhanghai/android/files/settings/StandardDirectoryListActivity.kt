/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.navigation.StandardDirectoriesLiveData
import me.zhanghai.android.files.navigation.StandardDirectory
import me.zhanghai.android.files.navigation.getExternalStorageDirectory
import me.zhanghai.android.files.navigation.moveStandardDirectory
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.ReorderDragHandle
import me.zhanghai.android.files.ui.SwitchPreference
import me.zhanghai.android.files.ui.rememberReorderableListState
import me.zhanghai.android.files.ui.reorderableItem
import me.zhanghai.android.files.util.observeAsStateCompat
import me.zhanghai.android.files.util.valueCompat

class StandardDirectoryListActivity : AppActivity() {
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
        val lazyListState = rememberLazyListState()
        val reorderableListState =
            rememberReorderableListState(lazyListState, ::moveStandardDirectory)
        LazyColumn(state = lazyListState, contentPadding = contentPadding) {
            items(standardDirectories, key = { it.key }) {
                Row(
                    modifier = Modifier
                        .then(
                            if (it.key != reorderableListState.draggedItemKey) {
                                Modifier.animateItem()
                            } else {
                                Modifier
                            }
                        )
                        .reorderableItem(reorderableListState, it.key)
                        .background(MaterialTheme.colorScheme.background),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SwitchPreference(
                        title = it.getTitle(context),
                        checked = it.isEnabled,
                        onCheckedChange = { isEnabled ->
                            setStandardDirectoryEnabled(it, isEnabled)
                        },
                        modifier = Modifier.weight(1f),
                        summary = if (it.isGallery) {
                            stringResource(R.string.settings_standard_directory_gallery_summary)
                        } else {
                            getExternalStorageDirectory(it.relativePath)
                        },
                        icon = painterResource(it.iconRes)
                    )
                    ReorderDragHandle(
                        state = reorderableListState,
                        key = it.key,
                        modifier = Modifier.padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
