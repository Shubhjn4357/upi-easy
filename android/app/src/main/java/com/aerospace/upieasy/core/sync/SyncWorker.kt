package com.aerospace.upieasy.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aerospace.upieasy.core.database.AppDatabase
import com.aerospace.upieasy.core.database.SyncStateEntity
import com.aerospace.upieasy.core.network.NetworkClient
import com.aerospace.upieasy.core.security.SessionManager

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sessionManager = SessionManager(applicationContext)
        val orgId = sessionManager.getCurrentOrgId() ?: return Result.success()

        val database = AppDatabase.getInstance(applicationContext)
        val apiService = NetworkClient.getApiService(sessionManager)

        return try {
            val syncState = database.syncDao().getSyncState(orgId)
            val afterSequence = syncState?.lastSequence ?: 0L

            val response = apiService.getSyncEvents(orgId, afterSequence)
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()!!.data
                if (data.latestSequence > afterSequence) {
                    database.syncDao().updateSyncState(
                        SyncStateEntity(
                            organizationId = orgId,
                            lastSequence = data.latestSequence,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                }
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
