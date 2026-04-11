package com.santttal.youtubedownloader.ui.download

import android.content.ClipboardManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.santttal.youtubedownloader.model.Quality
import com.santttal.youtubedownloader.util.UrlValidator
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel = koinViewModel(),
    initialUrl: String? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.onNotificationPermissionGranted()
        else viewModel.onNotificationPermissionDenied()
    }

    LaunchedEffect(initialUrl) {
        if (initialUrl != null) {
            viewModel.onShareUrlReceived(initialUrl)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: return@LifecycleEventEffect
        val url = UrlValidator.extractSupportedUrl(text) ?: return@LifecycleEventEffect
        if (url != uiState.url) {
            viewModel.onClipboardUrlDetected(url)
        }
    }

    LaunchedEffect(uiState.pendingDownload) {
        if (uiState.pendingDownload &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(uiState.clipboardSnackbarVisible) {
        if (uiState.clipboardSnackbarVisible) {
            val result = snackbarHostState.showSnackbar(
                message = "Ссылка на видео обнаружена",
                actionLabel = "Вставить",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onClipboardPasted()
            }
            viewModel.onClipboardSnackbarDismissed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("YouTube Downloader") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            OutlinedTextField(
                value = uiState.url,
                onValueChange = viewModel::onUrlChanged,
                label = { Text("Вставьте ссылку YouTube / Instagram") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { viewModel.fetchVideoInfo() }
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.infoLoading || uiState.videoInfo != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    if (uiState.infoLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (uiState.videoInfo != null) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            AsyncImage(
                                model = uiState.videoInfo!!.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(120.dp, 68.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = uiState.videoInfo!!.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = formatDuration(uiState.videoInfo!!.durationSeconds),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.infoError != null) {
                Text(
                    text = "Ошибка: ${uiState.infoError}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Quality chips only for YouTube (Instagram has no quality selection)
            if (!UrlValidator.isInstagramUrl(uiState.url)) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Quality.entries.forEach { quality ->
                        FilterChip(
                            selected = uiState.selectedQuality == quality,
                            onClick = { viewModel.onQualitySelected(quality) },
                            label = { Text(quality.label) },
                            leadingIcon = if (quality == Quality.MP3) {
                                { Icon(Icons.Default.MusicNote, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = uiState.downloadState) {
                is DownloadState.Resolving -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Получение ссылки...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                is DownloadState.Running -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val progressLabel = if (state.speedText.isNotEmpty()) {
                            "${state.progress}% · ${state.speedText}"
                        } else {
                            "${state.progress}%"
                        }
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.cancelDownload() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Отмена")
                        }
                    }
                }
                is DownloadState.Done -> {
                    Text(
                        text = "Загрузка завершена! Файл в папке Downloads.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Button(
                        onClick = { viewModel.startDownload() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.videoInfo != null
                    ) {
                        Text("Скачать ещё")
                    }
                }
                is DownloadState.Failed -> {
                    Text(
                        text = "Ошибка: ${state.reason}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Button(
                        onClick = { viewModel.startDownload() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.videoInfo != null
                    ) {
                        Text("Повторить")
                    }
                }
                is DownloadState.Cancelled -> {
                    Text(
                        text = "Загрузка отменена",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Button(
                        onClick = { viewModel.startDownload() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.videoInfo != null
                    ) {
                        Text("Скачать")
                    }
                }
                else -> {
                    Button(
                        onClick = { viewModel.startDownload() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.videoInfo != null
                    ) {
                        Text("Скачать")
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
