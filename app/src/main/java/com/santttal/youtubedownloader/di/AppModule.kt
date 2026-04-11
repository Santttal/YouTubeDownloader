package com.santttal.youtubedownloader.di

import com.santttal.youtubedownloader.data.DownloadRepository
import com.santttal.youtubedownloader.domain.StartDownloadUseCase
import com.santttal.youtubedownloader.domain.VideoInfoUseCase
import com.santttal.youtubedownloader.ui.download.DownloadViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { DownloadRepository() }
    factory { VideoInfoUseCase(get()) }
    factory { StartDownloadUseCase(androidContext()) }
    viewModel { DownloadViewModel(androidContext(), get(), get(), get()) }
}
