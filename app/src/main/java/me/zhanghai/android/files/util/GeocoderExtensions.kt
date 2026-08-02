/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

val isGeocoderPresent by lazy { Geocoder.isPresent() }

// The modern replacement is a callback-based overload (API 33+, needing an SDK gate since minSdk
// is 23) with its own error-reporting shape, not a drop-in swap. This synchronous overload is
// already off the main thread via Dispatchers.IO below, so it does not block the UI either way. Not
// verifiable without a real device with GPS-tagged photos/videos to exercise this against.
@Suppress("DEPRECATION")
@Throws(IOException::class)
suspend fun Geocoder.awaitGetFromLocation(
    latitude: Double,
    longitude: Double,
    maxResults: Int
): List<Address> =
    withContext(Dispatchers.IO) {
        getFromLocation(latitude, longitude, maxResults)
            ?: throw IOException(NullPointerException())
    }
