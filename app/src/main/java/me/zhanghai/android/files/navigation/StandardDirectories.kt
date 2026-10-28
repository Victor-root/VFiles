/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import kotlinx.coroutines.flow.StateFlow
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.derivedStateFlow

/** The standard directories, with their settings and in their order. */
object StandardDirectories {
    val flow: StateFlow<List<StandardDirectory>> = derivedStateFlow(
        Settings.STANDARD_DIRECTORY_SETTINGS.flow, Settings.STANDARD_DIRECTORY_ORDER.flow
    ) { standardDirectories }

    val value: List<StandardDirectory>
        get() = flow.value
}
