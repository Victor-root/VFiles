/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

// See the comment on PersistentBarLayout.kt: part of the same interacting raw-WindowInsets
// subsystem.
@file:Suppress("DEPRECATION")

package me.zhanghai.android.files.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout

// ScrollingViewBehavior grows the scrolling view by the window insets whenever the app bar fits
// system windows, but the app bar's own height already accounts for the top inset, whether it
// reserves it itself or hands it to its first child in edge-to-edge (see CoordinatorAppBarLayout).
// Measure against the window height instead, so the scrolling view ends exactly at the bottom of the
// window, once the app bar has scrolled away as well.
class FitsSystemWindowsScrollingViewBehavior : AppBarLayout.ScrollingViewBehavior {
    constructor()

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun onMeasureChild(
        parent: CoordinatorLayout,
        child: View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int
    ): Boolean {
        var parentHeightMeasureSpec = parentHeightMeasureSpec
        @SuppressLint("RestrictedApi")
        val parentInsets = parent.lastWindowInsets
        if (parentInsets != null) {
            val parentHeightSize = (View.MeasureSpec.getSize(parentHeightMeasureSpec)
                - parentInsets.systemWindowInsetTop - parentInsets.systemWindowInsetBottom)
            val parentHeightMode = View.MeasureSpec.getMode(parentHeightMeasureSpec)
            parentHeightMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                parentHeightSize, parentHeightMode
            )
        }
        return super.onMeasureChild(
            parent, child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed
        )
    }
}
