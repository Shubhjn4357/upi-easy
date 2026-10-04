package com.aerotech.upieasy.feature.offline.receiver

import android.content.Context
import android.content.Intent
import android.util.Log
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.database.entity.TransactionEntity
import com.aerotech.upieasy.core.util.PaymentAlertManager
import com.aerotech.upieasy.feature.offline.core.OfflinePaymentSessionManager
import com.aerotech.upieasy.feature.offline.core.TransactionDetector
import com.aerotech.upieasy.feature.offline.model.SimpleTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared ingestion pipeline for processing incoming bank confirmation SMS,
 * promoting payment sessions to SUCCESS, recording in Room, and triggering soundbox audio.
 */
object SmsIngestionPipeline {

    private const val TAG = "SmsIngestionPipeline"

    suspend fun ingest(
        context: Context,
        detector: TransactionDetector,
        sender: String,
        body: String
    ) {
        val transaction = detector.processSMS(sender, body)
        if (transaction == null) {
            Log.d(TAG, "Not an offline transaction SMS")
            return
        }

        val sessionManager = OfflinePaymentSessionManager.getInstance(context)
        val sessionTxnId = sessionManager.onSmsConfirmed(transaction)

        if (sessionTxnId == null && transaction.transactionType != "CREDIT") {
            // Save as standalone offline transaction in local Room database
            try {
                val db = AppDatabase.getInstance(context)
                val isFailed = transaction.status.equals("FAILED", ignoreCase = true)
                val entity = TransactionEntity(
                    id = transaction.transactionId,
                    organizationId = "offline_org",
                    bankAccountId = null,
                    upiAccountId = null,
                    type = "PAYMENT",
                    direction = transaction.transactionType,
                    amount = transaction.amount.toDoubleOrNull() ?: 0.0,
                    currency = "INR",
                    status = if (isFailed) "FAILED" else "SUCCESS",
                    paymentMethod = "OFFLINE_SMS",
                    referenceNumber = transaction.transactionId,
                    payerName = null,
                    payerVpa = null,
                    payeeName = transaction.recipientName ?: transaction.bankName,
                    payeeVpa = transaction.upiId ?: "${transaction.phoneNumber ?: "payee"}@upi",
                    note = transaction.smsExcerpt,
                    occurredAt = transaction.timestamp,
                    syncStatus = "QUEUED",
                    verificationStatus = "VERIFIED",
                    eventSource = "SMS_CONFIRMATION"
                )
                db.transactionDao().insertTransaction(entity)
                Log.d(TAG, "Recorded standalone offline transaction in Room")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving standalone transaction", e)
            }
        }

        withContext(Dispatchers.Main) {
            // Notify via heads-up notification
            OfflinePaymentNotifier.notifyResult(context, transaction)

            // Trigger TTS / audio chime if success
            if (transaction.status.equals("SUCCESS", ignoreCase = true)) {
                try {
                    val amountVal = transaction.amount.toDoubleOrNull() ?: 0.0
                    PaymentAlertManager.notifyPayment(
                        context = context,
                        amount = amountVal,
                        payerName = transaction.recipientName ?: "Offline Payee",
                        referenceNumber = transaction.transactionId
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Could not play soundbox voice notification", e)
                }
            }

            // Broadcast to any active overlay or UI listeners
            val intent = Intent("com.aerotech.upieasy.OFFLINE_PAYMENT_CONFIRMED").apply {
                putExtra("amount", transaction.amount)
                putExtra("status", transaction.status)
                putExtra("bank_name", transaction.bankName)
                putExtra("tx_id", transaction.transactionId)
                putExtra("recipient_name", transaction.recipientName)
            }
            context.sendBroadcast(intent)
        }
    }
}
