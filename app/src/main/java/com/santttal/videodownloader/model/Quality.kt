package com.santttal.videodownloader.model

enum class Quality(val label: String, val heightPx: Int) {
    Q360P(label = "360p", heightPx = 360),
    Q720P(label = "720p", heightPx = 720),
    Q1080P(label = "1080p", heightPx = 1080),
    MP3(label = "MP3", heightPx = 0);

    val isAudioOnly: Boolean get() = this == MP3
    val fileExtension: String get() = if (isAudioOnly) "m4a" else "mp4"
    val mimeType: String get() = if (isAudioOnly) "audio/mp4" else "video/mp4"
}
