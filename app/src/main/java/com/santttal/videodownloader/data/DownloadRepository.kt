package com.santttal.videodownloader.data

import com.santttal.videodownloader.model.Quality
import com.santttal.videodownloader.model.StreamUrls
import com.santttal.videodownloader.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

class DownloadRepository {

    suspend fun getVideoInfo(url: String): VideoInfo = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(url)
        VideoInfo(
            title = info.name ?: "Unknown",
            thumbnailUrl = info.thumbnails.firstOrNull()?.url ?: "",
            durationSeconds = info.duration
        )
    }

    suspend fun resolveStreamUrls(url: String, quality: Quality): StreamUrls = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(url)
        val title = info.name ?: "download"

        if (quality == Quality.MP3) {
            val audioStream = info.audioStreams
                .filter { it.isUrl }
                .maxByOrNull { it.averageBitrate }
            return@withContext StreamUrls(
                videoUrl = null,
                audioUrl = audioStream?.content,
                title = title,
                isVideoOnly = false,
                needsMux = false,
                isWebm = false
            )
        }

        val targetHeight = quality.heightPx

        // Debug: log ALL available streams
        android.util.Log.d("DownloadRepo", "=== ALL STREAMS for $url ===")
        android.util.Log.d("DownloadRepo", "Progressive: ${info.videoStreams.size}")
        info.videoStreams.forEach { s ->
            android.util.Log.d("DownloadRepo", "  P: ${s.getResolution()} | ${s.format?.name} | videoOnly=${s.isVideoOnly}")
        }
        android.util.Log.d("DownloadRepo", "Video-only: ${info.videoOnlyStreams.size}")
        info.videoOnlyStreams.forEach { s ->
            android.util.Log.d("DownloadRepo", "  V: ${s.getResolution()} | ${s.format?.name}")
        }
        android.util.Log.d("DownloadRepo", "Audio: ${info.audioStreams.size}")
        info.audioStreams.forEach { s ->
            android.util.Log.d("DownloadRepo", "  A: ${s.averageBitrate}kbps | ${s.format?.name}")
        }

        // Step 1: Try MPEG-4 streams (best for MediaMuxer)
        val mp4VideoOnly = info.videoOnlyStreams
            .filter { it.isUrl && it.format?.name == "MPEG-4" }
            .filter { extractHeight(it) <= targetHeight }
            .maxByOrNull { extractHeight(it) }

        val mp4Audio = info.audioStreams
            .filter { it.isUrl }
            .filter { it.format?.name == "m4a" || it.format?.name == "M4A" || it.format?.name == "MPEG-4" }
            .maxByOrNull { it.averageBitrate }

        val mp4Progressive = info.videoStreams
            .filter { it.isUrl && !it.isVideoOnly && it.format?.name == "MPEG-4" }
            .filter { extractHeight(it) <= targetHeight }
            .maxByOrNull { extractHeight(it) }

        val mp4DashHeight = mp4VideoOnly?.let { extractHeight(it) } ?: 0
        val mp4ProgressiveHeight = mp4Progressive?.let { extractHeight(it) } ?: 0
        val bestMp4Height = maxOf(mp4DashHeight, mp4ProgressiveHeight)

        // Step 2: If MP4 doesn't reach target, try WebM (VP9 — higher quality available)
        val webmVideoOnly = info.videoOnlyStreams
            .filter { it.isUrl && it.format?.name == "WebM" }
            .filter { extractHeight(it) <= targetHeight }
            .maxByOrNull { extractHeight(it) }

        val webmAudio = info.audioStreams
            .filter { it.isUrl }
            .filter { it.format?.name == "WebM Opus" || it.format?.name?.contains("WebM") == true }
            .maxByOrNull { it.averageBitrate }

        val webmDashHeight = webmVideoOnly?.let { extractHeight(it) } ?: 0

        android.util.Log.d("DownloadRepo", "Target: ${targetHeight}p | MP4 DASH: ${mp4DashHeight}p | MP4 progressive: ${mp4ProgressiveHeight}p | WebM DASH: ${webmDashHeight}p")

        // Decision: use whichever format reaches the target resolution (or closest)
        when {
            // MP4 DASH reaches target or is best — use MediaMuxer
            mp4DashHeight >= targetHeight || (mp4DashHeight >= webmDashHeight && mp4VideoOnly != null && mp4Audio != null) -> {
                if (mp4DashHeight > mp4ProgressiveHeight && mp4VideoOnly != null && mp4Audio != null) {
                    StreamUrls(videoUrl = mp4VideoOnly.content, audioUrl = mp4Audio.content,
                        title = title, isVideoOnly = true, needsMux = true, isWebm = false)
                } else if (mp4Progressive != null) {
                    StreamUrls(videoUrl = mp4Progressive.content, audioUrl = null,
                        title = title, isVideoOnly = false, needsMux = false, isWebm = false)
                } else {
                    StreamUrls(videoUrl = mp4VideoOnly?.content, audioUrl = mp4Audio?.content,
                        title = title, isVideoOnly = true, needsMux = mp4VideoOnly != null && mp4Audio != null, isWebm = false)
                }
            }
            // WebM DASH is higher quality — use MediaMuxer WEBM output
            webmDashHeight > bestMp4Height && webmVideoOnly != null && webmAudio != null -> {
                StreamUrls(videoUrl = webmVideoOnly.content, audioUrl = webmAudio.content,
                    title = title, isVideoOnly = true, needsMux = true, isWebm = true)
            }
            // MP4 progressive fallback
            mp4Progressive != null -> {
                StreamUrls(videoUrl = mp4Progressive.content, audioUrl = null,
                    title = title, isVideoOnly = false, needsMux = false, isWebm = false)
            }
            // Any DASH fallback
            mp4VideoOnly != null && mp4Audio != null -> {
                StreamUrls(videoUrl = mp4VideoOnly.content, audioUrl = mp4Audio.content,
                    title = title, isVideoOnly = true, needsMux = true, isWebm = false)
            }
            webmVideoOnly != null && webmAudio != null -> {
                StreamUrls(videoUrl = webmVideoOnly.content, audioUrl = webmAudio.content,
                    title = title, isVideoOnly = true, needsMux = true, isWebm = true)
            }
            else -> {
                // Last resort: take any stream
                val anyVideo = info.videoStreams.firstOrNull { it.isUrl }
                    ?: info.videoOnlyStreams.firstOrNull { it.isUrl }
                val anyAudio = info.audioStreams.firstOrNull { it.isUrl }
                StreamUrls(videoUrl = anyVideo?.content, audioUrl = anyAudio?.content,
                    title = title, isVideoOnly = anyVideo?.isVideoOnly == true,
                    needsMux = false, isWebm = false)
            }
        }
    }

    private fun extractHeight(stream: VideoStream): Int {
        // Resolution can be "1080p", "1080p60", "720p60" etc.
        // Extract just the number before 'p'
        val res = stream.resolution ?: return 0
        return res.substringBefore("p").toIntOrNull() ?: 0
    }
}
