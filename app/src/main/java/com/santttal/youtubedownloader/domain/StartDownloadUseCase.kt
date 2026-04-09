package com.santttal.youtubedownloader.domain

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.santttal.youtubedownloader.model.Quality
import com.santttal.youtubedownloader.worker.DownloadWorker

class StartDownloadUseCase(private val context: Context) {

    fun execute(url: String, quality: Quality, processId: String) {
        val inputData = workDataOf(
            "url" to url,
            "format" to quality.formatString,
            "processId" to processId,
            "isAudio" to quality.isAudioOnly
        )
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("yt-download", ExistingWorkPolicy.KEEP, request)
    }
}
