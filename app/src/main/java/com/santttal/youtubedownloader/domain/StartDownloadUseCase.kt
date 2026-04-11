package com.santttal.youtubedownloader.domain

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.santttal.youtubedownloader.model.StreamUrls
import com.santttal.youtubedownloader.worker.DownloadWorker

class StartDownloadUseCase(private val context: Context) {

    fun execute(streamUrls: StreamUrls, isAudio: Boolean, processId: String) {
        val inputData = workDataOf(
            DownloadWorker.KEY_VIDEO_URL to streamUrls.videoUrl,
            DownloadWorker.KEY_AUDIO_URL to streamUrls.audioUrl,
            DownloadWorker.KEY_TITLE to streamUrls.title,
            DownloadWorker.KEY_NEEDS_MUX to streamUrls.needsMux,
            DownloadWorker.KEY_IS_AUDIO to isAudio,
            DownloadWorker.KEY_IS_WEBM to streamUrls.isWebm,
            DownloadWorker.KEY_PROCESS_ID to processId
        )
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("yt-download", ExistingWorkPolicy.KEEP, request)
    }
}
