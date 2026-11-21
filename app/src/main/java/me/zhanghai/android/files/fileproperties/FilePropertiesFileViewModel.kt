/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.loadFileItem
import me.zhanghai.android.files.util.Stateful

class FilePropertiesFileViewModel(file: FileItem) : ViewModel() {
    private val loader = pathStatefulLoader(file.path, file) { file.path.loadFileItem() }
    val fileFlow: StateFlow<Stateful<FileItem>>
        get() = loader.flow

    fun reload() {
        loader.load()
    }
}
