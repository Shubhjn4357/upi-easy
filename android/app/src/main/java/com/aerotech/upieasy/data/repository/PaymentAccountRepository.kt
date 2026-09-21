package com.aerotech.upieasy.data.repository

import android.content.Context
import android.util.Log
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.database.entity.ObservedPaymentEventEntity
import com.aerotech.upieasy.core.database.entity.PaymentAccountEntity
import com.aerotech.upieasy.core.network.ApiService
import com.aerotech.upieasy.core.network.CreatePaymentAccountRequest
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.feature.notifications.PaymentAppDefinition
import kotlinx.coroutines.flow.Flow

class PaymentAccountRepository(
    private val context: Context,
    private val apiService: ApiService,
    private val database: AppDatabase,
    private val sessionManager: SessionManager
) {
    private val paymentAccountDao = database.paymentAccountDao()
    private val observedPaymentEventDao = database.observedPaymentEventDao()

    companion object {
        private const val TAG = "PaymentAccountRepository"
    }

    fun observePaymentAccounts(orgId: String): Flow<List<PaymentAccountEntity>> =
        paymentAccountDao.getAccountsForOrg(orgId)

    fun observeObservedEvents(orgId: String): Flow<List<ObservedPaymentEventEntity>> =
        observedPaymentEventDao.getEventsForOrg(orgId)

    suspend fun refreshPaymentAccounts(orgId: String): Result<List<PaymentAccountEntity>> {
        return try {
            val response = apiService.getPaymentAccounts(orgId)
            if (response.isSuccessful && response.body()?.success == true) {
                val dtos = response.body()?.data ?: emptyList()
                val entities = dtos.map { dto ->
                    PaymentAccountEntity(
                        id = dto.id,
                        organizationId = dto.organizationId,
                        label = dto.label,
                        upiId = dto.upiId,
                        paymentAppId = dto.paymentAppId,
                        paymentAppPackage = dto.paymentAppPackage,
                        status = dto.status,
                        detectionEnabled = dto.detectionEnabled,
                        notificationAccessRequired = dto.notificationAccessRequired,
                        lastNotificationDetectedAt = dto.lastNotificationDetectedAt,
                        createdAt = dto.createdAt ?: System.currentTimeMillis(),
                        updatedAt = dto.updatedAt ?: System.currentTimeMillis()
                    )
                }

                paymentAccountDao.insertAccounts(entities)
                Result.success(entities)
            } else {
                val local = paymentAccountDao.getAccountsForOrgSync(orgId)
                Result.success(local)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing payment accounts", e)
            val local = paymentAccountDao.getAccountsForOrgSync(orgId)
            Result.success(local)
        }
    }

    suspend fun createPaymentAccount(
        orgId: String,
        label: String,
        upiId: String,
        paymentApp: PaymentAppDefinition,
        detectionEnabled: Boolean = true
    ): Result<PaymentAccountEntity> {
        return try {
            val request = CreatePaymentAccountRequest(
                label = label.trim(),
                upiId = upiId.trim(),
                paymentAppId = paymentApp.id,
                paymentAppPackage = paymentApp.packageName,
                detectionEnabled = detectionEnabled,
                notificationAccessRequired = true
            )

            val response = apiService.createPaymentAccount(orgId, request)
            if (response.isSuccessful && response.body()?.success == true) {
                val dto = response.body()!!.data
                val entity = PaymentAccountEntity(
                    id = dto.id,
                    organizationId = dto.organizationId,
                    label = dto.label,
                    upiId = dto.upiId,
                    paymentAppId = dto.paymentAppId,
                    paymentAppPackage = dto.paymentAppPackage,
                    status = dto.status,
                    detectionEnabled = dto.detectionEnabled,
                    notificationAccessRequired = dto.notificationAccessRequired,
                    lastNotificationDetectedAt = dto.lastNotificationDetectedAt,
                    createdAt = dto.createdAt ?: System.currentTimeMillis(),
                    updatedAt = dto.updatedAt ?: System.currentTimeMillis()
                )
                paymentAccountDao.insertAccount(entity)
                Result.success(entity)
            } else {
                Result.failure(Exception("Failed to create payment account: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create payment account", e)
            Result.failure(e)
        }
    }

    suspend fun toggleDetection(id: String, enabled: Boolean): Result<Unit> {
        return try {
            paymentAccountDao.updateDetectionState(id, enabled)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePaymentAccount(orgId: String, id: String): Result<Unit> {
        return try {
            val response = apiService.deletePaymentAccount(orgId, id)
            if (response.isSuccessful) {
                paymentAccountDao.deleteAccount(id)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete account on server"))
            }
        } catch (e: Exception) {
            paymentAccountDao.deleteAccount(id)
            Result.success(Unit)
        }
    }
}
