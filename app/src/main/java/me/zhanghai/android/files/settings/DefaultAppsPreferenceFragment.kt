/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R
import me.zhanghai.android.files.ui.PreferenceFragmentCompat

class DefaultAppsPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        val context = requireContext()
        val screen = preferenceManager.createPreferenceScreen(context)
        for (category in FileOpenAppCategory.entries) {
            val preference = Preference(context).apply {
                key = category.name
                setTitle(category.titleRes)
                isPersistent = false
                isIconSpaceReserved = false
                summary = summaryFor(category)
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    showAppPicker(category)
                    true
                }
            }
            screen.addPreference(preference)
        }
        preferenceScreen = screen
    }

    override fun onResume() {
        super.onResume()

        // Refresh in case a chosen app was uninstalled while we were away.
        for (category in FileOpenAppCategory.entries) {
            findPreference<Preference>(category.name)?.summary = summaryFor(category)
        }
    }

    private fun summaryFor(category: FileOpenAppCategory): String =
        FileOpenDefaultApps.getPackage(category)?.let { appLabel(it) }
            ?: getString(R.string.settings_default_apps_ask)

    private fun appLabel(packageName: String): String? =
        try {
            val packageManager = requireContext().packageManager
            packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

    private fun showAppPicker(category: FileOpenAppCategory) {
        val context = requireContext()
        val packageManager = context.packageManager
        val queryIntent = Intent(Intent.ACTION_VIEW).setType(category.queryMimeType)
        val apps = packageManager.queryIntentActivities(queryIntent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .mapNotNull { packageName -> appLabel(packageName)?.let { packageName to it } }
            .sortedBy { it.second.lowercase() }
        // First entry clears the choice ("ask every time"); the rest are the candidate apps.
        val labels = (listOf(getString(R.string.settings_default_apps_ask)) + apps.map { it.second })
            .toTypedArray()
        val current = FileOpenDefaultApps.getPackage(category)
        val checkedItem = if (current == null) {
            0
        } else {
            apps.indexOfFirst { it.first == current }.takeIf { it >= 0 }?.plus(1) ?: 0
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(category.titleRes)
            .setSingleChoiceItems(labels, checkedItem) { dialog, which ->
                FileOpenDefaultApps.setPackage(category, if (which == 0) null else apps[which - 1].first)
                findPreference<Preference>(category.name)?.summary = summaryFor(category)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
