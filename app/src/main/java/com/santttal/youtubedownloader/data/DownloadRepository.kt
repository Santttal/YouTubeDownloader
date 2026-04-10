package com.santttal.youtubedownloader.data

import android.content.Context
import com.santttal.youtubedownloader.model.VideoInfo
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class DownloadRepository(private val context: Context) {

    /**
     * Fast video info via YouTube oEmbed API (title + thumbnail in ~1 second).
     * Extracts video ID for high-quality thumbnail URL.
     * Falls back to yt-dlp getInfo() if oEmbed fails.
     */
    suspend fun getVideoInfo(url: String): VideoInfo = withContext(Dispatchers.IO) {
        try {
            getVideoInfoFast(url)
        } catch (e: Exception) {
            android.util.Log.w("DownloadRepo", "oEmbed failed, falling back to yt-dlp", e)
            getVideoInfoYtDlp(url)
        }
    }

    private fun getVideoInfoFast(url: String): VideoInfo {
        val oembedUrl = "https://www.youtube.com/oembed?url=${java.net.URLEncoder.encode(url, "UTF-8")}&format=json"
        val conn = URL(oembedUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val title = json.getString("title")

            // Extract video ID for high-quality thumbnail
            val videoId = extractVideoId(url)
            val thumbnailUrl = if (videoId != null) {
                "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
            } else {
                json.optString("thumbnail_url", "")
            }

            return VideoInfo(
                title = title,
                thumbnailUrl = thumbnailUrl,
                durationSeconds = 0L // oEmbed doesn't provide duration
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun getVideoInfoYtDlp(url: String): VideoInfo {
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--force-ipv4")
            addOption("--source-address", "0.0.0.0")
            addOption("--extractor-args", "youtube:player_client=tv,tv_simply")
            addOption("--socket-timeout", "30")
            addOption("--geo-bypass")
        }
        val rawInfo = YoutubeDL.getInstance().getInfo(request)
        return VideoInfo(
            title = rawInfo.title ?: "Unknown",
            thumbnailUrl = rawInfo.thumbnail ?: "",
            durationSeconds = rawInfo.duration?.toLong() ?: 0L
        )
    }

    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("""youtu\.be/([\w\-]{11})"""),
            Regex("""youtube\.com/watch\?v=([\w\-]{11})"""),
            Regex("""youtube\.com/shorts/([\w\-]{11})"""),
            Regex("""youtube\.com/embed/([\w\-]{11})""")
        )
        for (pattern in patterns) {
            pattern.find(url)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }
}
