/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.audio

import androidx.lifecycle.ViewModel
import java8.nio.file.Path
import kotlinx.coroutines.flow.StateFlow
import me.zhanghai.android.files.fileproperties.pathStatefulLoader
import me.zhanghai.android.files.util.Stateful

class FilePropertiesAudioTabViewModel(path: Path) : ViewModel() {
    private val loader = pathStatefulLoader(path) { loadAudioInfo(path) }
    val audioInfoFlow: StateFlow<Stateful<AudioInfo>>
        get() = loader.flow

    fun reload() {
        loader.load()
    }
}
