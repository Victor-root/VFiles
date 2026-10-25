/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.test

import android.content.Context
import android.os.Environment
import android.os.UserHandle
import android.os.storage.StorageManager
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import me.zhanghai.android.files.app.AppProvider
import me.zhanghai.android.files.app.application
import org.junit.rules.ExternalResource
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.StorageVolumeBuilder
import java.io.File

/**
 * Robolectric doesn't create content providers, so run the app initialization ourselves. It
 * installs process-wide state like file system providers, so it only runs once per sandbox, and
 * later tests only get their new application instance.
 */
class AppProviderRule : ExternalResource() {
    override fun before() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // The app always has the primary storage volume.
        shadowOf(context.getSystemService<StorageManager>()).addStorageVolume(
            StorageVolumeBuilder(
                "emulated", File("/storage/emulated/0"), "Internal shared storage",
                UserHandle.getUserHandleForUid(0), Environment.MEDIA_MOUNTED
            )
                .setIsPrimary(true)
                .setIsEmulated(true)
                .build()
        )
        if (!isInitialized) {
            Robolectric.setupContentProvider(AppProvider::class.java)
            isInitialized = true
        } else {
            application = ApplicationProvider.getApplicationContext()
        }
    }

    companion object {
        private var isInitialized = false
    }
}
