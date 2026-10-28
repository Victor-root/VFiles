/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import java8.nio.file.Path
import kotlinx.coroutines.flow.StateFlow
import me.zhanghai.android.files.util.derivedStateFlow

/** The roots of the navigation drawer by their paths, like storages and standard directories. */
object NavigationRootMap {
    val flow: StateFlow<Map<Path, NavigationRoot>> = derivedStateFlow(NavigationItemList.flow) {
        NavigationItemList.value.mapNotNull { it as? NavigationRoot }.associateBy { it.path }
    }

    val value: Map<Path, NavigationRoot>
        get() = flow.value
}
