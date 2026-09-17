package com.aerospace.upieasy

import android.app.Application
import androidx.work.*
import com.aerospace.upieasy.core.sync.SyncWorker
import java.util.concurrent.TimeUnit

class UPIEasyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleBackgroundSync()
    }

    private fun scheduleBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UPIEasyDeltaSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
