package com.aerotech.upieasy.core.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val TAG = "SyncScheduler"
    private const val PERIODIC_WORK_NAME = "upi_easy_periodic_sync"
    private const val IMMEDIATE_WORK_NAME = "upi_easy_immediate_sync"

    /**
     * Schedules periodic background sync every 15 minutes (Android WorkManager minimum interval)
     * with network constraints and exponential backoff.
     */
    fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSyncRequest
        )
        Log.d(TAG, "Periodic sync scheduled (15 min interval)")
    }

    /**
     * Triggers an immediate one-time background sync when app enters foreground,
     * switches organizations, or receives an FCM push notification.
     */
    fun triggerImmediateSync(context: Context, organizationId: String? = null) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val dataBuilder = Data.Builder()
        if (!organizationId.isNullOrBlank()) {
            dataBuilder.putString("organizationId", organizationId)
        }

        val oneTimeSyncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(dataBuilder.build())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            oneTimeSyncRequest
        )
        Log.d(TAG, "Immediate sync enqueued for org: $organizationId")
    }
}
