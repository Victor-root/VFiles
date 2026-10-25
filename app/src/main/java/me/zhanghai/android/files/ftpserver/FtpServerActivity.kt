/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.app.StatusBarManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.ComposeActivity
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.compat.getSystemServiceCompat
import me.zhanghai.android.files.compat.mainExecutorCompat
import me.zhanghai.android.files.settings.PathPreference
import me.zhanghai.android.files.settings.SettingSwitchPreference
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.Preference
import me.zhanghai.android.files.ui.PreferenceCategory
import me.zhanghai.android.files.ui.SwitchPreference
import me.zhanghai.android.files.ui.TextFieldDialog
import me.zhanghai.android.files.util.copyText
import me.zhanghai.android.files.util.observeAsStateCompat
import me.zhanghai.android.files.util.requestAddTileService
import me.zhanghai.android.files.util.showToast

class FtpServerActivity : ComposeActivity() {
    @Composable
    override fun Content() {
        val state by FtpServerService.stateLiveData.observeAsStateCompat()
        FtpServerScreen(state)
    }
}

@Composable
internal fun FtpServerScreen(state: FtpServerService.State) {
    AppScaffold(title = stringResource(R.string.ftp_server_title)) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
        ) {
            val isStartingOrRunning = state == FtpServerService.State.STARTING
                || state == FtpServerService.State.RUNNING
            StatePreference(state, isStartingOrRunning)
            UrlPreference()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                AddTilePreference()
            }
            PreferenceCategory(stringResource(R.string.ftp_server_configuration_title))
            // The server only reads its configuration when it starts.
            val isConfigurable = !isStartingOrRunning
            val isAnonymousLogin by Settings.FTP_SERVER_ANONYMOUS_LOGIN.observeAsStateCompat()
            SettingSwitchPreference(
                Settings.FTP_SERVER_ANONYMOUS_LOGIN,
                stringResource(R.string.ftp_server_anonymous_login_title),
                enabled = isConfigurable
            )
            UsernamePreference(enabled = isConfigurable && !isAnonymousLogin)
            PasswordPreference(enabled = isConfigurable && !isAnonymousLogin)
            PortPreference(enabled = isConfigurable)
            PathPreference(
                Settings.FTP_SERVER_HOME_DIRECTORY,
                stringResource(R.string.ftp_server_home_directory_title),
                enabled = isConfigurable
            )
            SettingSwitchPreference(
                Settings.FTP_SERVER_WRITABLE,
                stringResource(R.string.ftp_server_writable_title),
                enabled = isConfigurable
            )
        }
    }
}

@Composable
private fun StatePreference(state: FtpServerService.State, isStartingOrRunning: Boolean) {
    val context = LocalContext.current
    SwitchPreference(
        title = stringResource(R.string.ftp_server_state_title),
        checked = isStartingOrRunning,
        // Intentionally not disabled while starting or stopping: on Android TV a disabled row
        // loses D-pad focus, and toggle() ignores clicks then.
        onCheckedChange = { FtpServerService.toggle(context) },
        summary = stringResource(
            when (state) {
                FtpServerService.State.STARTING -> R.string.ftp_server_state_summary_starting
                FtpServerService.State.RUNNING -> R.string.ftp_server_state_summary_running
                FtpServerService.State.STOPPING -> R.string.ftp_server_state_summary_stopping
                FtpServerService.State.STOPPED -> R.string.ftp_server_state_summary_stopped
            }
        )
    )
}

@Composable
private fun UrlPreference() {
    val context = LocalContext.current
    val isAnonymousLogin by Settings.FTP_SERVER_ANONYMOUS_LOGIN.observeAsStateCompat()
    val username by Settings.FTP_SERVER_USERNAME.observeAsStateCompat()
    val password by Settings.FTP_SERVER_PASSWORD.observeAsStateCompat()
    val port by Settings.FTP_SERVER_PORT.observeAsStateCompat()
    var networkChangeCount by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver = FtpServerUrl.createChangeReceiver(context) { ++networkChangeCount }
        receiver.register()
        onDispose { receiver.unregister() }
    }
    val url = remember(isAnonymousLogin, username, port, networkChangeCount) {
        FtpServerUrl.getUrl()
    }
    var isMenuShown by rememberSaveable { mutableStateOf(false) }
    Box {
        val showMenu = {
            if (url != null) {
                isMenuShown = true
            }
        }
        Preference(
            title = stringResource(R.string.ftp_server_url_title),
            modifier = Modifier.combinedClickable(onLongClick = showMenu, onClick = showMenu),
            summary = url ?: stringResource(R.string.ftp_server_url_summary_no_local_inet_address)
        )
        DropdownMenu(
            expanded = isMenuShown && url != null,
            onDismissRequest = { isMenuShown = false },
            offset = DpOffset(16.dp, 0.dp)
        ) {
            if (url != null) {
                Text(
                    text = url,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ftp_server_url_menu_copy_url)) },
                    onClick = {
                        isMenuShown = false
                        clipboardManager.copyText(url, context)
                    }
                )
                if (!isAnonymousLogin && password.isNotEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.ftp_server_url_menu_copy_password))
                        },
                        onClick = {
                            isMenuShown = false
                            clipboardManager.copyText(password, context)
                        }
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AddTilePreference() {
    val context = LocalContext.current
    Preference(
        title = stringResource(R.string.ftp_server_add_tile_title),
        onClick = { requestAddTile(context) }
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestAddTile(context: Context) {
    val statusBarManager = context.getSystemServiceCompat(StatusBarManager::class.java)
    statusBarManager.requestAddTileService(
        FtpServerTileService::class.java, context.mainExecutorCompat
    ) { result ->
        val resultRes = when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                return@requestAddTileService
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                R.string.ftp_server_add_tile_result_already_added
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                R.string.ftp_server_add_tile_result_added
            else -> R.string.ftp_server_add_tile_result_error
        }
        context.showToast(resultRes)
    }
}

@Composable
private fun UsernamePreference(enabled: Boolean) {
    val username by Settings.FTP_SERVER_USERNAME.observeAsStateCompat()
    val defaultUsername = stringResource(R.string.pref_default_value_ftp_server_username)
    TextFieldPreference(
        title = stringResource(R.string.ftp_server_username_title),
        summary = username,
        enabled = enabled,
        initialText = username,
        onConfirm = { Settings.FTP_SERVER_USERNAME.putValue(it.ifEmpty { defaultUsername }) }
    )
}

@Composable
private fun PasswordPreference(enabled: Boolean) {
    val password by Settings.FTP_SERVER_PASSWORD.observeAsStateCompat()
    TextFieldPreference(
        title = stringResource(R.string.ftp_server_password_title),
        summary = if (password.isNotEmpty()) {
            PASSWORD_MASK_CHAR.toString().repeat(password.length)
        } else {
            stringResource(R.string.ftp_server_password_summary_empty)
        },
        enabled = enabled,
        initialText = password,
        onConfirm = { Settings.FTP_SERVER_PASSWORD.putValue(it) },
        keyboardType = KeyboardType.Password,
        trim = false
    )
}

// @see android.text.method.PasswordTransformationMethod
private const val PASSWORD_MASK_CHAR = '•'

@Composable
private fun PortPreference(enabled: Boolean) {
    val port by Settings.FTP_SERVER_PORT.observeAsStateCompat()
    val errorInvalid = stringResource(R.string.ftp_server_port_error_invalid)
    TextFieldPreference(
        title = stringResource(R.string.ftp_server_port_title),
        summary = port.toString(),
        enabled = enabled,
        initialText = port.toString(),
        onConfirm = { Settings.FTP_SERVER_PORT.putValue(it.toInt()) },
        validate = { if ((it.toIntOrNull() ?: -1) < 0) errorInvalid else null },
        keyboardType = KeyboardType.Number
    )
}

@Composable
private fun TextFieldPreference(
    title: String,
    summary: String,
    enabled: Boolean,
    initialText: String,
    onConfirm: (String) -> Unit,
    validate: (String) -> String? = { null },
    keyboardType: KeyboardType = KeyboardType.Text,
    trim: Boolean = true
) {
    var isDialogShown by rememberSaveable { mutableStateOf(false) }
    Preference(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { isDialogShown = true }
    )
    if (isDialogShown) {
        TextFieldDialog(
            title = title,
            onConfirm = onConfirm,
            onDismissRequest = { isDialogShown = false },
            initialText = initialText,
            validate = validate,
            keyboardType = keyboardType,
            trim = trim
        )
    }
}
