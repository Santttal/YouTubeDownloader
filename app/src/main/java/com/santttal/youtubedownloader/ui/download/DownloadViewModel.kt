package com.santttal.youtubedownloader.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santttal.youtubedownloader.domain.StartDownloadUseCase
import com.santttal.youtubedownloader.domain.VideoInfoUseCase
import com.santttal.youtubedownloader.model.Quality
import com.santttal.youtubedownloader.model.VideoInfo
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed class DownloadState {
    object Idle : DownloadState()
    data class Running(val progress: Int, val processId: String) : DownloadState()
    object Done : DownloadState()
    data class Failed(val reason: String) : DownloadState()
    object Cancelled : DownloadState()
}

data class DownloadUiState(
    val url: String = "",
    val infoLoading: Boolean = false,
    val videoInfo: VideoInfo? = null,
    val selectedQuality: Quality = Quality.Q720P,
    val downloadState: DownloadState = DownloadState.Idle,
    val clipboardSnackbarVisible: Boolean = false,
    val clipboardUrl: String = ""
)

class DownloadViewModel(
    private val videoInfoUseCase: VideoInfoUseCase,
    private val startDownloadUseCase: StartDownloadUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    fun onUrlChanged(newUrl: String) {
        _uiState.update { it.copy(url = newUrl, videoInfo = null, infoLoading = false) }
    }

    fun onShareUrlReceived(url: String?) {
        if (url.isNullOrBlank()) return
        _uiState.update { it.copy(url = url) }
        fetchVideoInfo(url)
    }

    fun fetchVideoInfo(url: String = _uiState.value.url) {
        if (url.isBlank()) return
        _uiState.update { it.copy(infoLoading = true, videoInfo = null) }
        viewModelScope.launch {
            try {
                val info = videoInfoUseCase.execute(url)
                _uiState.update { it.copy(infoLoading = false, videoInfo = info) }
            } catch (e: Exception) {
                _uiState.update { it.copy(infoLoading = false) }
            }
        }
    }

    fun onQualitySelected(quality: Quality) {
        _uiState.update { it.copy(selectedQuality = quality) }
    }

    fun startDownload() {
        val state = _uiState.value
        val url = state.url
        if (url.isBlank()) return
        if (state.downloadState is DownloadState.Running) return
        val processId = UUID.randomUUID().toString()
        _uiState.update { it.copy(downloadState = DownloadState.Running(0, processId)) }
        startDownloadUseCase.execute(url, state.selectedQuality, processId)
    }

    fun cancelDownload() {
        val running = _uiState.value.downloadState as? DownloadState.Running ?: return
        viewModelScope.launch(Dispatchers.IO) {
            YoutubeDL.getInstance().destroyProcessById(running.processId)
        }
        _uiState.update { it.copy(downloadState = DownloadState.Cancelled) }
    }

    fun onClipboardUrlDetected(url: String) {
        _uiState.update { it.copy(clipboardSnackbarVisible = true, clipboardUrl = url) }
    }

    fun onClipboardPasted() {
        val clipUrl = _uiState.value.clipboardUrl
        _uiState.update { it.copy(url = clipUrl, clipboardSnackbarVisible = false, clipboardUrl = "") }
        fetchVideoInfo(clipUrl)
    }

    fun onClipboardSnackbarDismissed() {
        _uiState.update { it.copy(clipboardSnackbarVisible = false) }
    }
}
