/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

// The replacement, FragmentStateAdapter, is for ViewPager2. This adapter's only caller
// (FilePropertiesDialogFragment) uses TabLayout.setupWithViewPager(), the classic ViewPager (v1)
// integration, so migrating this adapter alone would not be enough: it would also mean swapping the
// underlying ViewPager widget and its TabLayout wiring, a real UI change beyond this warning.
// File-level so this also covers the FragmentPagerAdapter import itself, not just the class using it.
@file:Suppress("DEPRECATION")

package me.zhanghai.android.files.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter

class TabFragmentPagerAdapter(
    fragmentManager: FragmentManager,
    private vararg val tabs: Pair<CharSequence?, () -> Fragment>
) : FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
    override fun getItem(position: Int): Fragment = tabs[position].second()

    override fun getCount(): Int = tabs.size

    override fun getPageTitle(position: Int): CharSequence? = tabs[position].first
}
