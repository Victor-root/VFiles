/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.filelist.FileSortOptions.By
import me.zhanghai.android.files.filelist.FileSortOptions.Order
import me.zhanghai.android.files.navigation.NavigationRootMap
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.ui.ActionButton
import me.zhanghai.android.files.ui.MenuDivider
import me.zhanghai.android.files.ui.MenuItem
import me.zhanghai.android.files.ui.OverflowMenu
import me.zhanghai.android.files.ui.RoundedDropdownMenu
import me.zhanghai.android.files.ui.appTopAppBarColors
import me.zhanghai.android.files.ui.rememberTextFieldKeyboard

/** The colors of the app bar, which change once the list is scrolled. */
class FileListAppBarColors(val container: Color, val content: Color)

@Composable
private fun topAppBarColors(colors: FileListAppBarColors) =
    TopAppBarDefaults.topAppBarColors(
        containerColor = colors.container,
        scrolledContainerColor = colors.container,
        navigationIconContentColor = colors.content,
        titleContentColor = colors.content,
        actionIconContentColor = colors.content,
        subtitleContentColor = colors.content
    )

@Composable
fun fileListAppBarColors(isScrolled: Boolean): FileListAppBarColors {
    val colors = appTopAppBarColors()
    return FileListAppBarColors(
        if (isScrolled) colors.scrolledContainerColor else colors.containerColor,
        colors.titleContentColor
    )
}

@Composable
fun FileListTopAppBar(
    title: String,
    subtitle: String,
    isTitleScrolling: Boolean,
    colors: FileListAppBarColors,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    // A long title, like when picking files for another app, scrolls to be read.
                    modifier = if (isTitleScrolling) {
                        Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    } else {
                        Modifier
                    },
                    maxLines = 1,
                    overflow = if (isTitleScrolling) TextOverflow.Clip else TextOverflow.Ellipsis
                )
                Crossfade(targetState = subtitle) {
                    Text(
                        text = it,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = topAppBarColors(colors),
        // The status bar padding is above the part that hides on scroll.
        windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal)
    )
}

/** The app bar while searching, with the search field in place of the title. */
@Composable
fun FileListSearchAppBar(
    queryState: TextFieldState,
    colors: FileListAppBarColors,
    onSearch: () -> Unit,
    onCollapse: () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    TopAppBar(
        title = {
            Box(contentAlignment = Alignment.CenterStart) {
                if (queryState.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search),
                        color = colors.content.copy(alpha = 0.6f),
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                val keyboard =
                    rememberTextFieldKeyboard(KeyboardOptions(imeAction = ImeAction.Search))
                BasicTextField(
                    state = queryState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(keyboard.modifier)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.content),
                    keyboardOptions = keyboard.keyboardOptions,
                    onKeyboardAction = { onSearch() },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    cursorBrush = SolidColor(colors.content)
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onCollapse) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_icon_white_24dp),
                    contentDescription = stringResource(R.string.navigate_up)
                )
            }
        },
        actions = {
            if (queryState.text.isNotEmpty()) {
                IconButton(onClick = { queryState.clearText() }) {
                    Icon(
                        painter = painterResource(R.drawable.close_icon_white_24dp),
                        contentDescription = stringResource(R.string.clear_query)
                    )
                }
            }
            actions()
        },
        colors = topAppBarColors(colors),
        windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal)
    )
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
}

/** The app bar shown over the normal one while files are selected. */
@Composable
fun FileListSelectionAppBar(
    selectedFiles: FileItemSet,
    pickOptions: PickOptions?,
    currentPath: Path,
    controller: FileListController,
    colors: FileListAppBarColors
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.file_list_select_title_format, selectedFiles.size),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = controller::clearSelectedFiles) {
                Icon(
                    painter = painterResource(R.drawable.close_icon_white_24dp),
                    contentDescription = stringResource(R.string.close)
                )
            }
        },
        actions = {
            if (pickOptions != null) {
                PickSelectionActions(pickOptions, controller)
            } else {
                SelectionActions(selectedFiles, currentPath, controller)
            }
        },
        colors = topAppBarColors(colors),
        windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal)
    )
}

@Composable
private fun PickSelectionActions(pickOptions: PickOptions, controller: FileListController) {
    if (pickOptions.mode == PickOptions.Mode.CREATE_FILE) {
        ActionButton(R.drawable.save_icon_white_24dp, stringResource(R.string.save)) {
            controller.createSelectedFile()
        }
    } else {
        ActionButton(R.drawable.check_icon_white_24dp, stringResource(R.string.select)) {
            controller.pickSelectedFiles()
        }
    }
    if (pickOptions.allowMultiple) {
        OverflowMenu(controller.overflowMenuRequests) { dismiss ->
            MenuItem(
                R.drawable.select_all_icon_control_normal_24dp, stringResource(R.string.select_all)
            ) {
                dismiss()
                controller.selectAllFiles()
            }
        }
    }
}

@Composable
private fun SelectionActions(
    selectedFiles: FileItemSet,
    currentPath: Path,
    controller: FileListController
) {
    val isAnyFileReadOnly = selectedFiles.any { it.path.fileSystem.isReadOnly }
    val areAllFilesArchivePaths = selectedFiles.all { it.path.isArchivePath }
    if (!isAnyFileReadOnly) {
        ActionButton(R.drawable.cut_icon_control_normal_24dp, stringResource(R.string.cut)) {
            controller.cutFiles(selectedFiles)
        }
    }
    if (areAllFilesArchivePaths) {
        ActionButton(
            R.drawable.extract_icon_control_normal_24dp,
            stringResource(R.string.file_list_select_action_extract)
        ) { controller.copyFiles(selectedFiles) }
    } else {
        ActionButton(R.drawable.copy_icon_control_normal_24dp, stringResource(R.string.copy)) {
            controller.copyFiles(selectedFiles)
        }
    }
    if (!isAnyFileReadOnly) {
        ActionButton(R.drawable.delete_icon_control_normal_24dp, stringResource(R.string.delete)) {
            controller.confirmDeleteFiles(selectedFiles)
        }
    }
    OverflowMenu(controller.overflowMenuRequests) { dismiss ->
        val canExtract = selectedFiles.all { it.isArchiveFile }
        val canArchive = !currentPath.fileSystem.isReadOnly
        if (canExtract) {
            MenuItem(
                R.drawable.extract_icon_control_normal_24dp,
                stringResource(R.string.file_list_select_action_extract)
            ) {
                dismiss()
                controller.extractFiles(selectedFiles)
            }
        }
        if (canArchive) {
            MenuItem(
                R.drawable.archive_icon_control_normal_24dp,
                stringResource(R.string.file_list_select_action_archive)
            ) {
                dismiss()
                controller.showCreateArchiveDialog(selectedFiles)
            }
        }
        if (canExtract || canArchive) {
            MenuDivider()
        }
        MenuItem(R.drawable.share_icon_control_normal_24dp, stringResource(R.string.share)) {
            dismiss()
            controller.shareFiles(selectedFiles)
        }
        MenuDivider()
        MenuItem(
            R.drawable.select_all_icon_control_normal_24dp, stringResource(R.string.select_all)
        ) {
            dismiss()
            controller.selectAllFiles()
        }
    }
}

/** The menu choosing how the files are shown and sorted. */
@Composable
fun ViewSortMenuButton(
    viewType: FileViewType,
    sortOptions: FileSortOptions,
    isViewSortPathSpecific: Boolean,
    viewModel: FileListViewModel
) {
    var isMenuShown by remember { mutableStateOf(false) }
    Box {
        ActionButton(
            R.drawable.sort_icon_control_normal_24dp,
            stringResource(R.string.file_list_action_view_sort)
        ) { isMenuShown = true }
        RoundedDropdownMenu(expanded = isMenuShown, onDismissRequest = { isMenuShown = false }) {
            val dismiss = { isMenuShown = false }
            @Composable
            fun Item(
                iconRes: Int,
                titleRes: Int,
                isRadio: Boolean,
                isChecked: Boolean,
                onClick: () -> Unit
            ) {
                MenuItem(
                    iconRes = iconRes,
                    title = stringResource(titleRes),
                    trailingContent = {
                        if (isRadio) {
                            RadioButton(selected = isChecked, onClick = null)
                        } else {
                            Checkbox(checked = isChecked, onCheckedChange = null)
                        }
                    }
                ) {
                    dismiss()
                    onClick()
                }
            }

            Item(
                R.drawable.view_list_icon_control_normal_24dp, R.string.file_list_action_view_list,
                true, viewType == FileViewType.LIST
            ) { viewModel.viewType = FileViewType.LIST }
            Item(
                R.drawable.view_grid_icon_control_normal_24dp, R.string.file_list_action_view_grid,
                true, viewType == FileViewType.GRID
            ) { viewModel.viewType = FileViewType.GRID }
            MenuDivider()
            for ((by, iconRes, titleRes) in SORT_BY_ITEMS) {
                Item(iconRes, titleRes, true, sortOptions.by == by) { viewModel.setSortBy(by) }
            }
            MenuDivider()
            Item(
                R.drawable.sort_order_icon_control_normal_24dp,
                R.string.file_list_action_sort_order_ascending, false,
                sortOptions.order == Order.ASCENDING
            ) {
                viewModel.setSortOrder(
                    if (sortOptions.order == Order.ASCENDING) Order.DESCENDING else Order.ASCENDING
                )
            }
            Item(
                R.drawable.folder_icon_control_normal_24dp,
                R.string.file_list_action_sort_directories_first, false,
                sortOptions.isDirectoriesFirst
            ) { viewModel.setSortDirectoriesFirst(!sortOptions.isDirectoriesFirst) }
            MenuDivider()
            Item(
                R.drawable.tune_icon_control_normal_24dp,
                R.string.file_list_action_view_sort_path_specific, false, isViewSortPathSpecific
            ) { viewModel.isViewSortPathSpecific = !isViewSortPathSpecific }
        }
    }
}

private val SORT_BY_ITEMS = listOf(
    Triple(
        By.NAME, R.drawable.sort_by_name_icon_control_normal_24dp,
        R.string.file_list_action_sort_by_name
    ),
    Triple(
        By.TYPE, R.drawable.sort_by_type_icon_control_normal_24dp,
        R.string.file_list_action_sort_by_type
    ),
    Triple(
        By.SIZE, R.drawable.sort_by_size_icon_control_normal_24dp,
        R.string.file_list_action_sort_by_size
    ),
    Triple(
        By.LAST_MODIFIED, R.drawable.sort_by_date_icon_control_normal_24dp,
        R.string.file_list_action_sort_by_last_modified
    )
)

/** The overflow menu of the current folder. */
@Composable
fun FileListOverflowMenu(controller: FileListController, showHiddenFiles: Boolean) {
    OverflowMenu(controller.overflowMenuRequests) { dismiss ->
        @Composable
        fun Item(iconRes: Int, titleRes: Int, enabled: Boolean = true, onClick: () -> Unit) {
            MenuItem(iconRes, stringResource(titleRes), enabled) {
                dismiss()
                onClick()
            }
        }

        Item(R.drawable.arrow_up_icon_control_normal_24dp, R.string.file_list_action_navigate_up) {
            controller.navigateUp()
        }
        Item(
            R.drawable.navigate_to_icon_control_normal_24dp, R.string.file_list_action_navigate_to
        ) { controller.showNavigateToPathDialog() }
        Item(R.drawable.refresh_icon_control_normal_24dp, R.string.refresh) { controller.refresh() }
        Item(R.drawable.new_window_icon_control_normal_24dp, R.string.file_list_action_new_task) {
            controller.newTask()
        }
        MenuDivider()
        if (controller.canSelectAll) {
            Item(R.drawable.select_all_icon_control_normal_24dp, R.string.select_all) {
                controller.selectAllFiles()
            }
        }
        MenuItem(
            iconRes = R.drawable.visibility_icon_control_normal_24dp,
            title = stringResource(R.string.file_list_action_show_hidden_files),
            trailingContent = { Checkbox(checked = showHiddenFiles, onCheckedChange = null) }
        ) {
            dismiss()
            controller.toggleShowHiddenFiles()
        }
        MenuDivider()
        Item(R.drawable.share_icon_control_normal_24dp, R.string.share) {
            controller.shareCurrentDirectory()
        }
        Item(R.drawable.copy_icon_control_normal_24dp, R.string.file_list_action_copy_path) {
            controller.copyCurrentPath()
        }
        Item(
            R.drawable.terminal_icon_control_normal_24dp,
            R.string.file_list_action_open_in_terminal, controller.canOpenInTerminal
        ) { controller.openInTerminal() }
        MenuDivider()
        Item(
            R.drawable.bookmark_icon_control_normal_24dp, R.string.file_list_action_add_bookmark
        ) { controller.addCurrentDirectoryBookmark() }
        Item(
            R.drawable.shortcut_icon_control_normal_24dp,
            R.string.file_list_action_create_shortcut
        ) { controller.createCurrentDirectoryShortcut() }
    }
}

/** A bar at the bottom of the screen, above the navigation bar. */
@Composable
private fun FileListBottomBar(
    onClose: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                )
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.close_icon_white_24dp),
                    contentDescription = stringResource(R.string.close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                content()
            }
        }
    }
}

/** The files to paste into the current folder. */
@Composable
fun PasteBar(
    pasteState: PasteState,
    currentPath: Path,
    controller: FileListController,
    onClose: () -> Unit
) {
    val files = pasteState.files
    val areAllFilesArchivePaths = files.all { it.path.isArchivePath }
    FileListBottomBar(onClose = onClose) {
        Text(
            text = stringResource(
                if (pasteState.copy) {
                    if (areAllFilesArchivePaths) {
                        R.string.file_list_paste_extract_title_format
                    } else {
                        R.string.file_list_paste_copy_title_format
                    }
                } else {
                    R.string.file_list_paste_move_title_format
                },
                files.size
            ),
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(
            onClick = controller::pasteFiles,
            enabled = !currentPath.fileSystem.isReadOnly
        ) {
            Icon(
                painter = painterResource(R.drawable.paste_icon_control_normal_24dp),
                contentDescription = stringResource(
                    if (areAllFilesArchivePaths) {
                        R.string.file_list_paste_action_extract_here
                    } else {
                        R.string.paste
                    }
                )
            )
        }
    }
}

/** The name of the file to create, when another app asks where to create a file. */
@Composable
fun CreateFileBar(controller: FileListController) {
    FileListBottomBar(onClose = controller::cancelPicking) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val nameState = controller.createFileNameState
            if (nameState.text.isEmpty()) {
                Text(
                    text = stringResource(R.string.file_list_create_file_name_hint),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            // Left unfocused at first: the calling app nearly always passes the name it wants, so
            // taking focus would only bring up the keyboard over the list for nothing.
            val keyboard = rememberTextFieldKeyboard(KeyboardOptions(imeAction = ImeAction.Done))
            BasicTextField(
                state = nameState,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(keyboard.modifier),
                textStyle = MaterialTheme.typography.bodyLarge
                    .copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = keyboard.keyboardOptions,
                onKeyboardAction = { controller.createFileWithEnteredName() },
                lineLimits = TextFieldLineLimits.SingleLine,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
            )
        }
        ActionButton(R.drawable.save_icon_white_24dp, stringResource(R.string.save)) {
            controller.createFileWithEnteredName()
        }
    }
}

/** Picks the current folder, when another app asks for a folder. */
@Composable
fun OpenDirectoryBar(currentPath: Path, controller: FileListController) {
    val context = LocalContext.current
    val name = NavigationRootMap.value[currentPath]?.getName(context)
        ?: currentPath.name
    FileListBottomBar(onClose = controller::cancelPicking) {
        Text(
            text = stringResource(R.string.file_list_open_current_directory_format, name),
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium
        )
        ActionButton(R.drawable.check_icon_white_24dp, stringResource(R.string.select)) {
            controller.pickCurrentDirectory()
        }
    }
}

