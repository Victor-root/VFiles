/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.os.Bundle
import android.os.Parcelable
import me.zhanghai.android.files.app.appClassLoader

// Generic: no Class<T> available at these call sites for the API-33+ typed replacements.
@Suppress("DEPRECATION")
fun <T : Parcelable> Bundle.getParcelableSafe(key: String?): T? {
    classLoader = appClassLoader
    return getParcelable(key)
}
