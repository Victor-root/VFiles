/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.ComposeActivity
import me.zhanghai.android.files.file.ExternalStorageUri
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.createDocumentsUiViewDirectoryIntent
import me.zhanghai.android.files.util.showToast

/** Shows no UI, adds the shortcut if the system can open it and finishes. */
class AddExternalStorageShortcutActivity : ComposeActivity() {
    private val args by args<Args>()

    @Composable
    override fun Content() {
        LaunchedEffect(Unit) {
            addExternalStorageShortcut()
            finish()
        }
    }

    private fun addExternalStorageShortcut() {
        val uri = args.uri
        val hasDocumentsUi = uri.value.createDocumentsUiViewDirectoryIntent()
            .resolveActivity(packageManager) != null
        if (hasDocumentsUi) {
            val externalStorageShortcut = ExternalStorageShortcut(
                null, args.customNameRes?.let { getString(it) }, uri
            )
            Storages.addOrReplace(externalStorageShortcut)
        } else {
            showToast(R.string.activity_not_found)
        }
    }

    @Parcelize
    class Args(
        @param:StringRes val customNameRes: Int?,
        val uri: ExternalStorageUri
    ) : ParcelableArgs
}
