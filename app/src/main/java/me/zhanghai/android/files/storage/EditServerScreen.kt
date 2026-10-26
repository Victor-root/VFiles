/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.TextButton
import me.zhanghai.android.files.util.ActionState
import me.zhanghai.android.files.util.isReady
import me.zhanghai.android.files.util.showToast

/**
 * The screen shared by the server editors. A new server can be added directly or after connecting
 * to it successfully, and an existing server can be saved or removed. [getServerOrSetErrors]
 * returns the server described by the form, or null after showing the errors in the form.
 */
@Composable
fun <S : Storage> EditServerScreen(
    title: String,
    server: S?,
    addText: String,
    connectAndAddText: String,
    connectState: ActionState<S, Unit>,
    onConnect: (S) -> Unit,
    onFinishConnecting: () -> Unit,
    getServerOrSetErrors: () -> S?,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(connectState) {
        when (connectState) {
            is ActionState.Success -> {
                Storages.addOrReplace(connectState.argument)
                onFinish()
            }
            is ActionState.Error -> {
                val throwable = connectState.throwable
                throwable.printStackTrace()
                context.showToast(throwable.toString())
                onFinishConnecting()
            }
            is ActionState.Ready, is ActionState.Running -> {}
        }
    }
    val saveOrAdd = {
        val newServer = getServerOrSetErrors()
        if (newServer != null) {
            Storages.addOrReplace(newServer)
            onFinish()
        }
    }
    val isConnecting = connectState is ActionState.Running
    AppScaffold(title = title) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .consumeWindowInsets(contentPadding)
                .imePadding()
        ) {
            ServerForm(
                isConnecting = isConnecting,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                content = content
            )
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                if (server != null) {
                    TextButton(
                        onClick = {
                            Storages.remove(server)
                            onFinish()
                        },
                        enabled = !isConnecting
                    ) {
                        Text(stringResource(R.string.remove))
                    }
                } else {
                    TextButton(onClick = saveOrAdd, enabled = !isConnecting) { Text(addText) }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
                if (server != null) {
                    TextButton(onClick = saveOrAdd, enabled = !isConnecting) {
                        Text(stringResource(R.string.save))
                    }
                } else {
                    TextButton(
                        onClick = {
                            if (connectState.isReady) {
                                getServerOrSetErrors()?.let(onConnect)
                            }
                        },
                        enabled = !isConnecting
                    ) {
                        Text(connectAndAddText)
                    }
                }
            }
        }
    }
}

/** The fields of the form, replaced by a progress while connecting. */
@Composable
private fun ServerForm(
    isConnecting: Boolean,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier) {
        val scrollState = rememberScrollState()
        AnimatedVisibility(visible = !isConnecting, enter = fadeIn(), exit = fadeOut()) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
        AnimatedVisibility(
            visible = isConnecting,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CircularProgressIndicator()
        }
    }
}
