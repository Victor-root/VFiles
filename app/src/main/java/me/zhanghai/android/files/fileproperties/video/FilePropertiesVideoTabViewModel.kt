/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.video

import androidx.lifecycle.ViewModel
import java8.nio.file.Path
import kotlinx.coroutines.flow.StateFlow
import me.zhanghai.android.files.fileproperties.pathStatefulLoader
import me.zhanghai.android.files.util.Stateful

class FilePropertiesVideoTabViewModel(path: Path) : ViewModel() {
    private val loader = pathStatefulLoader(path) { loadVideoInfo(path) }
    val videoInfoFlow: StateFlow<Stateful<VideoInfo>>
        get() = loader.flow

    fun reload() {
        loader.load()
    }
}
