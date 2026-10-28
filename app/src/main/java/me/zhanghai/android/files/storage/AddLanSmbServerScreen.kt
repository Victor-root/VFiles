/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.Destination
import me.zhanghai.android.files.app.LocalNavigator
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful

@Parcelize
data object AddLanSmbServerDestination : Destination {
    @Composable
    override fun Content() {
        val viewModel = viewModel { AddLanSmbServerViewModel() }
        val lanSmbServerList by viewModel.lanSmbServerListFlow.collectAsStateWithLifecycle()
        LaunchedEffect(lanSmbServerList) {
            (lanSmbServerList as? Failure)?.throwable?.printStackTrace()
        }
        val navigator = LocalNavigator.current
        AddLanSmbServerScreen(
            lanSmbServerList = lanSmbServerList,
            onRefresh = viewModel::reload,
            onAddServer = {
                navigator.navigate(
                    EditSmbServerDestination(host = it?.host, closesPreviousScreen = true)
                )
            }
        )
    }
}

/** Lists the SMB servers found on the network, [onAddServer] with null adds one manually. */
@Composable
internal fun AddLanSmbServerScreen(
    lanSmbServerList: Stateful<List<LanSmbServer>>,
    onRefresh: () -> Unit,
    onAddServer: (LanSmbServer?) -> Unit
) {
    val isLoading = lanSmbServerList is Loading
    val servers = lanSmbServerList.value ?: emptyList()
    val title = stringResource(R.string.storage_add_lan_smb_server_title)
    AppScaffold(title = title) { contentPadding ->
        val pullToRefreshState = rememberPullToRefreshState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullToRefresh(
                    isRefreshing = false,
                    state = pullToRefreshState,
                    enabled = !isLoading,
                    onRefresh = onRefresh
                )
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
                if (isLoading && servers.isEmpty()) {
                    item(key = "loading") {
                        Text(
                            text = stringResource(R.string.storage_add_lan_smb_server_loading),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(start = 72.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                items(servers, key = { "${it.host}/${it.address.hostAddress}" }) { server ->
                    LanSmbServerItem(
                        iconRes = R.drawable.computer_icon_white_24dp,
                        title = server.host,
                        subtitle = server.address.hostAddress,
                        onClick = { onAddServer(server) }
                    )
                }
                item(key = "add") {
                    LanSmbServerItem(
                        iconRes = R.drawable.add_icon_white_24dp,
                        title = stringResource(R.string.storage_add_lan_smb_server_add),
                        onClick = { onAddServer(null) }
                    )
                }
            }
            val topPadding = PaddingValues(top = contentPadding.calculateTopPadding())
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .padding(topPadding)
                        .fillMaxWidth()
                )
            }
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = false,
                modifier = Modifier
                    .padding(topPadding)
                    .align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun LanSmbServerItem(
    @DrawableRes iconRes: Int,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null
) {
    ListItem(
        headlineContent = { Text(text = title, overflow = TextOverflow.Ellipsis, maxLines = 1) },
        modifier = Modifier.clickable(onClick = onClick),
        supportingContent = subtitle?.let {
            { Text(text = it, overflow = TextOverflow.Ellipsis, maxLines = 1) }
        },
        leadingContent = { Icon(painter = painterResource(iconRes), contentDescription = null) }
    )
}
