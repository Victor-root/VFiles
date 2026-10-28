/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.zhanghai.android.files.app.applicationScope

/**
 * A state computed again by [compute] whenever one of [sources] changes, as long as the app runs.
 * [compute] reads the sources itself, like the getters it replaces.
 */
fun <T> derivedStateFlow(vararg sources: Flow<*>, compute: () -> T): StateFlow<T> =
    combine(sources.toList()) { compute() }
        .stateIn(applicationScope, SharingStarted.Eagerly, compute())
