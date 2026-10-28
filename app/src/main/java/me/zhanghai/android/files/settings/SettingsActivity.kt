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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import me.zhanghai.android.files.R
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.app.Destination
import me.zhanghai.android.files.app.LocalNavigator
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.filelist.OpenApkDefaultAction
import me.zhanghai.android.files.navigation.StandardDirectories
import me.zhanghai.android.files.provider.root.RootStrategy
import me.zhanghai.android.files.storage.StorageListDestination
import me.zhanghai.android.files.theme.custom.ThemeColorPreference
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.BottomSheetListPreference
import me.zhanghai.android.files.ui.DialogListPreference
import me.zhanghai.android.files.ui.Preference
import me.zhanghai.android.files.ui.PreferenceCategory
import java.nio.charset.Charset

/** The settings opened from another app, like from the app info of the system settings. */
class SettingsActivity : AppActivity() {
    @Composable
    override fun Content() {
        SettingsDestination.Content()
    }
}

@Parcelize
data object SettingsDestination : Destination {
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
                Settings.BLACK_NIGHT_MODE, stringResource(R.string.settings_black_night_mode),
                icon = painterResource(R.drawable.dark_mode_icon_white_24dp)
            )
            SettingSwitchPreference(
                Settings.FILE_LIST_ANIMATION,
                stringResource(R.string.settings_file_list_animation_title),
                icon = painterResource(R.drawable.animation_icon_white_24dp)
            )
            SettingSwitchPreference(
                Settings.EDGE_TO_EDGE, stringResource(R.string.settings_edge_to_edge_title),
                stringResource(R.string.settings_edge_to_edge_summary),
                icon = painterResource(R.drawable.fullscreen_icon_white_24dp)
            )
            FileNameEllipsizePreference()
            PreferenceCategory(stringResource(R.string.settings_behavior_title))
            PathPreference(
                Settings.FILE_LIST_DEFAULT_DIRECTORY,
                stringResource(R.string.settings_default_directory_title),
                icon = painterResource(R.drawable.home_icon_white_24dp)
            )
            SettingSwitchPreference(
                Settings.DOUBLE_BACK_TO_EXIT,
                stringResource(R.string.settings_double_back_to_exit_title),
                icon = painterResource(R.drawable.arrow_back_icon_white_24dp)
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
                    stringResource(R.string.settings_show_pdf_thumbnail_pre_28_summary),
                    icon = painterResource(R.drawable.document_icon_white_24dp)
                )
            }
            SettingSwitchPreference(
                Settings.READ_REMOTE_FILES_FOR_THUMBNAIL,
                stringResource(R.string.settings_read_remote_files_for_thumbnail_title),
                icon = painterResource(R.drawable.image_icon_white_24dp)
            )
        }
    }
}

@Composable
private fun NightModePreference() {
    val nightMode by Settings.NIGHT_MODE.flow.collectAsState()
    val entries = stringArrayResource(R.array.settings_night_mode_entries)
    BottomSheetListPreference(
        title = stringResource(R.string.settings_night_mode_title),
        icon = painterResource(R.drawable.night_mode_icon_white_24dp),
        entries = NightMode.entries,
        entryLabel = { entries[it.ordinal] },
        value = nightMode,
        onValueChange = { Settings.NIGHT_MODE.putValue(it) }
    )
}

// TextUtils.TruncateAt also has a hidden value at runtime, which isn't offered.
private val FILE_NAME_ELLIPSIZE_ENTRIES = listOf(
    TextUtils.TruncateAt.START, TextUtils.TruncateAt.MIDDLE, TextUtils.TruncateAt.END,
    TextUtils.TruncateAt.MARQUEE
)

@Composable
private fun FileNameEllipsizePreference() {
    val ellipsize by Settings.FILE_NAME_ELLIPSIZE.flow.collectAsState()
    val entries = stringArrayResource(R.array.settings_file_name_ellipsize_entries)
    BottomSheetListPreference(
        title = stringResource(R.string.settings_file_name_ellipsize_title),
        icon = painterResource(R.drawable.more_horizontal_icon_white_24dp),
        entries = FILE_NAME_ELLIPSIZE_ENTRIES,
        entryLabel = { entries[it.ordinal] },
        value = ellipsize,
        onValueChange = { Settings.FILE_NAME_ELLIPSIZE.putValue(it) }
    )
}

@Composable
private fun StoragesPreference() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val storages by Settings.STORAGES.flow.collectAsState()
    val names = storages.filter { it.isVisible }.map { it.getName(context) }
    Preference(
        title = stringResource(R.string.settings_storages_title),
        icon = painterResource(R.drawable.sd_card_icon_white_24dp),
        summary = names.formatListOr(stringResource(R.string.settings_storages_summary_empty)),
        summaryMaxLines = 2,
        onClick = { navigator.navigate(StorageListDestination) }
    )
}

@Composable
private fun StandardDirectoriesPreference() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val standardDirectories by StandardDirectories.flow.collectAsState()
    val titles = standardDirectories.filter { it.isEnabled }.map { it.getTitle(context) }
    Preference(
        title = stringResource(R.string.settings_standard_directories_title),
        icon = painterResource(R.drawable.folder_icon_control_normal_24dp),
        summary = titles.formatListOr(
            stringResource(R.string.settings_standard_directories_summary_empty)
        ),
        onClick = { navigator.navigate(StandardDirectoryListDestination) }
    )
}

@Composable
private fun BookmarkDirectoriesPreference() {
    val navigator = LocalNavigator.current
    val bookmarkDirectories by Settings.BOOKMARK_DIRECTORIES.flow.collectAsState()
    Preference(
        title = stringResource(R.string.settings_bookmark_directories_title),
        icon = painterResource(R.drawable.bookmark_icon_white_24dp),
        summary = bookmarkDirectories.map { it.name }.formatListOr(
            stringResource(R.string.settings_bookmark_directories_summary_empty)
        ),
        onClick = { navigator.navigate(BookmarkDirectoryListDestination) }
    )
}

private fun List<String>.formatListOr(emptySummary: String): String =
    if (isNotEmpty()) ListFormatter.getInstance().format(this) else emptySummary

@Composable
private fun DefaultAppsPreference() {
    val navigator = LocalNavigator.current
    Preference(
        title = stringResource(R.string.settings_default_apps_title),
        icon = painterResource(R.drawable.apps_icon_white_24dp),
        summary = stringResource(R.string.settings_default_apps_summary),
        onClick = { navigator.navigate(DefaultAppsDestination) }
    )
}

@Composable
private fun RootStrategyPreference() {
    val rootStrategy by Settings.ROOT_STRATEGY.flow.collectAsState()
    val entries = stringArrayResource(R.array.settings_root_strategy_entries)
    var isSheetShown by rememberSaveable { mutableStateOf(false) }
    var runningJobCountToConfirm by rememberSaveable { mutableStateOf<Int?>(null) }
    BottomSheetListPreference(
        title = stringResource(R.string.settings_root_strategy_title),
        icon = painterResource(R.drawable.security_icon_white_24dp),
        entries = RootStrategy.entries,
        entryLabel = { entries[it.ordinal] },
        value = rootStrategy,
        onValueChange = { Settings.ROOT_STRATEGY.putValue(it) },
        isSheetShown = isSheetShown,
        onSheetShownChange = {
            // Changing it with file operations running may make them fail.
            val jobCount = FileJobService.runningJobCount
            if (it && jobCount != 0) {
                runningJobCountToConfirm = jobCount
            } else {
                isSheetShown = it
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
                        isSheetShown = true
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
    val encoding by Settings.ARCHIVE_FILE_NAME_ENCODING.flow.collectAsState()
    val charsets = remember { Charset.availableCharsets() }
    DialogListPreference(
        title = stringResource(R.string.settings_archive_file_name_encoding_title),
        icon = painterResource(R.drawable.archive_icon_control_normal_24dp),
        entries = charsets.keys.toList(),
        entryLabel = { charsets[it]?.displayName() ?: it },
        value = encoding,
        onValueChange = { Settings.ARCHIVE_FILE_NAME_ENCODING.putValue(it) }
    )
}

@Composable
private fun OpenApkDefaultActionPreference() {
    val action by Settings.OPEN_APK_DEFAULT_ACTION.flow.collectAsState()
    val entries = stringArrayResource(R.array.settings_open_apk_default_action_entries)
    BottomSheetListPreference(
        title = stringResource(R.string.settings_open_apk_default_action_title),
        icon = painterResource(R.drawable.install_unknown_apps_icon_white_24dp),
        entries = OpenApkDefaultAction.entries,
        entryLabel = { entries[it.ordinal] },
        value = action,
        onValueChange = { Settings.OPEN_APK_DEFAULT_ACTION.putValue(it) }
    )
}
