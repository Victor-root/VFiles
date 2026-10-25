/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.theme.night.NightMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DefaultAppsActivityTest {
    private val composeTestRule = createAndroidComposeRule<DefaultAppsActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    @Before
    fun setUp() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        Settings.FILE_OPEN_DEFAULT_APPS.putValue(emptyList())
        installImageViewer()
    }

    private fun installImageViewer() {
        val packageManager = shadowOf(composeTestRule.activity.packageManager)
        packageManager.installPackage(
            PackageInfo().apply {
                packageName = GALLERY_PACKAGE
                applicationInfo = ApplicationInfo().apply {
                    packageName = GALLERY_PACKAGE
                    nonLocalizedLabel = "Gallery"
                }
            }
        )
        val component = ComponentName(GALLERY_PACKAGE, "$GALLERY_PACKAGE.ViewActivity")
        packageManager.addActivityIfNotPresent(component)
        packageManager.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addDataScheme("content")
                addDataType("image/*")
            }
        )
    }

    @Test
    fun showsCategories() {
        composeTestRule.onNodeWithText("Default apps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Images").assertIsDisplayed()
        composeTestRule.onNodeWithText("Videos").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/default_apps_light.png")
        Settings.NIGHT_MODE.putValue(NightMode.ON)
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/default_apps_dark.png")
    }

    @Test
    fun picksAppForCategory() {
        composeTestRule.onNodeWithText("Images").performClick()
        composeTestRule.onNode(hasText("Ask every time") and isSelectable()).assertIsSelected()
        captureScreenRoboImage("build/screenshots/default_apps_picker.png")
        composeTestRule.onNode(hasText("Gallery") and isSelectable()).performClick()
        assertEquals(GALLERY_PACKAGE, FileOpenDefaultApps.getPackage(FileOpenAppCategory.IMAGE))
        composeTestRule.onNodeWithText("Gallery").assertIsDisplayed()
        composeTestRule.onNodeWithText("Images").performClick()
        composeTestRule.onNode(hasText("Gallery") and isSelectable()).assertIsSelected()
    }

    @Test
    fun askEveryTimeClearsApp() {
        FileOpenDefaultApps.setPackage(FileOpenAppCategory.IMAGE, GALLERY_PACKAGE)
        composeTestRule.onNodeWithText("Gallery").assertIsDisplayed()
        composeTestRule.onNodeWithText("Images").performClick()
        composeTestRule.onNode(hasText("Ask every time") and isSelectable()).performClick()
        assertEquals(null, FileOpenDefaultApps.getPackage(FileOpenAppCategory.IMAGE))
    }

    companion object {
        private const val GALLERY_PACKAGE = "com.example.gallery"
    }
}
