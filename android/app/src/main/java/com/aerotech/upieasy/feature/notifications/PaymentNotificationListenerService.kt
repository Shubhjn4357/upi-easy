package com.aerotech.upieasy.feature.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.database.TransactionEntity
import com.aerotech.upieasy.core.security.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

class PaymentNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    companion object {
        private const val TAG = "PaymentNotificationListener"
        private const val CHANNEL_ID = "upi_easy_payments"

        // Known UPI & Banking app package identifiers
        private val UPI_PACKAGE_NAMES = setOf(
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",                         // PhonePe
            "net.one97.paytm",                         // Paytm
            "in.org.npci.upiapp",                      // BHIM UPI
            "com.cred.android",                        // CRED
            "com.amazon.mShop.android.shopping",       // Amazon Pay
            "com.sbi.upi",                             // SBI UPI
            "com.msf.kbank.mobile",                    // Kotak
            "com.icicibank.pockets",                   // ICICI Pockets
            "com.snapwork.hdfc"                        // HDFC MobileBanking
        )

        private val AMOUNT_PATTERN = Pattern.compile(
            "(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE
        )

        private val RRN_PATTERN = Pattern.compile(
            "(?:UTR|RRN|Ref|UPI Ref(?: No)?|Reference No)?[:\\s#]*([0-9]{12})",
            Pattern.CASE_INSENSITIVE
        )
    }

    override fun onCreate() {
        super.onCreate()
        initTextToSpeech()
        createNotificationChannel()
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    private fun initTextToSpeech() {
        try {
            textToSpeech = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = textToSpeech?.setLanguage(Locale("en", "IN"))
                    isTtsReady = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize TextToSpeech soundbox engine", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        val extras = sbn.notification?.extras ?: return

        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val fullContent = "$title $text"

        // Check if from UPI package or contains UPI transaction keywords
        val isUpiPackage = UPI_PACKAGE_NAMES.contains(packageName)
        val isCreditKeyword = fullContent.contains("received", ignoreCase = true) ||
                fullContent.contains("credited", ignoreCase = true) ||
                fullContent.contains("paid to you", ignoreCase = true) ||
                fullContent.contains("deposited", ignoreCase = true)

        if ((isUpiPackage || packageName.contains("sms", ignoreCase = true)) && isCreditKeyword) {
            processPaymentNotification(fullContent)
        }
    }

    private fun processPaymentNotification(notificationText: String) {
        serviceScope.launch {
            try {
                val amountMatcher = AMOUNT_PATTERN.matcher(notificationText)
                if (!amountMatcher.find()) return@launch

                val amountStr = amountMatcher.group(1)?.replace(",", "") ?: return@launch
                val amountDouble = amountStr.toDoubleOrNull() ?: return@launch
                val amountInPaise = (amountDouble * 100).toLong()

                // Extract RRN / UTR if present
                val rrnMatcher = RRN_PATTERN.matcher(notificationText)
                val rrn = if (rrnMatcher.find()) rrnMatcher.group(1) else null

                val sessionManager = SessionManager(applicationContext)
                val orgId = sessionManager.getCurrentOrgId() ?: return@launch
                val soundEnabled = sessionManager.soundNotificationsFlow.first()

                val database = AppDatabase.getInstance(applicationContext)
                val txnEntity = TransactionEntity(
                    id = "txn_notif_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    organizationId = orgId,
                    amount = amountInPaise,
                    currency = "INR",
                    direction = "CREDIT",
                    status = "CONFIRMED",
                    payerVpa = null,
                    payerName = "UPI Customer",
                    payeeVpa = null,
                    referenceNumber = rrn,
                    occurredAt = System.currentTimeMillis()
                )

                // Save to offline Room database
                database.transactionDao().insertTransaction(txnEntity)
                Log.i(TAG, "Saved UPI payment from notification: ₹$amountDouble (RRN: $rrn)")

                // Trigger voice soundbox announcement if enabled in settings
                if (soundEnabled && isTtsReady) {
                    val speechText = "Received ${amountDouble.toInt()} Rupees on UPI Easy"
                    textToSpeech?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "PAYMENT_ANNOUNCE")
                }

                // Show local in-app confirmation notification
                showPaymentNotification(amountDouble, rrn)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing UPI payment notification", e)
            }
        }
    }

    private fun showPaymentNotification(amount: Double, rrn: String?) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val contentText = if (rrn != null) "₹$amount credited successfully (Ref: $rrn)" else "₹$amount credited successfully"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("UPI-Easy Payment Confirmed")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Payment Alerts"
            val descriptionText = "Instant notification on incoming UPI payment credits"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
