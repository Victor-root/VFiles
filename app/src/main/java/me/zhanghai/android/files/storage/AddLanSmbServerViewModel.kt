/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.toLinkedSet

class AddLanSmbServerViewModel : ViewModel() {
    private val _lanSmbServerListFlow =
        MutableStateFlow<Stateful<List<LanSmbServer>>>(Loading(null))
    val lanSmbServerListFlow: StateFlow<Stateful<List<LanSmbServer>>> =
        _lanSmbServerListFlow.asStateFlow()

    private var scanJob: Job? = null

    init {
        reload()
    }

    fun reload() {
        scanJob?.cancel()
        _lanSmbServerListFlow.value = Loading(_lanSmbServerListFlow.value.value)
        scanJob = viewModelScope.launch {
            val foundServerSet = mutableSetOf<LanSmbServer>()
            try {
                scanLanSmbServers().collect {
                    // Use linked set to preserve UI stability.
                    val serverSet =
                        _lanSmbServerListFlow.value.value?.toLinkedSet() ?: linkedSetOf()
                    serverSet += it
                    _lanSmbServerListFlow.value = Loading(serverSet.toList())
                    foundServerSet += it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _lanSmbServerListFlow.value = Failure(_lanSmbServerListFlow.value.value, e)
                return@launch
            }
            // Remove old servers that aren't found any more.
            _lanSmbServerListFlow.value = Success(
                _lanSmbServerListFlow.value.value.orEmpty().filter { it in foundServerSet }
            )
        }
    }
}
