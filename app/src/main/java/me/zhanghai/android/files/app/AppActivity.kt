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

abstract class AppActivity : ComponentActivity() {
    private var appliedLocales: String? = null

    // The system applies the app locales on Android 13+, and we do it ourselves before.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val locales = Settings.LOCALES.value
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

        setContent { AppTheme { ScreenStack { Content() } } }
    }

    override fun onResume() {
        super.onResume()

        val appliedLocales = appliedLocales
        if (appliedLocales != null && Settings.LOCALES.value != appliedLocales) {
            recreate()
        }
    }

    @Composable
    protected abstract fun Content()
}
