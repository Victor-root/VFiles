/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R

private val MENU_SHAPE = RoundedCornerShape(16.dp)

/** A menu over its anchor, with rounded corners. */
@Composable
fun RoundedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = MENU_SHAPE,
        content = content
    )
}

/** An item of a menu, with its icon if any and an optional trailing icon. */
@Composable
fun MenuItem(
    @DrawableRes iconRes: Int?,
    title: String,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(title) },
        onClick = onClick,
        leadingIcon = iconRes?.let {
            { Icon(painter = painterResource(it), contentDescription = null) }
        },
        trailingIcon = trailingContent,
        enabled = enabled
    )
}

/** A line between the groups of items of a menu. */
@Composable
fun MenuDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
}

/** An overflow button in an app bar, opening a menu whose content gets a function closing it. */
@Composable
fun OverflowMenu(content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit) {
    var isMenuShown by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isMenuShown = true }) {
            Icon(
                painter = painterResource(R.drawable.more_vertical_icon_white_24dp),
                contentDescription = stringResource(R.string.more_options)
            )
        }
        RoundedDropdownMenu(expanded = isMenuShown, onDismissRequest = { isMenuShown = false }) {
            content { isMenuShown = false }
        }
    }
}
