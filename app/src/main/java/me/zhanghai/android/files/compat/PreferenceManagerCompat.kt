/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.compat

import android.content.Context
import android.content.SharedPreferences

object PreferenceManagerCompat {
    fun getDefaultSharedPreferencesName(context: Context): String =
        "${context.packageName}_preferences"

    val defaultSharedPreferencesMode: Int
        get() = Context.MODE_PRIVATE

    fun getDefaultSharedPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(
            getDefaultSharedPreferencesName(context), defaultSharedPreferencesMode
        )
}
