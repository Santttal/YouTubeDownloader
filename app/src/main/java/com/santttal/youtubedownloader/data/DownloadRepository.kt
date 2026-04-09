package com.santttal.youtubedownloader.data

import android.content.Context
import com.santttal.youtubedownloader.model.VideoInfo
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadRepository(private val context: Context) {

    /**
     * Fetch video metadata from YouTube. Runs on Dispatchers.IO because
     * YoutubeDL.getInfo() is a blocking JNI call.
     */
    suspend fun getVideoInfo(url: String): VideoInfo = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-json")
        }
        val rawInfo = YoutubeDL.getInstance().getInfo(request)
        VideoInfo(
            title = rawInfo.title ?: "Unknown",
            thumbnailUrl = rawInfo.thumbnail ?: "",
            durationSeconds = rawInfo.duration?.toLong() ?: 0L
        )
    }
}
