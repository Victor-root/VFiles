/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.apk

import androidx.lifecycle.ViewModel
import java8.nio.file.Path
import kotlinx.coroutines.flow.StateFlow
import me.zhanghai.android.files.fileproperties.pathStatefulLoader
import me.zhanghai.android.files.util.Stateful

class FilePropertiesApkTabViewModel(path: Path) : ViewModel() {
    private val loader = pathStatefulLoader(path) { loadApkInfo(path) }
    val apkInfoFlow: StateFlow<Stateful<ApkInfo>>
        get() = loader.flow

    fun reload() {
        loader.load()
    }
}
