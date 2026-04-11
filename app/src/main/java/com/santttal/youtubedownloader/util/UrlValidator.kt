package com.santttal.youtubedownloader.util

object UrlValidator {
    private val YOUTUBE_PATTERN = Regex(
        """https?://(www\.)?(youtube\.com/(watch\?v=|shorts/|embed/)|youtu\.be/)[\w\-]{11}[\w\-&=?%]*"""
    )

    private val INSTAGRAM_PATTERN = Regex(
        """https?://(www\.)?instagram\.com/(reel|p|tv)/[\w\-]+"""
    )

    fun extractYouTubeUrl(text: String): String? =
        YOUTUBE_PATTERN.find(text)?.value

    fun extractInstagramUrl(text: String): String? =
        INSTAGRAM_PATTERN.find(text)?.value

    /** Extract first supported URL (YouTube or Instagram) from text. */
    fun extractSupportedUrl(text: String): String? =
        extractYouTubeUrl(text) ?: extractInstagramUrl(text)

    fun isYouTubeUrl(url: String): Boolean =
        YOUTUBE_PATTERN.containsMatchIn(url)

    fun isInstagramUrl(url: String): Boolean =
        INSTAGRAM_PATTERN.containsMatchIn(url)

    fun isSupportedUrl(url: String): Boolean =
        isYouTubeUrl(url) || isInstagramUrl(url)
}
