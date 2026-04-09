package com.santttal.youtubedownloader.model

enum class Quality(val label: String, val formatString: String) {
    Q360P(
        label = "360p",
        formatString = "bv*[height<=360][vcodec^=avc1][ext=mp4]+ba[acodec*=mp4a][ext=m4a]/b[height<=360][ext=mp4]/b[height<=360]"
    ),
    Q720P(
        label = "720p",
        formatString = "bv*[height<=720][vcodec^=avc1][ext=mp4]+ba[acodec*=mp4a][ext=m4a]/b[height<=720][ext=mp4]/b[height<=720]"
    ),
    Q1080P(
        label = "1080p",
        formatString = "bv*[height<=1080][vcodec^=avc1][ext=mp4]+ba[acodec*=mp4a][ext=m4a]/b[height<=1080][ext=mp4]/b[height<=1080]"
    ),
    MP3(
        label = "MP3",
        formatString = "bestaudio/best"
    );

    val isAudioOnly: Boolean get() = this == MP3
    val fileExtension: String get() = if (isAudioOnly) "mp3" else "mp4"
    val mimeType: String get() = if (isAudioOnly) "audio/mpeg" else "video/mp4"
}
