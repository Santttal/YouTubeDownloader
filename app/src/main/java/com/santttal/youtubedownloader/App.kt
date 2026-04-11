package com.santttal.youtubedownloader

import android.app.Application
import android.util.Log
import com.santttal.youtubedownloader.di.appModule
import com.santttal.youtubedownloader.util.NewPipeDownloader
import com.yausername.ffmpeg.FFmpeg
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize NewPipe Extractor (native Java — no Python overhead)
        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        NewPipe.init(
            NewPipeDownloader(okHttpClient),
            Localization("en", "US"),
            ContentCountry("US")
        )

        // FFmpeg is still needed for 1080p DASH muxing and MP3 conversion
        try {
            FFmpeg.getInstance().init(this)
        } catch (e: Exception) {
            Log.e("App", "FFmpeg init failed", e)
        }

        startKoin {
            androidContext(this@App)
            modules(appModule)
        }
    }
}
