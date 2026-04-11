package com.santttal.videodownloader.domain

import com.santttal.videodownloader.data.DownloadRepository
import com.santttal.videodownloader.model.VideoInfo

class VideoInfoUseCase(private val repository: DownloadRepository) {

    suspend fun execute(url: String): VideoInfo = repository.getVideoInfo(url)
}
