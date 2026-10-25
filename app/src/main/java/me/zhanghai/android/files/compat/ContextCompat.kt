/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.compat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

fun Context.checkSelfPermissionCompat(permission: String): Int =
    ContextCompat.checkSelfPermission(this, permission)

@ColorInt
fun Context.getColorCompat(@ColorRes id: Int): Int = getColorStateListCompat(id).defaultColor

fun Context.getColorStateListCompat(@ColorRes id: Int): ColorStateList =
    ContextCompat.getColorStateList(this, id)!!

fun <T> Context.getSystemServiceCompat(serviceClass: Class<T>): T =
    ContextCompat.getSystemService(this, serviceClass)!!

val Context.mainExecutorCompat: Executor
    get() = ContextCompat.getMainExecutor(this)

fun Context.registerReceiverCompat(
    receiver: BroadcastReceiver?,
    filter: IntentFilter,
    flags: Int
): Intent? = ContextCompat.registerReceiver(this, receiver, filter, flags)
