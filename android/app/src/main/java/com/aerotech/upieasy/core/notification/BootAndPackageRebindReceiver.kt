package com.aerotech.upieasy.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aerotech.upieasy.feature.notifications.PaymentNotificationListenerService
import com.aerotech.upieasy.feature.settings.isNotificationAccessGranted

/**
 * Ensures the PaymentNotificationListenerService reconnects and rebinds
 * automatically upon device boot and after app updates.
 */
class BootAndPackageRebindReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootRebindReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Received broadcast action: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                if (isNotificationAccessGranted(context)) {
                    Log.i(TAG, "Notification access is granted. Proactively requesting listener rebind...")
                    PaymentNotificationListenerService.requestRebindIfDisconnected(context)
                } else {
                    Log.w(TAG, "Notification access not yet granted by user.")
                }
            }
        }
    }
}
