/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.checksum

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.fileproperties.FilePropertiesItem
import me.zhanghai.android.files.fileproperties.FilePropertiesTab
import me.zhanghai.android.files.ui.rememberTextFieldKeyboard

@Composable
fun FilePropertiesChecksumTab(path: Path) {
    val viewModel = viewModel { FilePropertiesChecksumTabViewModel(path) }
    val stateful by viewModel.checksumInfoFlow.collectAsStateWithLifecycle()
    // Kept across refreshes.
    val compareState = rememberTextFieldState()
    FilePropertiesTab(stateful, onRefresh = viewModel::reload) { checksumInfo ->
        checksumInfo.checksums.forEach { (algorithm, checksum) ->
            FilePropertiesItem(algorithm.nameRes, checksum)
        }
        ChecksumCompareField(compareState, checksumInfo)
    }
}

/** A field telling which checksum matches the one typed, or one starting like it. */
@Composable
private fun ChecksumCompareField(state: TextFieldState, checksumInfo: ChecksumInfo) {
    val text = state.text.toString().trim()
    val matchingAlgorithm = if (text.isNotEmpty()) {
        checksumInfo.checksums.entries.firstOrNull { it.value.equals(text, true) }?.key
    } else {
        null
    }
    val prefixMatchingAlgorithm = if (text.isNotEmpty() && matchingAlgorithm == null) {
        checksumInfo.checksums.entries.firstOrNull { it.value.startsWith(text, true) }?.key
    } else {
        null
    }
    val isError = text.isNotEmpty() && matchingAlgorithm == null && prefixMatchingAlgorithm == null
    val supportingText = when {
        matchingAlgorithm != null ->
            stringResource(
                R.string.file_properties_checksum_compare_match_format,
                stringResource(matchingAlgorithm.nameRes)
            )
        prefixMatchingAlgorithm != null ->
            stringResource(
                R.string.file_properties_checksum_compare_prefix_match_format,
                stringResource(prefixMatchingAlgorithm.nameRes)
            )
        isError -> stringResource(R.string.file_properties_checksum_compare_no_match)
        else -> null
    }
    val keyboard = rememberTextFieldKeyboard()
    OutlinedTextField(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .then(keyboard.modifier),
        labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
        label = { Text(stringResource(R.string.file_properties_checksum_compare)) },
        placeholder = {
            Text(stringResource(R.string.file_properties_checksum_compare_placeholder))
        },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        keyboardOptions = keyboard.keyboardOptions,
        lineLimits = TextFieldLineLimits.MultiLine()
    )
}
