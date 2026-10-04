package com.aerotech.upieasy.feature.offline.receiver

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aerotech.upieasy.MainActivity
import com.aerotech.upieasy.R
import com.aerotech.upieasy.feature.offline.core.CurrencyFormat
import com.aerotech.upieasy.feature.offline.model.SimpleTransaction

object OfflinePaymentNotifier {

    private const val CHANNEL_ID = "upieasy_offline_payment_results"
    private const val CHANNEL_NAME = "Offline Payment Confirmations"
    private const val NOTIFICATION_ID = 2026

    fun notifyResult(context: Context, txn: SimpleTransaction) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies bank confirmation of offline UPI payments"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", "transactions")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isSuccess = txn.status.equals("SUCCESS", ignoreCase = true)
        val formattedAmount = "₹${CurrencyFormat.inr(txn.amount)}"
        val title = if (isSuccess) "Offline Payment Confirmed: $formattedAmount" else "Offline Payment Failed: $formattedAmount"
        val body = if (isSuccess) {
            "Sent to ${txn.recipientName ?: txn.phoneNumber ?: "payee"} via ${txn.bankName}. Ref: ${txn.transactionId}"
        } else {
            "Your bank reported the payment was not completed."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
