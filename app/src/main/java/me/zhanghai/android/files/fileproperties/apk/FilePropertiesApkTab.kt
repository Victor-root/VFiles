/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.apk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.longVersionCodeCompat
import me.zhanghai.android.files.fileproperties.FilePropertiesItem
import me.zhanghai.android.files.fileproperties.FilePropertiesTab
import me.zhanghai.android.files.util.observeAsStateCompat

@Composable
fun FilePropertiesApkTab(path: Path) {
    val viewModel = viewModel { FilePropertiesApkTabViewModel(path) }
    val stateful by viewModel.apkInfoLiveData.observeAsStateCompat()
    var isPermissionListDialogShown by rememberSaveable { mutableStateOf(false) }
    FilePropertiesTab(stateful, onRefresh = viewModel::reload) { apkInfo ->
        FilePropertiesItem(R.string.file_properties_apk_label, apkInfo.label)
        val packageInfo = apkInfo.packageInfo
        FilePropertiesItem(R.string.file_properties_apk_package_name, packageInfo.packageName)
        FilePropertiesItem(
            R.string.file_properties_apk_version,
            stringResource(
                R.string.file_properties_apk_version_format, packageInfo.versionName.orEmpty(),
                packageInfo.longVersionCodeCompat
            )
        )
        val applicationInfo = packageInfo.applicationInfo!!
        FilePropertiesItem(
            R.string.file_properties_apk_min_sdk_version,
            getSdkVersionText(applicationInfo.minSdkVersion)
        )
        FilePropertiesItem(
            R.string.file_properties_apk_target_sdk_version,
            getSdkVersionText(applicationInfo.targetSdkVersion)
        )
        val requestedPermissionsSize = packageInfo.requestedPermissions?.size ?: 0
        if (requestedPermissionsSize == 0) {
            FilePropertiesItem(
                R.string.file_properties_apk_requested_permissions,
                stringResource(R.string.file_properties_apk_requested_permissions_zero)
            )
        } else {
            FilePropertiesItem(
                R.string.file_properties_apk_requested_permissions,
                pluralStringResource(
                    R.plurals.file_properties_apk_requested_permissions_positive_format,
                    requestedPermissionsSize, requestedPermissionsSize
                )
            ) { isPermissionListDialogShown = true }
        }
        FilePropertiesItem(
            R.string.file_properties_apk_signature_digests,
            if (apkInfo.signingCertificateDigests.isNotEmpty()) {
                apkInfo.signingCertificateDigests.joinToString("\n")
            } else {
                stringResource(R.string.file_properties_apk_signature_digests_empty)
            }
        )
        if (apkInfo.pastSigningCertificateDigests.isNotEmpty()) {
            FilePropertiesItem(
                R.string.file_properties_apk_past_signature_digests,
                apkInfo.pastSigningCertificateDigests.joinToString("\n")
            )
        }
    }
    val permissionNames = stateful.value?.packageInfo?.requestedPermissions
    if (isPermissionListDialogShown && permissionNames != null) {
        PermissionListDialog(permissionNames) { isPermissionListDialogShown = false }
    }
}

@Composable
private fun getSdkVersionText(sdkVersion: Int): String {
    val names = stringArrayResource(R.array.file_properites_apk_sdk_version_names)
    val codeNames = stringArrayResource(R.array.file_properites_apk_sdk_version_codenames)
    return stringResource(
        R.string.file_properites_apk_sdk_version_format, names[sdkVersion.coerceIn(names.indices)],
        codeNames[sdkVersion.coerceIn(codeNames.indices)], sdkVersion
    )
}
