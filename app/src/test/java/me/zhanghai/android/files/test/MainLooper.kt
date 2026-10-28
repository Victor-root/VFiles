/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.test

import android.os.Looper
import org.robolectric.Shadows.shadowOf

/** Runs the main looper until [condition] holds, for work that also runs on other threads. */
fun idleMainLooperUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (true) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) {
            return
        }
        check(System.currentTimeMillis() < deadline) {
            "Condition not satisfied after $timeoutMillis ms"
        }
        Thread.sleep(10)
    }
}
