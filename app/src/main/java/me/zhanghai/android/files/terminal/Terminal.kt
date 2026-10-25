/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.terminal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import me.zhanghai.android.files.app.packageManager
import me.zhanghai.android.files.util.startActivitySafe

object Terminal {
    fun open(path: String, context: Context) {
        val componentName =
            packageManager.queryIntentActivities(Intent(Intent.ACTION_SEND).setType("*/*"), 0)
                .firstOrNull { it.activityInfo.name.endsWith(".TermHere") }?.activityInfo
                ?.let { ComponentName(it.packageName, it.name) }
                ?: ComponentName("jackpal.androidterm", "jackpal.androidterm.TermHere")
        val intent = Intent()
            .setComponent(componentName)
            .setAction(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_STREAM, path.toUri())
        context.startActivitySafe(intent)
    }
}
