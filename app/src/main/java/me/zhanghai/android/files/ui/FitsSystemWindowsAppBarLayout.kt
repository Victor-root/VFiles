/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

// See the comment on PersistentBarLayout.kt: part of the same interacting raw-WindowInsets
// subsystem, left as-is rather than risk an unverifiable visual regression.
@file:Suppress("DEPRECATION")

package me.zhanghai.android.files.ui

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.OnWindowInsetChangedAppBarLayout

open class FitsSystemWindowsAppBarLayout : OnWindowInsetChangedAppBarLayout {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    init {
        fitsSystemWindows = true
    }

    override fun onWindowInsetChanged(insets: WindowInsetsCompat): WindowInsetsCompat {
        val windowInsets = insets.toWindowInsets()!!
        updatePadding(
            left = windowInsets.systemWindowInsetLeft, right = windowInsets.systemWindowInsetRight
        )
        return super.onWindowInsetChanged(insets)
    }
}
