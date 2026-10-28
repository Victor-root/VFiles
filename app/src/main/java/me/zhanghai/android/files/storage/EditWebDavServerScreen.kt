/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.Destination
import me.zhanghai.android.files.app.LocalNavigator
import me.zhanghai.android.files.provider.webdav.client.AccessTokenAuthentication
import me.zhanghai.android.files.provider.webdav.client.Authority
import me.zhanghai.android.files.provider.webdav.client.NoneAuthentication
import me.zhanghai.android.files.provider.webdav.client.PasswordAuthentication
import me.zhanghai.android.files.provider.webdav.client.Protocol
import me.zhanghai.android.files.ui.FormDropdownField
import me.zhanghai.android.files.ui.FormErrors
import me.zhanghai.android.files.ui.FormPasswordField
import me.zhanghai.android.files.ui.FormTextField
import me.zhanghai.android.files.ui.rememberFormFieldState
import me.zhanghai.android.files.util.takeIfNotEmpty
import java.net.URI

@Parcelize
data class EditWebDavServerDestination(
    val server: WebDavServer? = null,
    val host: String? = null
) : Destination {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        EditWebDavServerScreen(
            server = server,
            host = host,
            onFinish = navigator::goBack,
            onCancel = navigator::goBack
        )
    }
}

private enum class WebDavAuthenticationType {
    PASSWORD,
    ACCESS_TOKEN,
    NONE
}

@Composable
private fun EditWebDavServerScreen(
    server: WebDavServer?,
    host: String?,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val viewModel = viewModel { EditWebDavServerViewModel() }
    val connectState by viewModel.connectState.collectAsStateWithLifecycle()
    val authority = server?.authority
    val authentication = server?.authentication
    val hostField = rememberFormFieldState(authority?.host ?: host.orEmpty())
    val portField = rememberFormFieldState(
        authority?.let { it.port.takeIf { port -> port != it.protocol.defaultPort } }?.toString()
            .orEmpty()
    )
    val pathField = rememberFormFieldState(server?.relativePath.orEmpty())
    val nameField = rememberFormFieldState(server?.customName.orEmpty())
    var protocol by rememberSaveable { mutableStateOf(authority?.protocol ?: Protocol.DAVS) }
    var authenticationType by rememberSaveable {
        mutableStateOf(
            when (authentication) {
                null, is PasswordAuthentication -> WebDavAuthenticationType.PASSWORD
                is AccessTokenAuthentication -> WebDavAuthenticationType.ACCESS_TOKEN
                is NoneAuthentication -> WebDavAuthenticationType.NONE
            }
        )
    }
    val usernameField = rememberFormFieldState(
        authority?.username?.takeIf { authentication is PasswordAuthentication }.orEmpty()
    )
    val passwordField = rememberFormFieldState(
        (authentication as? PasswordAuthentication)?.password.orEmpty()
    )
    val accessTokenField = rememberFormFieldState(
        (authentication as? AccessTokenAuthentication)?.accessToken.orEmpty()
    )

    val placeholderHost = hostField.text.takeIfNotEmpty()
    val namePlaceholder = if (placeholderHost != null) {
        val port = portField.text.takeIfNotEmpty()?.toIntOrNull() ?: protocol.defaultPort
        val path = pathField.text.trim()
        val username = if (authenticationType == WebDavAuthenticationType.PASSWORD) {
            usernameField.text
        } else {
            ""
        }
        val authority = Authority(protocol, placeholderHost, port, username)
        if (path.isNotEmpty()) "$authority/$path" else authority.toString()
    } else {
        stringResource(R.string.storage_edit_webdav_server_name_placeholder)
    }

    fun getServerOrSetErrors(): WebDavServer? {
        val errors = FormErrors()
        val host = hostField.text.takeIfNotEmpty()?.let { URI::class.canonicalizeHost(it) }
        if (host == null) {
            errors.set(hostField, R.string.storage_edit_webdav_server_host_error_empty)
        } else if (!URI::class.isValidHost(host)) {
            errors.set(hostField, R.string.storage_edit_webdav_server_host_error_invalid)
        }
        val port = portField.text.takeIfNotEmpty()
            .let { if (it != null) it.toIntOrNull() else protocol.defaultPort }
        if (port == null) {
            errors.set(portField, R.string.storage_edit_webdav_server_port_error_invalid)
        }
        val path = pathField.text.trim()
        val name = nameField.text.takeIfNotEmpty()
        val (username, newAuthentication) = when (authenticationType) {
            WebDavAuthenticationType.PASSWORD -> {
                val username = usernameField.text.takeIfNotEmpty()
                if (username == null) {
                    errors.set(
                        usernameField, R.string.storage_edit_webdav_server_username_error_empty
                    )
                }
                username to PasswordAuthentication(passwordField.text)
            }
            WebDavAuthenticationType.ACCESS_TOKEN -> {
                val accessToken = accessTokenField.text.takeIfNotEmpty()
                if (accessToken == null) {
                    errors.set(
                        accessTokenField,
                        R.string.storage_edit_webdav_server_access_token_error_empty
                    )
                }
                "" to accessToken?.let { AccessTokenAuthentication(it) }
            }
            WebDavAuthenticationType.NONE -> "" to NoneAuthentication
        }
        if (!errors.check()) {
            return null
        }
        val authority = Authority(protocol, host!!, port!!, username!!)
        return WebDavServer(server?.id, name, authority, newAuthentication!!, path)
    }

    EditServerScreen(
        title = stringResource(
            if (server != null) {
                R.string.storage_edit_webdav_server_title_edit
            } else {
                R.string.storage_edit_webdav_server_title_add
            }
        ),
        server = server,
        addText = stringResource(R.string.storage_edit_webdav_server_add),
        connectAndAddText = stringResource(R.string.storage_edit_webdav_server_connect_and_add),
        connectState = connectState,
        onConnect = viewModel::connect,
        onFinishConnecting = viewModel::finishConnecting,
        getServerOrSetErrors = ::getServerOrSetErrors,
        onFinish = onFinish,
        onCancel = onCancel
    ) {
        FormTextField(
            field = hostField,
            label = stringResource(R.string.storage_edit_webdav_server_host),
            keyboardType = KeyboardType.Uri
        )
        FormTextField(
            field = portField,
            label = stringResource(R.string.storage_edit_webdav_server_port),
            placeholder = protocol.defaultPort.toString(),
            keyboardType = KeyboardType.Number
        )
        FormTextField(
            field = pathField,
            label = stringResource(R.string.storage_edit_webdav_server_path),
            placeholder = stringResource(R.string.storage_edit_webdav_server_path_placeholder),
            keyboardType = KeyboardType.Uri
        )
        FormTextField(
            field = nameField,
            label = stringResource(R.string.storage_edit_webdav_server_name),
            placeholder = namePlaceholder
        )
        val protocolLabels =
            stringArrayResource(R.array.storage_edit_webdav_server_protocol_entries)
        FormDropdownField(
            label = stringResource(R.string.storage_edit_webdav_server_protocol),
            entries = Protocol.entries,
            entryLabel = { protocolLabels[it.ordinal] },
            value = protocol,
            onValueChange = { protocol = it }
        )
        val authenticationTypeLabels =
            stringArrayResource(R.array.storage_edit_webdav_server_authentication_type_entries)
        FormDropdownField(
            label = stringResource(R.string.storage_edit_webdav_server_authentication_type),
            entries = WebDavAuthenticationType.entries,
            entryLabel = { authenticationTypeLabels[it.ordinal] },
            value = authenticationType,
            onValueChange = { authenticationType = it }
        )
        AnimatedVisibility(visible = authenticationType == WebDavAuthenticationType.PASSWORD) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField(
                    field = usernameField,
                    label = stringResource(R.string.storage_edit_webdav_server_username)
                )
                FormPasswordField(
                    field = passwordField,
                    label = stringResource(R.string.storage_edit_webdav_server_password)
                )
            }
        }
        AnimatedVisibility(
            visible = authenticationType == WebDavAuthenticationType.ACCESS_TOKEN
        ) {
            FormTextField(
                field = accessTokenField,
                label = stringResource(R.string.storage_edit_webdav_server_access_token)
            )
        }
    }
}
