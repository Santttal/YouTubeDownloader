package com.santttal.youtubedownloader.data

import com.santttal.youtubedownloader.model.Quality
import com.santttal.youtubedownloader.model.StreamUrls
import com.santttal.youtubedownloader.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.AudioStream
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
                needsMux = false
            )
        }

        val targetHeight = quality.heightPx

        // Filter for MP4-compatible formats only (MediaMuxer can't handle WebM/VP9)
        // MPEG-4 video + m4a audio = safe for MediaMuxer
        val videoOnly = info.videoOnlyStreams
            .filter { it.isUrl }
            .filter { it.format?.name == "MPEG-4" }
            .filter { extractHeight(it) <= targetHeight }
            .maxByOrNull { extractHeight(it) }

        val bestAudio = info.audioStreams
            .filter { it.isUrl }
            .filter { it.format?.name == "m4a" || it.format?.name == "M4A" || it.format?.name == "MPEG-4" }
            .maxByOrNull { it.averageBitrate }

        // Use DASH if it gives us a higher resolution than the best progressive
        val bestProgressive = info.videoStreams
            .filter { it.isUrl && !it.isVideoOnly }
            .filter { it.format?.name == "MPEG-4" }
            .filter { extractHeight(it) <= targetHeight }
            .maxByOrNull { extractHeight(it) }

        val dashHeight = videoOnly?.let { extractHeight(it) } ?: 0
        val progressiveHeight = bestProgressive?.let { extractHeight(it) } ?: 0

        if (dashHeight > progressiveHeight && videoOnly != null && bestAudio != null) {
            // DASH gives higher quality — use video-only + audio, mux with FFmpeg
            StreamUrls(
                videoUrl = videoOnly.content,
                audioUrl = bestAudio.content,
                title = title,
                isVideoOnly = true,
                needsMux = true
            )
        } else if (bestProgressive != null) {
            // Progressive is same or better quality — no muxing needed
            StreamUrls(
                videoUrl = bestProgressive.content,
                audioUrl = null,
                title = title,
                isVideoOnly = false,
                needsMux = false
            )
        } else if (videoOnly != null && bestAudio != null) {
            // Only DASH available
            StreamUrls(
                videoUrl = videoOnly.content,
                audioUrl = bestAudio.content,
                title = title,
                isVideoOnly = true,
                needsMux = true
            )
        } else {
            // Fallback — take whatever is available
            StreamUrls(
                videoUrl = bestProgressive?.content ?: videoOnly?.content,
                audioUrl = bestAudio?.content,
                title = title,
                isVideoOnly = videoOnly != null,
                needsMux = false
            )
        }
    }

    private fun extractHeight(stream: VideoStream): Int {
        return stream.resolution?.replace("p", "")?.toIntOrNull() ?: 0
    }
}
