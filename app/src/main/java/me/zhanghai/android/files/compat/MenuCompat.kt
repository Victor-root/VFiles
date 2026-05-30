/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.compat

import android.annotation.SuppressLint
import android.view.Menu
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.view.MenuCompat

fun Menu.setGroupDividerEnabledCompat(groupDividerEnabled: Boolean) {
    MenuCompat.setGroupDividerEnabled(this, groupDividerEnabled)
}

// Android hides item icons in overflow / sub menu popups by default; opt back in so menu entries
// can show a leading icon next to their text.
@SuppressLint("RestrictedApi")
fun Menu.setOptionalIconsVisibleCompat(visible: Boolean) {
    (this as? MenuBuilder)?.setOptionalIconsVisible(visible)
}
