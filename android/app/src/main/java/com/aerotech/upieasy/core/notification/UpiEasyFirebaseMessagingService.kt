package com.aerotech.upieasy.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aerotech.upieasy.MainActivity
import com.aerotech.upieasy.R
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.network.RegisterDeviceRequest
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.core.sync.SyncScheduler
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UpiEasyFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "UpiEasyFCM"
        const val CHANNEL_ID = "upi_easy_alerts"
        const val CHANNEL_NAME = "UPI-Easy Alerts"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Registration Token: $token")

        // Persist token in local storage
        val sessionManager = SessionManager(applicationContext)

        // Register with server if authenticated
        serviceScope.launch {
            try {
                if (sessionManager.hasActiveSession()) {
                    val api = NetworkClient.getApiService(sessionManager)
                    val deviceId = sessionManager.getDeviceId() ?: "android_${Build.MODEL}"
                    api.registerDevice(
                        RegisterDeviceRequest(
                            deviceId = deviceId,
                            platform = "ANDROID",
                            deviceModel = Build.MODEL,
                            osVersion = Build.VERSION.RELEASE,
                            appVersion = "1.0.0",
                            fcmToken = token
                        )
                    )
                    Log.d(TAG, "Successfully registered new FCM token with backend")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register FCM token with backend", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}, data: ${remoteMessage.data}")

        val data = remoteMessage.data
        val eventType = data["type"] ?: ""
        val organizationId = data["organizationId"] ?: ""
        val inviteId = data["inviteId"] ?: ""
        val transactionId = data["transactionId"] ?: ""

        val title = remoteMessage.notification?.title
            ?: data["title"]
            ?: when (eventType) {
                "staff.invited", "STAFF_INVITATION" -> "New Staff Invitation"
                "staff.joined" -> "Staff Joined"
                "payment.received" -> "Payment Received"
                else -> "UPI-Easy"
            }

        val body = remoteMessage.notification?.body
            ?: data["body"]
            ?: data["message"]
            ?: "New update for your firm"

        // Authoritative background synchronization:
        // FCM acts as wake trigger, WorkManager performs incremental sync with server
        SyncScheduler.triggerImmediateSync(applicationContext, organizationId)

        // Build deep link Intent
        val deepLinkUri = when {
            eventType == "staff.invited" || eventType == "STAFF_INVITATION" ->
                Uri.parse("upieasy://organization/$organizationId/invitation/$inviteId")
            transactionId.isNotBlank() ->
                Uri.parse("upieasy://organization/$organizationId/transaction/$transactionId")
            else ->
                Uri.parse("upieasy://organization/$organizationId/dashboard")
        }

        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri, applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("organizationId", organizationId)
            putExtra("eventType", eventType)
            if (inviteId.isNotBlank()) putExtra("inviteId", inviteId)
            if (transactionId.isNotBlank()) putExtra("transactionId", transactionId)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        showNotification(title, body, pendingIntent)
    }

    private fun showNotification(title: String, body: String, pendingIntent: PendingIntent) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Firm notifications, transactions, and invitations"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
