package com.santttal.youtubedownloader.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DownloadWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Stub worker running in background — WorkManager wired correctly (Phase 1)")
        // Phase 2: real download logic replaces this stub
        return Result.success()
    }

    companion object {
        const val TAG = "DownloadWorker"
    }
}
