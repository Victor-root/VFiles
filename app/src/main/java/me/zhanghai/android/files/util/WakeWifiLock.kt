/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.net.wifi.WifiManager
import android.os.PowerManager
import me.zhanghai.android.files.app.powerManager
import me.zhanghai.android.files.app.wifiManager

class WakeWifiLock(tag: String) {
    private val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
        .apply { setReferenceCounted(false) }
    // Keeps the FTP server reachable while the screen is off. There is no direct replacement
    // constant for this exact "stay at full performance" semantic, and changing WiFi lock behavior
    // risks the FTP server dropping its connection when idle, which is not verifiable here.
    @Suppress("DEPRECATION")
    private val wifiLock =
        wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, tag)
            .apply { setReferenceCounted(false) }

    var isAcquired: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            if (value) {
                wakeLock.acquire()
                wifiLock.acquire()
            } else {
                wifiLock.release()
                wakeLock.release()
            }
            field = value
        }
}
