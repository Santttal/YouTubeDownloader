package com.santttal.youtubedownloader.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

object MediaStoreHelper {

    /**
     * Save a temp file to the public Downloads folder.
     * Returns the content URI of the saved file (for opening in notifications).
     */
    fun saveToDownloads(
        context: Context,
        tempFile: File,
        displayName: String,
        mimeType: String
    ): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
    ): Uri {
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

        return uri
    }

    private fun saveToDownloadsLegacy(
        context: Context,
        tempFile: File,
        displayName: String,
        mimeType: String
    ): Uri {
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
        return Uri.fromFile(destFile)
    }
}
