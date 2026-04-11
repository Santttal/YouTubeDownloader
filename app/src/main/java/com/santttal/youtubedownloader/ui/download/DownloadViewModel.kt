package com.santttal.youtubedownloader.ui.download

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.santttal.youtubedownloader.data.DownloadRepository
import com.santttal.youtubedownloader.domain.StartDownloadUseCase
import com.santttal.youtubedownloader.domain.VideoInfoUseCase
import com.santttal.youtubedownloader.model.Quality
import com.santttal.youtubedownloader.model.VideoInfo
import com.santttal.youtubedownloader.worker.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed class DownloadState {
    object Idle : DownloadState()
    object Resolving : DownloadState() // Getting stream URLs from YouTube
    data class Running(val progress: Int, val processId: String, val speedText: String = "") : DownloadState()
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
    val clipboardUrl: String = "",
    val pendingDownload: Boolean = false
)

class DownloadViewModel(
    private val context: Context,
    private val videoInfoUseCase: VideoInfoUseCase,
    private val downloadRepository: DownloadRepository,
    private val startDownloadUseCase: StartDownloadUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    private var lastProgressTime: Long = 0L
    private var lastProgressValue: Int = 0

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
        if (state.url.isBlank()) return
        if (state.downloadState is DownloadState.Running) return

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                _uiState.update { it.copy(pendingDownload = true) }
                return
            }
        }
        doStartDownload()
    }

    private fun doStartDownload() {
        val state = _uiState.value
        val url = state.url
        if (url.isBlank()) return
        val processId = UUID.randomUUID().toString()
        lastProgressTime = 0L
        lastProgressValue = 0
        _uiState.update { it.copy(downloadState = DownloadState.Resolving) }

        viewModelScope.launch {
            try {
                val streamUrls = downloadRepository.resolveStreamUrls(url, state.selectedQuality)
                _uiState.update { it.copy(downloadState = DownloadState.Running(0, processId)) }
                startDownloadUseCase.execute(streamUrls, state.selectedQuality.isAudioOnly, processId)
                observeDownloadWork()
            } catch (e: Exception) {
                android.util.Log.e("DownloadVM", "Stream resolution failed", e)
                _uiState.update { it.copy(downloadState = DownloadState.Failed(e.message ?: "Stream resolution error")) }
            }
        }
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
                                val now = System.currentTimeMillis()
                                val elapsed = now - lastProgressTime
                                val speedText = if (elapsed > 0 && lastProgressTime > 0) {
                                    val progressDelta = progress - lastProgressValue
                                    if (progressDelta > 0) {
                                        val bytesPerSec = (progressDelta.toLong() * 1_000_000L * 1000L) / (elapsed * 100L)
                                        formatSpeed(bytesPerSec)
                                    } else running.speedText
                                } else running.speedText
                                if (progress != lastProgressValue || elapsed > 1000) {
                                    lastProgressTime = now
                                    lastProgressValue = progress
                                }
                                _uiState.update { it.copy(downloadState = running.copy(progress = progress, speedText = speedText)) }
                            }
                        }
                        else -> {}
                    }
                }
        }
    }

    fun cancelDownload() {
        val running = _uiState.value.downloadState as? DownloadState.Running ?: return
        WorkManager.getInstance(context).cancelUniqueWork("yt-download")
        _uiState.update { it.copy(downloadState = DownloadState.Cancelled) }
    }

    fun onNotificationPermissionGranted() {
        _uiState.update { it.copy(pendingDownload = false) }
        doStartDownload()
    }

    fun onNotificationPermissionDenied() {
        _uiState.update { it.copy(pendingDownload = false) }
        doStartDownload()
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

    private fun formatSpeed(bytesPerSec: Long): String {
        return if (bytesPerSec >= 1_048_576L) {
            "%.1f MB/s".format(bytesPerSec / 1_048_576.0)
        } else if (bytesPerSec >= 1024L) {
            "%.0f KB/s".format(bytesPerSec / 1024.0)
        } else {
            "$bytesPerSec B/s"
        }
    }
}
