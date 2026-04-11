package com.santttal.youtubedownloader

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Functional tests for Instagram video extraction.
 * Uses Instagram GraphQL API with CSRF token (no login required for public Reels).
 *
 * ./gradlew testDebugUnitTest --tests "com.santttal.youtubedownloader.InstagramExtractorTest"
 */
class InstagramExtractorTest {

    companion object {
        const val TEST_SHORTCODE = "DW0-Bw0iLrY"
        const val IG_APP_ID = "936619743392459"
        const val IG_DOC_ID = "10015901848480474"
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Test
    fun `extract video URL from public Instagram Reel`() {
        val csrfToken = getCsrfToken()
        assertNotNull("Should get CSRF token", csrfToken)
        println("CSRF: ${csrfToken!!.take(10)}...")

        val videoUrl = extractVideoUrl(TEST_SHORTCODE, csrfToken)
        assertNotNull("Should extract video URL", videoUrl)
        assertTrue("URL should be HTTPS", videoUrl!!.startsWith("https://"))
        println("Video URL: ${videoUrl.take(120)}...")
    }

    @Test
    fun `download Instagram Reel video`() {
        val csrfToken = getCsrfToken()!!
        val videoUrl = extractVideoUrl(TEST_SHORTCODE, csrfToken)!!

        val downloadReq = Request.Builder()
            .url(videoUrl)
            .header("User-Agent", UA)
            .header("Range", "bytes=0-524287")
            .build()

        val start = System.currentTimeMillis()
        val resp = client.newCall(downloadReq).execute()
        assertTrue("Download should succeed, got ${resp.code}", resp.code in listOf(200, 206))

        val bytes = resp.body!!.bytes()
        val elapsed = System.currentTimeMillis() - start

        println("Downloaded: ${bytes.size} bytes in ${elapsed}ms")
        assertTrue("Should download at least 100KB", bytes.size > 100_000)
    }

    @Test
    fun `extract shortcode from various Instagram URL formats`() {
        val urls = mapOf(
            "https://www.instagram.com/reel/DW0-Bw0iLrY/" to "DW0-Bw0iLrY",
            "https://www.instagram.com/reel/DW0-Bw0iLrY/?igsh=MTN5MjVzZzE2d3Q2eg==" to "DW0-Bw0iLrY",
            "https://www.instagram.com/p/ABC123def/" to "ABC123def",
            "https://instagram.com/reel/XYZ789/" to "XYZ789",
            "https://www.instagram.com/p/ABC123def/?utm_source=ig_web" to "ABC123def",
        )

        urls.forEach { (url, expected) ->
            val shortcode = extractShortcode(url)
            assertEquals("Shortcode from $url", expected, shortcode)
            println("$url → $shortcode ✓")
        }
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

    private fun extractVideoUrl(shortcode: String, csrfToken: String): String? {
        val variables = """{"shortcode":"$shortcode","child_comment_count":0,"fetch_comment_count":0,"parent_comment_count":0,"has_threaded_comments":false}"""

        val formBody = FormBody.Builder()
            .add("variables", variables)
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
        if (!response.isSuccessful) return null

        val body = response.body!!.string()

        // Parse video_url from JSON response without org.json (not available in JVM tests)
        val videoUrlMatch = Regex(""""video_url"\s*:\s*"([^"]+)"""").find(body)
        val videoUrl = videoUrlMatch?.groupValues?.get(1)
            ?.replace("\\/", "/")  // unescape JSON slashes
            ?.replace("\\u0026", "&")  // unescape unicode ampersand

        val isVideo = body.contains(""""is_video":true""")
        return if (isVideo) videoUrl else null
    }

    private fun extractShortcode(url: String): String? {
        val regex = Regex("""instagram\.com/(?:reel|p|tv)/([A-Za-z0-9_-]+)""")
        return regex.find(url)?.groupValues?.get(1)
    }
}
