package com.santttal.youtubedownloader.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.santttal.youtubedownloader.ui.download.DownloadScreen
import com.santttal.youtubedownloader.ui.splash.SplashScreen
import com.santttal.youtubedownloader.ui.theme.AppTheme
import com.santttal.youtubedownloader.util.UrlValidator

class MainActivity : ComponentActivity() {

    private var sharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedUrl = extractSupportedUrl(intent)
        setContent {
            AppTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "splash"
                ) {
                    composable("splash") {
                        SplashScreen(onReady = {
                            navController.navigate("download") {
                                popUpTo("splash") { inclusive = true }
                            }
                        })
                    }
                    composable("download") {
                        DownloadScreen(initialUrl = sharedUrl)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUrl = extractSupportedUrl(intent)
    }

    private fun extractSupportedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type != "text/plain") return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.let { UrlValidator.extractSupportedUrl(it) }
    }
}
