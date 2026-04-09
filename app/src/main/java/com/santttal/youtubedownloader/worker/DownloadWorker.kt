package com.santttal.youtubedownloader.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.santttal.youtubedownloader.util.FilenameUtils
import com.santttal.youtubedownloader.util.MediaStoreHelper
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID

class DownloadWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Missing URL"))
        val format = inputData.getString(KEY_FORMAT)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Missing format"))
        val processId = inputData.getString(KEY_PROCESS_ID)
            ?: UUID.randomUUID().toString()
        val isAudio = inputData.getBoolean(KEY_IS_AUDIO, false)

        // Promote to foreground before any network I/O (required for Android 12+ background survival)
        setForeground(createForegroundInfo())

        val tempFile = File(applicationContext.cacheDir, "$processId.tmp")
        val tempMp3File = File(applicationContext.cacheDir, "$processId.mp3")
        val tempMp4File = File(applicationContext.cacheDir, "$processId.mp4")

        return try {
            val request = YoutubeDLRequest(url).apply {
                addOption("-f", format)
                addOption("-o", tempFile.absolutePath)
                addOption("--downloader", "libaria2c.so")
                addOption("--no-playlist")
                if (isAudio) {
                    addOption("--extract-audio")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "192k")
                }
            }

            YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
                setProgressAsync(workDataOf(KEY_PROGRESS to progress.toInt()))
            }

            // Determine the actual output file — yt-dlp may rename after post-processing
            val actualFile = when {
                tempFile.exists() -> tempFile
                isAudio && tempMp3File.exists() -> tempMp3File
                !isAudio && tempMp4File.exists() -> tempMp4File
                else -> return Result.failure(workDataOf(KEY_ERROR to "Output file not found after download"))
            }

            val extension = if (isAudio) "mp3" else "mp4"
            val mimeType = if (isAudio) "audio/mpeg" else "video/mp4"
            val displayName = FilenameUtils.buildFilename(
                "download_${System.currentTimeMillis()}",
                extension
            )

            MediaStoreHelper.saveToDownloads(applicationContext, actualFile, displayName, mimeType)

            Result.success()
        } catch (e: YoutubeDLException) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "YoutubeDL error")))
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to "Unexpected error: ${e.message}"))
        } finally {
            // Clean up all possible temp files regardless of success or failure
            if (tempFile.exists()) tempFile.delete()
            if (tempMp3File.exists()) tempMp3File.delete()
            if (tempMp4File.exists()) tempMp4File.delete()
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, NOTIF_CHANNEL_ID)
            .setContentTitle("Загрузка...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        return ForegroundInfo(NOTIF_ID, notification)
    }

    companion object {
        const val TAG = "DownloadWorker"
        const val KEY_URL = "url"
        const val KEY_FORMAT = "format"
        const val KEY_PROCESS_ID = "processId"
        const val KEY_IS_AUDIO = "isAudio"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val NOTIF_ID = 1001
        const val NOTIF_CHANNEL_ID = "download_channel"
    }
}
