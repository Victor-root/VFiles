/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.theme.custom

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.settings.Settings
import kotlin.math.abs

private const val ALIAS_PREFIX = "me.zhanghai.android.files.filelist.LauncherAlias_"

val ThemeColor.launcherAlias: ComponentName
    get() = ComponentName(application.packageName, ALIAS_PREFIX + launcherAliasSuffix!!)

/**
 * The launcher icon, changed by enabling one of the activity aliases of the manifest and disabling
 * the others.
 */
object LauncherIcon {
    /** Time for the launcher to finish drawing behind the app still shown. */
    private const val SETTLE_MILLIS = 300L

    /** The broadcast the launcher redraws from may never come back to us. */
    private const val TIMEOUT_MILLIS = 1500L

    val colors: List<ThemeColor> = ThemeColor.entries.filter { it.launcherAliasSuffix != null }

    private val defaultColor = ThemeColor.COLOR_PRIMARY

    /**
     * Only the enabled alias tells what the launcher shows, what was picked may differ after
     * restoring a backup.
     */
    val currentColor: ThemeColor
        get() {
            val packageManager = application.packageManager
            return colors.firstOrNull {
                when (packageManager.getComponentEnabledSetting(it.launcherAlias)) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
                    else -> it == defaultColor
                }
            } ?: defaultColor
        }

    /** The icon perceptually closest to [color]. */
    fun nearestColor(color: Int): ThemeColor {
        val target = color.toHsv()
        return colors.minBy {
            val candidate = ContextCompat.getColor(application, it.resourceId).toHsv()
            val hueDistance = abs(candidate[0] - target[0])
            minOf(hueDistance, 360f - hueDistance) +
                abs(candidate[1] - target[1]) * 100f + abs(candidate[2] - target[2]) * 100f
        }
    }

    private fun Int.toHsv(): FloatArray = FloatArray(3).also { Color.colorToHSV(this, it) }

    /**
     * Returns once the launcher has redrawn, as closing the app earlier reveals the icon changing.
     * The app has to close then with finishAffinity(), because the task was started from an alias
     * that is now disabled.
     */
    suspend fun apply(color: ThemeColor) {
        val isPackageChanged = CompletableDeferred<Unit>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.data?.schemeSpecificPart == context.packageName) {
                    isPackageChanged.complete(Unit)
                }
            }
        }
        // Registered before changing the aliases so that the broadcast can't be missed.
        ContextCompat.registerReceiver(
            application, receiver,
            IntentFilter(Intent.ACTION_PACKAGE_CHANGED).apply { addDataScheme("package") },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        try {
            withContext(Dispatchers.IO) { setEnabledColor(color) }
            withTimeoutOrNull(TIMEOUT_MILLIS) { isPackageChanged.await() }
            delay(SETTLE_MILLIS)
        } finally {
            application.unregisterReceiver(receiver)
        }
    }

    /** Puts back the icon of a restored backup, which the aliases don't remember. */
    fun applySavedColor() {
        val color = Settings.LAUNCHER_ICON_COLOR.storedValue
        if (color != currentColor) {
            setEnabledColor(color)
        }
    }

    private fun setEnabledColor(color: ThemeColor) {
        val packageManager = application.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // A single broadcast, so that the launcher refreshes almost at once.
            packageManager.setComponentEnabledSettings(
                colors.map {
                    PackageManager.ComponentEnabledSetting(
                        it.launcherAlias, it.toEnabledState(color), PackageManager.DONT_KILL_APP
                    )
                }
            )
        } else {
            // Enabling first, so that the app is never missing from the launcher.
            (listOf(color) + (colors - color)).forEach {
                packageManager.setComponentEnabledSetting(
                    it.launcherAlias, it.toEnabledState(color), PackageManager.DONT_KILL_APP
                )
            }
        }
        Settings.LAUNCHER_ICON_COLOR.putValue(color)
    }

    private fun ThemeColor.toEnabledState(enabledColor: ThemeColor): Int =
        if (this == enabledColor) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
}
