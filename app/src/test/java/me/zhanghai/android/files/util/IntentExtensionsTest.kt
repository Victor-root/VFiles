/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.content.ComponentName
import android.content.Intent
import androidx.core.content.IntentCompat
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.viewer.saveas.SaveAsActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IntentExtensionsTest {
    @get:Rule
    val appProviderRule = AppProviderRule()

    @Test
    fun chooserLeavesOutSaveAs() {
        val chooserIntent = Intent(Intent.ACTION_VIEW).withChooser()
        val excludedComponents = IntentCompat.getParcelableArrayExtra(
            chooserIntent, Intent.EXTRA_EXCLUDE_COMPONENTS, ComponentName::class.java
        )!!
        assertEquals(
            listOf(SaveAsActivity::class.java.name), excludedComponents.map {
                (it as ComponentName).className
            }
        )
    }
}
