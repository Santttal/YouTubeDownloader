package com.santttal.youtubedownloader.di

import com.santttal.youtubedownloader.ui.download.DownloadViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { DownloadViewModel() }
    // Phase 2: single { DownloadRepository(get()) }
}
