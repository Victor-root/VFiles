/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.app

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.test.AppProviderRule
import me.zhanghai.android.files.theme.AppTheme
import me.zhanghai.android.files.theme.night.NightMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ScreenStackTest {
    private val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(AppProviderRule()).around(composeTestRule)

    @Before
    fun setUp() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        navigators.clear()
        lifecycles.clear()
        viewModels.clear()
    }

    private fun show(rootHasBackHandler: Boolean = false) {
        composeTestRule.setContent {
            AppTheme { ScreenStack { TestScreen("Root", rootHasBackHandler) } }
        }
    }

    private fun navigate(from: String, destination: TestDestination) {
        composeTestRule.runOnIdle { navigators.getValue(from).navigate(destination) }
        composeTestRule.waitForIdle()
    }

    private fun goBack(from: String) {
        composeTestRule.runOnIdle { navigators.getValue(from).goBack() }
        composeTestRule.waitForIdle()
    }

    private fun pressBack() {
        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
    }

    private fun isShown(name: String): Boolean =
        composeTestRule.onAllNodes(hasText("$name screen"))
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun opensAndClosesScreens() {
        show()
        composeTestRule.onNodeWithText("Root screen").assertIsDisplayed()
        navigate("Root", TestDestination("A"))
        composeTestRule.onNodeWithText("A screen").assertIsDisplayed()
        assertFalse(isShown("Root"))
        pressBack()
        composeTestRule.onNodeWithText("Root screen").assertIsDisplayed()
        assertFalse(isShown("A"))
    }

    @Test
    fun keepsTheStateOfTheScreensBelow() {
        show()
        composeTestRule.onNodeWithText("Root count 0").performClick()
        composeTestRule.onNodeWithText("Root count 1").assertIsDisplayed()
        navigate("Root", TestDestination("A"))
        pressBack()
        // Not even saved and restored, since the screen stays composed.
        composeTestRule.onNodeWithText("Root count 1").assertIsDisplayed()
    }

    @Test
    fun stopsTheScreensBelow() {
        show()
        assertEquals(Lifecycle.State.RESUMED, lifecycles.getValue("Root").currentState)
        navigate("Root", TestDestination("A"))
        assertEquals(Lifecycle.State.CREATED, lifecycles.getValue("Root").currentState)
        assertEquals(Lifecycle.State.RESUMED, lifecycles.getValue("A").currentState)
        val lifecycleA = lifecycles.getValue("A")
        pressBack()
        assertEquals(Lifecycle.State.RESUMED, lifecycles.getValue("Root").currentState)
        assertEquals(Lifecycle.State.DESTROYED, lifecycleA.currentState)
    }

    @Test
    fun keepsTheScreenBelowADialogShown() {
        show()
        navigate("Root", TestDestination("Dialog", isDialog = true))
        composeTestRule.onNodeWithText("Root screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dialog screen").assertIsDisplayed()
        assertEquals(Lifecycle.State.STARTED, lifecycles.getValue("Root").currentState)
        assertEquals(Lifecycle.State.RESUMED, lifecycles.getValue("Dialog").currentState)
        pressBack()
        assertFalse(isShown("Dialog"))
        assertEquals(Lifecycle.State.RESUMED, lifecycles.getValue("Root").currentState)
    }

    @Test
    fun goBackClosesItsOwnScreen() {
        show()
        navigate("Root", TestDestination("A"))
        navigate("A", TestDestination("B"))
        // Like an activity finishing itself while another one is above it.
        goBack("A")
        composeTestRule.onNodeWithText("B screen").assertIsDisplayed()
        pressBack()
        composeTestRule.onNodeWithText("Root screen").assertIsDisplayed()
    }

    @Test
    fun replaceOpensInPlaceOfTheScreen() {
        show()
        navigate("Root", TestDestination("Dialog", isDialog = true))
        composeTestRule.runOnIdle {
            navigators.getValue("Dialog").replace(TestDestination("A"))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("A screen").assertIsDisplayed()
        assertFalse(isShown("Dialog"))
        pressBack()
        composeTestRule.onNodeWithText("Root screen").assertIsDisplayed()
    }

    @Test
    fun goBackFromTheActivityScreenFinishesTheActivity() {
        show()
        goBack("Root")
        assertTrue(composeTestRule.activity.isFinishing)
    }

    @Test
    fun backHandlersOfTheScreenComeFirst() {
        show()
        navigate("Root", TestDestination("A", hasBackHandler = true))
        pressBack()
        // Handled by the screen instead of closing it.
        composeTestRule.onNodeWithText("A back pressed").assertIsDisplayed()
        composeTestRule.onNodeWithText("A screen").assertIsDisplayed()
    }

    @Test
    fun backHandlersOfTheScreensBelowAreIgnored() {
        show(rootHasBackHandler = true)
        navigate("Root", TestDestination("A"))
        pressBack()
        composeTestRule.onNodeWithText("Root screen").assertIsDisplayed()
        assertFalse(isShown("Root back pressed"))
    }

    @Test
    fun eachScreenHasItsOwnViewModels() {
        show()
        navigate("Root", TestDestination("A"))
        val viewModelA = viewModels.getValue("A")
        navigate("A", TestDestination("B"))
        assertNotSame(viewModelA, viewModels.getValue("B"))
        assertNotSame(viewModels.getValue("Root"), viewModelA)
        pressBack()
        pressBack()
        assertTrue(viewModelA.isCleared)
        assertFalse(viewModels.getValue("Root").isCleared)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun restoresFocusOnceShownAgain() {
        show()
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val rootButton = composeTestRule.onNodeWithText("Root count 0")
        rootButton.performSemanticsAction(SemanticsActions.RequestFocus)
        rootButton.assertIsFocused()
        navigate("Root", TestDestination("A"))
        // Like a new activity, the screen opened gets the focus, so that keys never go to the one
        // hidden below.
        composeTestRule.onNodeWithText("A count 0").assertIsFocused()
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("A count 1").assertIsFocused()
        pressBack()
        composeTestRule.onNodeWithText("Root count 0").assertIsFocused()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun restoresFocusWhenGoingBackWhileOpening() {
        show()
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val rootButton = composeTestRule.onNodeWithText("Root count 0")
        rootButton.performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnIdle { navigators.getValue("Root").navigate(TestDestination("A")) }
        // Before the screen opened is entirely shown.
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        assertFalse(isShown("A"))
        rootButton.assertIsFocused()
    }

    @Test
    fun goingBackSeveralScreensDoesNotShowTheOnesBetween() {
        show()
        navigate("Root", TestDestination("A"))
        navigate("A", TestDestination("B"))
        val lifecycleA = lifecycles.getValue("A")
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnIdle { navigators.getValue("B").goBack(2) }
        // While B closes, A closes with it without being shown again.
        composeTestRule.mainClock.advanceTimeBy(100)
        assertFalse(isShown("A"))
        assertEquals(Lifecycle.State.CREATED, lifecycleA.currentState)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Root screen").assertIsDisplayed()
        assertEquals(Lifecycle.State.DESTROYED, lifecycleA.currentState)
        assertEquals(Lifecycle.State.RESUMED, lifecycles.getValue("Root").currentState)
    }

    @Test
    fun quickSecondBackReachesTheScreenBelow() {
        show()
        navigate("Root", TestDestination("A"))
        navigate("A", TestDestination("B"))
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Root screen").assertIsDisplayed()
        assertFalse(isShown("A"))
    }
}

@RunWith(RobolectricTestRunner::class)
class ScreenStackRecreationTest {
    @get:Rule
    val appProviderRule = AppProviderRule()

    @Before
    fun setUp() {
        Settings.NIGHT_MODE.putValue(NightMode.OFF)
        viewModels.clear()
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager).addOrUpdateActivity(
            ActivityInfo().apply {
                name = TestActivity::class.java.name
                packageName = context.packageName
            }
        )
    }

    @Test
    fun keepsTheScreensAndTheirViewModelsAcrossRecreation() {
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { navigators.getValue("Root").navigate(TestDestination("A")) }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val viewModelA = viewModels.getValue("A")
            scenario.recreate()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertSame(viewModelA, viewModels.getValue("A"))
            assertFalse(viewModelA.isCleared)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertTrue(viewModelA.isCleared)
        }
    }

    @Test
    fun backHandlersOfTheScreenStillComeFirstAfterTheAppWasInTheBackground() {
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                navigators.getValue("Root")
                    .navigate(TestDestination("A", hasBackHandler = true))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            // Handled by the screen instead of closing it.
            assertEquals(Lifecycle.State.RESUMED, lifecycles.getValue("A").currentState)
        }
    }
}

class TestActivity : AppActivity() {
    @Composable
    override fun Content() {
        TestScreen("Root")
    }
}

@Parcelize
private data class TestDestination(
    val name: String,
    override val isDialog: Boolean = false,
    val hasBackHandler: Boolean = false
) : Destination {
    @Composable
    override fun Content() {
        TestScreen(name, hasBackHandler)
    }
}

private val navigators = mutableMapOf<String, Navigator>()
private val lifecycles = mutableMapOf<String, Lifecycle>()
private val viewModels = mutableMapOf<String, TestViewModel>()

private class TestViewModel : ViewModel() {
    var isCleared = false

    override fun onCleared() {
        isCleared = true
    }
}

@Composable
private fun TestScreen(name: String, hasBackHandler: Boolean = false) {
    navigators[name] = LocalNavigator.current
    lifecycles[name] = LocalLifecycleOwner.current.lifecycle
    viewModels[name] = viewModel { TestViewModel() }
    var count by remember { mutableIntStateOf(0) }
    var backPressCount by remember { mutableIntStateOf(0) }
    BackHandler(enabled = hasBackHandler) { ++backPressCount }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            Text("$name screen")
            Button(onClick = { ++count }) { Text("$name count $count") }
            if (backPressCount > 0) {
                Text("$name back pressed")
            }
        }
    }
}
