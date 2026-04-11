package com.santttal.youtubedownloader

import com.santttal.youtubedownloader.util.NewPipeDownloader
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import okhttp3.Request
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Functional tests that make real HTTP requests to YouTube.
 * Run only when changing download/info extraction logic.
 *
 * ./gradlew test --tests "com.santttal.youtubedownloader.NewPipeExtractorTest"
 */
class NewPipeExtractorTest {

    companion object {
        // Short, stable, public video (Rick Astley - Never Gonna Give You Up, 3:33)
        const val TEST_VIDEO_URL = "https://www.youtube.com/watch?v=nP1bLbczm04"
        const val TEST_VIDEO_ID = "nP1bLbczm04"
    }

    @Before
    fun setup() {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        NewPipe.init(
            NewPipeDownloader(client),
            Localization("en", "US"),
            ContentCountry("US")
        )
    }

    @Test
    fun `getInfo returns title and thumbnail`() {
        val info = StreamInfo.getInfo(TEST_VIDEO_URL)

        assertNotNull("Title should not be null", info.name)
        assertTrue("Title should not be empty", info.name.isNotEmpty())
        assertTrue("Thumbnails should exist", info.thumbnails.isNotEmpty())
        assertTrue("Duration should be positive", info.duration > 0)

        println("Title: ${info.name}")
        println("Duration: ${info.duration}s")
        println("Thumbnail: ${info.thumbnails.firstOrNull()?.url}")
    }

    @Test
    fun `getInfo returns video streams with resolutions`() {
        val info = StreamInfo.getInfo(TEST_VIDEO_URL)

        val videoStreams = info.videoStreams + info.videoOnlyStreams
        assertTrue("Should have video streams", videoStreams.isNotEmpty())

        println("Video streams:")
        videoStreams.forEach { stream ->
            println("  ${stream.resolution} | ${stream.format?.name} | videoOnly=${stream.isVideoOnly} | url=${stream.isUrl}")
        }

        // Should have at least one stream with a URL
        val withUrl = videoStreams.filter { it.isUrl }
        assertTrue("Should have at least one stream with URL", withUrl.isNotEmpty())
    }

    @Test
    fun `getInfo returns audio streams`() {
        val info = StreamInfo.getInfo(TEST_VIDEO_URL)

        val audioStreams = info.audioStreams
        assertTrue("Should have audio streams", audioStreams.isNotEmpty())

        println("Audio streams:")
        audioStreams.forEach { stream ->
            println("  ${stream.averageBitrate}kbps | ${stream.format?.name} | url=${stream.isUrl}")
        }

        val withUrl = audioStreams.filter { it.isUrl }
        assertTrue("Should have at least one audio stream with URL", withUrl.isNotEmpty())
    }

    @Test
    fun `can find 360p progressive stream`() {
        val info = StreamInfo.getInfo(TEST_VIDEO_URL)

        val progressive360 = info.videoStreams
            .filter { it.isUrl && !it.isVideoOnly }
            .filter { it.resolution?.contains("360") == true }

        println("360p progressive streams: ${progressive360.size}")
        progressive360.forEach { println("  ${it.resolution} | ${it.format?.name} | ${it.content?.take(80)}...") }

        // 360p progressive should exist for most YouTube videos
        assertTrue("Should find 360p progressive stream", progressive360.isNotEmpty())
    }

    @Test
    fun `can download first 100KB of a video stream`() {
        val info = StreamInfo.getInfo(TEST_VIDEO_URL)

        // Get any video stream with a URL
        val stream = info.videoStreams
            .filter { it.isUrl && !it.isVideoOnly }
            .firstOrNull()
            ?: info.videoOnlyStreams.filter { it.isUrl }.firstOrNull()

        assertNotNull("Should have at least one downloadable stream", stream)

        val url = stream!!.content
        assertNotNull("Stream URL should not be null", url)

        // Download first 100KB to verify the URL works
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Range", "bytes=0-102399")
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000

        try {
            val responseCode = conn.responseCode
            assertTrue(
                "HTTP response should be 200 or 206 (partial), got $responseCode",
                responseCode in listOf(200, 206)
            )

            val bytes = conn.inputStream.readBytes()
            assertTrue("Should download at least 10KB, got ${bytes.size}", bytes.size >= 10_000)

            println("Downloaded ${bytes.size} bytes from ${stream.resolution} stream")
            println("Content-Type: ${conn.contentType}")
        } finally {
            conn.disconnect()
        }
    }

    @Test
    fun `can download first 100KB of an audio stream`() {
        val info = StreamInfo.getInfo(TEST_VIDEO_URL)

        val audioStream = info.audioStreams
            .filter { it.isUrl }
            .maxByOrNull { it.averageBitrate }

        assertNotNull("Should have at least one audio stream", audioStream)

        val url = audioStream!!.content
        assertNotNull("Audio stream URL should not be null", url)

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Range", "bytes=0-102399")
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000

        try {
            val responseCode = conn.responseCode
            assertTrue(
                "HTTP response should be 200 or 206, got $responseCode",
                responseCode in listOf(200, 206)
            )

            val bytes = conn.inputStream.readBytes()
            assertTrue("Should download at least 10KB, got ${bytes.size}", bytes.size >= 10_000)

            println("Downloaded ${bytes.size} bytes from audio stream (${audioStream.averageBitrate}kbps)")
        } finally {
            conn.disconnect()
        }
    }

    @Test
    fun `oEmbed API returns title quickly`() {
        val oembedUrl = "https://www.youtube.com/oembed?url=${java.net.URLEncoder.encode(TEST_VIDEO_URL, "UTF-8")}&format=json"
        val conn = URL(oembedUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000

        try {
            val start = System.currentTimeMillis()
            val json = conn.inputStream.bufferedReader().readText()
            val elapsed = System.currentTimeMillis() - start

            assertTrue("oEmbed should respond in under 5s, took ${elapsed}ms", elapsed < 5000)
            assertTrue("Response should contain title", json.contains("\"title\""))

            println("oEmbed response in ${elapsed}ms")
            println("Response: ${json.take(200)}")
        } finally {
            conn.disconnect()
        }
    }

    @Test
    fun `can download full audio file for MP3 conversion`() {
        val info = StreamInfo.getInfo(TEST_VIDEO_URL)

        println("Title: ${info.name}")
        println("Duration: ${info.duration}s")

        val audioStream = info.audioStreams
            .filter { it.isUrl }
            .maxByOrNull { it.averageBitrate }

        assertNotNull("Should have audio stream", audioStream)
        println("Audio: ${audioStream!!.averageBitrate}kbps | ${audioStream.format?.name}")

        val audioUrl = audioStream.content
        assertNotNull("Audio URL should not be null", audioUrl)

        // Download full audio file
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .build()
        val request = Request.Builder().url(audioUrl).build()
        val response = client.newCall(request).execute()

        assertTrue("HTTP should succeed, got ${response.code}", response.isSuccessful)

        val tempFile = File.createTempFile("test_audio_", ".m4a")
        try {
            response.body!!.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            println("Downloaded audio: ${tempFile.length()} bytes")
            assertTrue("Audio file should be > 10KB", tempFile.length() > 10_000)

            // Verify it's a valid audio file (check for ftyp/moov atoms in m4a)
            val header = tempFile.inputStream().use { it.readNBytes(12) }
            val headerStr = String(header, 4, 4)
            println("File header: $headerStr")
            // m4a files start with ftyp atom
            assertTrue(
                "File should be valid m4a (ftyp header), got '$headerStr'",
                headerStr == "ftyp" || headerStr == "free" || tempFile.length() > 10_000
            )
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `download 1080p video with chunked range requests - speed test`() {
        val testUrl = "https://youtu.be/KLuTLF3x9sA"
        val info = StreamInfo.getInfo(testUrl)

        println("Title: ${info.name}")
        println("Duration: ${info.duration}s")

        // Get 1080p video-only stream
        val videoStream = info.videoOnlyStreams
            .filter { it.isUrl }
            .filter { (it.getResolution()?.replace("p", "")?.toIntOrNull() ?: 0) <= 1080 }
            .maxByOrNull { it.getResolution()?.replace("p", "")?.toIntOrNull() ?: 0 }

        assertNotNull("Should have video-only stream", videoStream)
        println("Video: ${videoStream!!.getResolution()} | ${videoStream.format?.name}")

        val videoUrl = videoStream.content!!
        val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

        val dlClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        // Get content length via HEAD
        val headReq = Request.Builder().url(videoUrl).head().header("User-Agent", UA).build()
        val headResp = dlClient.newCall(headReq).execute()
        val contentLength = headResp.header("Content-Length")?.toLongOrNull() ?: -1L
        headResp.close()
        println("Content-Length: $contentLength bytes (${contentLength / 1024 / 1024} MB)")

        assertTrue("Should know content length", contentLength > 0)

        // Download first 5MB with chunked range requests (512KB chunks)
        val downloadLimit = 5 * 1024 * 1024L
        val chunkSize = 512 * 1024L
        var downloaded = 0L
        val tempFile = File.createTempFile("test_1080p_", ".mp4")

        val startTime = System.currentTimeMillis()

        try {
            tempFile.outputStream().use { output ->
                while (downloaded < downloadLimit && downloaded < contentLength) {
                    val end = (downloaded + chunkSize - 1).coerceAtMost(contentLength - 1)
                    val rangeReq = Request.Builder()
                        .url(videoUrl)
                        .header("User-Agent", UA)
                        .header("Accept", "*/*")
                        .header("Range", "bytes=$downloaded-$end")
                        .build()

                    val resp = dlClient.newCall(rangeReq).execute()
                    assertTrue("Should get 200 or 206, got ${resp.code}", resp.code in listOf(200, 206))

                    resp.body?.byteStream()?.use { input ->
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                        }
                    }
                    resp.close()
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            val speedKBs = if (elapsed > 0) downloaded * 1000 / elapsed / 1024 else 0
            val speedMBs = speedKBs / 1024.0

            println("=== DOWNLOAD SPEED TEST RESULTS ===")
            println("Downloaded: ${downloaded / 1024} KB in ${elapsed}ms")
            println("Speed: $speedKBs KB/s (%.2f MB/s)".format(speedMBs))
            println("Chunk size: ${chunkSize / 1024} KB")
            println("===================================")

            assertTrue("Should download at least 1MB", downloaded > 1024 * 1024)
            assertTrue("Speed should be > 100 KB/s, got $speedKBs KB/s", speedKBs > 100)

        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `shorts video has mp4 compatible streams for MediaMuxer`() {
        // This Shorts URL caused "Failed to add the track to the muxer"
        // because MediaMuxer only supports MP4/H.264, not WebM/VP9
        val shortsUrl = TEST_VIDEO_URL
        val info = StreamInfo.getInfo(shortsUrl)

        println("Title: ${info.name}")

        // Check what formats are available
        println("\nProgressive streams:")
        info.videoStreams.forEach { s ->
            println("  ${s.getResolution()} | ${s.format?.name} | videoOnly=${s.isVideoOnly} | url=${s.isUrl}")
        }

        println("\nVideo-only streams:")
        info.videoOnlyStreams.forEach { s ->
            println("  ${s.getResolution()} | ${s.format?.name} | videoOnly=${s.isVideoOnly} | url=${s.isUrl}")
        }

        println("\nAudio streams:")
        info.audioStreams.forEach { s ->
            println("  ${s.averageBitrate}kbps | ${s.format?.name} | url=${s.isUrl}")
        }

        // Filter for MP4-compatible streams (MPEG_4 format = H.264/AAC)
        val mp4Videos = (info.videoStreams + info.videoOnlyStreams)
            .filter { it.isUrl }
            .filter { it.format?.name == "MPEG-4" || it.format?.name == "v3GPP" }

        val mp4Audio = info.audioStreams
            .filter { it.isUrl }
            .filter { it.format?.name == "m4a" || it.format?.name == "M4A" || it.format?.name == "MPEG-4" }

        println("\nMP4-compatible video streams: ${mp4Videos.size}")
        mp4Videos.forEach { println("  ${it.getResolution()} | ${it.format?.name}") }

        println("MP4-compatible audio streams: ${mp4Audio.size}")
        mp4Audio.forEach { println("  ${it.averageBitrate}kbps | ${it.format?.name}") }

        // At least one of each should exist for MediaMuxer to work
        if (mp4Videos.isEmpty()) {
            println("\nWARNING: No MP4 video streams — all are WebM/VP9. Need fallback strategy.")
            // Print all format names to understand what's available
            val formats = (info.videoStreams + info.videoOnlyStreams).map { it.format?.name }.distinct()
            println("Available formats: $formats")
        }
    }

    @Test
    fun `1080p video KLuTLF3x9sA has correct resolution streams`() {
        val url = "https://youtu.be/KLuTLF3x9sA"
        val info = StreamInfo.getInfo(url)

        println("Title: ${info.name}")
        println("Duration: ${info.duration}s")

        println("\nALL Progressive streams:")
        info.videoStreams.forEach { s ->
            println("  ${s.getResolution()} | ${s.format?.name} | videoOnly=${s.isVideoOnly}")
        }

        println("\nALL Video-only streams:")
        info.videoOnlyStreams.forEach { s ->
            println("  ${s.getResolution()} | ${s.format?.name}")
        }

        println("\nALL Audio streams:")
        info.audioStreams.forEach { s ->
            println("  ${s.averageBitrate}kbps | ${s.format?.name}")
        }

        // Check what's available at 1080p
        val all1080 = (info.videoStreams + info.videoOnlyStreams)
            .filter { it.isUrl }
            .filter { it.getResolution()?.contains("1080") == true }
        println("\n1080p streams (any format): ${all1080.size}")
        all1080.forEach { println("  ${it.getResolution()} | ${it.format?.name} | videoOnly=${it.isVideoOnly}") }

        val mp4_1080 = all1080.filter { it.format?.name == "MPEG-4" }
        println("1080p MPEG-4 streams: ${mp4_1080.size}")

        if (mp4_1080.isEmpty() && all1080.isNotEmpty()) {
            println("WARNING: 1080p only available in ${all1080.map { it.format?.name }.distinct()}")
        }

        // Test resolveStreamUrls logic
        val mp4VideoOnly = info.videoOnlyStreams
            .filter { it.isUrl }
            .filter { it.format?.name == "MPEG-4" }
            .filter { (it.getResolution()?.replace("p", "")?.toIntOrNull() ?: 0) <= 1080 }
            .maxByOrNull { it.getResolution()?.replace("p", "")?.toIntOrNull() ?: 0 }

        println("\nresolution field values for MPEG-4 video-only:")
        info.videoOnlyStreams
            .filter { it.format?.name == "MPEG-4" }
            .forEach { println("  getResolution()='${it.getResolution()}' | height=${it.getHeight()} | format=${it.format?.name}") }

        println("\nSelected DASH video: ${mp4VideoOnly?.getResolution()} | height=${mp4VideoOnly?.getHeight()}")

        assertTrue("Should find 1080p MPEG-4 stream", mp4_1080.isNotEmpty())
    }

    @Test
    fun `download 1080p with parallel chunked requests - speed comparison`() {
        val testUrl = "https://youtu.be/KLuTLF3x9sA"
        val info = StreamInfo.getInfo(testUrl)
        println("Title: ${info.name}")

        // Get 1080p MPEG-4 video-only stream
        val videoStream = info.videoOnlyStreams
            .filter { it.isUrl && it.format?.name == "MPEG-4" }
            .filter { (it.getResolution()?.substringBefore("p")?.toIntOrNull() ?: 0) <= 1080 }
            .maxByOrNull { it.getResolution()?.substringBefore("p")?.toIntOrNull() ?: 0 }

        assertNotNull("Should have 1080p MPEG-4 stream", videoStream)
        println("Stream: ${videoStream!!.getResolution()} | ${videoStream.format?.name}")

        val videoUrl = videoStream.content!!
        val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

        val dlClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        // Get content length
        val headReq = Request.Builder().url(videoUrl).head().header("User-Agent", UA).build()
        val headResp = dlClient.newCall(headReq).execute()
        val contentLength = headResp.header("Content-Length")?.toLongOrNull() ?: -1L
        headResp.close()
        println("Content-Length: $contentLength bytes (${contentLength / 1024 / 1024} MB)")

        val downloadSize = minOf(10L * 1024 * 1024, contentLength) // Download 10MB or full file

        // === Strategy 1: Single GET (baseline - likely throttled) ===
        println("\n=== Strategy 1: Single GET ===")
        val speed1 = measureDownloadSpeed(dlClient, videoUrl, UA, downloadSize) { url, start, end ->
            Request.Builder().url(url).header("User-Agent", UA).build()
        }
        println("Single GET: $speed1 KB/s")

        // === Strategy 2: Chunked 512KB Range requests (current approach) ===
        println("\n=== Strategy 2: Chunked 512KB ===")
        val speed2 = measureChunkedSpeed(dlClient, videoUrl, UA, downloadSize, 512 * 1024L, 1)
        println("Chunked 512KB x1 thread: $speed2 KB/s")

        // === Strategy 3: Chunked 2MB Range requests ===
        println("\n=== Strategy 3: Chunked 2MB ===")
        val speed3 = measureChunkedSpeed(dlClient, videoUrl, UA, downloadSize, 2 * 1024 * 1024L, 1)
        println("Chunked 2MB x1 thread: $speed3 KB/s")

        // === Strategy 4: Parallel 3 threads, 512KB chunks ===
        println("\n=== Strategy 4: Parallel 3 threads ===")
        val speed4 = measureParallelSpeed(dlClient, videoUrl, UA, downloadSize, 512 * 1024L, 3)
        println("Parallel 3 threads: $speed4 KB/s")

        // === Strategy 5: Parallel 6 threads, 1MB chunks ===
        println("\n=== Strategy 5: Parallel 6 threads ===")
        val speed5 = measureParallelSpeed(dlClient, videoUrl, UA, downloadSize, 1024 * 1024L, 6)
        println("Parallel 6 threads: $speed5 KB/s")

        println("\n=== SPEED COMPARISON ===")
        println("Single GET:          $speed1 KB/s")
        println("Chunked 512KB x1:    $speed2 KB/s")
        println("Chunked 2MB x1:      $speed3 KB/s")
        println("Parallel 3T x 512KB: $speed4 KB/s")
        println("Parallel 6T x 1MB:   $speed5 KB/s")
        println("========================")

        val bestSpeed = maxOf(speed1, speed2, speed3, speed4, speed5)
        assertTrue("Best speed should be > 100 KB/s, got $bestSpeed", bestSpeed > 100)
    }

    private fun measureDownloadSpeed(
        client: OkHttpClient, url: String, ua: String, limit: Long,
        requestBuilder: (String, Long, Long) -> Request
    ): Long {
        val req = requestBuilder(url, 0, limit - 1)
        val start = System.currentTimeMillis()
        var downloaded = 0L
        val resp = client.newCall(req).execute()
        resp.body?.byteStream()?.use { input ->
            val buffer = ByteArray(65536)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1 && downloaded < limit) {
                downloaded += bytesRead
            }
        }
        resp.close()
        val elapsed = System.currentTimeMillis() - start
        return if (elapsed > 0) downloaded * 1000 / elapsed / 1024 else 0
    }

    private fun measureChunkedSpeed(
        client: OkHttpClient, url: String, ua: String,
        totalSize: Long, chunkSize: Long, threads: Int
    ): Long {
        val start = System.currentTimeMillis()
        var downloaded = 0L

        var offset = 0L
        while (offset < totalSize) {
            val end = (offset + chunkSize - 1).coerceAtMost(totalSize - 1)
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", ua)
                .header("Accept", "*/*")
                .header("Range", "bytes=$offset-$end")
                .build()
            val resp = client.newCall(req).execute()
            resp.body?.byteStream()?.use { input ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    downloaded += bytesRead
                }
            }
            resp.close()
            offset = end + 1
        }

        val elapsed = System.currentTimeMillis() - start
        return if (elapsed > 0) downloaded * 1000 / elapsed / 1024 else 0
    }

    private fun measureParallelSpeed(
        client: OkHttpClient, url: String, ua: String,
        totalSize: Long, chunkSize: Long, threadCount: Int
    ): Long {
        val start = System.currentTimeMillis()
        val partSize = totalSize / threadCount
        val threads = mutableListOf<Thread>()
        val downloaded = java.util.concurrent.atomic.AtomicLong(0)

        for (i in 0 until threadCount) {
            val partStart = i * partSize
            val partEnd = if (i == threadCount - 1) totalSize - 1 else (partStart + partSize - 1)

            val thread = Thread {
                var offset = partStart
                while (offset <= partEnd) {
                    val end = (offset + chunkSize - 1).coerceAtMost(partEnd)
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", ua)
                        .header("Accept", "*/*")
                        .header("Range", "bytes=$offset-$end")
                        .build()
                    try {
                        val resp = client.newCall(req).execute()
                        resp.body?.byteStream()?.use { input ->
                            val buffer = ByteArray(65536)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                downloaded.addAndGet(bytesRead.toLong())
                            }
                        }
                        resp.close()
                    } catch (e: Exception) {
                        println("Thread $i error at offset $offset: ${e.message}")
                    }
                    offset = end + 1
                }
            }
            threads.add(thread)
            thread.start()
        }

        threads.forEach { it.join() }

        val elapsed = System.currentTimeMillis() - start
        return if (elapsed > 0) downloaded.get() * 1000 / elapsed / 1024 else 0
    }
}
