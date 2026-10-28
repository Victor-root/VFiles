/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.checksum

import androidx.lifecycle.ViewModel
import java8.nio.file.Path
import kotlinx.coroutines.flow.StateFlow
import me.zhanghai.android.files.fileproperties.pathStatefulLoader
import me.zhanghai.android.files.util.Stateful

class FilePropertiesChecksumTabViewModel(path: Path) : ViewModel() {
    private val loader = pathStatefulLoader(path) { loadChecksumInfo(path) }
    val checksumInfoFlow: StateFlow<Stateful<ChecksumInfo>>
        get() = loader.flow

    fun reload() {
        loader.load()
    }
}
