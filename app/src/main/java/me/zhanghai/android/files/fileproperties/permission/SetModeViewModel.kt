/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.permission

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.zhanghai.android.files.provider.common.PosixFileModeBit
import me.zhanghai.android.files.util.toEnumSet

class SetModeViewModel(mode: Set<PosixFileModeBit>) : ViewModel() {
    private val _modeFlow = MutableStateFlow(mode)
    val modeFlow: StateFlow<Set<PosixFileModeBit>>
        get() = _modeFlow
    val mode: Set<PosixFileModeBit>
        get() = _modeFlow.value

    fun toggleModeBit(modeBit: PosixFileModeBit) {
        val mode = _modeFlow.value.toEnumSet()
        if (modeBit in mode) {
            mode -= modeBit
        } else {
            mode += modeBit
        }
        _modeFlow.value = mode
    }
}
