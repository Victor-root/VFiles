/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.video

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.format
import me.zhanghai.android.files.file.formatLong
import me.zhanghai.android.files.filelist.name
import me.zhanghai.android.files.fileproperties.FilePropertiesItem
import me.zhanghai.android.files.fileproperties.FilePropertiesTab
import me.zhanghai.android.files.fileproperties.LocationItems

@Composable
fun FilePropertiesVideoTab(path: Path) {
    val viewModel = viewModel { FilePropertiesVideoTabViewModel(path) }
    val stateful by viewModel.videoInfoFlow.collectAsStateWithLifecycle()
    FilePropertiesTab(stateful, onRefresh = viewModel::reload) { videoInfo ->
        if (videoInfo.title != null) {
            FilePropertiesItem(R.string.file_properties_media_title, videoInfo.title)
        }
        if (videoInfo.dimensions != null) {
            FilePropertiesItem(
                R.string.file_properties_media_dimensions,
                stringResource(
                    R.string.file_properties_media_dimensions_format, videoInfo.dimensions.width,
                    videoInfo.dimensions.height
                )
            )
        }
        if (videoInfo.duration != null) {
            FilePropertiesItem(R.string.file_properties_media_duration, videoInfo.duration.format())
        }
        if (videoInfo.date != null) {
            FilePropertiesItem(
                R.string.file_properties_media_date_time, videoInfo.date.formatLong()
            )
        }
        if (videoInfo.location != null) {
            LocationItems(
                videoInfo.location.first.toDouble(), videoInfo.location.second.toDouble(),
                path.name
            )
        }
        if (videoInfo.bitRate != null) {
            FilePropertiesItem(
                R.string.file_properties_media_bit_rate,
                stringResource(
                    R.string.file_properties_media_bit_rate_format, videoInfo.bitRate / 1000
                )
            )
        }
    }
}
