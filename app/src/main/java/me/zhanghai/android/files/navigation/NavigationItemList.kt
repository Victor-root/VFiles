/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import kotlinx.coroutines.flow.StateFlow
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.storage.StorageVolumeList
import me.zhanghai.android.files.util.derivedStateFlow

/** The items of the navigation drawer. */
object NavigationItemList {
    val flow: StateFlow<List<NavigationItem?>> = derivedStateFlow(
        Settings.STORAGES.flow, StorageVolumeList.flow, StandardDirectories.flow,
        Settings.BOOKMARK_DIRECTORIES.flow,
        // Refresh so a removable volume's custom name / icon updates as soon as it's edited.
        Settings.STORAGE_VOLUME_CUSTOMIZATIONS.flow
    ) { navigationItems }

    val value: List<NavigationItem?>
        get() = flow.value
}
