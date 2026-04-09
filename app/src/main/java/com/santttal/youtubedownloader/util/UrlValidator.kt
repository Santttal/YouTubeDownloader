package com.santttal.youtubedownloader.util

object UrlValidator {
    private val YOUTUBE_PATTERN = Regex(
        """https?://(www\.)?(youtube\.com/(watch\?v=|shorts/|embed/)|youtu\.be/)[\w\-]{11}[\w\-&=?%]*"""
    )

    /** Extract first YouTube URL found in arbitrary text (clipboard content, share payload). */
    fun extractYouTubeUrl(text: String): String? =
        YOUTUBE_PATTERN.find(text)?.value

    /** True if the string itself is a valid YouTube URL. */
    fun isValidYouTubeUrl(url: String): Boolean =
        YOUTUBE_PATTERN.matches(url) || extractYouTubeUrl(url) == url.trim()
}
