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
import android.os.Environment
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.createManageAppAllFilesAccessPermissionIntent
import me.zhanghai.android.files.util.isExternalStorageAccessGranted
import me.zhanghai.android.files.util.supportsExternalStorageManager

/** Asks for the permissions the app needs on its first launch, before showing the files. */
class OnboardingActivity : AppActivity() {
    private var finishing = false

    // Incremented when coming back from a permission request, so that grants are checked again.
    private var grantCheckCount by mutableIntStateOf(0)

    private val requestNotificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            grantCheckCount++
        }

    private val requestSystemSettingsLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            grantCheckCount++
        }

    // POST_NOTIFICATIONS only exists on Android 13+, below that there is nothing to ask for.
    private val pages = buildList {
        add(OnboardingPage.FilesAccess)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(OnboardingPage.Notifications)
        }
        add(OnboardingPage.InstallApps)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        LifecycleResumeEffect(Unit) {
            grantCheckCount++
            onPauseOrDispose {}
        }
        val grantedPages = remember(grantCheckCount) { pages.filter { it.isGranted(context) } }
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, top = 48.dp, end = 24.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.onboarding_welcome_title, stringResource(R.string.app_name)
                        ),
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(R.string.onboarding_welcome_description),
                        modifier = Modifier.padding(bottom = 32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    for (page in pages) {
                        PermissionCard(
                            page = page,
                            isGranted = page in grantedPages,
                            onGrant = { page.requestGrant(this@OnboardingActivity) }
                        )
                    }
                }
                // Only the required pages gate the button, the optional ones can be skipped.
                // Granting the last permission deliberately doesn't leave on its own: the file list
                // already skips this screen when there is nothing to ask for, so leaving here could
                // only happen mid-flow, without the user ever confirming.
                Button(
                    onClick = ::finishOnboarding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 24.dp),
                    enabled = pages.none { it.isRequired && it !in grantedPages }
                ) { Text(stringResource(R.string.onboarding_finish)) }
            }
        }
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

    fun openStorageSettings() {
        // Must follow the same condition as isExternalStorageAccessGranted(), otherwise a device
        // with no "All files access" page would be sent to a screen that does not exist while
        // being checked for the permission it was never asked for.
        val intent = if (Environment::class.supportsExternalStorageManager()) {
            Environment::class.createManageAppAllFilesAccessPermissionIntent(packageName)
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

    sealed class OnboardingPage(
        @param:DrawableRes val iconRes: Int,
        @param:StringRes val titleRes: Int,
        @param:StringRes val descriptionRes: Int,
        val isRequired: Boolean
    ) {
        open fun getDescription(context: Context): String = context.getString(descriptionRes)

        abstract fun isGranted(context: Context): Boolean
        abstract fun requestGrant(activity: OnboardingActivity)

        object FilesAccess : OnboardingPage(
            iconRes = R.drawable.sd_card_icon_white_24dp,
            titleRes = R.string.onboarding_files_title,
            descriptionRes = R.string.onboarding_files_description,
            isRequired = true
        ) {
            override fun getDescription(context: Context): String =
                context.getString(descriptionRes, context.getString(R.string.app_name))

            override fun isGranted(context: Context): Boolean =
                Environment::class.isExternalStorageAccessGranted()

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
            override fun getDescription(context: Context): String =
                context.getString(descriptionRes, context.getString(R.string.app_name))

            override fun isGranted(context: Context): Boolean =
                context.packageManager.canRequestPackageInstalls()

            override fun requestGrant(activity: OnboardingActivity) {
                activity.openInstallAppsSettings()
            }
        }
    }
}

@Composable
private fun PermissionCard(
    page: OnboardingActivity.OnboardingPage,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    val context = LocalContext.current
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(page.iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = stringResource(page.titleRes),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(
                            if (page.isRequired) {
                                R.string.onboarding_required
                            } else {
                                R.string.onboarding_optional
                            }
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = remember(page, context) { page.getDescription(context) },
                        modifier = Modifier.padding(top = 5.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (isGranted) {
                    Icon(
                        painter = painterResource(R.drawable.check_icon_white_24dp),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(32.dp)
                            .padding(4.dp),
                        tint = colorResource(android.R.color.holo_green_dark)
                    )
                }
            }
            if (!isGranted) {
                OutlinedButton(
                    onClick = onGrant,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 12.dp)
                ) { Text(stringResource(R.string.onboarding_grant)) }
            }
        }
    }
}
