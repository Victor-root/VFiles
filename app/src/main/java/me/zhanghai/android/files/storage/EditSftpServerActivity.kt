/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hierynomus.sshj.common.KeyDecryptionFailedException
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.ComposeActivity
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.provider.sftp.client.Authentication
import me.zhanghai.android.files.provider.sftp.client.Authority
import me.zhanghai.android.files.provider.sftp.client.PasswordAuthentication
import me.zhanghai.android.files.provider.sftp.client.PublicKeyAuthentication
import me.zhanghai.android.files.ui.FormDropdownField
import me.zhanghai.android.files.ui.FormErrors
import me.zhanghai.android.files.ui.FormPasswordField
import me.zhanghai.android.files.ui.FormTextField
import me.zhanghai.android.files.ui.rememberFormFieldState
import me.zhanghai.android.files.util.ActionState
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.isReady
import me.zhanghai.android.files.util.launchSafe
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.takeIfNotEmpty
import java.net.URI

class EditSftpServerActivity : ComposeActivity() {
    private val args by args<Args>()

    @Composable
    override fun Content() {
        EditSftpServerScreen(server = args.server, onFinish = ::finish)
    }

    @Parcelize
    class Args(val server: SftpServer? = null) : ParcelableArgs
}

private enum class SftpAuthenticationType {
    PASSWORD,
    PUBLIC_KEY
}

@Composable
private fun EditSftpServerScreen(server: SftpServer?, onFinish: () -> Unit) {
    val viewModel = viewModel { EditSftpServerViewModel() }
    val connectState by viewModel.connectState.collectAsStateWithLifecycle()
    val authority = server?.authority
    val authentication = server?.authentication
    val hostField = rememberFormFieldState(authority?.host.orEmpty())
    val portField = rememberFormFieldState(
        authority?.port?.takeIf { it != Authority.DEFAULT_PORT }?.toString().orEmpty()
    )
    val pathField = rememberFormFieldState(server?.relativePath.orEmpty())
    val nameField = rememberFormFieldState(server?.customName.orEmpty())
    var authenticationType by rememberSaveable {
        mutableStateOf(
            if (authentication is PublicKeyAuthentication) {
                SftpAuthenticationType.PUBLIC_KEY
            } else {
                SftpAuthenticationType.PASSWORD
            }
        )
    }
    val usernameField = rememberFormFieldState(authority?.username.orEmpty())
    val passwordField = rememberFormFieldState(
        (authentication as? PasswordAuthentication)?.password.orEmpty()
    )
    val privateKeyField = rememberFormFieldState(
        (authentication as? PublicKeyAuthentication)?.privateKey.orEmpty()
    )
    val privateKeyPasswordField = rememberFormFieldState(
        (authentication as? PublicKeyAuthentication)?.privateKeyPassword.orEmpty()
    )

    val context = LocalContext.current
    val readPrivateKeyFileState by viewModel.readPrivateKeyFileState
        .collectAsStateWithLifecycle()
    LaunchedEffect(readPrivateKeyFileState) {
        when (val state = readPrivateKeyFileState) {
            is ActionState.Ready -> {}
            is ActionState.Running -> privateKeyField.textFieldState.clearText()
            is ActionState.Success -> {
                privateKeyField.textFieldState.setTextAndPlaceCursorAtEnd(state.result)
                viewModel.finishReadingPrivateKeyFile()
            }
            is ActionState.Error -> {
                val throwable = state.throwable
                throwable.printStackTrace()
                context.showToast(throwable.toString())
                viewModel.finishReadingPrivateKeyFile()
            }
        }
    }
    val openPrivateKeyFileLauncher =
        rememberLauncherForActivityResult(FileListActivity.OpenFileContract()) {
            if (it != null) {
                viewModel.readPrivateKeyFile(it)
            }
        }

    val placeholderHost = hostField.text.takeIfNotEmpty()
    val namePlaceholder = if (placeholderHost != null) {
        val port = portField.text.takeIfNotEmpty()?.toIntOrNull() ?: Authority.DEFAULT_PORT
        val path = pathField.text.trim()
        val authority = Authority(placeholderHost, port, usernameField.text)
        if (path.isNotEmpty()) "$authority/$path" else authority.toString()
    } else {
        stringResource(R.string.storage_edit_sftp_server_name_placeholder)
    }

    fun getServerOrSetErrors(): SftpServer? {
        val errors = FormErrors()
        val host = hostField.text.takeIfNotEmpty()?.let { URI::class.canonicalizeHost(it) }
        if (host == null) {
            errors.set(hostField, R.string.storage_edit_sftp_server_host_error_empty)
        } else if (!URI::class.isValidHost(host)) {
            errors.set(hostField, R.string.storage_edit_sftp_server_host_error_invalid)
        }
        val port = portField.text.takeIfNotEmpty()
            .let { if (it != null) it.toIntOrNull() else Authority.DEFAULT_PORT }
        if (port == null) {
            errors.set(portField, R.string.storage_edit_sftp_server_port_error_invalid)
        }
        val path = pathField.text.trim()
        val name = nameField.text.takeIfNotEmpty()
        val username = usernameField.text.takeIfNotEmpty()
        if (username == null) {
            errors.set(usernameField, R.string.storage_edit_sftp_server_username_error_empty)
        }
        val newAuthentication: Authentication? = when (authenticationType) {
            SftpAuthenticationType.PASSWORD -> PasswordAuthentication(passwordField.text)
            SftpAuthenticationType.PUBLIC_KEY -> {
                val privateKey = privateKeyField.text.takeIfNotEmpty()
                val privateKeyPassword = privateKeyPasswordField.text.takeIfNotEmpty()
                if (privateKey == null) {
                    errors.set(
                        privateKeyField, R.string.storage_edit_sftp_server_private_key_error_empty
                    )
                    null
                } else {
                    val exception =
                        PublicKeyAuthentication.validate(privateKey, privateKeyPassword)
                    if (exception != null) {
                        exception.printStackTrace()
                        if (exception is KeyDecryptionFailedException) {
                            errors.set(
                                privateKeyPasswordField,
                                R.string.storage_edit_sftp_server_private_key_password_error_invalid
                            )
                        } else {
                            errors.set(
                                privateKeyField,
                                R.string.storage_edit_sftp_server_private_key_error_invalid
                            )
                        }
                    }
                    PublicKeyAuthentication(privateKey, privateKeyPassword)
                }
            }
        }
        if (!errors.check()) {
            return null
        }
        val authority = Authority(host!!, port!!, username!!)
        return SftpServer(server?.id, name, authority, newAuthentication!!, path)
    }

    EditServerScreen(
        title = stringResource(
            if (server != null) {
                R.string.storage_edit_sftp_server_title_edit
            } else {
                R.string.storage_edit_sftp_server_title_add
            }
        ),
        server = server,
        addText = stringResource(R.string.storage_edit_sftp_server_add),
        connectAndAddText = stringResource(R.string.storage_edit_sftp_server_connect_and_add),
        connectState = connectState,
        onConnect = viewModel::connect,
        onFinishConnecting = viewModel::finishConnecting,
        getServerOrSetErrors = ::getServerOrSetErrors,
        onFinish = onFinish,
        onCancel = onFinish
    ) {
        FormTextField(
            field = hostField,
            label = stringResource(R.string.storage_edit_sftp_server_host),
            keyboardType = KeyboardType.Uri
        )
        FormTextField(
            field = portField,
            label = stringResource(R.string.storage_edit_sftp_server_port),
            placeholder = stringResource(R.string.storage_edit_sftp_server_port_placeholder),
            keyboardType = KeyboardType.Number
        )
        FormTextField(
            field = pathField,
            label = stringResource(R.string.storage_edit_sftp_server_path),
            placeholder = stringResource(R.string.storage_edit_sftp_server_path_placeholder),
            keyboardType = KeyboardType.Uri
        )
        FormTextField(
            field = nameField,
            label = stringResource(R.string.storage_edit_sftp_server_name),
            placeholder = namePlaceholder
        )
        val authenticationTypeLabels =
            stringArrayResource(R.array.storage_edit_sftp_server_authentication_type_entries)
        FormDropdownField(
            label = stringResource(R.string.storage_edit_sftp_server_authentication_type),
            entries = SftpAuthenticationType.entries,
            entryLabel = { authenticationTypeLabels[it.ordinal] },
            value = authenticationType,
            onValueChange = { authenticationType = it }
        )
        FormTextField(
            field = usernameField,
            label = stringResource(R.string.storage_edit_sftp_server_username)
        )
        AnimatedVisibility(visible = authenticationType == SftpAuthenticationType.PASSWORD) {
            FormPasswordField(
                field = passwordField,
                label = stringResource(R.string.storage_edit_sftp_server_password)
            )
        }
        AnimatedVisibility(visible = authenticationType == SftpAuthenticationType.PUBLIC_KEY) {
            // Editing the private key or its password may fix the error of the other one.
            val clearPrivateKeyErrors = {
                privateKeyField.errorRes = null
                privateKeyPasswordField.errorRes = null
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField(
                    field = privateKeyField,
                    label = stringResource(R.string.storage_edit_sftp_server_private_key),
                    placeholder = if (readPrivateKeyFileState is ActionState.Running) {
                        stringResource(R.string.loading)
                    } else {
                        null
                    },
                    isMultiLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (readPrivateKeyFileState.isReady) {
                                    openPrivateKeyFileLauncher.launchSafe(
                                        listOf(MimeType.ANY), context
                                    )
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.file_icon_white_24dp),
                                contentDescription = stringResource(
                                    R.string.storage_edit_sftp_server_private_key_open_file
                                )
                            )
                        }
                    },
                    onTextChange = clearPrivateKeyErrors
                )
                FormPasswordField(
                    field = privateKeyPasswordField,
                    label = stringResource(R.string.storage_edit_sftp_server_private_key_password),
                    placeholder = stringResource(
                        R.string.storage_edit_sftp_server_private_key_password_placeholder
                    ),
                    onTextChange = clearPrivateKeyErrors
                )
            }
        }
    }
}
