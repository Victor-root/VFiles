/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.startActivitySafe
import org.xmlpull.v1.XmlPullParser

private class Notice(
    val name: String,
    val url: String?,
    val copyright: String?,
    @param:RawRes val licenseRes: Int?
)

@Composable
fun LicensesDialog(onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val notices = remember(context) { context.readNotices() }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.close)) }
        },
        title = { Text(stringResource(R.string.about_licenses_title)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items(notices, key = { it.name }) { NoticeItem(it) }
            }
        }
    )
}

@Composable
private fun NoticeItem(notice: Notice) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // A whole clickable title instead of an inline link, so that it's reachable with a D-pad.
        Column(
            modifier = if (notice.url != null) {
                Modifier.clickable {
                    context.startActivitySafe(Uri.parse(notice.url).createViewIntent())
                }
            } else {
                Modifier
            }
        ) {
            Text(text = notice.name, style = MaterialTheme.typography.bodyMedium)
            if (notice.url != null) {
                Text(
                    text = notice.url,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        val licenseText = remember(notice) {
            listOfNotNull(
                notice.copyright,
                notice.licenseRes?.let { context.resources.openRawResource(it).reader().readText() }
            ).joinToString("\n\n")
        }
        Text(
            text = licenseText,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(12.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun Context.readNotices(): List<Notice> =
    resources.openRawResource(R.raw.licenses).use { inputStream ->
        val parser = Xml.newPullParser().apply { setInput(inputStream, null) }
        val notices = mutableListOf<Notice>()
        var fields = mutableMapOf<String, String>()
        var tag: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    if (tag == "notice") {
                        fields = mutableMapOf()
                    }
                }
                XmlPullParser.TEXT -> tag?.let { fields[it] = parser.text.trim() }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "notice") {
                        notices += Notice(
                            fields.getValue("name"), fields["url"], fields["copyright"],
                            fields["license"]?.let { LICENSE_TEXTS.getValue(it) }
                        )
                    }
                    tag = null
                }
            }
        }
        notices
    }

private val LICENSE_TEXTS = mapOf(
    "Apache Software License 2.0" to R.raw.license_apache_2_0,
    "BSD 2-Clause License" to R.raw.license_bsd_2_clause,
    "BSD 3-Clause License" to R.raw.license_bsd_3_clause,
    "GNU General Public License 2.0" to R.raw.license_gpl_2_0,
    "GNU General Public License 3.0" to R.raw.license_gpl_3_0,
    "GNU Lesser General Public License 2.1" to R.raw.license_lgpl_2_1,
    "MIT License" to R.raw.license_mit,
    "Mozilla Public License 2.0" to R.raw.license_mpl_2_0
)
