package com.santttal.youtubedownloader.util

object FilenameUtils {
    private val ILLEGAL_CHARS = Regex("""[\\/:*?"<>|]""")

    /**
     * Produce a filesystem-safe filename from a video title.
     * Example: "My Video: Part 1!" -> "My Video_ Part 1!.mp4"
     */
    fun buildFilename(title: String, extension: String): String {
        val sanitized = ILLEGAL_CHARS.replace(title, "_").trim().take(200)
        return "$sanitized.$extension"
    }
}
