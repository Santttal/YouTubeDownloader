package com.santttal.youtubedownloader.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import android.media.MediaExtractor
import android.media.MediaMuxer
import com.santttal.youtubedownloader.util.FilenameUtils
import com.santttal.youtubedownloader.util.MediaStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class DownloadWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .build()

    override suspend fun doWork(): Result {
        val videoUrl = inputData.getString(KEY_VIDEO_URL)
        val audioUrl = inputData.getString(KEY_AUDIO_URL)
        val title = inputData.getString(KEY_TITLE) ?: "download"
        val needsMux = inputData.getBoolean(KEY_NEEDS_MUX, false)
        val isAudio = inputData.getBoolean(KEY_IS_AUDIO, false)
        val processId = inputData.getString(KEY_PROCESS_ID) ?: ""

        Log.d(TAG, "doWork: videoUrl=${videoUrl != null}, audioUrl=${audioUrl != null}, needsMux=$needsMux, isAudio=$isAudio")

        if (videoUrl == null && audioUrl == null) {
            return Result.failure(workDataOf(KEY_ERROR to "No stream URLs provided"))
        }

        setForeground(createForegroundInfo(0))

        val cacheDir = applicationContext.cacheDir

        return try {
            if (isAudio && audioUrl != null) {
                // Audio-only: download and save as m4a (native YouTube format, plays everywhere)
                val audioFile = File(cacheDir, "$processId.m4a")
                downloadFile(audioUrl, audioFile)

                val displayName = FilenameUtils.buildFilename(title, "m4a")
                val savedUri = MediaStoreHelper.saveToDownloads(applicationContext, audioFile, displayName, "audio/mp4")
                postCompletionNotification(displayName, savedUri, "audio/mp4")
                Result.success()

            } else if (needsMux && videoUrl != null && audioUrl != null) {
                // DASH: download video (0-70%) + audio (70-95%), mux (95-100%)
                val videoFile = File(cacheDir, "$processId.video.mp4")
                val audioFile = File(cacheDir, "$processId.audio.m4a")
                val muxedFile = File(cacheDir, "$processId.muxed.mp4")

                downloadFile(videoUrl, videoFile, progressOffset = 0, progressScale = 70)
                downloadFile(audioUrl, audioFile, progressOffset = 70, progressScale = 25)
                reportProgress(95)
                muxStreams(videoFile, audioFile, muxedFile)
                reportProgress(100)

                val displayName = FilenameUtils.buildFilename(title, "mp4")
                val savedUri = MediaStoreHelper.saveToDownloads(applicationContext, muxedFile, displayName, "video/mp4")
                postCompletionNotification(displayName, savedUri, "video/mp4")
                Result.success()

            } else if (videoUrl != null) {
                // Progressive: single file with video + audio
                val outFile = File(cacheDir, "$processId.mp4")
                downloadFile(videoUrl, outFile)

                val displayName = FilenameUtils.buildFilename(title, "mp4")
                val savedUri = MediaStoreHelper.saveToDownloads(applicationContext, outFile, displayName, "video/mp4")
                postCompletionNotification(displayName, savedUri, "video/mp4")
                Result.success()

            } else {
                Result.failure(workDataOf(KEY_ERROR to "Invalid stream configuration"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Download error")))
        } finally {
            // Clean up all temp files
            cacheDir.listFiles()
                ?.filter { it.name.startsWith(processId) }
                ?.forEach { it.delete() }
        }
    }

    private suspend fun downloadFile(
        url: String,
        outputFile: File,
        progressOffset: Int = 0,
        progressScale: Int = 100
    ) = withContext(Dispatchers.IO) {
        // First: HEAD request to get content length
        val headRequest = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", USER_AGENT)
            .build()
        val headResponse = client.newCall(headRequest).execute()
        val contentLength = headResponse.header("Content-Length")?.toLongOrNull() ?: -1L
        headResponse.close()

        if (contentLength <= 0) {
            // Fallback: single GET without range
            downloadFileSingle(url, outputFile, progressOffset, progressScale)
            return@withContext
        }

        // Chunked download with Range headers (avoids YouTube throttling)
        val chunkSize = 512 * 1024L // 512KB chunks like NewPipe
        var downloaded = 0L
        var lastReportedProgress = -1

        outputFile.outputStream().use { output ->
            while (downloaded < contentLength) {
                val end = (downloaded + chunkSize - 1).coerceAtMost(contentLength - 1)
                val rangeRequest = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .header("Range", "bytes=$downloaded-$end")
                    .build()

                val response = client.newCall(rangeRequest).execute()
                if (!response.isSuccessful && response.code != 206) {
                    response.close()
                    throw RuntimeException("HTTP ${response.code}: ${response.message}")
                }

                response.body?.byteStream()?.use { input ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        val fileProgress = (downloaded * progressScale / contentLength).toInt()
                        val totalProgress = (progressOffset + fileProgress).coerceAtMost(progressOffset + progressScale)
                        if (totalProgress != lastReportedProgress) {
                            lastReportedProgress = totalProgress
                            reportProgress(totalProgress)
                        }
                    }
                }
                response.close()
            }
        }

        Log.d(TAG, "Downloaded ${outputFile.name}: ${outputFile.length()} bytes (chunked)")
    }

    private suspend fun downloadFileSingle(
        url: String,
        outputFile: File,
        progressOffset: Int,
        progressScale: Int
    ) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw RuntimeException("HTTP ${response.code}: ${response.message}")
        }

        val body = response.body ?: throw RuntimeException("Empty response body")
        val contentLength = body.contentLength()
        var downloaded = 0L
        var lastReportedProgress = -1

        body.byteStream().use { input ->
            outputFile.outputStream().use { output ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead

                    if (contentLength > 0) {
                        val fileProgress = (downloaded * progressScale / contentLength).toInt()
                        val totalProgress = (progressOffset + fileProgress).coerceAtMost(progressOffset + progressScale)
                        if (totalProgress != lastReportedProgress) {
                            lastReportedProgress = totalProgress
                            reportProgress(totalProgress)
                        }
                    }
                }
            }
        }

        Log.d(TAG, "Downloaded ${outputFile.name}: ${outputFile.length()} bytes (single)")
    }

    private fun reportProgress(progress: Int) {
        setProgressAsync(workDataOf(KEY_PROGRESS to progress))
        val notificationManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIF_ID, createForegroundInfo(progress).notification)
    }

    private fun muxStreams(videoFile: File, audioFile: File, outputFile: File) {
        Log.d(TAG, "MediaMuxer: muxing ${videoFile.name} + ${audioFile.name}")

        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()

        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Add video track
            val videoTrackIndex = findTrack(videoExtractor, "video/")
            videoExtractor.selectTrack(videoTrackIndex)
            val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
            val muxerVideoTrack = muxer.addTrack(videoFormat)

            // Add audio track
            val audioTrackIndex = findTrack(audioExtractor, "audio/")
            audioExtractor.selectTrack(audioTrackIndex)
            val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)
            val muxerAudioTrack = muxer.addTrack(audioFormat)

            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024) // 1MB buffer
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            // Copy video samples
            while (true) {
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // Copy audio samples
            while (true) {
                val sampleSize = audioExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                bufferInfo.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                audioExtractor.advance()
            }

            muxer.stop()
            muxer.release()

            Log.d(TAG, "Muxed ${outputFile.name}: ${outputFile.length()} bytes")
        } finally {
            videoExtractor.release()
            audioExtractor.release()
        }
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return i
        }
        throw RuntimeException("No $mimePrefix track found")
    }

    private fun postCompletionNotification(displayName: String, fileUri: android.net.Uri, mimeType: String) {
        val notificationManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, mimeType)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NOTIF_CHANNEL_ID)
            .setContentTitle("Загрузка завершена")
            .setContentText(displayName)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Открыть", pendingIntent)
            .build()

        notificationManager.notify(COMPLETION_NOTIF_ID, notification)
    }

    private fun createForegroundInfo(progress: Int = 0): ForegroundInfo {
        val notificationManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, NOTIF_CHANNEL_ID)
            .setContentTitle(if (progress > 0) "Загрузка... $progress%" else "Загрузка...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
        return ForegroundInfo(NOTIF_ID, notification)
    }

    companion object {
        const val TAG = "DownloadWorker"
        const val KEY_VIDEO_URL = "videoUrl"
        const val KEY_AUDIO_URL = "audioUrl"
        const val KEY_TITLE = "title"
        const val KEY_NEEDS_MUX = "needsMux"
        const val KEY_IS_AUDIO = "isAudio"
        const val KEY_PROCESS_ID = "processId"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val NOTIF_ID = 1001
        const val COMPLETION_NOTIF_ID = 1002
        const val NOTIF_CHANNEL_ID = "download_channel"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}
