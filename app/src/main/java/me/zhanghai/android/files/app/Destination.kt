/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.app

import android.app.Activity
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A screen opened inside the current activity, above the screens opened before it, like the
 * activities it replaces.
 */
interface Destination : Parcelable {
    /** Whether it's shown above the screen below it, which stays visible, instead of over it. */
    val isDialog: Boolean
        get() = false

    @Composable
    fun Content()
}

/**
 * Opens screens above all the others, and closes the current screen, which is the activity itself
 * for its own screen, like finishing it.
 */
@Stable
class Navigator internal constructor(
    private val entries: SnapshotStateList<ScreenEntry>,
    private val entryId: String,
    private val activity: Activity
) {
    fun navigate(destination: Destination) {
        entries += ScreenEntry(destination)
    }

    fun goBack() {
        val index = entries.indexOfFirst { it.id == entryId }
        when {
            index == 0 -> activity.finish()
            index > 0 -> entries.removeAt(index)
        }
    }

    /**
     * Closes the current screen, like a dialog, and opens [destination] in its place, or above it
     * for the screen of the activity itself.
     */
    fun replace(destination: Destination) {
        val index = entries.indexOfFirst { it.id == entryId }
        when {
            index == 0 -> navigate(destination)
            index > 0 -> entries[index] = ScreenEntry(destination)
        }
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator provided") }
