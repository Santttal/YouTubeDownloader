package com.santttal.youtubedownloader.di

import com.santttal.youtubedownloader.data.DownloadRepository
import com.santttal.youtubedownloader.domain.StartDownloadUseCase
import com.santttal.youtubedownloader.domain.VideoInfoUseCase
import com.santttal.youtubedownloader.ui.download.DownloadViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { DownloadRepository(androidContext()) }
    factory { VideoInfoUseCase(get()) }
    factory { StartDownloadUseCase(androidContext()) }
    // TODO(02-04): replace with viewModel { DownloadViewModel(get(), get()) } after Plan 04 updates ViewModel
    viewModel { DownloadViewModel() }
}
