/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.about

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.ui.AppScaffold
import me.zhanghai.android.files.ui.LicensesDialog
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.startActivitySafe

class AboutActivity : AppActivity() {
    @Composable
    override fun Content() {
        AboutScreen()
    }
}

private val GITHUB_URI = "https://github.com/Victor-root/VFiles".toUri()
private val AUTHOR_URI = "https://github.com/Victor-root".toUri()

@Composable
private fun AboutScreen() {
    val context = LocalContext.current
    var isLicensesDialogShown by rememberSaveable { mutableStateOf(false) }
    AppScaffold(title = stringResource(R.string.about_title)) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AboutCard {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    leadingContent = { LauncherIcon() },
                    colors = transparentListItemColors()
                )
                AboutItem(
                    iconRes = R.drawable.information_icon_white_24dp,
                    title = stringResource(R.string.about_version_title),
                    subtitle = stringResource(R.string.app_version)
                )
                AboutItem(
                    iconRes = R.drawable.github_icon_white_24dp,
                    title = stringResource(R.string.about_github_title),
                    onClick = { context.startActivitySafe(GITHUB_URI.createViewIntent()) }
                )
                AboutItem(
                    iconRes = R.drawable.text_file_icon_white_24dp,
                    title = stringResource(R.string.about_licenses_title),
                    onClick = { isLicensesDialogShown = true }
                )
            }
            AboutCard {
                Text(
                    text = stringResource(R.string.about_author_title),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
                AboutItem(
                    iconRes = R.drawable.person_icon_white_24dp,
                    title = stringResource(R.string.about_author_name_title),
                    subtitle = stringResource(R.string.about_fork_subtitle),
                    onClick = { context.startActivitySafe(AUTHOR_URI.createViewIntent()) }
                )
                AboutItem(
                    iconRes = R.drawable.github_icon_white_24dp,
                    title = stringResource(R.string.about_author_github_title),
                    onClick = { context.startActivitySafe(AUTHOR_URI.createViewIntent()) }
                )
            }
        }
    }
    if (isLicensesDialogShown) {
        LicensesDialog(onDismissRequest = { isLicensesDialogShown = false })
    }
}

@Composable
private fun AboutCard(content: @Composable () -> Unit) {
    Card {
        Column(modifier = Modifier.padding(vertical = 8.dp)) { content() }
    }
}

@Composable
private fun AboutItem(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(title) },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(painter = painterResource(iconRes), contentDescription = null) },
        colors = transparentListItemColors()
    )
}

@Composable
private fun LauncherIcon() {
    val context = LocalContext.current
    // The launcher icon is an adaptive icon, which painterResource() doesn't support.
    val bitmap = remember(context) {
        ContextCompat.getDrawable(context, R.mipmap.launcher_icon)!!.toBitmap().asImageBitmap()
    }
    Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
}

@Composable
private fun transparentListItemColors() =
    ListItemDefaults.colors(containerColor = Color.Transparent)
