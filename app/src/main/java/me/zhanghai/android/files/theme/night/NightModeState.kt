/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.theme.night

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun NightMode.isNightModeActive(): Boolean =
    when (this) {
        NightMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        NightMode.OFF -> false
        NightMode.ON -> true
        NightMode.AUTO_TIME -> isNightTime()
        NightMode.AUTO_BATTERY -> isPowerSaveMode()
    }

// Same fallback as AppCompat's TwilightManager without a location: night from 22:00 to 6:00.
private val NIGHT_START = LocalTime.of(22, 0)
private val NIGHT_END = LocalTime.of(6, 0)

@Composable
private fun isNightTime(): Boolean {
    val isNight by produceState(LocalTime.now().isNight()) {
        while (true) {
            val now = LocalDateTime.now()
            val nextChange = if (value) NIGHT_END else NIGHT_START
            var nextChangeDateTime = now.toLocalDate().atTime(nextChange)
            if (!nextChangeDateTime.isAfter(now)) {
                nextChangeDateTime = nextChangeDateTime.plusDays(1)
            }
            delay(Duration.between(now, nextChangeDateTime).toMillis())
            value = LocalTime.now().isNight()
        }
    }
    return isNight
}

private fun LocalTime.isNight(): Boolean = this >= NIGHT_START || this < NIGHT_END

@Composable
private fun isPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService<PowerManager>()!! }
    var isPowerSaveMode by remember { mutableStateOf(powerManager.isPowerSaveMode) }
    DisposableEffect(context, powerManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                isPowerSaveMode = powerManager.isPowerSaveMode
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isPowerSaveMode = powerManager.isPowerSaveMode
        onDispose { context.unregisterReceiver(receiver) }
    }
    return isPowerSaveMode
}
