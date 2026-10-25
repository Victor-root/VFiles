/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.test

import androidx.test.core.app.ApplicationProvider
import me.zhanghai.android.files.app.AppProvider
import me.zhanghai.android.files.app.application
import org.junit.rules.ExternalResource
import org.robolectric.Robolectric

/**
 * Robolectric doesn't create content providers, so run the app initialization ourselves. It
 * installs process-wide state like file system providers, so it only runs once per sandbox, and
 * later tests only get their new application instance.
 */
class AppProviderRule : ExternalResource() {
    override fun before() {
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
