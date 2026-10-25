/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

private const val DISABLED_ALPHA = 0.38f

@Composable
fun PreferenceCategory(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall
    )
}

@Composable
fun Preference(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    summaryMaxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    PreferenceLayout(
        title = title,
        summary = summary,
        summaryMaxLines = summaryMaxLines,
        enabled = enabled,
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
            else Modifier
        ),
        trailingContent = trailingContent
    )
}

@Composable
fun SwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true
) {
    PreferenceLayout(
        title = title,
        summary = summary,
        enabled = enabled,
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange
        ),
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }
    )
}

/** A choice among a few entries, shown as a menu over the preference. */
@Composable
fun <T> DropdownListPreference(
    title: String,
    entries: List<T>,
    entryLabel: @Composable (T) -> String,
    value: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var isMenuShown by rememberSaveable { mutableStateOf(false) }
    DropdownListPreference(
        title = title,
        entries = entries,
        entryLabel = entryLabel,
        value = value,
        onValueChange = onValueChange,
        isMenuShown = isMenuShown,
        onMenuShownChange = { isMenuShown = it },
        modifier = modifier,
        enabled = enabled
    )
}

@Composable
fun <T> DropdownListPreference(
    title: String,
    entries: List<T>,
    entryLabel: @Composable (T) -> String,
    value: T,
    onValueChange: (T) -> Unit,
    isMenuShown: Boolean,
    onMenuShownChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(modifier = modifier) {
        Preference(
            title = title,
            summary = entryLabel(value),
            enabled = enabled,
            onClick = { onMenuShownChange(true) }
        )
        DropdownMenu(
            expanded = isMenuShown,
            onDismissRequest = { onMenuShownChange(false) },
            offset = DpOffset(16.dp, 0.dp)
        ) {
            for (entry in entries) {
                val isSelected = entry == value
                DropdownMenuItem(
                    text = {
                        Text(
                            text = entryLabel(entry),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            }
                        )
                    },
                    onClick = {
                        onMenuShownChange(false)
                        if (!isSelected) {
                            onValueChange(entry)
                        }
                    }
                )
            }
        }
    }
}

/** A choice among many entries, shown in a dialog. */
@Composable
fun <T> DialogListPreference(
    title: String,
    entries: List<T>,
    entryLabel: (T) -> String,
    value: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var isDialogShown by rememberSaveable { mutableStateOf(false) }
    Preference(
        title = title,
        modifier = modifier,
        summary = entryLabel(value),
        enabled = enabled,
        onClick = { isDialogShown = true }
    )
    if (isDialogShown) {
        SingleChoiceDialog(
            title = title,
            entries = entries,
            entryLabel = entryLabel,
            selectedEntry = value,
            onEntrySelected = {
                isDialogShown = false
                onValueChange(it)
            },
            onDismissRequest = { isDialogShown = false }
        )
    }
}

@Composable
fun <T> SingleChoiceDialog(
    title: String,
    entries: List<T>,
    entryLabel: (T) -> String,
    selectedEntry: T,
    onEntrySelected: (T) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = { Text(title) },
        text = {
            val selectedIndex = entries.indexOf(selectedEntry)
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0)
            )
            LazyColumn(state = listState) {
                itemsIndexed(entries) { index, entry ->
                    val isSelected = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onEntrySelected(entry) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = entryLabel(entry), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    )
}

@Composable
private fun PreferenceLayout(
    title: String,
    summary: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    summaryMaxLines: Int = Int.MAX_VALUE,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Text(
                        text = summary,
                        maxLines = summaryMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(16.dp))
            trailingContent()
        }
    }
}
