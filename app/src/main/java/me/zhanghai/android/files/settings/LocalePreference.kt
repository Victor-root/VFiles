/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.app.LocaleManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.compat.LocaleConfigCompat
import me.zhanghai.android.files.ui.DialogListPreference
import me.zhanghai.android.files.ui.Preference
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.takeIfNotEmpty
import me.zhanghai.android.files.util.toList
import me.zhanghai.android.files.util.valueCompat
import java.util.Locale

@Composable
fun LocalePreference() {
    val title = stringResource(R.string.settings_locale_title)
    val systemDefault = stringResource(R.string.system_default)
    val icon = painterResource(R.drawable.translate_icon_white_24dp)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Prefer using the system setting because it has better support for locales, and refresh
        // when coming back from it.
        val context = LocalContext.current
        var locale by remember { mutableStateOf(applicationLocale) }
        LifecycleResumeEffect(Unit) {
            locale = applicationLocale
            onPauseOrDispose {}
        }
        Preference(
            title = title,
            summary = locale?.sentenceCasedLocalizedDisplayName ?: systemDefault,
            icon = icon,
            onClick = {
                context.startActivitySafe(
                    Intent(
                        android.provider.Settings.ACTION_APP_LOCALE_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                )
            }
        )
    } else {
        val context = LocalContext.current
        val activity = LocalActivity.current
        val supportedLocales = remember(context) {
            LocaleConfigCompat(context).supportedLocales!!.toList()
                .sortedBy { it.toLanguageTag() }
        }
        DialogListPreference<Locale?>(
            title = title,
            entries = listOf(null) + supportedLocales,
            entryLabel = { it?.sentenceCasedLocalizedDisplayName ?: systemDefault },
            icon = icon,
            value = Settings.LOCALES.valueCompat.takeIfNotEmpty()
                ?.let { Locale.forLanguageTag(it) },
            onValueChange = {
                Settings.LOCALES.putValue(it?.toLanguageTag().orEmpty())
                activity?.recreate()
            }
        )
    }
}

// The locale set in the system settings, from Android 13.
private val applicationLocale: Locale?
    get() = LocaleManagerCompat.getApplicationLocales(application).toList().firstOrNull()

private val Locale.sentenceCasedLocalizedDisplayName: String
    // See com.android.internal.app.LocaleHelper.toSentenceCase() for a proper case conversion
    // implementation which requires android.icu.text.CaseMap that's only available on API 29+.
    get() = getDisplayName(this).replaceFirstChar { it.titlecase(this) }
