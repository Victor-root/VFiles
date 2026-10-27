/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.ui.ActionBottomSheet
import me.zhanghai.android.files.ui.SheetItem
import me.zhanghai.android.files.util.PerfLog

private const val UNSELECTED_ALPHA = 0.7f

/**
 * The folders of the trail, with the current one highlighted. On TV it's a read-only path display
 * that stays out of the D-pad focus path.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Breadcrumb(
    data: BreadcrumbData,
    isReadOnly: Boolean,
    onNavigateTo: (Path) -> Unit,
    onCopyPath: (Path) -> Unit,
    onOpenInNewTask: (Path) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    PerfLog.Recomposed("Breadcrumb")
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var hasScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(data.paths, data.selectedIndex) {
        // Jump there the first time, scroll smoothly afterwards.
        if (hasScrolled) {
            listState.animateScrollToItem(data.selectedIndex)
        } else {
            listState.scrollToItem(data.selectedIndex)
            hasScrolled = true
        }
    }
    LazyRow(
        modifier = modifier
            .height(48.dp)
            .then(if (isReadOnly) Modifier.focusProperties { canFocus = false } else Modifier),
        state = listState,
        contentPadding = contentPadding,
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(data.paths, key = { _, path -> path }) { index, path ->
            val isSelected = index == data.selectedIndex
            var isMenuShown by remember { mutableStateOf(false) }
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .combinedClickable(
                            enabled = !isReadOnly,
                            onClick = {
                                if (isSelected) {
                                    // Already there, bring it back into view.
                                    hasScrolled = false
                                } else {
                                    onNavigateTo(path)
                                }
                            },
                            onLongClick = { isMenuShown = true }
                        )
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val color = LocalContentColor.current.let {
                        if (isSelected) it else it.copy(alpha = UNSELECTED_ALPHA)
                    }
                    Text(
                        text = data.nameProducers[index](context),
                        modifier = Modifier.padding(end = 8.dp),
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (index != data.paths.lastIndex) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_end_icon_white_24dp),
                            contentDescription = null,
                            modifier = Modifier.offset(x = (-4).dp),
                            tint = color
                        )
                    }
                }
                if (isMenuShown) {
                    ActionBottomSheet(
                        onDismissRequest = { isMenuShown = false },
                        title = data.nameProducers[index](context)
                    ) { dismiss ->
                        SheetItem(
                            R.drawable.copy_path_icon_control_normal_24dp,
                            stringResource(R.string.file_list_breadcrumb_action_copy_path)
                        ) {
                            dismiss()
                            onCopyPath(path)
                        }
                        SheetItem(
                            R.drawable.new_window_icon_control_normal_24dp,
                            stringResource(R.string.file_list_breadcrumb_action_open_in_new_task)
                        ) {
                            dismiss()
                            onOpenInNewTask(path)
                        }
                    }
                }
            }
        }
    }
}
