/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hierynomus.smbj.auth.AuthenticationContext
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.provider.smb.client.Authority
import me.zhanghai.android.files.ui.FormDropdownField
import me.zhanghai.android.files.ui.FormErrors
import me.zhanghai.android.files.ui.FormPasswordField
import me.zhanghai.android.files.ui.FormTextField
import me.zhanghai.android.files.ui.rememberFormFieldState
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.takeIfNotEmpty
import java.net.URI

class EditSmbServerActivity : AppActivity() {
    private val args by args<Args>()

    @Composable
    override fun Content() {
        EditSmbServerScreen(
            server = args.server,
            host = args.host,
            onFinish = {
                setResult(RESULT_OK)
                finish()
            },
            onCancel = ::finish
        )
    }

    @Parcelize
    class Args(
        val server: SmbServer? = null,
        val host: String? = null
    ) : ParcelableArgs

    class Contract : ActivityResultContract<Args, Boolean>() {
        override fun createIntent(context: Context, input: Args): Intent =
            EditSmbServerActivity::class.createIntent().putArgs(input)

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
            resultCode == Activity.RESULT_OK
    }
}

private enum class SmbAuthenticationType {
    PASSWORD,
    GUEST,
    ANONYMOUS;

    /** The fixed credentials of this type, or null when the user enters them. */
    val authenticationContext: AuthenticationContext?
        get() =
            when (this) {
                PASSWORD -> null
                GUEST -> AuthenticationContext.guest()
                ANONYMOUS -> AuthenticationContext.anonymous()
            }
}

@Composable
private fun EditSmbServerScreen(
    server: SmbServer?,
    host: String?,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val viewModel = viewModel { EditSmbServerViewModel() }
    val connectState by viewModel.connectState.collectAsStateWithLifecycle()
    val authority = server?.authority
    val initialAuthenticationType = if (server != null) {
        SmbAuthenticationType.entries.firstOrNull { type ->
            type.authenticationContext?.let {
                server.authority.username == it.username && server.authority.domain == it.domain
                    && server.password == it.password.concatToString()
            } == true
        } ?: SmbAuthenticationType.PASSWORD
    } else {
        SmbAuthenticationType.PASSWORD
    }
    val isPasswordAuthentication = initialAuthenticationType == SmbAuthenticationType.PASSWORD
    val hostField = rememberFormFieldState(authority?.host ?: host.orEmpty())
    val portField = rememberFormFieldState(
        authority?.port?.takeIf { it != Authority.DEFAULT_PORT }?.toString().orEmpty()
    )
    val pathField = rememberFormFieldState(server?.relativePath.orEmpty())
    val nameField = rememberFormFieldState(server?.customName.orEmpty())
    var authenticationType by rememberSaveable { mutableStateOf(initialAuthenticationType) }
    val usernameField = rememberFormFieldState(
        authority?.username?.takeIf { isPasswordAuthentication }.orEmpty()
    )
    val domainField = rememberFormFieldState(
        authority?.domain?.takeIf { isPasswordAuthentication }.orEmpty()
    )
    val passwordField = rememberFormFieldState(
        server?.password?.takeIf { isPasswordAuthentication }.orEmpty()
    )
    var encrypt by rememberSaveable {
        mutableStateOf(authority?.encrypt ?: Authority.DEFAULT_ENCRYPT)
    }

    // The username, domain and password for the selected authentication type.
    fun getCredentials(): Triple<String?, String?, String> {
        val context = authenticationType.authenticationContext
        return if (context != null) {
            Triple(context.username, context.domain, context.password.concatToString())
        } else {
            Triple(
                usernameField.text.takeIfNotEmpty(), domainField.text.takeIfNotEmpty(),
                passwordField.text
            )
        }
    }

    val placeholderHost = hostField.text.takeIfNotEmpty()
    val namePlaceholder = if (placeholderHost != null) {
        val port = portField.text.takeIfNotEmpty()?.toIntOrNull() ?: Authority.DEFAULT_PORT
        val path = pathField.text.trim()
        val (username, domain) = getCredentials()
        val authority = Authority(placeholderHost, port, username.orEmpty(), domain, encrypt)
        if (path.isNotEmpty()) "$authority/$path" else authority.toString()
    } else {
        stringResource(R.string.storage_edit_smb_server_name_placeholder)
    }

    fun getServerOrSetErrors(): SmbServer? {
        val errors = FormErrors()
        val host = hostField.text.takeIfNotEmpty()?.let { URI::class.canonicalizeHost(it) }
        if (host == null) {
            errors.set(hostField, R.string.storage_edit_smb_server_host_error_empty)
        } else if (!URI::class.isValidHost(host)) {
            errors.set(hostField, R.string.storage_edit_smb_server_host_error_invalid)
        }
        val port = portField.text.takeIfNotEmpty()
            .let { if (it != null) it.toIntOrNull() else Authority.DEFAULT_PORT }
        if (port == null) {
            errors.set(portField, R.string.storage_edit_smb_server_port_error_invalid)
        }
        val path = pathField.text.trim()
        val name = nameField.text.takeIfNotEmpty()
        val (username, domain, password) = getCredentials()
        if (username == null) {
            errors.set(usernameField, R.string.storage_edit_smb_server_username_error_empty)
        }
        if (!errors.check()) {
            return null
        }
        val authority = Authority(host!!, port!!, username!!, domain, encrypt)
        return SmbServer(server?.id, name, authority, password, path)
    }

    EditServerScreen(
        title = stringResource(
            if (server != null) {
                R.string.storage_edit_smb_server_title_edit
            } else {
                R.string.storage_edit_smb_server_title_add
            }
        ),
        server = server,
        addText = stringResource(R.string.storage_edit_smb_server_add),
        connectAndAddText = stringResource(R.string.storage_edit_smb_server_connect_and_add),
        connectState = connectState,
        onConnect = viewModel::connect,
        onFinishConnecting = viewModel::finishConnecting,
        getServerOrSetErrors = ::getServerOrSetErrors,
        onFinish = onFinish,
        onCancel = onCancel
    ) {
        FormTextField(
            field = hostField,
            label = stringResource(R.string.storage_edit_smb_server_host),
            keyboardType = KeyboardType.Uri
        )
        FormTextField(
            field = portField,
            label = stringResource(R.string.storage_edit_smb_server_port),
            placeholder = stringResource(R.string.storage_edit_smb_server_port_placeholder),
            keyboardType = KeyboardType.Number
        )
        FormTextField(
            field = pathField,
            label = stringResource(R.string.storage_edit_smb_server_path),
            placeholder = stringResource(R.string.storage_edit_smb_server_path_placeholder),
            keyboardType = KeyboardType.Uri
        )
        FormTextField(
            field = nameField,
            label = stringResource(R.string.storage_edit_smb_server_name),
            placeholder = namePlaceholder
        )
        val authenticationTypeLabels =
            stringArrayResource(R.array.storage_edit_smb_server_authentication_type_entries)
        FormDropdownField(
            label = stringResource(R.string.storage_edit_smb_server_authentication_type),
            entries = SmbAuthenticationType.entries,
            entryLabel = { authenticationTypeLabels[it.ordinal] },
            value = authenticationType,
            onValueChange = { authenticationType = it }
        )
        AnimatedVisibility(visible = authenticationType == SmbAuthenticationType.PASSWORD) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField(
                    field = usernameField,
                    label = stringResource(R.string.storage_edit_smb_server_username)
                )
                FormTextField(
                    field = domainField,
                    label = stringResource(R.string.storage_edit_smb_server_domain)
                )
                FormPasswordField(
                    field = passwordField,
                    label = stringResource(R.string.storage_edit_smb_server_password)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = encrypt, role = Role.Checkbox, onValueChange = { encrypt = it })
                .padding(vertical = 8.dp)
        ) {
            Checkbox(checked = encrypt, onCheckedChange = null)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.storage_edit_smb_server_encrypt),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.storage_edit_smb_server_encrypt_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
