/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.util.removeFirst
import me.zhanghai.android.files.util.valueCompat

/** The package the user chose to open a [FileOpenAppCategory] with. */
@Parcelize
data class FileOpenDefaultApp(val category: String, val packageName: String) : Parcelable

object FileOpenDefaultApps {
    fun getPackage(category: FileOpenAppCategory): String? =
        Settings.FILE_OPEN_DEFAULT_APPS.valueCompat
            .firstOrNull { it.category == category.name }
            ?.packageName

    fun setPackage(category: FileOpenAppCategory, packageName: String?) {
        val apps = Settings.FILE_OPEN_DEFAULT_APPS.valueCompat.toMutableList()
            .apply { removeFirst { it.category == category.name } }
        if (packageName != null) {
            apps += FileOpenDefaultApp(category.name, packageName)
        }
        Settings.FILE_OPEN_DEFAULT_APPS.putValue(apps)
    }
}
