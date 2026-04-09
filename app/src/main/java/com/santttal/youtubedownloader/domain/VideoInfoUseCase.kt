package com.santttal.youtubedownloader.domain

import com.santttal.youtubedownloader.data.DownloadRepository
import com.santttal.youtubedownloader.model.VideoInfo

class VideoInfoUseCase(private val repository: DownloadRepository) {

    suspend fun execute(url: String): VideoInfo = repository.getVideoInfo(url)
}
