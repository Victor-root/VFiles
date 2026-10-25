/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.theme.night

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources

/**
 * Returns a context whose resources follow [isNight] instead of the system night mode, so that the
 * `-night` resources match the app setting. Everything else goes to this context.
 */
fun Context.withNightMode(isNight: Boolean): Context {
    val configuration = resources.configuration
    val uiModeNight =
        if (isNight) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    if (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == uiModeNight) {
        return this
    }
    val nightConfiguration = Configuration(configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or uiModeNight
    }
    return NightModeContext(this, nightConfiguration)
}

private class NightModeContext(
    base: Context,
    configuration: Configuration
) : ContextWrapper(base) {
    private val resources = base.createConfigurationContext(configuration).resources

    private val theme = resources.newTheme().apply { setTo(base.theme) }

    override fun getResources(): Resources = resources

    override fun getTheme(): Resources.Theme = theme
}
