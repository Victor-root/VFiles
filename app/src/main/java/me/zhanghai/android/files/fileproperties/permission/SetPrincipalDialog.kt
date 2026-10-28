/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.permission

import android.content.pm.ApplicationInfo
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.fileproperties.StatefulList
import me.zhanghai.android.files.fileproperties.ViewModelStoreScope
import me.zhanghai.android.files.provider.common.PosixFileAttributes
import me.zhanghai.android.files.provider.common.PosixGroup
import me.zhanghai.android.files.provider.common.PosixUser
import me.zhanghai.android.files.provider.common.toByteString
import me.zhanghai.android.files.ui.CheckboxItem
import me.zhanghai.android.files.ui.rememberTextFieldKeyboard
import me.zhanghai.android.files.util.Success

@Composable
fun SetOwnerDialog(file: FileItem, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val ownerId = (file.attributes as PosixFileAttributes).owner()!!.id
    SetPrincipalDialog(
        file = file,
        titleRes = R.string.file_properties_permission_set_owner_title,
        principalIconRes = R.drawable.person_icon_control_normal_24dp,
        principalId = ownerId,
        createViewModel = { SetOwnerViewModel(ownerId) },
        setPrincipal = { path, principal, recursive ->
            val owner = PosixUser(principal.id, principal.name?.toByteString())
            FileJobService.setOwner(path, owner, recursive, context)
        },
        onDismissRequest = onDismissRequest
    )
}

@Composable
fun SetGroupDialog(file: FileItem, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val groupId = (file.attributes as PosixFileAttributes).group()!!.id
    SetPrincipalDialog(
        file = file,
        titleRes = R.string.file_properties_permission_set_group_title,
        principalIconRes = R.drawable.people_icon_control_normal_24dp,
        principalId = groupId,
        createViewModel = { SetGroupViewModel(groupId) },
        setPrincipal = { path, principal, recursive ->
            val group = PosixGroup(principal.id, principal.name?.toByteString())
            FileJobService.setGroup(path, group, recursive, context)
        },
        onDismissRequest = onDismissRequest
    )
}

/**
 * Picks the principal with [principalId] of [file] among those listed by the view model, which is
 * scrolled to once the list is loaded.
 */
@Composable
fun SetPrincipalDialog(
    file: FileItem,
    @StringRes titleRes: Int,
    @DrawableRes principalIconRes: Int,
    principalId: Int,
    createViewModel: () -> SetPrincipalViewModel,
    setPrincipal: (path: Path, principal: PrincipalItem, recursive: Boolean) -> Unit,
    onDismissRequest: () -> Unit
) {
    ViewModelStoreScope {
        val viewModel = viewModel { createViewModel() }
        val principalListStateful by viewModel.filteredPrincipalListFlow
            .collectAsStateWithLifecycle()
        val selectedId by viewModel.selectionFlow.collectAsStateWithLifecycle()
        val filterState = rememberTextFieldState()
        LaunchedEffect(filterState) {
            snapshotFlow { filterState.text.toString() }.collect { viewModel.filter = it }
        }
        var isRecursive by rememberSaveable { mutableStateOf(false) }
        val listState = rememberLazyListState()
        var hasScrolledToSelection by rememberSaveable { mutableStateOf(false) }
        val selectedPrincipalFocusRequester = remember { FocusRequester() }
        LaunchedEffect(principalListStateful) {
            val principals = (principalListStateful as? Success)?.value
            if (principals == null || hasScrolledToSelection) {
                return@LaunchedEffect
            }
            val index = principals.indexOfFirst { it.id == selectedId }
            if (index != -1) {
                listState.scrollToItem(index)
                // Lets the item be composed, so that it's focused first on a D-pad.
                withFrameNanos {}
                selectedPrincipalFocusRequester.requestFocus()
            }
            hasScrolledToSelection = true
        }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismissRequest()
                        if (!isRecursive && selectedId == principalId) {
                            return@TextButton
                        }
                        val principals = (viewModel.principalListStateful as? Success)?.value
                        val principal = principals?.find { it.id == selectedId }
                            ?: return@TextButton
                        setPrincipal(file.path, principal, isRecursive)
                    }
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            title = { Text(stringResource(titleRes)) },
            text = {
                Column {
                    val keyboard = rememberTextFieldKeyboard()
                    OutlinedTextField(
                        state = filterState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(keyboard.modifier),
                        placeholder = {
                            Text(
                                stringResource(
                                    R.string.file_properties_permission_set_principal_filter_hint
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(
                                    R.drawable.filter_icon_control_normal_24dp
                                ),
                                contentDescription = null
                            )
                        },
                        keyboardOptions = keyboard.keyboardOptions,
                        lineLimits = TextFieldLineLimits.SingleLine
                    )
                    StatefulList(
                        stateful = principalListStateful,
                        key = { it.id },
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(top = 8.dp),
                        state = listState
                    ) { principal ->
                        val isSelected = principal.id == selectedId
                        PrincipalListItem(
                            principal = principal,
                            principalIconRes = principalIconRes,
                            isSelected = isSelected,
                            onClick = { viewModel.select(principal.id) },
                            modifier = if (isSelected) {
                                Modifier.focusRequester(selectedPrincipalFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                    }
                    if (file.attributes.isDirectory) {
                        CheckboxItem(
                            text = stringResource(R.string.file_properties_permission_recursive),
                            checked = isRecursive,
                            onCheckedChange = { isRecursive = it }
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun PrincipalListItem(
    principal: PrincipalItem,
    @DrawableRes principalIconRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PrincipalIcon(principal.applicationInfos.firstOrNull(), principalIconRes)
        Spacer(modifier = Modifier.width(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = getPrincipalText(principal.id, principal.name),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = principal.applicationLabels.firstOrNull()
                    ?: stringResource(R.string.file_properties_permission_set_principal_system),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        RadioButton(selected = isSelected, onClick = null)
    }
}

/** The icon of the app with [applicationInfo] once loaded, otherwise [iconRes]. */
@Composable
private fun PrincipalIcon(applicationInfo: ApplicationInfo?, @DrawableRes iconRes: Int) {
    Box(modifier = Modifier.size(24.dp)) {
        var isAppIconShown by remember(applicationInfo) { mutableStateOf(false) }
        if (!isAppIconShown) {
            Icon(painter = painterResource(iconRes), contentDescription = null)
        }
        if (applicationInfo != null) {
            AsyncImage(
                model = applicationInfo,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { isAppIconShown = true }
            )
        }
    }
}
