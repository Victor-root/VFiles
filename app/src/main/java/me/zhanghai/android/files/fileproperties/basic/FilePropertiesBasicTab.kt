/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.basic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java8.nio.file.FileVisitResult
import java8.nio.file.FileVisitor
import java8.nio.file.Files
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.asFileSize
import me.zhanghai.android.files.file.fileSize
import me.zhanghai.android.files.file.formatLong
import me.zhanghai.android.files.filelist.getMimeTypeName
import me.zhanghai.android.files.filelist.name
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.fileproperties.FilePropertiesFileViewModel
import me.zhanghai.android.files.fileproperties.FilePropertiesItem
import me.zhanghai.android.files.fileproperties.FilePropertiesTab
import me.zhanghai.android.files.provider.archive.ArchiveFileAttributes
import me.zhanghai.android.files.provider.archive.archiveFile
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.util.observeAsStateCompat
import java.io.IOException

@Composable
fun FilePropertiesBasicTab(viewModel: FilePropertiesFileViewModel) {
    val stateful by viewModel.fileLiveData.observeAsStateCompat()
    FilePropertiesTab(stateful, onRefresh = viewModel::reload) { file ->
        FilePropertiesItem(R.string.file_properties_basic_name, file.name)
        val path = file.path
        if (path.isArchivePath) {
            FilePropertiesItem(
                R.string.file_properties_basic_archive_file,
                path.archiveFile.toUserFriendlyString()
            )
            val attributes = file.attributes as ArchiveFileAttributes
            FilePropertiesItem(R.string.file_properties_basic_archive_entry, attributes.entryName())
        } else {
            val parentPath = path.parent
            if (parentPath != null) {
                FilePropertiesItem(
                    R.string.file_properties_basic_parent_directory, parentPath.toString()
                )
            }
        }
        FilePropertiesItem(R.string.file_properties_basic_type, getTypeText(file))
        val symbolicLinkTarget = file.symbolicLinkTarget
        if (symbolicLinkTarget != null) {
            FilePropertiesItem(
                R.string.file_properties_basic_symbolic_link_target, symbolicLinkTarget
            )
        }
        if (file.attributes.isDirectory) {
            // Counted again for each new value of the file, like after a refresh.
            val contents by remember(file) { path.directoryContentsFlow() }
                .collectAsState(DirectoryContents(0, 0))
            FilePropertiesItem(
                R.string.file_properties_basic_contents, getDirectoryContentsText(contents)
            )
        } else {
            FilePropertiesItem(R.string.file_properties_basic_size, getSizeText(file))
        }
        FilePropertiesItem(
            R.string.file_properties_basic_last_modification_time,
            file.attributes.lastModifiedTime().toInstant().formatLong()
        )
    }
}

@Composable
private fun getTypeText(file: FileItem): String {
    val typeFormatRes = if (file.attributesNoFollowLinks.isSymbolicLink
        && !file.isSymbolicLinkBroken) {
        R.string.file_properties_basic_type_symbolic_link_format
    } else {
        R.string.file_properties_basic_type_format
    }
    return stringResource(
        typeFormatRes, file.getMimeTypeName(LocalContext.current), file.mimeType.value
    )
}

private class DirectoryContents(val count: Int, val size: Long)

private fun Path.directoryContentsFlow(): Flow<DirectoryContents> =
    channelFlow {
        val directory = this@directoryContentsFlow
        var count = 0
        var size = 0L
        Files.walkFileTree(directory, object : FileVisitor<Path> {
            private var lastTimeMillis = System.currentTimeMillis()

            override fun preVisitDirectory(
                directory: Path,
                attributes: BasicFileAttributes
            ): FileVisitResult = visit(directory, attributes, null)

            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult =
                visit(file, attributes, null)

            override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult =
                visit(file, null, exception)

            override fun postVisitDirectory(
                directory: Path,
                exception: IOException?
            ): FileVisitResult = visit(null, null, exception)

            private fun visit(
                path: Path?,
                attributes: BasicFileAttributes?,
                exception: IOException?
            ): FileVisitResult {
                if (!isActive) {
                    return FileVisitResult.TERMINATE
                }
                if (path == directory) {
                    return FileVisitResult.CONTINUE
                }
                path?.let { ++count }
                attributes?.let { size += it.size() }
                exception?.printStackTrace()
                val currentTimeMillis = System.currentTimeMillis()
                if (currentTimeMillis >= lastTimeMillis + DIRECTORY_CONTENTS_INTERVAL_MILLIS) {
                    trySend(DirectoryContents(count, size))
                    lastTimeMillis = currentTimeMillis
                }
                return FileVisitResult.CONTINUE
            }
        })
        trySend(DirectoryContents(count, size))
    }
        .conflate()
        .flowOn(Dispatchers.IO)

@Composable
private fun getDirectoryContentsText(contents: DirectoryContents): String {
    if (contents.count == 0) {
        return stringResource(R.string.empty)
    }
    val fileSize = contents.size.asFileSize()
    val context = LocalContext.current
    val sizeText = if (fileSize.isHumanReadableInBytes) {
        fileSize.formatInBytes(context)
    } else {
        fileSize.formatHumanReadable(context)
    }
    return pluralStringResource(
        R.plurals.file_properties_basic_contents_format, contents.count, contents.count, sizeText
    )
}

@Composable
private fun getSizeText(file: FileItem): String {
    val size = file.attributes.fileSize
    val context = LocalContext.current
    val sizeInBytes = size.formatInBytes(context)
    return if (size.isHumanReadableInBytes) {
        sizeInBytes
    } else {
        stringResource(
            R.string.file_properties_basic_size_with_human_readable_format,
            size.formatHumanReadable(context), sizeInBytes
        )
    }
}

private const val DIRECTORY_CONTENTS_INTERVAL_MILLIS = 200L
