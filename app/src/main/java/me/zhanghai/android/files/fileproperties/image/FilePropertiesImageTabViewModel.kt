/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.image

import androidx.lifecycle.ViewModel
import java8.nio.file.Path
import me.zhanghai.android.files.file.MimeType
import kotlinx.coroutines.flow.StateFlow
import me.zhanghai.android.files.fileproperties.pathStatefulLoader
import me.zhanghai.android.files.util.Stateful

class FilePropertiesImageTabViewModel(path: Path, mimeType: MimeType) : ViewModel() {
    private val loader = pathStatefulLoader(path) { loadImageInfo(path, mimeType) }
    val imageInfoFlow: StateFlow<Stateful<ImageInfo>>
        get() = loader.flow

    fun reload() {
        loader.load()
    }
}
