package com.santttal.youtubedownloader.ui.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.santttal.youtubedownloader.domain.StartDownloadUseCase
import com.santttal.youtubedownloader.domain.VideoInfoUseCase
import com.santttal.youtubedownloader.model.Quality
import com.santttal.youtubedownloader.model.VideoInfo
import com.santttal.youtubedownloader.worker.DownloadWorker
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
    val infoError: String? = null,
    val selectedQuality: Quality = Quality.Q720P,
    val downloadState: DownloadState = DownloadState.Idle,
    val clipboardSnackbarVisible: Boolean = false,
    val clipboardUrl: String = ""
)

class DownloadViewModel(
    private val context: Context,
    private val videoInfoUseCase: VideoInfoUseCase,
    private val startDownloadUseCase: StartDownloadUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    fun onUrlChanged(newUrl: String) {
        _uiState.update { it.copy(url = newUrl, videoInfo = null, infoLoading = false, infoError = null) }
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
                android.util.Log.e("DownloadVM", "fetchVideoInfo failed", e)
                _uiState.update { it.copy(infoLoading = false, infoError = e.message ?: e.toString()) }
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
        observeDownloadWork()
    }

    private fun observeDownloadWork() {
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow("yt-download")
                .collect { workInfos ->
                    val info = workInfos.firstOrNull() ?: return@collect
                    when (info.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            _uiState.update { it.copy(downloadState = DownloadState.Done) }
                        }
                        WorkInfo.State.FAILED -> {
                            // If user already cancelled, don't overwrite with error
                            if (_uiState.value.downloadState is DownloadState.Cancelled) return@collect
                            val error = info.outputData.getString(DownloadWorker.KEY_ERROR) ?: "Unknown error"
                            _uiState.update { it.copy(downloadState = DownloadState.Failed(error)) }
                        }
                        WorkInfo.State.CANCELLED -> {
                            _uiState.update { it.copy(downloadState = DownloadState.Cancelled) }
                        }
                        WorkInfo.State.RUNNING -> {
                            val progress = info.progress.getInt(DownloadWorker.KEY_PROGRESS, 0)
                            val running = _uiState.value.downloadState as? DownloadState.Running
                            if (running != null) {
                                _uiState.update { it.copy(downloadState = running.copy(progress = progress)) }
                            }
                        }
                        else -> { /* ENQUEUED, BLOCKED — ignore */ }
                    }
                }
        }
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
