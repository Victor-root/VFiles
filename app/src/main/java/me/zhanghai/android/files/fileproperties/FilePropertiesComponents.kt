/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import me.zhanghai.android.files.R
import me.zhanghai.android.files.ui.ReadOnlyFormTextField
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.showToast

/**
 * Gives [content] its own view models, cleared when it leaves the composition, like those of a
 * dialog fragment.
 */
@Composable
fun ViewModelStoreScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides rememberViewModelStoreOwner(),
        content = content
    )
}

private enum class TabState {
    LOADING,
    ERROR,
    CONTENT
}

/**
 * The content of a tab, shown once there's a value, which can then be refreshed by pulling. An
 * error is shown in place of the content, or as a toast once there's a value.
 */
@Composable
fun <T : Any> FilePropertiesTab(
    stateful: Stateful<T>,
    onRefresh: () -> Unit,
    content: @Composable ColumnScope.(T) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(stateful) {
        if (stateful is Failure) {
            stateful.throwable.printStackTrace()
            if (stateful.value != null) {
                context.showToast(stateful.throwable.toString())
            }
        }
    }
    AnimatedContent(
        targetState = stateful,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = {
            when {
                it.value != null -> TabState.CONTENT
                it is Failure -> TabState.ERROR
                else -> TabState.LOADING
            }
        },
        label = "FilePropertiesTab"
    ) { targetStateful ->
        val value = targetStateful.value
        if (value != null) {
            PullToRefreshBox(isRefreshing = targetStateful is Loading, onRefresh = onRefresh) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) { content(value) }
            }
        } else {
            StateBox(Modifier.padding(horizontal = 24.dp)) {
                if (targetStateful is Failure) {
                    ErrorText(targetStateful.throwable)
                } else {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/** A list loaded in the background, with its loading, error and empty states. */
@Composable
fun <T : Any> StatefulList(
    stateful: Stateful<List<T>>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    item: @Composable (T) -> Unit
) {
    when (stateful) {
        is Loading -> StateBox(modifier) { CircularProgressIndicator() }
        is Failure -> StateBox(modifier) { ErrorText(stateful.throwable) }
        is Success ->
            if (stateful.value.isEmpty()) {
                StateBox(modifier) {
                    Text(
                        text = stringResource(R.string.empty),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(modifier = modifier, state = state) {
                    items(stateful.value, key = key) { item(it) }
                }
            }
    }
}

@Composable
private fun StateBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun ErrorText(throwable: Throwable) {
    Text(
        text = throwable.toString(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge
    )
}

/**
 * A property, whose text can be focused and selected. With [onClick], it's shown like the anchor
 * of a dropdown, which opens a dialog instead of a menu.
 */
@Composable
fun FilePropertiesItem(@StringRes labelRes: Int, text: String, onClick: (() -> Unit)? = null) {
    val label = stringResource(labelRes)
    if (onClick == null) {
        ReadOnlyFormTextField(text, label)
        return
    }
    ExposedDropdownMenuBox(expanded = false, onExpandedChange = { onClick() }) {
        OutlinedTextField(
            state = remember(text) { TextFieldState(text) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) }
        )
    }
}

/** A checkbox with its text, toggled by clicking anywhere on the row. */
@Composable
fun CheckboxItem(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA)
            },
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private const val DISABLED_CONTENT_ALPHA = 0.38f
