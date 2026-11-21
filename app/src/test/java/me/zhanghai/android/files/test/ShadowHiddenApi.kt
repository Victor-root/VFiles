/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.test

import me.zhanghai.android.files.hiddenapi.HiddenApi
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/** The native library isn't built for the JVM, and there are no hidden API checks to disable. */
@Implements(HiddenApi::class)
class ShadowHiddenApi {
    @Implementation
    fun disableHiddenApiChecks() {}
}
