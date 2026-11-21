/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.Destination
import me.zhanghai.android.files.app.LocalNavigator
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.ui.ReadOnlyTextField
import me.zhanghai.android.files.ui.TextFieldDialog

@Parcelize
data class EditBookmarkDirectoryDestination(
    val bookmarkDirectory: BookmarkDirectory
) : Destination {
    override val isDialog: Boolean
        get() = true

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        EditBookmarkDirectoryDialog(bookmarkDirectory, onDismissRequest = navigator::goBack)
    }
}

@Composable
private fun EditBookmarkDirectoryDialog(
    bookmarkDirectory: BookmarkDirectory,
    onDismissRequest: () -> Unit
) {
    var path by rememberSaveable { mutableStateOf(bookmarkDirectory.path) }
    val openPathLauncher =
        rememberLauncherForActivityResult(FileListActivity.OpenDirectoryContract()) {
            if (it != null) {
                path = it
            }
        }
    val defaultName = bookmarkDirectory.defaultName
    TextFieldDialog(
        title = stringResource(R.string.navigation_edit_bookmark_directory_title),
        onConfirm = { name ->
            val customName = name.takeIf { it.isNotEmpty() && it != defaultName }
            BookmarkDirectories.replace(
                bookmarkDirectory.copy(customName = customName, path = path)
            )
        },
        onDismissRequest = onDismissRequest,
        initialText = bookmarkDirectory.name,
        label = stringResource(R.string.navigation_edit_bookmark_directory_name),
        extraContent = {
            ReadOnlyTextField(
                value = path.toUserFriendlyString(),
                label = stringResource(R.string.navigation_edit_bookmark_directory_path),
                onClick = { openPathLauncher.launch(path) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )
        },
        placeholder = defaultName,
        neutralButton = {
            TextButton(
                onClick = {
                    BookmarkDirectories.remove(bookmarkDirectory)
                    onDismissRequest()
                }
            ) { Text(stringResource(R.string.remove)) }
        }
    )
}
