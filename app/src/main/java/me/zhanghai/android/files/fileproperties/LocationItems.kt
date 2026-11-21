/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties

import android.content.Intent
import android.location.Geocoder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.awaitGetFromLocation
import me.zhanghai.android.files.util.createViewLocation
import me.zhanghai.android.files.util.isGeocoderPresent
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.userFriendlyString

/**
 * The coordinates where a media was taken, which open in a map app labeled with [label], and their
 * address when there's a geocoder.
 */
@Composable
fun LocationItems(latitude: Double, longitude: Double, label: String) {
    val context = LocalContext.current
    FilePropertiesItem(
        R.string.file_properties_media_coordinates,
        stringResource(R.string.file_properties_media_coordinates_format, latitude, longitude)
    ) {
        context.startActivitySafe(
            Intent::class.createViewLocation(latitude.toFloat(), longitude.toFloat(), label)
        )
    }
    if (isGeocoderPresent) {
        val unknownText = stringResource(R.string.unknown)
        val address by produceState<String?>(null, latitude, longitude, unknownText) {
            value = try {
                Geocoder(context).awaitGetFromLocation(latitude, longitude, 1).first()
                    .userFriendlyString
            } catch (e: Exception) {
                null
            } ?: unknownText
        }
        FilePropertiesItem(
            R.string.file_properties_media_address, address ?: stringResource(R.string.loading)
        )
    }
}
