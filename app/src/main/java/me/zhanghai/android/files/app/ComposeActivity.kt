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
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import me.zhanghai.android.files.theme.AppTheme

abstract class ComposeActivity : ComponentActivity() {
    private var appliedLocales: LocaleListCompat? = null

    // The system applies the app locales on Android 13+, and we do it ourselves before.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val locales = LocaleManagerCompat.getApplicationLocales(newBase)
            appliedLocales = locales
            if (!locales.isEmpty) {
                applyOverrideConfiguration(
                    Configuration().apply {
                        setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
                    }
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent { AppTheme(hasDarkSystemBars) { Content() } }
    }

    /** Whether the screen always draws dark content behind the system bars. */
    protected open val hasDarkSystemBars: Boolean = false

    override fun onResume() {
        super.onResume()

        val appliedLocales = appliedLocales
        if (appliedLocales != null
            && LocaleManagerCompat.getApplicationLocales(this) != appliedLocales) {
            recreate()
        }
    }

    @Composable
    protected abstract fun Content()
}
