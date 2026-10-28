/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.Destination
import me.zhanghai.android.files.app.LocalNavigator
import me.zhanghai.android.files.file.ExternalStorageUri
import me.zhanghai.android.files.util.createDocumentsUiViewDirectoryIntent
import me.zhanghai.android.files.util.showToast

/** Shows no UI, adds the shortcut if the system can open it and closes. */
@Parcelize
data class AddExternalStorageShortcutDestination(
    @param:StringRes val customNameRes: Int?,
    val uri: ExternalStorageUri
) : Destination {
    override val isDialog: Boolean
        get() = true

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        LaunchedEffect(Unit) {
            context.addExternalStorageShortcut(customNameRes, uri)
            navigator.goBack()
        }
    }
}

private fun Context.addExternalStorageShortcut(
    @StringRes customNameRes: Int?,
    uri: ExternalStorageUri
) {
    val hasDocumentsUi = uri.value.createDocumentsUiViewDirectoryIntent()
        .resolveActivity(packageManager) != null
    if (hasDocumentsUi) {
        val externalStorageShortcut =
            ExternalStorageShortcut(null, customNameRes?.let { getString(it) }, uri)
        Storages.addOrReplace(externalStorageShortcut)
    } else {
        showToast(R.string.activity_not_found)
    }
}
