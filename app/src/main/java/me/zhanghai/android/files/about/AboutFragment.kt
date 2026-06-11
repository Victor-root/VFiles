/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.about

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.AboutFragmentBinding
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.LicensesDialogFragment
import me.zhanghai.android.files.update.UpdateManager
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.startActivitySafe

class AboutFragment : Fragment() {
    private lateinit var binding: AboutFragmentBinding

    private var checkingUpdate = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        AboutFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        binding.gitHubLayout.setOnClickListener { startActivitySafe(GITHUB_URI.createViewIntent()) }
        binding.licensesLayout.setOnClickListener { LicensesDialogFragment.show(this) }
        binding.authorNameLayout.setOnClickListener {
            startActivitySafe(AUTHOR_RESUME_URI.createViewIntent())
        }
        binding.authorGitHubLayout.setOnClickListener {
            startActivitySafe(AUTHOR_GITHUB_URI.createViewIntent())
        }

        binding.updateLayout.setOnClickListener { onUpdateClicked() }
        // Reflect the latest known update state, and keep reflecting it if a background check lands
        // while the user is on this screen.
        Settings.UPDATE_AVAILABLE_VERSION.observe(viewLifecycleOwner) { refreshUpdateRow() }
        // Opening About is a good moment to refresh (still throttled to once every 12h).
        UpdateManager.checkInBackground()
    }

    private fun refreshUpdateRow() {
        if (checkingUpdate) {
            return
        }
        val release = UpdateManager.getStoredRelease()
        if (release != null) {
            binding.updateTitleText.setText(R.string.about_update_available_title)
            binding.updateSummaryText.text =
                getString(R.string.about_update_available_summary_format, release.versionName)
            binding.updateBadgeDot.isVisible = true
        } else {
            binding.updateTitleText.setText(R.string.about_check_for_update_title)
            binding.updateSummaryText.setText(R.string.about_update_summary_idle)
            binding.updateBadgeDot.isVisible = false
        }
    }

    private fun onUpdateClicked() {
        if (checkingUpdate) {
            return
        }
        val release = UpdateManager.getStoredRelease()
        if (release != null) {
            // Silent background download. The only thing the user sees is the system installer's
            // "Update?" prompt once the APK has been fetched.
            binding.updateBadgeDot.isVisible = false
            binding.updateSummaryText.setText(R.string.about_update_downloading)
            UpdateManager.downloadAndInstall(release) { ok ->
                if (!isAdded) {
                    return@downloadAndInstall
                }
                if (ok) {
                    refreshUpdateRow()
                } else {
                    binding.updateSummaryText.setText(R.string.about_update_download_failed)
                }
            }
        } else {
            checkingUpdate = true
            binding.updateTitleText.setText(R.string.about_check_for_update_title)
            binding.updateSummaryText.setText(R.string.about_update_checking)
            UpdateManager.forceCheck { result ->
                if (!isAdded) {
                    return@forceCheck
                }
                checkingUpdate = false
                if (result != null) {
                    refreshUpdateRow()
                } else {
                    binding.updateSummaryText.setText(R.string.about_update_up_to_date)
                }
            }
        }
    }

    companion object {
        private val GITHUB_URI = Uri.parse("https://github.com/Victor-root/MaterialFiles")
        private val AUTHOR_RESUME_URI = Uri.parse("https://github.com/Victor-root")
        private val AUTHOR_GITHUB_URI = Uri.parse("https://github.com/Victor-root")
    }
}
