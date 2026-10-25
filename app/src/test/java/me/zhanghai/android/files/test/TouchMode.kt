/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.test

import android.app.Activity
import android.view.ViewGroup

/**
 * Leaves touch mode like a D-pad does on a TV, so that clickable items become focusable. Key
 * events injected by Compose tests bypass the view root which would do it otherwise.
 */
fun Activity.leaveTouchMode() {
    findViewById<ViewGroup>(android.R.id.content).getChildAt(0).requestFocusFromTouch()
}
