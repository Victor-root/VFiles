/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.zhanghai.android.files.app.applicationScope

/**
 * A state computed again by [compute] whenever one of [sources] changes, as long as the app runs.
 * [compute] reads the sources itself, like the getters it replaces.
 */
fun <T> derivedStateFlow(vararg sources: Flow<*>, compute: () -> T): StateFlow<T> =
    combine(sources.toList()) { compute() }
        .stateIn(applicationScope, SharingStarted.Eagerly, compute())

/**
 * Calls [action] for each value while [subscriptionCount] is positive. Values while it isn't call
 * [action] once when it is again. Keeps waiting for that after this flow completes, so it never
 * returns and must be cancelled.
 */
suspend fun Flow<*>.collectWhileSubscribed(
    subscriptionCount: StateFlow<Int>,
    action: () -> Unit
): Unit = coroutineScope {
    var isEmittedWhileUnsubscribed = false
    launch {
        subscriptionCount.map { it > 0 }.distinctUntilChanged().collect { isSubscribed ->
            if (isSubscribed && isEmittedWhileUnsubscribed) {
                isEmittedWhileUnsubscribed = false
                action()
            }
        }
    }
    collect {
        if (subscriptionCount.value > 0) {
            action()
        } else {
            isEmittedWhileUnsubscribed = true
        }
    }
}
