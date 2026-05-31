/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.databinding.OnboardingActivityBinding
import me.zhanghai.android.files.databinding.OnboardingPermissionItemBinding
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.createIntent

class OnboardingActivity : AppActivity() {

    private lateinit var binding: OnboardingActivityBinding

    private var finishing = false

    private val pages = listOf(
        OnboardingPage.FilesAccess,
        OnboardingPage.Notifications,
        OnboardingPage.InstallApps
    )

    private val requestNotificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    private val requestSystemSettingsLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = OnboardingActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindCard(binding.cardFiles, OnboardingPage.FilesAccess)
        bindCard(binding.cardNotifications, OnboardingPage.Notifications)
        bindCard(binding.cardInstall, OnboardingPage.InstallApps)

        binding.continueButton.setOnClickListener { finishOnboarding() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun bindCard(card: OnboardingPermissionItemBinding, page: OnboardingPage) {
        card.iconImage.setImageResource(page.iconRes)
        card.titleText.setText(page.titleRes)
        card.descriptionText.setText(page.descriptionRes)
        card.badgeText.setText(
            if (page.isRequired) R.string.onboarding_required else R.string.onboarding_optional
        )
        card.grantButton.setText(R.string.onboarding_grant)
        card.grantButton.setOnClickListener { page.requestGrant(this) }
        card.grantedIcon.setColorFilter(
            ContextCompat.getColor(this, android.R.color.holo_green_dark)
        )
    }

    private fun refresh() {
        if (finishing) {
            return
        }

        updateCard(binding.cardFiles, OnboardingPage.FilesAccess)
        updateCard(binding.cardNotifications, OnboardingPage.Notifications)
        updateCard(binding.cardInstall, OnboardingPage.InstallApps)

        val requiredGranted = pages.filter { it.isRequired }.all { it.isGranted(this) }
        binding.continueButton.isEnabled = requiredGranted

        // Everything granted — continue straight to the app.
        if (pages.all { it.isGranted(this) }) {
            finishOnboarding()
        }
    }

    private fun updateCard(card: OnboardingPermissionItemBinding, page: OnboardingPage) {
        val granted = page.isGranted(this)
        card.grantButton.isVisible = !granted
        card.grantedIcon.isVisible = granted
    }

    private fun finishOnboarding() {
        if (finishing) {
            return
        }
        finishing = true
        Settings.ONBOARDING_COMPLETED.putValue(true)
        startActivity(FileListActivity::class.createIntent())
        finish()
    }

    // ── permission launchers (called by pages) ────────────────────────────────

    fun openStorageSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        } else {
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        }
        requestSystemSettingsLauncher.launch(intent)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun requestNotificationPermission() {
        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun openInstallAppsSettings() {
        val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        requestSystemSettingsLauncher.launch(intent)
    }

    // ── pages ─────────────────────────────────────────────────────────────────

    sealed class OnboardingPage(
        @DrawableRes val iconRes: Int,
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int,
        val isRequired: Boolean
    ) {
        abstract fun isGranted(context: Context): Boolean
        abstract fun requestGrant(activity: OnboardingActivity)

        object FilesAccess : OnboardingPage(
            iconRes = R.drawable.sd_card_icon_white_24dp,
            titleRes = R.string.onboarding_files_title,
            descriptionRes = R.string.onboarding_files_description,
            isRequired = true
        ) {
            override fun isGranted(context: Context): Boolean =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                }

            override fun requestGrant(activity: OnboardingActivity) {
                activity.openStorageSettings()
            }
        }

        object Notifications : OnboardingPage(
            iconRes = R.drawable.notification_icon_white_24dp,
            titleRes = R.string.onboarding_notifications_title,
            descriptionRes = R.string.onboarding_notifications_description,
            isRequired = false
        ) {
            override fun isGranted(context: Context): Boolean =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

            override fun requestGrant(activity: OnboardingActivity) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activity.requestNotificationPermission()
                }
            }
        }

        object InstallApps : OnboardingPage(
            iconRes = R.drawable.install_unknown_apps_icon_white_24dp,
            titleRes = R.string.onboarding_install_apps_title,
            descriptionRes = R.string.onboarding_install_apps_description,
            isRequired = false
        ) {
            override fun isGranted(context: Context): Boolean =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.packageManager.canRequestPackageInstalls()
                } else {
                    true
                }

            override fun requestGrant(activity: OnboardingActivity) {
                activity.openInstallAppsSettings()
            }
        }
    }
}
