/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.Destination
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.Preference
import me.zhanghai.android.files.ui.SingleChoiceDialog

@Parcelize
data object DefaultAppsDestination : Destination {
    @Composable
    override fun Content() {
        DefaultAppsScreen()
    }
}

@Composable
private fun DefaultAppsScreen() {
    val context = LocalContext.current
    val defaultApps by Settings.FILE_OPEN_DEFAULT_APPS.flow.collectAsState()
    // Refresh in case a chosen app was uninstalled while we were away.
    var resumeCount by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        ++resumeCount
        onPauseOrDispose {}
    }
    val summaries = remember(defaultApps, resumeCount, context) {
        FileOpenAppCategory.entries.associateWith { category ->
            FileOpenDefaultApps.getPackage(category)?.let { context.getAppLabel(it) }
                ?: context.getNoChoiceLabel(category)
        }
    }
    var pickingCategory by rememberSaveable { mutableStateOf<FileOpenAppCategory?>(null) }
    AppScaffold(title = stringResource(R.string.settings_default_apps_title)) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
        ) {
            for (category in FileOpenAppCategory.entries) {
                Preference(
                    title = stringResource(category.titleRes),
                    summary = summaries.getValue(category),
                    onClick = { pickingCategory = category }
                )
            }
        }
    }
    pickingCategory?.let { category ->
        AppPickerDialog(category, onDismissRequest = { pickingCategory = null })
    }
}

@Composable
private fun AppPickerDialog(category: FileOpenAppCategory, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val apps = remember(category, context) { context.queryApps(category) }
    val noChoiceLabel = remember(category, context) { context.getNoChoiceLabel(category) }
    // The first entry clears the choice, which leaves it to Android, the rest are the candidate
    // apps, this one included.
    val packageNames = listOf(null) + apps.keys
    val currentPackageName = FileOpenDefaultApps.getPackage(category)?.takeIf { it in apps }
    SingleChoiceDialog(
        title = stringResource(category.titleRes),
        entries = packageNames,
        entryLabel = { if (it != null) apps.getValue(it) else noChoiceLabel },
        selectedEntry = currentPackageName,
        onEntrySelected = {
            onDismissRequest()
            FileOpenDefaultApps.setPackage(category, it)
        },
        onDismissRequest = onDismissRequest
    )
}

/**
 * Returns the label of the choice leaving the files to Android: the app picked there with
 * "Always", or else asking every time.
 */
private fun Context.getNoChoiceLabel(category: FileOpenAppCategory): String =
    getAndroidDefaultPackage(category)?.let { getAppLabel(it) }
        ?.let { getString(R.string.settings_default_apps_android_default_format, it) }
        ?: getString(R.string.settings_default_apps_ask)

/** Returns the package of the app that Android opens [category] with without asking, if any. */
private fun Context.getAndroidDefaultPackage(category: FileOpenAppCategory): String? {
    val queryIntent = category.createQueryIntent()
    val defaultActivity = packageManager.resolveActivity(
        queryIntent, PackageManager.MATCH_DEFAULT_ONLY
    )?.activityInfo ?: return null
    // Without one, it resolves to the chooser of the system, which isn't among the candidates.
    val isCandidate = packageManager.queryIntentActivities(
        queryIntent, PackageManager.MATCH_DEFAULT_ONLY
    )
        .any {
            it.activityInfo.packageName == defaultActivity.packageName &&
                it.activityInfo.name == defaultActivity.name
        }
    return defaultActivity.packageName.takeIf { isCandidate }
}

// The same shape of intent the file list actually fires: a content: URI from our own FileProvider,
// plus the type (Path.fileProviderUri and Uri.createViewIntent). An intent carrying a type but no
// URI only matches filters that declare no URI format at all, so querying by type alone silently
// drops every app whose filter is scheme-qualified, which is how most viewers declare theirs.
private fun FileOpenAppCategory.createQueryIntent(): Intent {
    val queryUri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_CONTENT)
        .authority(BuildConfig.FILE_PROVIDIER_AUTHORITY)
        .build()
    return Intent(Intent.ACTION_VIEW).setDataAndType(queryUri, queryMimeType)
}

/** Returns the labels of the apps that can open [category], by package name. */
private fun Context.queryApps(category: FileOpenAppCategory): Map<String, String> =
    packageManager.queryIntentActivities(category.createQueryIntent(), 0)
        .map { it.activityInfo.packageName }
        .distinct()
        .mapNotNull { packageName -> getAppLabel(packageName)?.let { packageName to it } }
        .sortedBy { it.second.lowercase() }
        .toMap()

private fun Context.getAppLabel(packageName: String): String? =
    try {
        packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
