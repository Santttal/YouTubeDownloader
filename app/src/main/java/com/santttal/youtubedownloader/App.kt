package com.santttal.youtubedownloader

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.santttal.youtubedownloader.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // YoutubeDL must be initialized before any Worker or Activity uses it.
        // Crash prevention: catch YoutubeDLException here; update is retried on SplashScreen.
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
        } catch (e: YoutubeDLException) {
            Log.e("App", "YoutubeDL/FFmpeg init failed — will retry on SplashScreen", e)
        }
        startKoin {
            androidContext(this@App)
            modules(appModule)
        }
    }
}
