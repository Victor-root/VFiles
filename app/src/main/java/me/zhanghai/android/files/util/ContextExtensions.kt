/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import android.util.TypedValue
import android.widget.Toast
import androidx.annotation.ArrayRes
import androidx.annotation.BoolRes
import androidx.annotation.DimenRes
import androidx.annotation.Dimension
import androidx.annotation.IntegerRes
import androidx.annotation.PluralsRes
import androidx.core.util.TypedValueCompat
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.getFloatCompat
import me.zhanghai.android.files.compat.mainExecutorCompat

val Context.activity: Activity?
    get() {
        var context = this
        while (true) {
            when (context) {
                is Activity -> return context
                is ContextWrapper -> context = context.baseContext
                else -> return null
            }
        }
    }

// True on Android TV / Google TV (leanback). Used to apply TV-only behavior (e.g. D-pad focus
// handling, disabling the breadcrumb) without changing the phone/tablet experience.
val Context.isTelevision: Boolean
    get() {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        return packageManager.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_LEANBACK
        )
    }

fun Context.getBoolean(@BoolRes id: Int): Boolean = resources.getBoolean(id)

@Dimension
fun Context.getDimension(@DimenRes id: Int): Float = resources.getDimension(id)

@Dimension
fun Context.getDimensionPixelOffset(@DimenRes id: Int): Int = resources.getDimensionPixelOffset(id)

@Dimension
fun Context.getDimensionPixelSize(@DimenRes id: Int): Int = resources.getDimensionPixelSize(id)

fun Context.getFloat(@DimenRes id: Int): Float = resources.getFloatCompat(id)

fun Context.getInteger(@IntegerRes id: Int): Int = resources.getInteger(id)

fun Context.getQuantityString(@PluralsRes id: Int, quantity: Int): String =
    resources.getQuantityString(id, quantity)

fun Context.getQuantityString(@PluralsRes id: Int, quantity: Int, vararg formatArgs: Any?): String =
    resources.getQuantityString(id, quantity, *formatArgs)

fun Context.getQuantityText(@PluralsRes id: Int, quantity: Int): CharSequence =
    resources.getQuantityText(id, quantity)

fun Context.getStringArray(@ArrayRes id: Int): Array<String> = resources.getStringArray(id)

fun Context.getTextArray(@ArrayRes id: Int): Array<CharSequence> = resources.getTextArray(id)

@Dimension
fun Context.dpToDimension(@Dimension(unit = Dimension.DP) dp: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

@Dimension
fun Context.dpToDimension(@Dimension(unit = Dimension.DP) dp: Int) = dpToDimension(dp.toFloat())

@Dimension
fun Context.dpToDimensionPixelOffset(@Dimension(unit = Dimension.DP) dp: Float): Int =
    dpToDimension(dp).toInt()

@Dimension
fun Context.dpToDimensionPixelOffset(@Dimension(unit = Dimension.DP) dp: Int) =
    dpToDimensionPixelOffset(dp.toFloat())

@Dimension
fun Context.dpToDimensionPixelSize(@Dimension(unit = Dimension.DP) dp: Float): Int {
    val value = dpToDimension(dp)
    val size = (if (value >= 0) value + 0.5f else value - 0.5f).toInt()
    return when {
        size != 0 -> size
        value == 0f -> 0
        value > 0 -> 1
        else -> -1
    }
}

@Dimension
fun Context.dpToDimensionPixelSize(@Dimension(unit = Dimension.DP) dp: Int) =
    dpToDimensionPixelSize(dp.toFloat())

@Dimension(unit = Dimension.DP)
fun Context.dimensionToDp(@Dimension dimension: Float): Float =
    TypedValueCompat.pxToDp(dimension, resources.displayMetrics)

@Dimension(unit = Dimension.DP)
fun Context.dimensionToDp(@Dimension dimension: Int): Float = dimensionToDp(dimension.toFloat())

fun Context.showToast(textRes: Int, duration: Int = Toast.LENGTH_SHORT) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        mainExecutorCompat.execute { showToast(textRes, duration) }
        return
    }
    Toast.makeText(this, textRes, duration).show()
}

fun Context.showToast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        mainExecutorCompat.execute { showToast(text, duration) }
        return
    }
    Toast.makeText(this, text, duration).show()
}

fun Context.startActivitySafe(intent: Intent, options: Bundle? = null) {
    try {
        startActivity(intent, options)
    } catch (e: ActivityNotFoundException) {
        showToast(R.string.activity_not_found)
    }
}
