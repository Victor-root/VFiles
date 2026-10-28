/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import java8.nio.file.Path
import me.zhanghai.android.files.app.Destination

abstract class NavigationItem {
    abstract val id: Long

    @get:DrawableRes
    abstract val iconRes: Int

    // The icon is tinted like the title when there's no color for it.
    open val iconColor: NavigationIconColor? = null

    abstract fun getTitle(context: Context): String

    open fun getSubtitle(context: Context): String? = null

    // Whether to indent this row, to show it's nested under a group (e.g. one of several partitions
    // of the same removable drive).
    open val isIndented: Boolean = false

    // Whether this is a non-interactive section header (e.g. the parent row of a removable drive
    // whose partitions are listed beneath it). Such rows aren't clickable or focusable.
    open val isHeader: Boolean = false

    open fun isChecked(listener: Listener): Boolean = false

    abstract fun onClick(listener: Listener)

    open fun onLongClick(listener: Listener): Boolean = false

    interface Listener {
        val currentPath: Path
        fun navigateTo(path: Path)
        fun navigateToRoot(path: Path)
        fun launchIntent(intent: Intent)
        fun navigate(destination: Destination)
        fun showToast(textRes: Int)
        fun closeNavigationDrawer()
    }
}
