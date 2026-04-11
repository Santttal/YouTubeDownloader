package com.santttal.videodownloader.di

import com.santttal.videodownloader.data.DownloadRepository
import com.santttal.videodownloader.data.InstagramRepository
import com.santttal.videodownloader.domain.StartDownloadUseCase
import com.santttal.videodownloader.domain.VideoInfoUseCase
import com.santttal.videodownloader.ui.download.DownloadViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { DownloadRepository() }
    single { InstagramRepository() }
    factory { VideoInfoUseCase(get()) }
    factory { StartDownloadUseCase(androidContext()) }
    viewModel { DownloadViewModel(androidContext(), get(), get(), get(), get()) }
}
