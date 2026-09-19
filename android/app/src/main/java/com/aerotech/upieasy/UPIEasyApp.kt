package com.aerotech.upieasy

import android.app.Application
import android.util.Log
import androidx.work.*
import com.aerotech.upieasy.core.sync.SyncWorker
import java.util.concurrent.TimeUnit

class UPIEasyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("UPIEasyFatal", "FATAL EXCEPTION on thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        scheduleBackgroundSync()
    }

    private fun scheduleBackgroundSync() {
        try {
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
        } catch (e: Throwable) {
            Log.e("UPIEasyApp", "Failed to schedule background sync on startup: ${e.message}", e)
        }
    }
}
