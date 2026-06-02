/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.shape.MaterialShapeDrawable
import me.zhanghai.android.files.util.activity

class CoordinatorAppBarLayout : FitsSystemWindowsAppBarLayout {
    private val syncBackgroundColorViews = mutableListOf<View>()

    private var offset = 0
    private val tempClipBounds = Rect()

    // Last header color the system bars were synced to, so the appearance can be re-applied after
    // something temporarily overrode it (e.g. an open navigation drawer).
    @ColorInt
    private var lastSystemBarColor: Int? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    init {
        val defaultBackgroundColor = (background as? MaterialShapeDrawable)?.fillColor?.defaultColor
        if (defaultBackgroundColor != null) {
            val window = context.activity!!.window
            window.statusBarColor = Color.TRANSPARENT
            // Keep the lifted (scrolled) state the same color as the resting app bar. Otherwise
            // lift-on-scroll repaints it with a tonal surface color the moment content scrolls
            // under it, which would make our colored header (and the synced nav bar) flash white.
            setLiftOnScrollColor(ColorStateList.valueOf(defaultBackgroundColor))
            syncSystemBars(defaultBackgroundColor)
        }

        addLiftOnScrollListener { _, backgroundColor ->
            onBackgroundColorChanged(backgroundColor)
        }

        addOnOffsetChangedListener { _, offset ->
            this.offset = offset
            updateFirstChildClipBounds()
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        getChildAt(0)?.let {
            it.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateFirstChildClipBounds()
            }
        }
    }

    fun syncBackgroundColorTo(view: View) {
        syncBackgroundColorViews += view
    }

    private fun onBackgroundColorChanged(backgroundColor: Int) {
        syncBackgroundColorViews.forEach {
            (it.background as? MaterialShapeDrawable)?.fillColor =
                ColorStateList.valueOf(backgroundColor)
        }
        syncSystemBars(backgroundColor)
    }

    // Re-apply the system-bar appearance for the current header color. Call this when something that
    // temporarily overrode it (e.g. an open navigation drawer) goes away.
    fun refreshSystemBars() {
        lastSystemBarColor?.let { syncSystemBars(it) }
    }

    private fun syncSystemBars(@ColorInt backgroundColor: Int) {
        lastSystemBarColor = backgroundColor
        val window = context.activity?.window ?: return
        window.navigationBarColor = backgroundColor
        // Match the system-bar icon tints to the background luminance: dark icons only on light
        // colors (e.g. a flashy yellow), light (white) icons otherwise — including in light mode,
        // where the colored header is usually dark enough that black icons would be hard to read.
        val isLight = ColorUtils.calculateLuminance(backgroundColor) > 0.5
        val controller = WindowInsetsControllerCompat(window, this)
        controller.isAppearanceLightStatusBars = isLight
        // windowLightNavigationBar (and its programmatic equivalent) require API 26+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            controller.isAppearanceLightNavigationBars = isLight
        }
    }

    private fun updateFirstChildClipBounds() {
        val firstChild = getChildAt(0) ?: return
        tempClipBounds.set(0, -offset, firstChild.width, firstChild.height)
        // Work around a bug before Android N that an empty clip bounds doesn't clip.
        // Making the clip bounds somewhere outside view bounds doesn't work, so as a hack we just
        // assume that the first child won't draw anything in its top-left pixel.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            if (tempClipBounds.isEmpty) {
                tempClipBounds.set(0, 0, 1, 1)
            }
        }
        firstChild.clipBounds = tempClipBounds
    }
}
