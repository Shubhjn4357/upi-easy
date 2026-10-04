package com.aerotech.upieasy.feature.offline.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.aerotech.upieasy.feature.offline.core.TransactionDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * High-priority SMS BroadcastReceiver listening for bank confirmations during active offline payment operations.
 */
class SimpleSMSReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SimpleSMSReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0]?.originatingAddress ?: return
        val body = messages.filterNotNull().joinToString("") { it.messageBody ?: "" }
        if (body.isBlank()) return

        val detector = TransactionDetector.getInstance(context)
        if (!detector.shouldProcessSMS()) {
            return
        }

        if (!detector.tryClaimSms(body)) {
            Log.d(TAG, "SMS already claimed by pipeline")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(8_000) {
                    SmsIngestionPipeline.ingest(context, detector, sender, body)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in SMS ingestion pipeline", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
