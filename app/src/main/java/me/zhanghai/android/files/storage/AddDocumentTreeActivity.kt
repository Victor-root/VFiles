/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import me.zhanghai.android.files.app.ComposeActivity
import me.zhanghai.android.files.file.asDocumentTreeUriOrNull
import me.zhanghai.android.files.file.takePersistablePermission
import me.zhanghai.android.files.util.launchSafe

/** Shows no UI but the system picker of the document tree to add. */
class AddDocumentTreeActivity : ComposeActivity() {
    @Composable
    override fun Content() {
        val openDocumentTreeLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
                val treeUri = it?.asDocumentTreeUriOrNull()
                if (treeUri != null) {
                    treeUri.takePersistablePermission()
                    Storages.addOrReplace(DocumentTree(null, null, treeUri))
                }
                finish()
            }
        val context = LocalContext.current
        var isLaunched by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (!isLaunched) {
                isLaunched = true
                openDocumentTreeLauncher.launchSafe(null, context)
            }
        }
    }
}
