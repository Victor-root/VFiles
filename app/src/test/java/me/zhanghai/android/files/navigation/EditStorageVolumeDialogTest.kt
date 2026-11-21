/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.storage.StorageVolumeCustomization
import me.zhanghai.android.files.storage.StorageVolumeCustomizations
import me.zhanghai.android.files.storage.VolumeIconType
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.test.DestinationTestActivity
import me.zhanghai.android.files.theme.night.NightMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import android.provider.Settings as AndroidSettings

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditStorageVolumeDialogTest {
    private val composeTestRule = createEmptyComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    private lateinit var activity: DestinationTestActivity

    @Before
    fun resetSettings() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        Settings.STORAGE_VOLUME_CUSTOMIZATIONS.putValue(emptyList())
    }

    private fun launch() {
        DestinationTestActivity.launch(EditStorageVolumeDestination(UUID, "Kingston"))
            .onActivity { activity = it }
    }

    @Test
    fun showsCurrentCustomization() {
        StorageVolumeCustomizations.set(UUID, "Photos", VolumeIconType.USB)
        launch()
        composeTestRule.onNodeWithText("Removable volume").assertIsDisplayed()
        composeTestRule.onNodeWithText("Photos").assertIsDisplayed()
        composeTestRule.onNodeWithText("USB drive").assertIsSelected()
        captureScreenRoboImage("build/screenshots/edit_storage_volume_light.png")
        Settings.NIGHT_MODE.putValue(NightMode.ON)
        composeTestRule.waitForIdle()
        captureScreenRoboImage("build/screenshots/edit_storage_volume_dark.png")
    }

    @Test
    fun savesNameAndIcon() {
        launch()
        composeTestRule.onNodeWithText("Automatic").assertIsSelected()
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("Photos")
        composeTestRule.onNodeWithText("SD card").performClick()
        composeTestRule.onNodeWithText("OK").performClick()
        assertEquals(
            StorageVolumeCustomization(UUID, "Photos", VolumeIconType.SD_CARD),
            StorageVolumeCustomizations.get(UUID)
        )
        assertTrue(activity.isFinishing)
    }

    @Test
    fun ejectOpensStorageSettings() {
        launch()
        composeTestRule.onNodeWithText("Eject").performClick()
        val intent = shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .nextStartedActivity
        assertEquals(AndroidSettings.ACTION_INTERNAL_STORAGE_SETTINGS, intent.action)
        assertTrue(activity.isFinishing)
    }

    companion object {
        private const val UUID = "1234-ABCD"
    }
}
