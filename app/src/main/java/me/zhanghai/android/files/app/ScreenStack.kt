/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.app

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.util.UUID

private const val TRANSITION_DURATION_MILLIS = 300

// How far a screen slides in or out, relative to its width.
private const val TRANSITION_SLIDE_FRACTION = 0.1f

/** An opened screen, or the screen of the activity itself without [destination]. */
@Parcelize
internal class ScreenEntry(
    val destination: Destination?,
    val id: String = UUID.randomUUID().toString()
) : Parcelable

private val ENTRIES_SAVER = listSaver<SnapshotStateList<ScreenEntry>, ScreenEntry>(
    save = { it.toList() },
    restore = { it.toMutableStateList() }
)

/**
 * The screens opened in the activity, above [rootContent], its own screen.
 *
 * Like the activities they replace, the screens below the one on top stay composed with their
 * state, scroll position and focus, but they are stopped, not laid out and so neither drawn nor
 * focusable or touchable. Each screen has its own view models, which outlive configuration changes.
 */
@Composable
internal fun ScreenStack(rootContent: @Composable () -> Unit) {
    val activity = LocalActivity.current!!
    val entries = rememberSaveable(saver = ENTRIES_SAVER) { mutableStateListOf(ScreenEntry(null)) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val viewModelStores = viewModel<ScreenViewModelStores>()
    val parentLifecycle = LocalLifecycleOwner.current.lifecycle
    val focusManager = LocalFocusManager.current
    val inputModeManager = LocalInputModeManager.current
    // The screens shown, which also include the ones closing while they animate out.
    val states = remember {
        // Like the screens closing when the activity was recreated, whose view models remain.
        viewModelStores.retain(entries.mapTo(mutableSetOf()) { it.id })
        entries.map { ScreenEntryState(it, parentLifecycle, isEntered = true) }
            .toMutableStateList()
    }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(entries) {
        snapshotFlow { entries.toList() }.collect { newEntries ->
            val newIds = newEntries.mapTo(mutableSetOf()) { it.id }
            for (state in states.filter { !it.isLeaving && it.entry.id !in newIds }) {
                state.isLeaving = true
                // Its state is kept while it's shown closing, but isn't saved anymore.
                saveableStateHolder.removeState(state.entry.id)
                coroutineScope.launch {
                    if (!state.isDialog) {
                        state.progress.animateTo(0f, tween(TRANSITION_DURATION_MILLIS))
                    }
                    states -= state
                    viewModelStores.remove(state.entry.id)
                    state.lifecycleOwner.destroy()
                }
            }
            val shownIds = states.mapTo(mutableSetOf()) { it.entry.id }
            for (entry in newEntries.filter { it.id !in shownIds }) {
                val isDialog = entry.destination?.isDialog == true
                val state = ScreenEntryState(entry, parentLifecycle, isEntered = isDialog)
                if (isDialog) {
                    states += state
                    continue
                }
                // The screens below are about to be covered, so their focus is kept to be restored
                // when they are shown again.
                val coveredStates = states.filter { !it.isLeaving }
                for (coveredState in coveredStates) {
                    if (coveredState.focusRequester.saveFocusedChild()) {
                        coveredState.hasSavedFocus = true
                    }
                }
                states += state
                coroutineScope.launch {
                    state.progress.animateTo(1f, tween(TRANSITION_DURATION_MILLIS))
                    state.isEntered = true
                }
                coroutineScope.launch {
                    // Like a new activity, the screen gets the focus with keys once it's composed
                    // and laid out, unless it took it itself, and the screens below never keep it,
                    // since they are still shown and could otherwise get it while it animates in.
                    withFrameNanos {}
                    withFrameNanos {}
                    if (state.hasFocus || state.isLeaving) {
                        return@launch
                    }
                    val isFocused = inputModeManager.inputMode == InputMode.Keyboard &&
                        state.focusRequester.requestFocus()
                    if (!isFocused && coveredStates.any { it.hasFocus }) {
                        focusManager.clearFocus(force = true)
                    }
                }
            }
        }
    }
    // The screens below one entirely shown over them are hidden, and stay so while they close with
    // it, like when several screens close at once. Those below one opening over them, and those
    // closing, can't take the focus anymore, like when a drawer closing focuses its screen.
    val openingIndex = states.indexOfLast { !it.isLeaving && !it.isDialog }
    val topIndex = states.indexOfLast { !it.isLeaving }
    // Composed before the screens, so that their own handlers come first. It closes the last
    // screen opened right away, so that a quick second back closes the one below it.
    BackHandler(enabled = entries.size > 1) {
        if (entries.size > 1) {
            entries.removeAt(entries.lastIndex)
        } else {
            activity.finish()
        }
    }
    Layout(
        content = {
            for ((index, state) in states.withIndex()) {
                val isHidden = (index + 1..states.lastIndex).any {
                    val aboveState = states[it]
                    !aboveState.isDialog && aboveState.isEntered &&
                        (!aboveState.isLeaving || state.isLeaving)
                }
                // A closing screen is stopped like a finishing activity, so that it neither handles
                // back nor affects the system bars anymore.
                val maxLifecycleState = when {
                    isHidden || state.isLeaving -> Lifecycle.State.CREATED
                    index == topIndex -> Lifecycle.State.RESUMED
                    else -> Lifecycle.State.STARTED
                }
                val isCovered = state.isLeaving || index < openingIndex
                key(state.entry.id) {
                    val navigator = remember { Navigator(entries, state.entry.id, activity) }
                    ScreenEntryContent(
                        state, isHidden, isCovered, maxLifecycleState, navigator,
                        saveableStateHolder, viewModelStores, rootContent
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}

@Composable
private fun ScreenEntryContent(
    state: ScreenEntryState,
    isHidden: Boolean,
    isCovered: Boolean,
    maxLifecycleState: Lifecycle.State,
    navigator: Navigator,
    saveableStateHolder: SaveableStateHolder,
    viewModelStores: ScreenViewModelStores,
    rootContent: @Composable () -> Unit
) {
    SideEffect {
        state.isCovered = isCovered
        state.lifecycleOwner.maxState = maxLifecycleState
    }
    // As soon as the screen over it closes, even while it was still opening.
    LaunchedEffect(isCovered) {
        if (!isCovered && state.hasSavedFocus) {
            state.hasSavedFocus = false
            // Lets it be laid out again first.
            withFrameNanos {}
            state.focusRequester.restoreFocusedChild()
        }
    }
    val destination = state.entry.destination
    val viewModelStoreOwner = if (destination != null) {
        remember(state) { viewModelStores.getOwner(state.entry.id) }
    } else {
        LocalViewModelStoreOwner.current!!
    }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Layout(
        content = {
            CompositionLocalProvider(
                LocalNavigator provides navigator,
                LocalLifecycleOwner provides state.lifecycleOwner,
                LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                saveableStateHolder.SaveableStateProvider(state.entry.id) {
                    Layout(
                        content = { destination?.Content() ?: rootContent() },
                        modifier = Modifier
                            .focusRequester(state.focusRequester)
                            .onFocusChanged { state.hasFocus = it.hasFocus }
                            .focusProperties {
                                onEnter = { if (state.isCovered) cancelFocusChange() }
                            }
                            .focusGroup()
                    ) { measurables, constraints ->
                        val placeables = measurables.map { it.measure(constraints) }
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeables.forEach { it.place(0, 0) }
                        }
                    }
                }
            }
        },
        modifier = Modifier
            .graphicsLayer {
                val progress = state.progress.value
                alpha = progress
                val slide = (1 - progress) * TRANSITION_SLIDE_FRACTION * size.width
                translationX = if (isRtl) -slide else slide
            }
            // Touches never go through a screen to the one below, and a closing screen ignores
            // them.
            .pointerInput(state.isLeaving) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (state.isLeaving) {
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            // Nor is it read by accessibility services.
            .then(if (isHidden) Modifier.clearAndSetSemantics {} else Modifier)
    ) { measurables, constraints ->
        // A hidden screen is neither measured nor placed, and so neither drawn nor focusable.
        if (isHidden) {
            layout(0, 0) {}
        } else {
            val placeables = measurables.map { it.measure(constraints) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEach { it.place(0, 0) }
            }
        }
    }
}

private class ScreenEntryState(
    val entry: ScreenEntry,
    parentLifecycle: Lifecycle,
    isEntered: Boolean
) {
    val isDialog: Boolean = entry.destination?.isDialog == true

    val progress = Animatable(if (isEntered) 1f else 0f)

    var isEntered by mutableStateOf(isEntered)

    var isLeaving by mutableStateOf(false)

    var isCovered = false

    val lifecycleOwner = ScreenLifecycleOwner(parentLifecycle)

    val focusRequester = FocusRequester()

    var hasFocus = false

    var hasSavedFocus = false
}

/** The lifecycle of a screen, which follows the activity but stops while the screen is hidden. */
private class ScreenLifecycleOwner(private val parentLifecycle: Lifecycle) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = registry

    var maxState = Lifecycle.State.RESUMED
        set(value) {
            field = value
            updateState()
        }

    private val observer = LifecycleEventObserver { _, _ -> updateState() }

    init {
        parentLifecycle.addObserver(observer)
    }

    private fun updateState() {
        if (registry.currentState == Lifecycle.State.DESTROYED) {
            return
        }
        registry.currentState = minOf(parentLifecycle.currentState, maxState)
    }

    fun destroy() {
        parentLifecycle.removeObserver(observer)
        if (registry.currentState != Lifecycle.State.INITIALIZED) {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}

/** The view models of the opened screens, kept by the activity across configuration changes. */
internal class ScreenViewModelStores : ViewModel() {
    private val stores = mutableMapOf<String, ViewModelStore>()

    fun getOwner(id: String): ViewModelStoreOwner {
        val store = stores.getOrPut(id) { ViewModelStore() }
        return object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }

    fun remove(id: String) {
        stores.remove(id)?.clear()
    }

    fun retain(ids: Set<String>) {
        for (id in stores.keys - ids) {
            remove(id)
        }
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}
