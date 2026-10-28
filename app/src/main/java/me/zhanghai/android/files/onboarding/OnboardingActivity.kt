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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
                // Kept above the cards, so that moving the focus down to them with a D-pad doesn't
                // scroll it away.
                Text(
                    text = stringResource(
                        R.string.onboarding_welcome_title, stringResource(R.string.app_name)
                    ),
                    modifier = Modifier.padding(start = 24.dp, top = 32.dp, end = 24.dp),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = stringResource(R.string.onboarding_welcome_description),
                    modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    // On wide screens like TVs, the permissions are tiles side by side, so that
                    // they all fit without scrolling.
                    val isTiled = maxWidth >= TILED_MIN_WIDTH
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isTiled) {
                            Row(
                                modifier = Modifier.height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (page in pages) {
                                    PermissionTile(
                                        page = page,
                                        isGranted = page in grantedPages,
                                        onGrant = { page.requestGrant(this@OnboardingActivity) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                }
                            }
                        } else {
                            for (page in pages) {
                                PermissionCard(
                                    page = page,
                                    isGranted = page in grantedPages,
                                    onGrant = { page.requestGrant(this@OnboardingActivity) }
                                )
                            }
                        }
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
                        .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 24.dp)
                        .heightIn(min = 56.dp),
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
            iconRes = R.drawable.file_directory_icon,
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
    PermissionSurface(isGranted, Modifier.fillMaxWidth()) {
        // On medium screens, the action is next to the text, so that the cards are short.
        BoxWithConstraints {
            val isWide = maxWidth >= WIDE_CARD_MIN_WIDTH
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PermissionIcon(page)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        PermissionTitle(page)
                        if (isWide) {
                            PermissionDescription(page, Modifier.padding(top = 6.dp))
                        }
                    }
                    if (isWide) {
                        PermissionAction(isGranted, onGrant, Modifier.padding(start = 16.dp))
                    }
                }
                // On narrow screens, the text takes the whole width below the icon.
                if (!isWide) {
                    PermissionDescription(page, Modifier.padding(top = 12.dp))
                    PermissionAction(
                        isGranted = isGranted,
                        onGrant = onGrant,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionTile(
    page: OnboardingActivity.OnboardingPage,
    isGranted: Boolean,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier
) {
    PermissionSurface(isGranted, modifier) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PermissionIcon(page)
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = stringResource(page.titleRes),
                        style = MaterialTheme.typography.titleMedium
                    )
                    RequirementBadge(page.isRequired, Modifier.padding(top = 4.dp))
                }
            }
            PermissionDescription(page, Modifier.padding(top = 12.dp))
            // Keeps the actions of all tiles on the same line.
            Spacer(modifier = Modifier.weight(1f))
            PermissionAction(isGranted, onGrant, Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun PermissionSurface(
    isGranted: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (isGranted) GRANTED_COLOR else MaterialTheme.colorScheme.outlineVariant
        ),
        content = content
    )
}

@Composable
private fun PermissionIcon(page: OnboardingActivity.OnboardingPage) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = TONAL_ALPHA),
                RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(page.iconRes),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PermissionTitle(page: OnboardingActivity.OnboardingPage) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(page.titleRes),
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.titleMedium
        )
        RequirementBadge(
            isRequired = page.isRequired,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun PermissionDescription(
    page: OnboardingActivity.OnboardingPage,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Text(
        text = remember(page, context) { page.getDescription(context) },
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun PermissionAction(
    isGranted: Boolean,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isGranted) {
        GrantedBadge(modifier)
    } else {
        FilledTonalButton(
            onClick = onGrant,
            modifier = modifier,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = TONAL_ALPHA),
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) { Text(stringResource(R.string.onboarding_grant)) }
    }
}

@Composable
private fun RequirementBadge(isRequired: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (isRequired) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (isRequired) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Text(
            text = stringResource(
                if (isRequired) R.string.onboarding_required else R.string.onboarding_optional
            ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun GrantedBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .background(GRANTED_COLOR.copy(alpha = TONAL_ALPHA), CircleShape)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.check_icon_white_24dp),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = GRANTED_COLOR
        )
        Text(
            text = stringResource(R.string.onboarding_granted),
            modifier = Modifier.padding(start = 8.dp),
            color = GRANTED_COLOR,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

private val WIDE_CARD_MIN_WIDTH = 600.dp
private val TILED_MIN_WIDTH = 840.dp
private val GRANTED_COLOR = Color(0xFF22A667)
private const val TONAL_ALPHA = 0.12f
