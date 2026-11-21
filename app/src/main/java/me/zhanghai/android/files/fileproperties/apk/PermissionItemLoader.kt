/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.apk

import me.zhanghai.android.files.app.packageManager
import me.zhanghai.android.files.util.getPermissionInfoOrNull

/** Loads the information of permissions by their names. */
fun loadPermissionItems(permissionNames: Array<String>): List<PermissionItem> =
    permissionNames.map { name ->
        val packageManager = packageManager
        val permissionInfo = packageManager.getPermissionInfoOrNull(name, 0)
        val label = permissionInfo?.loadLabel(packageManager)?.toString().takeIf { it != name }
        val description = permissionInfo?.loadDescription(packageManager)?.toString()
        PermissionItem(name, permissionInfo, label, description)
    }
