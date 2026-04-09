package com.santttal.youtubedownloader.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.santttal.youtubedownloader.ui.download.DownloadScreen
import com.santttal.youtubedownloader.ui.splash.SplashScreen
import com.santttal.youtubedownloader.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                        DownloadScreen()
                    }
                }
            }
        }
    }
}
