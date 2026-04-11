package com.santttal.youtubedownloader.model

data class StreamUrls(
    val videoUrl: String?,
    val audioUrl: String?,
    val title: String,
    val isVideoOnly: Boolean,
    val needsMux: Boolean
)
