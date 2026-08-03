/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

// Extends FitsSystemWindowsAppBarLayout, part of the same interacting raw-WindowInsets subsystem
// (see the comment there). window.statusBarColor/navigationBarColor are additionally deprecated in
// favor of edge-to-edge, which Android 15+ enforces regardless: a real fix would mean redrawing this
// app bar's content behind the system bars using WindowInsetsCompat padding, not a mechanical
// substitution, and isn't verifiable without a real device. Left as-is for the default (off) path.
//
// With Settings.EDGE_TO_EDGE on, the navigation bar goes transparent and the RecyclerViews below
// already scroll into it (clipToPadding="false" plus an inset-consuming bottom padding, see
// CoordinatorScrollingFrameLayout/CoordinatorScrollingLinearLayout), and the app bar's single child
// gets scroll flags so the header moves away with the content instead of staying pinned.
//
// For the status bar the header has to scroll away *completely*, so the content behind it reaches
// the top of the window. Two things stand in the way by default, both confirmed against the
// decompiled aars rather than from memory:
//  - AppBarLayout reserves the top inset for itself: onMeasure()/onLayout() add getTopInset() to its
//    own height and push its children down by it, and getTotalScrollRange() never counts that
//    reserved height, so it stays pinned however far the header scrolls. onWindowInsetChanged()
//    below therefore hands AppBarLayout a copy of the insets with the top zeroed, and applies the
//    real top inset to the first child instead, as that child's own padding (and height, for the
//    fixed-height toolbars some layouts put here directly). That space then belongs to the child, so
//    it counts toward the scroll range and leaves with it.
//  - This view keeps fitsSystemWindows = true either way: its parent CoordinatorLayout offsets any
//    child that does not fit system windows itself down by the top inset, which would just put the
//    reserved strip straight back.
// The content then reaches y = 0 through ScrollingViewBehavior, which pins its top to the app bar's
// bottom edge, once CoordinatorScrollingFrameLayout/CoordinatorScrollingLinearLayout stop shrinking
// it by the insets.
@file:Suppress("DEPRECATION")

package me.zhanghai.android.files.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.shape.MaterialShapeDrawable
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.activity
import me.zhanghai.android.files.util.getColorByAttr
import me.zhanghai.android.files.util.valueCompat

class CoordinatorAppBarLayout : FitsSystemWindowsAppBarLayout {
    private val syncBackgroundColorViews = mutableListOf<View>()

    private var offset = 0
    private val tempClipBounds = Rect()

    // First child metrics from before any top inset was added to them, so re-applying an inset stays
    // idempotent across inset passes.
    private var firstChildBasePaddingTop = 0
    private var firstChildBaseHeight = 0
    private var hasFirstChildBase = false

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
            if (Settings.EDGE_TO_EDGE.valueCompat) {
                updateStatusBarAppearance()
            }
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        getChildAt(0)?.let {
            it.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateFirstChildClipBounds()
            }
            if (Settings.EDGE_TO_EDGE.valueCompat) {
                it.updateLayoutParams<AppBarLayout.LayoutParams> {
                    scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                        AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS or
                        AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
                }
            }
        }
    }

    override fun onWindowInsetChanged(insets: WindowInsetsCompat): WindowInsetsCompat {
        if (!Settings.EDGE_TO_EDGE.valueCompat) {
            return super.onWindowInsetChanged(insets)
        }
        // See the file comment: take the top inset over from AppBarLayout, which would otherwise
        // reserve it as permanently pinned height, and hand it to the first child instead.
        val windowInsets = insets.toWindowInsets()!!
        applyTopInsetToFirstChild(windowInsets.systemWindowInsetTop)
        super.onWindowInsetChanged(
            WindowInsetsCompat.toWindowInsetsCompat(
                windowInsets.replaceSystemWindowInsets(
                    windowInsets.systemWindowInsetLeft, 0, windowInsets.systemWindowInsetRight,
                    windowInsets.systemWindowInsetBottom
                )
            )
        )
        // Pass the untouched insets on: only this view's own reservation is being suppressed.
        return insets
    }

    private fun applyTopInsetToFirstChild(topInset: Int) {
        val firstChild = getChildAt(0) ?: return
        if (!hasFirstChildBase) {
            firstChildBasePaddingTop = firstChild.paddingTop
            firstChildBaseHeight = firstChild.layoutParams.height
            hasFirstChildBase = true
        }
        firstChild.updatePadding(top = firstChildBasePaddingTop + topInset)
        // Padding alone only grows a child that measures itself. A fixed height (e.g. a bare Toolbar
        // at ?actionBarSize, which is what several layouts put here) has to grow by the inset too,
        // or the padding just squashes its content instead.
        if (firstChildBaseHeight >= 0) {
            firstChild.updateLayoutParams<ViewGroup.LayoutParams> {
                height = firstChildBaseHeight + topInset
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
        val controller = WindowInsetsControllerCompat(window, this)
        if (Settings.EDGE_TO_EDGE.valueCompat) {
            // Leave the navigation bar transparent so the scrolling content behind it shows through
            // instead of being covered by a solid color.
            window.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // The status bar is transparent too (set in init), and the whole point here is to
                // see the content scrolling behind it, so turn off the translucent scrim the
                // system would otherwise paint over it by default.
                window.isStatusBarContrastEnforced = false
                // The navigation bar, unlike the status bar, sits over arbitrary scrolling content
                // at all times rather than over a header we control, so leave its scrim on to keep
                // the buttons legible.
                window.isNavigationBarContrastEnforced = true
            }
            // windowLightNavigationBar (and its programmatic equivalent) require API 26+.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                controller.isAppearanceLightNavigationBars = contentBackgroundColor.isLight
            }
            updateStatusBarAppearance()
        } else {
            window.navigationBarColor = backgroundColor
            // By default the system may paint a translucent contrast scrim over the navigation bar
            // (e.g. under gesture navigation, where it's enforced even for opaque colors). On some
            // devices (notably large screens) that scrim swallows our color and leaves the bar
            // looking white. Opt out so the exact color above is what's shown.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            // Match the system-bar icon tints to the background luminance: dark icons only on light
            // colors (e.g. a flashy yellow), light (white) icons otherwise, including in light mode,
            // where the colored header is usually dark enough that black icons would be hard to
            // read.
            val isLight = backgroundColor.isLight
            controller.isAppearanceLightStatusBars = isLight
            // windowLightNavigationBar (and its programmatic equivalent) require API 26+.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                controller.isAppearanceLightNavigationBars = isLight
            }
        }
    }

    // Edge-to-edge only: the status bar sits over the header while it's expanded and over the
    // scrolling content once the header has moved out from under it, so tint its icons for whichever
    // of the two is actually there.
    private fun updateStatusBarAppearance() {
        val window = context.activity?.window ?: return
        val headerColor = lastSystemBarColor ?: return
        val scrollRange = totalScrollRange
        val isCollapsed = scrollRange > 0 && -offset >= scrollRange / 2
        WindowInsetsControllerCompat(window, this).isAppearanceLightStatusBars =
            if (isCollapsed) contentBackgroundColor.isLight else headerColor.isLight
    }

    @get:ColorInt
    private val contentBackgroundColor: Int
        get() = context.getColorByAttr(com.google.android.material.R.attr.colorSurface)

    private val Int.isLight: Boolean
        get() = ColorUtils.calculateLuminance(this) > 0.5

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
