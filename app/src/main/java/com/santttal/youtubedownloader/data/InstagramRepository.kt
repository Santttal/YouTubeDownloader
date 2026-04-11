package com.santttal.youtubedownloader.data

import com.santttal.youtubedownloader.model.StreamUrls
import com.santttal.youtubedownloader.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class InstagramRepository {

    companion object {
        private const val IG_APP_ID = "936619743392459"
        private const val IG_DOC_ID = "10015901848480474"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun getVideoInfo(url: String): VideoInfo = withContext(Dispatchers.IO) {
        val shortcode = extractShortcode(url)
            ?: throw IllegalArgumentException("Invalid Instagram URL")
        val csrfToken = getCsrfToken()
            ?: throw RuntimeException("Failed to get Instagram CSRF token")
        val media = fetchMedia(shortcode, csrfToken)

        VideoInfo(
            title = media.optString("title", "").ifEmpty {
                media.optJSONObject("edge_media_to_caption")
                    ?.optJSONArray("edges")?.optJSONObject(0)
                    ?.optJSONObject("node")?.optString("text", "Instagram Video")
                    ?.take(80)
                    ?: "Instagram Video"
            },
            thumbnailUrl = media.optString("display_url", ""),
            durationSeconds = media.optDouble("video_duration", 0.0).toLong()
        )
    }

    suspend fun resolveStreamUrls(url: String): StreamUrls = withContext(Dispatchers.IO) {
        val shortcode = extractShortcode(url)
            ?: throw IllegalArgumentException("Invalid Instagram URL")
        val csrfToken = getCsrfToken()
            ?: throw RuntimeException("Failed to get Instagram CSRF token")
        val media = fetchMedia(shortcode, csrfToken)

        if (!media.optBoolean("is_video", false)) {
            throw RuntimeException("This Instagram post is not a video")
        }

        val videoUrl = media.getString("video_url")
        val title = media.optString("title", "").ifEmpty { "instagram_$shortcode" }

        StreamUrls(
            videoUrl = videoUrl,
            audioUrl = null,
            title = title,
            isVideoOnly = false,
            needsMux = false,
            isWebm = false
        )
    }

    private fun getCsrfToken(): String? {
        val request = Request.Builder()
            .url("https://www.instagram.com/")
            .header("User-Agent", UA)
            .build()

        val response = client.newCall(request).execute()
        response.body?.close()

        return response.headers("Set-Cookie")
            .firstOrNull { it.startsWith("csrftoken=") }
            ?.substringAfter("csrftoken=")
            ?.substringBefore(";")
    }

    private fun fetchMedia(shortcode: String, csrfToken: String): JSONObject {
        val variables = JSONObject().apply {
            put("shortcode", shortcode)
            put("child_comment_count", 0)
            put("fetch_comment_count", 0)
            put("parent_comment_count", 0)
            put("has_threaded_comments", false)
        }

        val formBody = FormBody.Builder()
            .add("variables", variables.toString())
            .add("doc_id", IG_DOC_ID)
            .build()

        val request = Request.Builder()
            .url("https://www.instagram.com/graphql/query/")
            .post(formBody)
            .header("User-Agent", UA)
            .header("X-IG-App-ID", IG_APP_ID)
            .header("X-CSRFToken", csrfToken)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "https://www.instagram.com/")
            .header("Cookie", "csrftoken=$csrfToken")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Instagram API error: ${response.code}")
        }

        val json = JSONObject(response.body!!.string())
        return json.getJSONObject("data").getJSONObject("xdt_shortcode_media")
    }

    fun extractShortcode(url: String): String? {
        val regex = Regex("""instagram\.com/(?:reel|p|tv)/([A-Za-z0-9_-]+)""")
        return regex.find(url)?.groupValues?.get(1)
    }

    fun isInstagramUrl(url: String): Boolean {
        return url.contains("instagram.com/reel/") ||
                url.contains("instagram.com/p/") ||
                url.contains("instagram.com/tv/")
    }
}
