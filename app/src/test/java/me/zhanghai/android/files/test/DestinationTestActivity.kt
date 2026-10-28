/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.test

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.core.content.IntentCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.app.Destination
import org.robolectric.Shadows.shadowOf

/** Shows the destination it's launched with as its own screen, like the activities showing one. */
class DestinationTestActivity : AppActivity() {
    @Composable
    override fun Content() {
        IntentCompat.getParcelableExtra(intent, EXTRA_DESTINATION, Destination::class.java)!!
            .Content()
    }

    companion object {
        private const val EXTRA_DESTINATION = "destination"

        /**
         * Launches an activity showing [destination], for tests setting up the device first, or
         * recreating the activity. It's launched for a result, so that the scenario tells once
         * it's closed.
         */
        fun launch(destination: Destination): ActivityScenario<DestinationTestActivity> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            // Only the tests have this activity.
            shadowOf(context.packageManager).addOrUpdateActivity(
                ActivityInfo().apply {
                    name = DestinationTestActivity::class.java.name
                    packageName = context.packageName
                }
            )
            return ActivityScenario.launchActivityForResult(
                Intent(context, DestinationTestActivity::class.java)
                    .putExtra(EXTRA_DESTINATION, destination)
            )
        }
    }
}
