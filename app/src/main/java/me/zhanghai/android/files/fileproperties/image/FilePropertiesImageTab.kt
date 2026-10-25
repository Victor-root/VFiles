/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.formatLong
import me.zhanghai.android.files.filelist.name
import me.zhanghai.android.files.fileproperties.FilePropertiesItem
import me.zhanghai.android.files.fileproperties.FilePropertiesTab
import me.zhanghai.android.files.fileproperties.LocationItems
import me.zhanghai.android.files.util.observeAsStateCompat
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun FilePropertiesImageTab(path: Path, mimeType: MimeType) {
    val viewModel = viewModel { FilePropertiesImageTabViewModel(path, mimeType) }
    val stateful by viewModel.imageInfoLiveData.observeAsStateCompat()
    FilePropertiesTab(stateful, onRefresh = viewModel::reload) { imageInfo ->
        FilePropertiesItem(
            R.string.file_properties_media_dimensions,
            if (imageInfo.dimensions != null) {
                stringResource(
                    R.string.file_properties_media_dimensions_format,
                    imageInfo.dimensions.width, imageInfo.dimensions.height
                )
            } else {
                stringResource(R.string.unknown)
            }
        )
        val exifInfo = imageInfo.exifInfo
        if (exifInfo != null) {
            ExifInfoItems(exifInfo, path)
        }
    }
}

@Composable
private fun ExifInfoItems(exifInfo: ExifInfo, path: Path) {
    if (exifInfo.dateTimeOriginal != null) {
        FilePropertiesItem(
            R.string.file_properties_media_date_time, exifInfo.dateTimeOriginal.formatLong()
        )
    }
    if (exifInfo.gpsCoordinates != null) {
        LocationItems(exifInfo.gpsCoordinates.first, exifInfo.gpsCoordinates.second, path.name)
    }
    if (exifInfo.gpsAltitude != null) {
        FilePropertiesItem(
            R.string.file_properties_image_gps_altitude,
            stringResource(R.string.file_properties_image_gps_altitude_format, exifInfo.gpsAltitude)
        )
    }
    val equipment = getEquipment(exifInfo.make, exifInfo.model)
    if (equipment != null) {
        FilePropertiesItem(R.string.file_properties_image_equipment, equipment)
    }
    if (exifInfo.fNumber != null) {
        FilePropertiesItem(
            R.string.file_properties_image_f_number,
            stringResource(R.string.file_properties_image_f_number_format, exifInfo.fNumber)
        )
    }
    if (exifInfo.shutterSpeedValue != null) {
        FilePropertiesItem(
            R.string.file_properties_image_shutter_speed,
            getShutterSpeedText(exifInfo.shutterSpeedValue)
        )
    }
    if (exifInfo.focalLength != null) {
        FilePropertiesItem(
            R.string.file_properties_image_focal_length,
            stringResource(R.string.file_properties_image_focal_length_format, exifInfo.focalLength)
        )
    }
    if (exifInfo.photographicSensitivity != null) {
        FilePropertiesItem(
            R.string.file_properties_image_photographic_sensitivity,
            stringResource(
                R.string.file_properties_image_photographic_sensitivity_format,
                exifInfo.photographicSensitivity
            )
        )
    }
    if (exifInfo.software != null) {
        FilePropertiesItem(R.string.file_properties_image_software, exifInfo.software)
    }
    if (exifInfo.description != null) {
        FilePropertiesItem(R.string.file_properties_image_description, exifInfo.description)
    }
    if (exifInfo.artist != null) {
        FilePropertiesItem(R.string.file_properties_image_artist, exifInfo.artist)
    }
    if (exifInfo.copyright != null) {
        FilePropertiesItem(R.string.file_properties_image_copyright, exifInfo.copyright)
    }
}

@Composable
private fun getEquipment(make: String?, model: String?): String? =
    when {
        make != null && model != null -> {
            if (model.startsWith(make, true)) {
                model
            } else {
                stringResource(R.string.file_properties_image_equipment_format, make, model)
            }
        }
        make != null -> make
        model != null -> model
        else -> null
    }

// @see com.android.documentsui.inspector.MediaView.formatShutterSpeed
@Composable
private fun getShutterSpeedText(value: Double): String =
    if (value <= 0) {
        val shutterSpeed = 2.0.pow(-1 * value)
        ((shutterSpeed * 10.0).roundToInt() / 10.0).toString()
    } else {
        val approximateDenominator = 2.0.pow(value).toInt() + 1
        stringResource(
            R.string.file_properties_image_shutter_speed_with_denominator_format,
            approximateDenominator
        )
    }
