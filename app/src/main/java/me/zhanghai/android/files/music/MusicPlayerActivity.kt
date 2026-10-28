/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.music

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.app.Destination
import me.zhanghai.android.files.app.LocalNavigator
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.util.extraPathList

/**
 * The music player opened from the notification, or to play a song from the files or from another
 * app, with the other songs of its folder after it.
 */
class MusicPlayerActivity : AppActivity() {
    // What's left to play from an intent, once connected to the player.
    private var intentToPlay by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Not again when recreated.
        if (savedInstanceState == null) {
            intentToPlay = intent
        }
        super.onCreate(savedInstanceState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentToPlay = intent
    }

    @Composable
    override fun Content() {
        val controller = rememberMusicController()
        val intent = intentToPlay
        LaunchedEffect(controller, intent) {
            if (controller == null || intent == null) {
                return@LaunchedEffect
            }
            intentToPlay = null
            val items = intent.toMusicItems()
            if (items.isNotEmpty()) {
                controller.playItems(items, intent.getIntExtra(EXTRA_POSITION, 0))
            }
        }
        MusicPlayerScreen(controller, onClose = ::finish)
    }

    private fun Intent.toMusicItems(): List<MusicItem> {
        val paths = extraPathList
        if (paths.isNotEmpty()) {
            return paths.map { it.toMusicItem() }
        }
        val uri = data ?: return emptyList()
        return listOf(MusicItem(uri.toString(), uri, uri.lastPathSegment.orEmpty()))
    }

    private fun Path.toMusicItem(): MusicItem {
        val uri = fileProviderUri
        return MusicItem(uri.toString(), uri, fileName?.toString().orEmpty())
    }

    companion object {
        private val EXTRA_POSITION = "${MusicPlayerActivity::class.java.name}.extra.POSITION"

        /** Plays [paths] from the one at [position]. */
        fun putExtras(intent: Intent, paths: List<Path>, position: Int) {
            intent.extraPathList = paths
            intent.putExtra(EXTRA_POSITION, position)
        }
    }
}

/** The music player, over the music library. */
@Parcelize
data object MusicPlayerDestination : Destination {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        MusicPlayerScreen(rememberMusicController(), onClose = { navigator.goBack() })
    }
}
