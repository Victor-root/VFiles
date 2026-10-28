/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.StatefulLoader
import me.zhanghai.android.files.util.Success

abstract class SetPrincipalViewModel(
    loadPrincipals: () -> List<PrincipalItem>,
    selectedId: Int
) : ViewModel() {
    private val loader = StatefulLoader(viewModelScope, loadPrincipals).apply { load() }
    val principalListStateful: Stateful<List<PrincipalItem>>
        get() = loader.value

    private val filterFlow = MutableStateFlow("")
    var filter: String
        get() = filterFlow.value
        set(value) {
            filterFlow.value = value
        }

    val filteredPrincipalListFlow: StateFlow<Stateful<List<PrincipalItem>>> =
        combine(loader.flow, filterFlow, ::filterPrincipals)
            .stateIn(viewModelScope, SharingStarted.Eagerly, loader.value)

    private val _selectionFlow = MutableStateFlow(selectedId)
    val selectionFlow: StateFlow<Int>
        get() = _selectionFlow

    fun select(id: Int) {
        _selectionFlow.value = id
    }

    private fun filterPrincipals(
        principalListStateful: Stateful<List<PrincipalItem>>,
        filter: String
    ): Stateful<List<PrincipalItem>> =
        if (principalListStateful is Success && filter.isNotEmpty()) {
            Success(principalListStateful.value.filter { it.applyFilter(filter) })
        } else {
            principalListStateful
        }

    private fun PrincipalItem.applyFilter(filter: String): Boolean =
        (filter in id.toString() || (name != null && filter in name)
            || applicationInfos.any { filter in it.packageName }
            || applicationLabels.any { filter in it })
}
