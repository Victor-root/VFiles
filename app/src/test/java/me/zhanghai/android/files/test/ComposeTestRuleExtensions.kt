/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.test

import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * Waits for [condition] while work runs on background threads, like file I/O, whose results are
 * posted to the main thread. waitUntil() alone doesn't give these threads time to run.
 */
fun ComposeTestRule.waitForBackgroundWork(
    timeoutMillis: Long = 5_000,
    condition: () -> Boolean
) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (true) {
        waitForIdle()
        if (condition()) {
            return
        }
        check(System.currentTimeMillis() < deadline) {
            "Condition not satisfied after $timeoutMillis ms"
        }
        Thread.sleep(20)
    }
}
