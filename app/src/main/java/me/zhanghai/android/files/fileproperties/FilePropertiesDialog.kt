/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.isApk
import me.zhanghai.android.files.file.isAudio
import me.zhanghai.android.files.file.isImage
import me.zhanghai.android.files.file.isVideo
import me.zhanghai.android.files.filelist.name
import me.zhanghai.android.files.fileproperties.apk.FilePropertiesApkTab
import me.zhanghai.android.files.fileproperties.audio.FilePropertiesAudioTab
import me.zhanghai.android.files.fileproperties.basic.FilePropertiesBasicTab
import me.zhanghai.android.files.fileproperties.checksum.FilePropertiesChecksumTab
import me.zhanghai.android.files.fileproperties.image.FilePropertiesImageTab
import me.zhanghai.android.files.fileproperties.permission.FilePropertiesPermissionTab
import me.zhanghai.android.files.fileproperties.video.FilePropertiesVideoTab
import me.zhanghai.android.files.provider.common.PosixFileAttributes
import me.zhanghai.android.files.ui.TextButton
import me.zhanghai.android.files.util.isGetPackageArchiveInfoCompatible
import me.zhanghai.android.files.util.isMediaMetadataRetrieverCompatible

/** The properties of [file], in tabs depending on its type. */
@Composable
fun FilePropertiesDialog(file: FileItem, onDismissRequest: () -> Unit) {
    // A dialog for another file gets its own view models.
    key(file.path) {
        ViewModelStoreScope { FilePropertiesDialogContent(file, onDismissRequest) }
    }
}

@Composable
private fun FilePropertiesDialogContent(file: FileItem, onDismissRequest: () -> Unit) {
    val fileViewModel = viewModel { FilePropertiesFileViewModel(file) }
    val tabs = remember(file) { FilePropertiesTabType.entries.filter { it.isAvailable(file) } }
    val pagerState = rememberPagerState { tabs.size }
    val coroutineScope = rememberCoroutineScope()
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(vertical = 24.dp)) {
                Text(
                    text = stringResource(R.string.file_properties_title_format, file.name),
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                    color = AlertDialogDefaults.titleContentColor,
                    style = MaterialTheme.typography.headlineSmall
                )
                PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    edgePadding = 8.dp
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = index == pagerState.currentPage,
                            onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = { Text(stringResource(tab.titleRes)) }
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.Top
                ) { page ->
                    when (tabs[page]) {
                        FilePropertiesTabType.BASIC -> FilePropertiesBasicTab(fileViewModel)
                        FilePropertiesTabType.PERMISSION ->
                            FilePropertiesPermissionTab(fileViewModel)
                        FilePropertiesTabType.IMAGE ->
                            FilePropertiesImageTab(file.path, file.mimeType)
                        FilePropertiesTabType.AUDIO -> FilePropertiesAudioTab(file.path)
                        FilePropertiesTabType.VIDEO -> FilePropertiesVideoTab(file.path)
                        FilePropertiesTabType.APK -> FilePropertiesApkTab(file.path)
                        FilePropertiesTabType.CHECKSUM -> FilePropertiesChecksumTab(file.path)
                    }
                }
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(start = 24.dp, top = 16.dp, end = 24.dp)
                ) { Text(stringResource(android.R.string.ok)) }
            }
        }
    }
}

private enum class FilePropertiesTabType(@param:StringRes val titleRes: Int) {
    BASIC(R.string.file_properties_basic),
    PERMISSION(R.string.file_properties_permission),
    IMAGE(R.string.file_properties_image),
    AUDIO(R.string.file_properties_audio),
    VIDEO(R.string.file_properties_video),
    APK(R.string.file_properties_apk),
    CHECKSUM(R.string.file_properties_checksum);

    fun isAvailable(file: FileItem): Boolean =
        when (this) {
            BASIC -> true
            PERMISSION -> {
                val attributes = file.attributes
                attributes is PosixFileAttributes && (attributes.owner() != null
                    || attributes.group() != null || attributes.mode() != null
                    || attributes.seLinuxContext() != null)
            }
            IMAGE -> file.mimeType.isImage
            AUDIO -> file.mimeType.isAudio && file.path.isMediaMetadataRetrieverCompatible
            VIDEO -> file.mimeType.isVideo && file.path.isMediaMetadataRetrieverCompatible
            APK -> file.mimeType.isApk && file.path.isGetPackageArchiveInfoCompatible
            CHECKSUM -> file.attributes.isRegularFile
        }
}
