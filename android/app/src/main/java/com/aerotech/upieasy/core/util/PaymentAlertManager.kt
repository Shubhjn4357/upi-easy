package com.aerotech.upieasy.core.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aerotech.upieasy.core.security.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

data class PaymentAlert(
    val amount: Double,
    val payerName: String?,
    val referenceNumber: String?,
    val timestamp: Long = System.currentTimeMillis()
)

object PaymentAlertManager {
    private const val TAG = "PaymentAlertManager"
    private const val CHANNEL_ID = "upi_easy_payments"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _activePaymentAlert = MutableStateFlow<PaymentAlert?>(null)
    val activePaymentAlert: StateFlow<PaymentAlert?> = _activePaymentAlert.asStateFlow()

    fun init(context: Context) {
        createNotificationChannel(context)
        initTts(context.applicationContext)
    }

    private fun initTts(appContext: Context) {
        if (textToSpeech != null) return
        try {
            textToSpeech = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = textToSpeech?.setLanguage(Locale("en", "IN"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        textToSpeech?.language = Locale.US
                    }
                    isTtsReady = true
                    Log.i(TAG, "Soundbox TextToSpeech engine initialized successfully")
                } else {
                    Log.w(TAG, "TextToSpeech init failed with code: $status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TextToSpeech", e)
        }
    }

    fun notifyPayment(
        context: Context,
        amount: Double,
        payerName: String?,
        referenceNumber: String?
    ) {
        val appContext = context.applicationContext
        init(appContext)

        val alert = PaymentAlert(
            amount = amount,
            payerName = payerName ?: "UPI Customer",
            referenceNumber = referenceNumber
        )

        // 1. Post to in-app popup StateFlow
        _activePaymentAlert.value = alert

        // 2. Show high-priority heads-up system notification
        showSystemNotification(appContext, alert)

        // 3. Play voice soundbox notification if user enabled sound
        scope.launch(Dispatchers.IO) {
            try {
                val sessionManager = SessionManager(appContext)
                val soundEnabled = sessionManager.soundNotificationsFlow.first()
                if (soundEnabled) {
                    val amountStr = if (amount % 1.0 == 0.0) {
                        amount.toLong().toString()
                    } else {
                        String.format(Locale.US, "%.2f", amount)
                    }
                    val speechText = "payment of $amountStr received"
                    speakAnnouncement(appContext, speechText)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to speak payment alert", e)
            }
        }
    }

    private fun speakAnnouncement(appContext: Context, text: String) {
        if (isTtsReady && textToSpeech != null) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "PAYMENT_ALERT_${System.currentTimeMillis()}")
        } else {
            // Re-attempt init and queue speech
            textToSpeech = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val res = textToSpeech?.setLanguage(Locale("en", "IN"))
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        textToSpeech?.language = Locale.US
                    }
                    isTtsReady = true
                    textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "PAYMENT_ALERT_${System.currentTimeMillis()}")
                }
            }
        }
    }

    private fun showSystemNotification(context: Context, alert: PaymentAlert) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(context)

        val formattedAmount = if (alert.amount % 1.0 == 0.0) {
            alert.amount.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", alert.amount)
        }

        val title = "Payment of ₹$formattedAmount received"
        val body = buildString {
            append("Received from ${alert.payerName ?: "UPI Customer"}")
            if (!alert.referenceNumber.isNullOrBlank()) {
                append(" (UTR: ${alert.referenceNumber})")
            }
        }

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 350, 150, 350))
            .setAutoCancel(true)

        val notifId = (System.currentTimeMillis() % 100000).toInt()
        notificationManager.notify(notifId, builder.build())
    }

    fun dismissAlert() {
        _activePaymentAlert.value = null
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Payment Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Instant voice soundbox alerts and notifications on received payments"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 350, 150, 350)
                    setSound(soundUri, audioAttributes)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}
