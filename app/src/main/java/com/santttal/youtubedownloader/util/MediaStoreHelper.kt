package com.santttal.youtubedownloader.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

object MediaStoreHelper {

    /**
     * Save a temp file to the public Downloads folder.
     *
     * - API 29+ (Android 10+): Uses MediaStore.Downloads with IS_PENDING=1 before write,
     *   IS_PENDING=0 after write. No WRITE_EXTERNAL_STORAGE permission needed.
     * - API 26-28: Writes directly to Environment.DIRECTORY_DOWNLOADS and calls
     *   MediaScannerConnection to index the file.
     */
    fun saveToDownloads(
        context: Context,
        tempFile: File,
        displayName: String,
        mimeType: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToDownloadsApi29(context, tempFile, displayName, mimeType)
        } else {
            saveToDownloadsLegacy(context, tempFile, displayName, mimeType)
        }
    }

    private fun saveToDownloadsApi29(
        context: Context,
        tempFile: File,
        displayName: String,
        mimeType: String
    ) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore insert returned null for $displayName")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                tempFile.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("Could not open output stream for $uri")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw IOException("Failed to write to MediaStore: ${e.message}", e)
        }
    }

    private fun saveToDownloadsLegacy(
        context: Context,
        tempFile: File,
        displayName: String,
        mimeType: String
    ) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir.mkdirs()
        val destFile = File(downloadsDir, displayName)
        tempFile.inputStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(destFile.absolutePath),
            arrayOf(mimeType),
            null
        )
    }
}
