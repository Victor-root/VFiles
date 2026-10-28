/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.audio

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.format
import me.zhanghai.android.files.fileproperties.FilePropertiesItem
import me.zhanghai.android.files.fileproperties.FilePropertiesTab

@Composable
fun FilePropertiesAudioTab(path: Path) {
    val viewModel = viewModel { FilePropertiesAudioTabViewModel(path) }
    val stateful by viewModel.audioInfoFlow.collectAsStateWithLifecycle()
    FilePropertiesTab(stateful, onRefresh = viewModel::reload) { audioInfo ->
        if (audioInfo.title != null) {
            FilePropertiesItem(R.string.file_properties_media_title, audioInfo.title)
        }
        if (audioInfo.artist != null) {
            FilePropertiesItem(R.string.file_properties_audio_artist, audioInfo.artist)
        }
        if (audioInfo.album != null) {
            FilePropertiesItem(R.string.file_properties_audio_album, audioInfo.album)
        }
        if (audioInfo.albumArtist != null) {
            FilePropertiesItem(R.string.file_properties_audio_album_artist, audioInfo.albumArtist)
        }
        if (audioInfo.composer != null) {
            FilePropertiesItem(R.string.file_properties_audio_composer, audioInfo.composer)
        }
        if (audioInfo.discNumber != null) {
            FilePropertiesItem(R.string.file_properties_audio_disc_number, audioInfo.discNumber)
        }
        if (audioInfo.trackNumber != null) {
            FilePropertiesItem(R.string.file_properties_audio_track_number, audioInfo.trackNumber)
        }
        if (audioInfo.year != null) {
            FilePropertiesItem(R.string.file_properties_audio_year, audioInfo.year)
        }
        if (audioInfo.genre != null) {
            FilePropertiesItem(R.string.file_properties_audio_genre, audioInfo.genre)
        }
        if (audioInfo.duration != null) {
            FilePropertiesItem(R.string.file_properties_media_duration, audioInfo.duration.format())
        }
        if (audioInfo.bitRate != null) {
            FilePropertiesItem(
                R.string.file_properties_media_bit_rate,
                stringResource(
                    R.string.file_properties_media_bit_rate_format, audioInfo.bitRate / 1000
                )
            )
        }
        if (audioInfo.sampleRate != null) {
            FilePropertiesItem(
                R.string.file_properties_audio_sample_rate,
                stringResource(
                    R.string.file_properties_audio_sample_rate_format, audioInfo.sampleRate
                )
            )
        }
    }
}
