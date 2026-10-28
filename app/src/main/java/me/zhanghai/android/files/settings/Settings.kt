/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.os.Environment
import android.text.TextUtils
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.R
import me.zhanghai.android.files.filelist.ArchiveType
import me.zhanghai.android.files.filelist.FileSortOptions
import me.zhanghai.android.files.filelist.FileViewType
import me.zhanghai.android.files.filelist.OpenApkDefaultAction
import me.zhanghai.android.files.navigation.BookmarkDirectory
import me.zhanghai.android.files.navigation.StandardDirectorySettings
import me.zhanghai.android.files.provider.root.RootStrategy
import me.zhanghai.android.files.storage.FileSystemRoot
import me.zhanghai.android.files.storage.PrimaryStorageVolume
import me.zhanghai.android.files.storage.Storage
import me.zhanghai.android.files.storage.StorageVolumeCustomization
import me.zhanghai.android.files.theme.custom.ThemeColor
import me.zhanghai.android.files.theme.night.NightMode

object Settings {
    val STORAGES: Setting<List<Storage>> =
        ParcelValueSetting(
            R.string.pref_key_storages,
            listOf(FileSystemRoot(null, false), PrimaryStorageVolume(null, true))
        )

    val FILE_LIST_DEFAULT_DIRECTORY: Setting<Path> =
        ParcelValueSetting(
            R.string.pref_key_file_list_default_directory,
            @Suppress("DEPRECATION")
            Paths.get(Environment.getExternalStorageDirectory().absolutePath)
        )

    val FILE_LIST_PERSISTENT_DRAWER_OPEN: Setting<Boolean> =
        BooleanSetting(
            R.string.pref_key_file_list_persistent_drawer_open,
            R.bool.pref_default_value_file_list_persistent_drawer_open
        )

    val FILE_LIST_SHOW_HIDDEN_FILES: Setting<Boolean> =
        BooleanSetting(
            R.string.pref_key_file_list_show_hidden_files,
            R.bool.pref_default_value_file_list_show_hidden_files
        )

    val FILE_LIST_VIEW_TYPE: Setting<FileViewType> =
        EnumSetting(
            R.string.pref_key_file_list_view_type, R.string.pref_default_value_file_list_view_type,
            FileViewType::class.java
        )

    val FILE_LIST_SORT_OPTIONS: Setting<FileSortOptions> =
        ParcelValueSetting(
            R.string.pref_key_file_list_sort_options,
            FileSortOptions(FileSortOptions.By.NAME, FileSortOptions.Order.ASCENDING, true)
        )

    /** The language tags of the app locales before Android 13, empty to follow the system. */
    val LOCALES: Setting<String> =
        StringSetting(R.string.pref_key_locales, R.string.pref_default_value_locales)

    val CREATE_ARCHIVE_TYPE: Setting<ArchiveType> =
        EnumSetting(
            R.string.pref_key_create_archive_type, R.string.pref_default_value_create_archive_type,
            ArchiveType::class.java
        )

    val FTP_SERVER_ANONYMOUS_LOGIN: Setting<Boolean> =
        BooleanSetting(
            R.string.pref_key_ftp_server_anonymous_login,
            R.bool.pref_default_value_ftp_server_anonymous_login
        )

    val FTP_SERVER_USERNAME: Setting<String> =
        StringSetting(
            R.string.pref_key_ftp_server_username, R.string.pref_default_value_ftp_server_username
        )

    val FTP_SERVER_PASSWORD: Setting<String> =
        StringSetting(
            R.string.pref_key_ftp_server_password, R.string.pref_default_value_empty
        )

    val FTP_SERVER_PORT: Setting<Int> =
        IntegerSetting(
            R.string.pref_key_ftp_server_port, R.integer.pref_default_value_ftp_server_port
        )

    val FTP_SERVER_HOME_DIRECTORY: Setting<Path> =
        ParcelValueSetting(
            R.string.pref_key_ftp_server_home_directory,
            @Suppress("DEPRECATION")
            Paths.get(Environment.getExternalStorageDirectory().absolutePath)
        )

    val FTP_SERVER_WRITABLE: Setting<Boolean> =
        BooleanSetting(
            R.string.pref_key_ftp_server_writable, R.bool.pref_default_value_ftp_server_writable
        )

    val THEME_COLOR: Setting<ThemeColor> =
        EnumSetting(
            R.string.pref_key_theme_color, R.string.pref_default_value_theme_color,
            ThemeColor::class.java
        )

    val NIGHT_MODE: Setting<NightMode> =
        EnumSetting(
            R.string.pref_key_night_mode, R.string.pref_default_value_night_mode,
            NightMode::class.java
        )

    val BLACK_NIGHT_MODE: Setting<Boolean> =
        BooleanSetting(
            R.string.pref_key_black_night_mode, R.bool.pref_default_value_black_night_mode
        )

    val FILE_LIST_ANIMATION: Setting<Boolean> =
        BooleanSetting(
            R.string.pref_key_file_list_animation, R.bool.pref_default_value_file_list_animation
        )

    val DOUBLE_BACK_TO_EXIT: Setting<Boolean> =
        BooleanSetting(
            R.string.pref_key_double_back_to_exit, R.bool.pref_default_value_double_back_to_exit
        )

    val EDGE_TO_EDGE: Setting<Boolean> =
        BooleanSetting(
            R.string.pref_key_edge_to_edge, R.bool.pref_default_value_edge_to_edge
        )

    val FILE_NAME_ELLIPSIZE: Setting<TextUtils.TruncateAt> =
        EnumSetting(
            R.string.pref_key_file_name_ellipsize, R.string.pref_default_value_file_name_ellipsize,
            TextUtils.TruncateAt::class.java
        )

    val STANDARD_DIRECTORY_SETTINGS: Setting<List<StandardDirectorySettings>> =
        ParcelValueSetting(R.string.pref_key_standard_directory_settings, emptyList())

    // The keys of the standard directories in the order chosen by the user, or empty for the
    // default one.
    val STANDARD_DIRECTORY_ORDER: Setting<List<String>> =
        ParcelValueSetting(R.string.pref_key_standard_directory_order, emptyList())

    val BOOKMARK_DIRECTORIES: Setting<List<BookmarkDirectory>> =
        ParcelValueSetting(R.string.pref_key_bookmark_directories, emptyList())

    val STORAGE_VOLUME_CUSTOMIZATIONS: Setting<List<StorageVolumeCustomization>> =
        ParcelValueSetting(R.string.pref_key_storage_volume_customizations, emptyList())

    val FILE_OPEN_DEFAULT_APPS: Setting<List<FileOpenDefaultApp>> =
        ParcelValueSetting(R.string.pref_key_file_open_default_apps, emptyList())

    val ROOT_STRATEGY: Setting<RootStrategy> =
        EnumSetting(
            R.string.pref_key_root_strategy, R.string.pref_default_value_root_strategy,
            RootStrategy::class.java
        )

    val ARCHIVE_FILE_NAME_ENCODING: Setting<String> =
        StringSetting(
            R.string.pref_key_archive_file_name_encoding,
            R.string.pref_default_value_archive_file_name_encoding
        )

    val OPEN_APK_DEFAULT_ACTION: Setting<OpenApkDefaultAction> =
        EnumSetting(
            R.string.pref_key_open_apk_default_action,
            R.string.pref_default_value_open_apk_default_action,
            OpenApkDefaultAction::class.java
        )

    val SHOW_PDF_THUMBNAIL_PRE_28: Setting<Boolean> = BooleanSetting(
        R.string.pref_key_show_pdf_thumbnail_pre_28,
        R.bool.pref_default_value_show_pdf_thumbnail_pre_28
    )

    val READ_REMOTE_FILES_FOR_THUMBNAIL: Setting<Boolean> =
        BooleanSetting(
            R.string.pref_key_read_remote_files_for_thumbnail,
            R.bool.pref_default_value_read_remote_files_for_thumbnail
        )

    val ONBOARDING_COMPLETED: Setting<Boolean> =
        BooleanSetting(
            R.string.pref_key_onboarding_completed,
            R.bool.pref_default_value_onboarding_completed
        )
}
