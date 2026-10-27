/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.FastScroller
import me.zhanghai.android.files.ui.MenuItem
import me.zhanghai.android.files.ui.OverflowMenu
import me.zhanghai.android.files.ui.SingleChoiceDialog
import me.zhanghai.android.files.ui.asFastScrollable
import me.zhanghai.android.files.util.ActionState
import me.zhanghai.android.files.util.DataState
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.isReady
import me.zhanghai.android.files.util.showToast
import java.nio.charset.Charset

class TextEditorActivity : AppActivity() {
    private lateinit var file: Path

    override fun onCreate(savedInstanceState: Bundle?) {
        val file = intent.extraPath
        if (file == null) {
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        this.file = file
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun Content() {
        TextEditorScreen(viewModel { TextEditorViewModel(file) }, onFinish = { finish() })
    }
}

@Composable
private fun TextEditorScreen(viewModel: TextEditorViewModel, onFinish: () -> Unit) {
    val context = LocalContext.current
    val file by viewModel.file.collectAsStateWithLifecycle()
    val textState by viewModel.textState.collectAsStateWithLifecycle()
    val isTextChanged by viewModel.isTextChanged.collectAsStateWithLifecycle()
    val encoding by viewModel.encoding.collectAsStateWithLifecycle()
    val writeFileState by viewModel.writeFileState.collectAsStateWithLifecycle()
    var isCloseDialogShown by rememberSaveable { mutableStateOf(false) }
    var isReloadDialogShown by rememberSaveable { mutableStateOf(false) }
    var isEncodingDialogShown by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(writeFileState) {
        when (writeFileState) {
            is ActionState.Success -> {
                context.showToast(R.string.text_editor_save_success)
                viewModel.finishWritingFile()
                viewModel.isTextChanged.value = false
            }
            // The error is toasted by the file job service.
            is ActionState.Error -> viewModel.finishWritingFile()
            else -> {}
        }
    }
    BackHandler(enabled = isTextChanged) { isCloseDialogShown = true }

    val fileName = file.fileName.toString()
    AppScaffold(
        title = stringResource(
            if (isTextChanged) {
                R.string.text_editor_title_changed_format
            } else {
                R.string.text_editor_title_format
            },
            fileName
        ),
        actions = {
            IconButton(
                onClick = {
                    viewModel.writeFile(
                        file, viewModel.textFieldState.text.toString(), context
                    )
                },
                enabled = writeFileState.isReady
            ) {
                Icon(
                    painter = painterResource(R.drawable.save_icon_white_24dp),
                    contentDescription = stringResource(R.string.save)
                )
            }
            OverflowMenu { dismiss ->
                MenuItem(
                    R.drawable.refresh_icon_control_normal_24dp, stringResource(R.string.reload)
                ) {
                    dismiss()
                    if (isTextChanged) {
                        isReloadDialogShown = true
                    } else {
                        viewModel.reload()
                    }
                }
                MenuItem(null, stringResource(R.string.text_editor_encoding)) {
                    dismiss()
                    isEncodingDialogShown = true
                }
            }
        }
    ) { contentPadding ->
        AnimatedContent(
            targetState = textState::class,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .imePadding(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "textState"
        ) { stateClass ->
            when (stateClass) {
                DataState.Loading::class ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                DataState.Error::class ->
                    TextEditorError((textState as? DataState.Error)?.throwable)
                else -> TextEditorTextField(viewModel)
            }
        }
    }

    if (isCloseDialogShown) {
        KeepEditingDialog(
            text = stringResource(R.string.text_editor_close_message),
            otherActionText = stringResource(R.string.discard),
            onOtherAction = onFinish,
            onDismissRequest = { isCloseDialogShown = false }
        )
    }
    if (isReloadDialogShown) {
        KeepEditingDialog(
            text = stringResource(R.string.text_editor_reload_message),
            otherActionText = stringResource(R.string.reload),
            onOtherAction = {
                viewModel.isTextChanged.value = false
                viewModel.reload()
            },
            onDismissRequest = { isReloadDialogShown = false }
        )
    }
    if (isEncodingDialogShown) {
        val charsets = remember { Charset.availableCharsets() }
        SingleChoiceDialog(
            title = stringResource(R.string.text_editor_encoding),
            entries = charsets.values.toList(),
            entryLabel = { it.displayName() },
            selectedEntry = encoding,
            onEntrySelected = {
                isEncodingDialogShown = false
                viewModel.encoding.value = it
            },
            onDismissRequest = { isEncodingDialogShown = false }
        )
    }
}

@Composable
private fun TextEditorTextField(viewModel: TextEditorViewModel) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        BasicTextField(
            state = viewModel.textFieldState,
            modifier = Modifier.fillMaxSize(),
            // Only called for edits by the user, not when the text is loaded.
            inputTransformation = InputTransformation { viewModel.isTextChanged.value = true },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = LocalContentColor.current
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            scrollState = scrollState,
            decorator = { innerTextField ->
                Box(modifier = Modifier.padding(16.dp)) { innerTextField() }
            }
        )
        FastScroller(
            scrollable = scrollState.asFastScrollable(),
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun TextEditorError(throwable: Throwable?) {
    LaunchedEffect(throwable) { throwable?.printStackTrace() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.error_icon_white_240dp),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = throwable.toString(),
            modifier = Modifier.padding(16.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/** Unsaved changes would be lost: keep editing (the safe default) or do [onOtherAction]. */
@Composable
private fun KeepEditingDialog(
    text: String,
    otherActionText: String,
    onOtherAction: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.keep_editing)) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onOtherAction()
                }
            ) { Text(otherActionText) }
        },
        text = { Text(text) }
    )
}
