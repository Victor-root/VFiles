/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.apk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.StatefulLoader

class PermissionListViewModel(permissionNames: Array<String>) : ViewModel() {
    private val loader =
        StatefulLoader(viewModelScope) { loadPermissionItems(permissionNames) }.apply { load() }
    val permissionListFlow: StateFlow<Stateful<List<PermissionItem>>>
        get() = loader.flow
}
