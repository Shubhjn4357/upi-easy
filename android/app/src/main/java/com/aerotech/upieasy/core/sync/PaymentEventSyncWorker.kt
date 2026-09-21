package com.aerotech.upieasy.core.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.network.ObservedSourceDto
import com.aerotech.upieasy.core.network.PostObservedPaymentEventRequest
import com.aerotech.upieasy.core.security.SessionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class PaymentEventSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "PaymentEventSyncWorker"
        private const val WORK_NAME = "upi_easy_payment_event_sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<PaymentEventSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
            Log.d(TAG, "Enqueued PaymentEventSyncWorker")
        }
    }

    override suspend fun doWork(): Result {
        val sessionManager = SessionManager(applicationContext)
        val database = AppDatabase.getInstance(applicationContext)
        val apiService = NetworkClient.getApiService(sessionManager)

        val pendingEvents = database.observedPaymentEventDao().getPendingUploadEvents(limit = 20)
        if (pendingEvents.isEmpty()) {
            return Result.success()
        }

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        var anyFailed = false

        for (event in pendingEvents) {
            val orgId = event.organizationId ?: sessionManager.getCurrentOrgId()
            if (orgId == null) {
                Log.w(TAG, "Skipping event ${event.id}: Missing organization ID")
                continue
            }

            try {
                val sourceType = when {
                    event.sourcePackage.contains("phonepe") -> "NOTIFICATION_PHONEPE"
                    event.sourcePackage.contains("paisa") -> "NOTIFICATION_GPAY"
                    else -> "NOTIFICATION_PHONEPE"
                }

                val req = PostObservedPaymentEventRequest(
                    clientEventId = event.id,
                    source = ObservedSourceDto(
                        type = sourceType,
                        packageName = event.sourcePackage
                    ),
                    paymentAccountId = event.paymentAccountId,
                    qrId = event.qrId,
                    amountMinor = event.amountMinor,
                    currency = event.currency,
                    direction = event.direction,
                    payerName = event.payerName,
                    payerVpa = event.payerVpa,
                    reference = event.reference,
                    observedAt = isoFormat.format(Date(event.observedAt)),
                    verificationStatus = "OBSERVED",
                    matchStatus = event.matchStatus,
                    fingerprint = event.eventFingerprint
                )

                val response = apiService.postObservedPaymentEvent(orgId, req)
                if (response.isSuccessful && response.body()?.accepted == true) {
                    database.observedPaymentEventDao().updateSyncState(event.id, "SYNCED")
                    Log.i(TAG, "Successfully synced observed event ${event.id} to server")
                } else if (response.code() in 400..499 && response.code() != 408 && response.code() != 429) {
                    // Non-retryable validation error
                    database.observedPaymentEventDao().updateSyncState(event.id, "REJECTED")
                    Log.e(TAG, "Event ${event.id} rejected by server: HTTP ${response.code()}")
                } else {
                    anyFailed = true
                    database.observedPaymentEventDao().updateSyncState(event.id, "FAILED")
                    Log.w(TAG, "Server error syncing event ${event.id}: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                anyFailed = true
                database.observedPaymentEventDao().updateSyncState(event.id, "FAILED")
                Log.e(TAG, "Network failure syncing event ${event.id}", e)
            }
        }

        return if (anyFailed) Result.retry() else Result.success()
    }
}
