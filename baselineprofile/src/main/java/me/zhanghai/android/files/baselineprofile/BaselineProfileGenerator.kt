/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile of the app, which lets Android compile ahead of time the code run
 * when starting it and scrolling its files, run with ":app:generateBaselineProfile".
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = true) {
            // Lets the app list the files, as the user does once, which skips the welcome screen.
            device.executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
            pressHome()
            startActivityAndWait()
            scrollFiles()
        }
    }

    private fun MacrobenchmarkScope.scrollFiles() {
        val list = device.wait(Until.findObject(By.scrollable(true)), TIMEOUT_MILLIS) ?: return
        // Keeps clear of the edges, where the system gestures are.
        list.setGestureMargin(device.displayWidth / 10)
        repeat(2) {
            list.fling(Direction.DOWN)
            device.waitForIdle()
            list.fling(Direction.UP)
            device.waitForIdle()
        }
    }

    companion object {
        private const val PACKAGE_NAME = "fr.vroot.vfiles"
        private const val TIMEOUT_MILLIS = 5_000L
    }
}
