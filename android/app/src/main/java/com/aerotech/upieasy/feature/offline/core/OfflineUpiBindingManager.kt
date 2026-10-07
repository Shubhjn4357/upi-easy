package com.aerotech.upieasy.feature.offline.core

import android.content.Context
import android.util.Log
import com.aerotech.upieasy.UPIEasyApp
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.database.entity.UpiAccountEntity
import com.aerotech.upieasy.core.network.AddUpiRequest
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.security.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Manages the automatic generation, binding, local persistence,
 * and remote synchronization of the merchant's offline-pay UPI handle.
 */
object OfflineUpiBindingManager {
    private const val TAG = "OfflineUpiBindingMgr"

    /**
     * Generates an offline UPI handle for a 10-digit mobile number.
     * Standard UPI 123Pay format: <10-digit-mobile>@upi
     */
    fun generateOfflineUpiId(phoneNumber: String): String {
        val cleanPhone = phoneNumber.filter { it.isDigit() }.takeLast(10)
        return if (cleanPhone.length == 10) "$cleanPhone@upi" else "offline@upi"
    }

    /**
     * Binds and saves the generated offline UPI handle to Room and SessionManager,
     * and synchronizes it with the backend if online.
     */
    suspend fun ensureOfflineUpiBound(
        context: Context,
        sessionManager: SessionManager,
        database: AppDatabase,
        mobileNumberOverride: String? = null,
        bankNameHint: String? = null
    ): String = withContext(Dispatchers.IO) {
        val savedUpi = sessionManager.getOfflineUpiId()
        val mobile = mobileNumberOverride?.filter { it.isDigit() }?.takeLast(10)
            ?: sessionManager.mobileNumberFlow.first()?.filter { it.isDigit() }?.takeLast(10)
            ?: run {
                val detected = SetupHelper.getDetectedSims(context).firstOrNull()
                detected?.carrierName?.filter { it.isDigit() }?.takeLast(10)
            }
            ?: "9876543210"

        val generatedVpa = if (savedUpi.isNullOrBlank() || !savedUpi.contains("@upi")) {
            generateOfflineUpiId(mobile)
        } else {
            savedUpi
        }

        val orgId = sessionManager.getCurrentOrgId() ?: "default_org"
        val existingAccounts = try {
            database.upiDao().getAllUpiAccountsFlow().first()
        } catch (_: Exception) {
            emptyList()
        }

        val alreadyExists = existingAccounts.any { it.vpa.equals(generatedVpa, ignoreCase = true) }

        if (!alreadyExists) {
            val merchantName = sessionManager.userNameFlow.first()
                ?: sessionManager.currentOrgNameFlow.first()
                ?: "Merchant Offline"

            val entity = UpiAccountEntity(
                id = "offline_upi_${mobile}_${System.currentTimeMillis()}",
                organizationId = orgId,
                vpa = generatedVpa,
                payeeName = merchantName,
                merchantCategoryCode = "5411",
                isDefault = existingAccounts.isEmpty(),
                status = "ACTIVE",
                transactionCount = 0
            )

            try {
                database.upiDao().insertUpiAccount(entity)
                Log.d(TAG, "Saved offline UPI handle $generatedVpa into Room")
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting offline UPI handle", e)
            }
        }

        sessionManager.setOfflineUpiId(generatedVpa)

        // Sync with backend API if network is available
        try {
            val apiService = NetworkClient.getApiService(sessionManager)
            apiService.addUpiAccount(
                orgId = orgId,
                request = AddUpiRequest(
                    vpa = generatedVpa,
                    payeeName = sessionManager.userNameFlow.first() ?: "Merchant Offline",
                    isDefault = false
                )
            )
            Log.d(TAG, "Synced offline UPI handle to backend: $generatedVpa")
        } catch (e: Exception) {
            Log.d(TAG, "Offline UPI sync queued for later (working offline): ${e.message}")
        }

        try {
            UPIEasyApp.triggerImmediateSync(context)
        } catch (_: Exception) {}

        generatedVpa
    }
}
