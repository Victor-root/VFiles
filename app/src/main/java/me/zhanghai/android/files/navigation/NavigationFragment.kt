/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import java8.nio.file.Path
import me.zhanghai.android.files.databinding.NavigationFragmentBinding
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe


class NavigationFragment : Fragment(), NavigationItem.Listener {
    private lateinit var binding: NavigationFragmentBinding

    private lateinit var adapter: NavigationListAdapter

    lateinit var listener: Listener

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        NavigationFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.setHasFixedSize(true)
        // TODO: Needed?
        //binding.recyclerView.setItemAnimator(new NoChangeAnimationItemAnimator())
        val context = requireContext()
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = NavigationListAdapter(this, context)
        binding.recyclerView.adapter = adapter

        val viewLifecycleOwner = viewLifecycleOwner
        NavigationItemListLiveData.observe(viewLifecycleOwner) { onNavigationItemsChanged(it) }
        listener.observeCurrentPath(viewLifecycleOwner) { onCurrentPathChanged(it) }
    }

    private fun onNavigationItemsChanged(navigationItems: List<NavigationItem?>) {
        adapter.replace(navigationItems)
    }

    // Rebuild the items so the live free-space subtitles (e.g. "Internal storage") are recomputed.
    // Called when the drawer opens, so deleting files is reflected without restarting the app.
    fun refresh() {
        if (::adapter.isInitialized) {
            adapter.replace(navigationItems)
        }
    }

    // Recompute just the free-space subtitles in place (no flicker / focus loss). Used when the
    // sidebar is permanently visible, where there's no "drawer opened" moment to refresh on. May be
    // called before the view (and adapter) exist, so guard against that.
    fun refreshSubtitles() {
        if (::adapter.isInitialized) {
            adapter.notifySubtitleChanged()
        }
    }

    // Moves D-pad focus to the currently active (checked) row, or falls back to the first row.
    // Called when the drawer opens on Android TV so the cursor lands exactly where the user is,
    // e.g. on "Downloads" if that was the last browsed location.
    fun focusList(): Boolean {
        val recyclerView = binding.recyclerView
        val layoutManager = recyclerView.layoutManager ?: return recyclerView.requestFocus()
        // Find the position of the checked item (the one matching the current path).
        val targetPosition = (0 until adapter.itemCount).firstOrNull { pos ->
            val item = adapter.getItem(pos)
            item != null && item.isChecked(this)
        } ?: 0
        // Scroll it into view first, then request focus on its view holder.
        layoutManager.scrollToPosition(targetPosition)
        recyclerView.post {
            val vh = recyclerView.findViewHolderForAdapterPosition(targetPosition)
            vh?.itemView?.requestFocus() ?: recyclerView.requestFocus()
        }
        return true
    }

    private fun onCurrentPathChanged(path: Path) {
        adapter.notifyCheckedChanged()
    }

    override val currentPath: Path
        get() = listener.currentPath

    override fun navigateTo(path: Path) {
        listener.navigateTo(path)
    }

    override fun navigateToRoot(path: Path) {
        listener.navigateToRoot(path)
    }

    override fun launchIntent(intent: Intent) {
        startActivitySafe(intent)
    }

    override fun showToast(textRes: Int) {
        showToast(textRes, Toast.LENGTH_LONG)
    }

    override fun closeNavigationDrawer() {
        listener.closeNavigationDrawer()
    }

    interface Listener {
        val currentPath: Path
        fun navigateTo(path: Path)
        fun navigateToRoot(path: Path)
        fun navigateToDefaultRoot()
        fun observeCurrentPath(owner: LifecycleOwner, observer: (Path) -> Unit)
        fun closeNavigationDrawer()
    }
}
