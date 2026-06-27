/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.theme.custom

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.recreateCompat
import me.zhanghai.android.files.compat.setThemeCompat
import me.zhanghai.android.files.compat.themeResIdCompat
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.theme.night.NightModeHelper
import me.zhanghai.android.files.util.SimpleActivityLifecycleCallbacks
import me.zhanghai.android.files.util.valueCompat

object CustomThemeHelper {
    private val activityBaseThemes = mutableMapOf<Activity, Int>()

    fun initialize(application: Application) {
        application.registerActivityLifecycleCallbacks(object : SimpleActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                check(activityBaseThemes.containsKey(activity)) {
                    "Activity must extend AppActivity: $activity"
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                activityBaseThemes.remove(activity)
            }
        })
    }

    fun apply(activity: Activity) {
        val baseThemeRes = activity.themeResIdCompat
        activityBaseThemes[activity] = baseThemeRes
        val customThemeRes = getCustomThemeRes(baseThemeRes, activity)
        activity.setThemeCompat(customThemeRes)
        applyWallpaperDynamicColors(activity)
    }

    // When the "wallpaper" (dynamic) color is selected with Material Design 3, the base theme would
    // otherwise take its colors from the system accent (@android:color/system_accent*). On OEM skins
    // (e.g. ColorOS) that accent is a fixed value that ignores the wallpaper, so we instead seed a
    // content-based Material 3 palette from the real wallpaper color and apply it as an overlay,
    // overriding the system accent. Falls back to the base theme's system-accent colors when the
    // wallpaper color can't be read (older API, or the read failed).
    private fun applyWallpaperDynamicColors(activity: Activity) {
        if (Settings.THEME_COLOR.valueCompat != ThemeColor.DYNAMIC
            || !Settings.MATERIAL_DESIGN_3.valueCompat) {
            return
        }
        val seedColor = activity.wallpaperAccentColor() ?: return
        val options = DynamicColorsOptions.Builder()
            .setContentBasedSource(seedColor)
            .build()
        DynamicColors.applyToActivityIfAvailable(activity, options)
    }

    fun sync() {
        for ((activity, baseThemeRes) in activityBaseThemes) {
            val currentThemeRes = activity.themeResIdCompat
            val customThemeRes = getCustomThemeRes(baseThemeRes, activity)
            if (currentThemeRes != customThemeRes) {
                // Ignore ".Black" theme changes when not in night mode.
                if (!NightModeHelper.isInNightMode(activity as AppCompatActivity)
                    && isBlackThemeChange(currentThemeRes, customThemeRes, activity)) {
                    continue
                }
                if (activity is OnThemeChangedListener) {
                    (activity as OnThemeChangedListener).onThemeChanged(customThemeRes)
                } else {
                    activity.recreateCompat()
                }
            }
        }
    }

    private fun getCustomThemeRes(@StyleRes baseThemeRes: Int, context: Context): Int {
        val resources = context.resources
        val baseThemeName = resources.getResourceName(baseThemeRes)
        val themeColor = Settings.THEME_COLOR.valueCompat
        val isMaterial3 = Settings.MATERIAL_DESIGN_3.valueCompat
        // With Material Design 3, the "dynamic" color follows the system/wallpaper colors, which the
        // base Material3 theme already applies; so we leave it without a color suffix in that case.
        val isDynamic = themeColor == ThemeColor.DYNAMIC && isMaterial3
        // DYNAMIC has no fixed-color theme of its own, so if it somehow ends up selected without
        // Material Design 3 we fall back to the default color to keep the theme name valid.
        val effectiveThemeColor =
            if (themeColor == ThemeColor.DYNAMIC && !isMaterial3) ThemeColor.entries[0] else themeColor
        val themeColorName = resources.getResourceEntryName(effectiveThemeColor.resourceId)
        val customThemeName = if (isMaterial3) {
            val defaultThemeName = resources.getResourceEntryName(R.style.Theme_MaterialFiles)
            val material3ThemeName =
                resources.getResourceEntryName(R.style.Theme_MaterialFiles_Material3)
            baseThemeName.replace(defaultThemeName, material3ThemeName) +
                if (isDynamic) "" else ".$themeColorName"
        } else {
            "$baseThemeName.$themeColorName"
        } + if (Settings.BLACK_NIGHT_MODE.valueCompat) ".Black" else ""
        return resources.getIdentifier(customThemeName, null, null)
    }

    private fun isBlackThemeChange(
        @StyleRes themeRes1: Int,
        @StyleRes themeRes2: Int,
        context: Context
    ): Boolean {
        val resources = context.resources
        val themeName1 = resources.getResourceName(themeRes1)
        val themeName2 = resources.getResourceName(themeRes2)
        return themeName1 == "$themeName2.Black" || themeName2 == "$themeName1.Black"
    }

    interface OnThemeChangedListener {
        fun onThemeChanged(@StyleRes theme: Int)
    }
}
