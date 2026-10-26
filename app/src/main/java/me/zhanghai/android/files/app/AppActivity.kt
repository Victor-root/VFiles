/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.app

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.theme.AppTheme
import me.zhanghai.android.files.util.valueCompat

abstract class AppActivity : ComponentActivity() {
    private var appliedLocales: String? = null

    private var framePerformanceHint: FramePerformanceHint? = null

    // The system applies the app locales on Android 13+, and we do it ourselves before.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val locales = Settings.LOCALES.valueCompat
            appliedLocales = locales
            if (locales.isNotEmpty()) {
                applyOverrideConfiguration(
                    Configuration().apply { setLocales(LocaleList.forLanguageTags(locales)) }
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent { AppTheme(hasDarkSystemBars) { Content() } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            framePerformanceHint = FramePerformanceHint(window)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            framePerformanceHint?.close()
        }
    }

    /** Whether the screen always draws dark content behind the system bars. */
    protected open val hasDarkSystemBars: Boolean = false

    override fun onResume() {
        super.onResume()

        val appliedLocales = appliedLocales
        if (appliedLocales != null && Settings.LOCALES.valueCompat != appliedLocales) {
            recreate()
        }
    }

    @Composable
    protected abstract fun Content()
}
