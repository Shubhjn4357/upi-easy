package com.aerotech.upieasy.core.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.database.entity.SyncStateEntity
import com.aerotech.upieasy.core.database.entity.TransactionEntity
import com.aerotech.upieasy.core.database.entity.UpiAccountEntity
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.security.SessionManager
import com.google.gson.Gson

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sessionManager = SessionManager(applicationContext)
        val orgId = inputData.getString("organizationId")?.takeIf { it.isNotBlank() }
            ?: sessionManager.getCurrentOrgId()
            ?: return Result.success()

        val database = AppDatabase.getInstance(applicationContext)
        val apiService = NetworkClient.getApiService(sessionManager)
        val gson = Gson()

        return try {
            val syncState = database.syncDao().getSyncState(orgId)
            val afterSequence = syncState?.lastSequence ?: 0L

            val response = apiService.getSyncEvents(orgId, afterSequence)
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()!!.data

                for (event in data.events) {
                    try {
                        val jsonElement = gson.toJsonTree(event.payload)
                        if (!jsonElement.isJsonObject) continue
                        val obj = jsonElement.asJsonObject

                        when (event.eventType) {
                            "transaction.created", "PAYMENT_OBSERVED", "transaction.observed" -> {
                                val txnId = obj.get("id")?.asString ?: obj.get("transactionId")?.asString ?: ""
                                if (txnId.isNotBlank()) {
                                    val existing = database.transactionDao().getTransactionById(txnId)
                                    val direction = obj.get("direction")?.asString ?: "RECEIVED"
                                    val status = obj.get("status")?.asString ?: if (event.eventType == "PAYMENT_OBSERVED") "SUCCESS" else "PENDING"
                                    val amount = if (obj.has("amount")) obj.get("amount").asDouble else if (obj.has("amountMinor")) obj.get("amountMinor").asDouble / 100.0 else 0.0
                                    val payerName = if (obj.has("payerName") && !obj.get("payerName").isJsonNull) obj.get("payerName").asString else null
                                    val refNum = if (obj.has("referenceNumber") && !obj.get("referenceNumber").isJsonNull) obj.get("referenceNumber").asString else if (obj.has("reference") && !obj.get("reference").isJsonNull) obj.get("reference").asString else null

                                    val entity = TransactionEntity(
                                        id = txnId,
                                        organizationId = obj.get("organizationId")?.asString ?: orgId,
                                        bankAccountId = if (obj.has("bankAccountId") && !obj.get("bankAccountId").isJsonNull) obj.get("bankAccountId").asString else null,
                                        upiAccountId = if (obj.has("upiAccountId") && !obj.get("upiAccountId").isJsonNull) obj.get("upiAccountId").asString else null,
                                        type = obj.get("type")?.asString ?: "PAYMENT",
                                        direction = direction,
                                        amount = amount,
                                        currency = obj.get("currency")?.asString ?: "INR",
                                        status = status,
                                        paymentMethod = obj.get("paymentMethod")?.asString ?: "UPI",
                                        referenceNumber = refNum,
                                        payerName = payerName,
                                        payerVpa = if (obj.has("payerVpa") && !obj.get("payerVpa").isJsonNull) obj.get("payerVpa").asString else null,
                                        payeeName = obj.get("payeeName")?.asString ?: "",
                                        payeeVpa = obj.get("payeeVpa")?.asString ?: "",
                                        note = if (obj.has("note") && !obj.get("note").isJsonNull) obj.get("note").asString else null,
                                        occurredAt = if (obj.has("occurredAt")) obj.get("occurredAt").asLong else System.currentTimeMillis(),
                                        syncStatus = "SYNCED"
                                    )
                                    database.transactionDao().insertTransaction(entity)

                                    // If this is a new confirmed received payment, announce & notify
                                    if (existing == null &&
                                        (direction == "CREDIT" || direction == "RECEIVED") &&
                                        (status == "CONFIRMED" || status == "SUCCESS")
                                    ) {
                                        com.aerotech.upieasy.core.util.PaymentAlertManager.notifyPayment(
                                            context = applicationContext,
                                            amount = amount,
                                            payerName = payerName,
                                            referenceNumber = refNum
                                        )
                                    }
                                }
                            }

                            "transaction.updated", "transaction.status_changed", "transaction.reconciled", "transaction.failed", "transaction.refunded" -> {
                                val txnId = obj.get("id")?.asString ?: obj.get("transactionId")?.asString ?: ""
                                val status = obj.get("status")?.asString ?: "PENDING"
                                val ref = if (obj.has("referenceNumber") && !obj.get("referenceNumber").isJsonNull) obj.get("referenceNumber").asString else null
                                if (txnId.isNotBlank()) {
                                    val existing = database.transactionDao().getTransactionById(txnId)
                                    database.transactionDao().updateStatusAndRef(txnId, status, ref)

                                    if ((existing == null || (existing.status != "CONFIRMED" && existing.status != "SUCCESS")) &&
                                        (status == "CONFIRMED" || status == "SUCCESS")
                                    ) {
                                        val amt = existing?.amount ?: obj.get("amount")?.asDouble ?: 0.0
                                        val payer = existing?.payerName ?: if (obj.has("payerName") && !obj.get("payerName").isJsonNull) obj.get("payerName").asString else null
                                        val finalRef = ref ?: existing?.referenceNumber
                                        if (amt > 0.0) {
                                            com.aerotech.upieasy.core.util.PaymentAlertManager.notifyPayment(
                                                context = applicationContext,
                                                amount = amt,
                                                payerName = payer,
                                                referenceNumber = finalRef
                                            )
                                        }
                                    }
                                }
                            }

                            "transaction.deleted" -> {
                                val txId = obj.get("id")?.asString ?: ""
                                if (txId.isNotBlank()) {
                                    database.transactionDao().deleteTransaction(txId)
                                }
                            }

                            "upi.created" -> {
                                val upiId = obj.get("id")?.asString ?: ""
                                if (upiId.isNotBlank()) {
                                    val isDefault = obj.get("isDefault")?.asBoolean ?: false
                                    if (isDefault) {
                                        database.upiDao().clearDefaultUpi(orgId)
                                    }
                                    val upiEntity = UpiAccountEntity(
                                        id = upiId,
                                        organizationId = obj.get("organizationId")?.asString ?: orgId,
                                        vpa = obj.get("vpa")?.asString ?: "",
                                        payeeName = obj.get("payeeName")?.asString ?: "",
                                        merchantCategoryCode = obj.get("merchantCategoryCode")?.asString ?: "5411",
                                        isDefault = isDefault,
                                        status = obj.get("status")?.asString ?: "ACTIVE",
                                        transactionCount = obj.get("transactionCount")?.asInt ?: 0
                                    )
                                    database.upiDao().insertUpiAccount(upiEntity)
                                }
                            }

                            "upi.default_changed" -> {
                                val upiId = obj.get("upiId")?.asString ?: ""
                                if (upiId.isNotBlank()) {
                                    database.upiDao().clearDefaultUpi(orgId)
                                    database.upiDao().setDefaultUpi(upiId)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SyncWorker", "Error processing sync event: ${event.eventType}", e)
                    }
                }

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
            Log.e("SyncWorker", "SyncWorker execution failed", e)
            Result.retry()
        }
    }
}

