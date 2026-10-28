/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.test

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import me.zhanghai.android.files.app.Destination
import me.zhanghai.android.files.app.ScreenStack
import me.zhanghai.android.files.theme.AppTheme

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

/** Shows [destination] as the screen of the activity, like the activities showing it. */
fun ComposeContentTestRule.setDestinationContent(destination: Destination) {
    setContent { DestinationContent(destination) }
}

/**
 * Launches an activity showing [destination] as its screen, for tests setting up the device before
 * the activity is created.
 */
fun launchDestination(destination: Destination): ActivityScenario<ComponentActivity> =
    ActivityScenario.launch(ComponentActivity::class.java)
        .onActivity { it.setContent { DestinationContent(destination) } }

@Composable
private fun DestinationContent(destination: Destination) {
    AppTheme { ScreenStack { destination.Content() } }
}
