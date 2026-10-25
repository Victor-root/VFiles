/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.icu.text.ListFormatter
import android.os.Build
import android.text.TextUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.ComposeActivity
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.filelist.OpenApkDefaultAction
import me.zhanghai.android.files.navigation.StandardDirectoriesLiveData
import me.zhanghai.android.files.provider.root.RootStrategy
import me.zhanghai.android.files.storage.StorageListActivity
import me.zhanghai.android.files.theme.custom.ThemeColorPreference
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.DialogListPreference
import me.zhanghai.android.files.ui.DropdownListPreference
import me.zhanghai.android.files.ui.Preference
import me.zhanghai.android.files.ui.PreferenceCategory
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.observeAsStateCompat
import me.zhanghai.android.files.util.startActivitySafe
import java.nio.charset.Charset

class SettingsActivity : ComposeActivity() {
    @Composable
    override fun Content() {
        SettingsScreen()
    }
}

@Composable
private fun SettingsScreen() {
    AppScaffold(title = stringResource(R.string.settings_title)) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
        ) {
            PreferenceCategory(stringResource(R.string.settings_interface_title))
            LocalePreference()
            ThemeColorPreference()
            NightModePreference()
            SettingSwitchPreference(
                Settings.BLACK_NIGHT_MODE, stringResource(R.string.settings_black_night_mode)
            )
            SettingSwitchPreference(
                Settings.FILE_LIST_ANIMATION,
                stringResource(R.string.settings_file_list_animation_title)
            )
            SettingSwitchPreference(
                Settings.EDGE_TO_EDGE, stringResource(R.string.settings_edge_to_edge_title),
                stringResource(R.string.settings_edge_to_edge_summary)
            )
            FileNameEllipsizePreference()
            PreferenceCategory(stringResource(R.string.settings_behavior_title))
            PathPreference(
                Settings.FILE_LIST_DEFAULT_DIRECTORY,
                stringResource(R.string.settings_default_directory_title)
            )
            StoragesPreference()
            StandardDirectoriesPreference()
            BookmarkDirectoriesPreference()
            DefaultAppsPreference()
            RootStrategyPreference()
            ArchiveFileNameEncodingPreference()
            OpenApkDefaultActionPreference()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                SettingSwitchPreference(
                    Settings.SHOW_PDF_THUMBNAIL_PRE_28,
                    stringResource(R.string.settings_show_pdf_thumbnail_pre_28_title),
                    stringResource(R.string.settings_show_pdf_thumbnail_pre_28_summary)
                )
            }
            SettingSwitchPreference(
                Settings.READ_REMOTE_FILES_FOR_THUMBNAIL,
                stringResource(R.string.settings_read_remote_files_for_thumbnail_title)
            )
        }
    }
}

@Composable
private fun NightModePreference() {
    val nightMode by Settings.NIGHT_MODE.observeAsStateCompat()
    val entries = stringArrayResource(R.array.settings_night_mode_entries)
    DropdownListPreference(
        title = stringResource(R.string.settings_night_mode_title),
        entries = NightMode.entries,
        entryLabel = { entries[it.ordinal] },
        value = nightMode,
        onValueChange = { Settings.NIGHT_MODE.putValue(it) }
    )
}

@Composable
private fun FileNameEllipsizePreference() {
    val ellipsize by Settings.FILE_NAME_ELLIPSIZE.observeAsStateCompat()
    val entries = stringArrayResource(R.array.settings_file_name_ellipsize_entries)
    DropdownListPreference(
        title = stringResource(R.string.settings_file_name_ellipsize_title),
        entries = TextUtils.TruncateAt.entries,
        entryLabel = { entries[it.ordinal] },
        value = ellipsize,
        onValueChange = { Settings.FILE_NAME_ELLIPSIZE.putValue(it) }
    )
}

@Composable
private fun StoragesPreference() {
    val context = LocalContext.current
    val storages by Settings.STORAGES.observeAsStateCompat()
    val names = storages.filter { it.isVisible }.map { it.getName(context) }
    Preference(
        title = stringResource(R.string.settings_storages_title),
        summary = names.formatListOr(stringResource(R.string.settings_storages_summary_empty)),
        summaryMaxLines = 2,
        onClick = { context.startActivitySafe(StorageListActivity::class.createIntent()) }
    )
}

@Composable
private fun StandardDirectoriesPreference() {
    val context = LocalContext.current
    val standardDirectories by StandardDirectoriesLiveData.observeAsStateCompat()
    val titles = standardDirectories.filter { it.isEnabled }.map { it.getTitle(context) }
    Preference(
        title = stringResource(R.string.settings_standard_directories_title),
        summary = titles.formatListOr(
            stringResource(R.string.settings_standard_directories_summary_empty)
        ),
        onClick = {
            context.startActivitySafe(StandardDirectoryListActivity::class.createIntent())
        }
    )
}

@Composable
private fun BookmarkDirectoriesPreference() {
    val context = LocalContext.current
    val bookmarkDirectories by Settings.BOOKMARK_DIRECTORIES.observeAsStateCompat()
    Preference(
        title = stringResource(R.string.settings_bookmark_directories_title),
        summary = bookmarkDirectories.map { it.name }.formatListOr(
            stringResource(R.string.settings_bookmark_directories_summary_empty)
        ),
        onClick = {
            context.startActivitySafe(BookmarkDirectoryListActivity::class.createIntent())
        }
    )
}

private fun List<String>.formatListOr(emptySummary: String): String =
    if (isNotEmpty()) ListFormatter.getInstance().format(this) else emptySummary

@Composable
private fun DefaultAppsPreference() {
    val context = LocalContext.current
    Preference(
        title = stringResource(R.string.settings_default_apps_title),
        summary = stringResource(R.string.settings_default_apps_summary),
        onClick = { context.startActivitySafe(DefaultAppsActivity::class.createIntent()) }
    )
}

@Composable
private fun RootStrategyPreference() {
    val rootStrategy by Settings.ROOT_STRATEGY.observeAsStateCompat()
    val entries = stringArrayResource(R.array.settings_root_strategy_entries)
    var isMenuShown by rememberSaveable { mutableStateOf(false) }
    var runningJobCountToConfirm by rememberSaveable { mutableStateOf<Int?>(null) }
    DropdownListPreference(
        title = stringResource(R.string.settings_root_strategy_title),
        entries = RootStrategy.entries,
        entryLabel = { entries[it.ordinal] },
        value = rootStrategy,
        onValueChange = { Settings.ROOT_STRATEGY.putValue(it) },
        isMenuShown = isMenuShown,
        onMenuShownChange = {
            // Changing it with file operations running may make them fail.
            val jobCount = FileJobService.runningJobCount
            if (it && jobCount != 0) {
                runningJobCountToConfirm = jobCount
            } else {
                isMenuShown = it
            }
        }
    )
    val jobCount = runningJobCountToConfirm
    if (jobCount != null) {
        val dismiss = { runningJobCountToConfirm = null }
        AlertDialog(
            onDismissRequest = dismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        dismiss()
                        isMenuShown = true
                    }
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = dismiss) { Text(stringResource(R.string.maybe_later)) }
            },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.settings_root_strategy_message_format, jobCount, jobCount
                    )
                )
            }
        )
    }
}

@Composable
private fun ArchiveFileNameEncodingPreference() {
    val encoding by Settings.ARCHIVE_FILE_NAME_ENCODING.observeAsStateCompat()
    val charsets = remember { Charset.availableCharsets() }
    DialogListPreference(
        title = stringResource(R.string.settings_archive_file_name_encoding_title),
        entries = charsets.keys.toList(),
        entryLabel = { charsets[it]?.displayName() ?: it },
        value = encoding,
        onValueChange = { Settings.ARCHIVE_FILE_NAME_ENCODING.putValue(it) }
    )
}

@Composable
private fun OpenApkDefaultActionPreference() {
    val action by Settings.OPEN_APK_DEFAULT_ACTION.observeAsStateCompat()
    val entries = stringArrayResource(R.array.settings_open_apk_default_action_entries)
    DropdownListPreference(
        title = stringResource(R.string.settings_open_apk_default_action_title),
        entries = OpenApkDefaultAction.entries,
        entryLabel = { entries[it.ordinal] },
        value = action,
        onValueChange = { Settings.OPEN_APK_DEFAULT_ACTION.putValue(it) }
    )
}
