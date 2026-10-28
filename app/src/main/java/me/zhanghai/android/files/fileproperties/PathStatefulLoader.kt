/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java8.nio.file.Path
import kotlinx.coroutines.launch
import me.zhanghai.android.files.filelist.loadOnChanges
import me.zhanghai.android.files.util.StatefulLoader

/**
 * A loader of [load] for the view model, which loads now, or starts with [initialValue] when there's
 * one, and loads again whenever [path] changes while it's shown.
 */
fun <T : Any> ViewModel.pathStatefulLoader(
    path: Path,
    initialValue: T? = null,
    load: () -> T
): StatefulLoader<T> {
    val loader = StatefulLoader(viewModelScope, load)
    if (initialValue != null) {
        loader.setValue(initialValue)
    } else {
        loader.load()
    }
    viewModelScope.launch { path.loadOnChanges(loader.subscriptionCount) { loader.load() } }
    return loader
}
