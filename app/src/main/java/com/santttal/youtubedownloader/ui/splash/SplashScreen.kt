package com.santttal.youtubedownloader.ui.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Composable
fun SplashScreen(onReady: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("Подготовка...") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                withTimeout(120_000L) {
                    statusText = "Обновление yt-dlp..."
                    // Use NIGHTLY channel — YouTube changes API frequently,
                    // stable releases lag behind
                    val status = YoutubeDL.getInstance().updateYoutubeDL(
                        context,
                        YoutubeDL.UpdateChannel._NIGHTLY
                    )
                    android.util.Log.d("SplashScreen", "yt-dlp update result: $status")
                    progress = 1f
                }
                statusText = "Готово"
            } catch (e: Exception) {
                android.util.Log.e("SplashScreen", "yt-dlp update failed", e)
                statusText = "Обновление пропущено"
            }
        }
        onReady()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "YouTube Downloader",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
