package com.aerotech.upieasy.feature.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.database.entity.ObservedPaymentEventEntity
import com.aerotech.upieasy.core.database.entity.TransactionEntity
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.core.sync.PaymentEventSyncWorker
import com.aerotech.upieasy.core.util.PaymentAlertManager
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PaymentNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deduplicator = NotificationEventDeduplicator()
    private val accountResolver = PaymentAccountResolver()
    private val parsers: List<PaymentNotificationParser> = listOf(
        GooglePayNotificationParser(),
        PhonePeNotificationParser(),
        BhimNotificationParser(),
        PaytmNotificationParser()
    )

    companion object {
        private const val TAG = "PaymentNotificationListener"
        private const val CHANNEL_ID = "upi_easy_payments"

        // Section 10 & 21: Strict package filter for the 4 supported applications
        val SUPPORTED_PACKAGES = setOf(
           GooglePayNotificationParser.PACKAGE_NAME,
            PhonePeNotificationParser.PACKAGE_NAME,
            BhimNotificationParser.PACKAGE_NAME,
            PaytmNotificationParser.PACKAGE_NAME
        )

        /**
         * Proactively requests the Android OS to rebind this NotificationListenerService
         * if disconnected or unbound.
         */
        fun requestRebindIfDisconnected(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    val component = ComponentName(context, PaymentNotificationListenerService::class.java)
                    requestRebind(component)
                    Log.i(TAG, "Proactively requested rebind for component $component")
                } catch (e: Exception) {
                    Log.d(TAG, "requestRebind not permitted or listener already bound: ${e.message}")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "PaymentNotificationListenerService created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected successfully")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected! Proactively requesting immediate rebind...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val component = ComponentName(this, PaymentNotificationListenerService::class.java)
                requestRebind(component)
                Log.i(TAG, "requestRebind invoked successfully on disconnection")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rebind NotificationListenerService", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Section 65 & 66: Notification removal does NOT mean payment reversal!
        // Do not delete records or alter transaction statuses on removal.
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return

        // Section 10: Strict package filter
        if (packageName !in SUPPORTED_PACKAGES) {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Section 11: Extract normalized notification data
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        val raw = RawPaymentNotification(
            packageName = packageName,
            notificationKey = sbn.key ?: "${packageName}_${sbn.id}_${sbn.postTime}",
            notificationId = sbn.id,
            postTime = sbn.postTime,
            title = title,
            text = text,
            bigText = bigText,
            subText = subText,
            category = notification.category
        )

        processNotification(raw)
    }

    private fun processNotification(raw: RawPaymentNotification) {
        serviceScope.launch {
            try {
                // Section 12: Select parser
                val parser = parsers.find { it.supports(raw.packageName) } ?: return@launch
                val event = parser.parse(raw) ?: return@launch

                // Section 19: Compute fingerprint
                val fingerprint = NotificationEventDeduplicator.computeFingerprint(
                    packageName = raw.packageName,
                    notificationKey = raw.notificationKey,
                    title = raw.title,
                    text = raw.text ?: raw.bigText,
                    postTime = raw.postTime
                )

                // Check in-memory deduplicator
                if (deduplicator.isDuplicate(fingerprint)) {
                    Log.d(TAG, "Dropping duplicate notification in-memory: $fingerprint")
                    return@launch
                }

                val context = applicationContext
                val sessionManager = SessionManager(context)
                val orgId = sessionManager.getCurrentOrgId()
                val database = AppDatabase.getInstance(context)

                // Check Room database deduplication
                if (orgId != null) {
                    val existing = database.observedPaymentEventDao().getEventByFingerprint(orgId, fingerprint)
                    if (existing != null) {
                        Log.d(TAG, "Dropping duplicate notification from Room: $fingerprint")
                        return@launch
                    }
                }

                // Section 17 & 18: Account Resolution
                val activeAccounts = if (orgId != null) {
                    database.paymentAccountDao().getActiveAccountsForPackage(raw.packageName)
                } else {
                    emptyList()
                }

                val resolution = accountResolver.resolve(
                    sourcePackage = raw.packageName,
                    payerVpa = event.payerVpa,
                    availableAccounts = activeAccounts
                )

                val amountMinor = event.amount?.multiply(BigDecimal(100))?.toLong()
                val eventId = "evt_obs_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                val txnId = "txn_obs_${UUID.randomUUID().toString().replace("-", "").take(16)}"

                // Section 20: Store observed event entity
                val observedEntity = ObservedPaymentEventEntity(
                    id = eventId,
                    organizationId = orgId,
                    paymentAccountId = resolution.paymentAccountId,
                    qrId = null,
                    sourcePackage = raw.packageName,
                    sourceApp = event.sourceApp,
                    direction = event.direction.name,
                    amountMinor = amountMinor,
                    currency = "INR",
                    payerName = event.payerName,
                    payerVpa = event.payerVpa,
                    reference = event.reference,
                    notificationTitle = raw.title,
                    notificationText = raw.text ?: raw.bigText,
                    eventFingerprint = fingerprint,
                    matchStatus = resolution.status.name,
                    verificationStatus = "OBSERVED",
                    observedAt = raw.postTime,
                    syncState = "PENDING_UPLOAD"
                )

                database.observedPaymentEventDao().insertEvent(observedEntity)
                Log.i(TAG, "Saved observed payment event: ${event.amount} (${event.direction}) from ${event.sourceApp}")

                // Update payment account last detection timestamp
                resolution.paymentAccountId?.let { paId ->
                    database.paymentAccountDao().updateLastDetectedTime(paId, raw.postTime)
                }

                // Record genuine payment notifications (RECEIVED or SENT) into local ledger database
                if (event.amount != null && (event.direction == PaymentDirection.RECEIVED || event.direction == PaymentDirection.SENT)) {
                    val amountDouble = event.amount.toDouble()
                    val orgName = sessionManager.currentOrgNameFlow.first() ?: "Merchant Store"
                    val accountLabel = resolution.candidateAccounts.firstOrNull()?.label ?: "${event.sourceApp} Account"
                    val payeeVpa = resolution.candidateAccounts.firstOrNull()?.upiId ?: "merchant@upi"
                    val isReceived = event.direction == PaymentDirection.RECEIVED
                    val directionStr = if (isReceived) "RECEIVED" else "SENT"

                    val payerName = if (isReceived) (event.payerName ?: "UPI Customer") else orgName
                    val payeeName = if (isReceived) accountLabel else (event.payerName ?: "Vendor / Merchant")

                    val txnEntity = TransactionEntity(
                        id = txnId,
                        organizationId = orgId ?: "org_default",
                        bankAccountId = null,
                        upiAccountId = null,
                        type = "PAYMENT",
                        direction = directionStr,
                        amount = amountDouble,
                        currency = "INR",
                        status = "UNKNOWN", // Section 2 & 28: Observed notification must remain UNKNOWN until authoritative verification
                        paymentMethod = "UPI",
                        referenceNumber = event.reference,
                        payerName = payerName,
                        payerVpa = event.payerVpa,
                        payeeName = payeeName,
                        payeeVpa = payeeVpa,
                        note = "Payment $directionStr observed from ${event.sourceApp}",
                        occurredAt = raw.postTime,
                        syncStatus = "PENDING",
                        paymentAccountId = resolution.paymentAccountId,
                        verificationStatus = "OBSERVED",
                        eventSource = "NOTIFICATION_${event.sourceApp.uppercase().replace(" ", "_")}"
                    )

                    database.transactionDao().insertTransaction(txnEntity)

                    // Trigger soundbox voice announcement & system notification
                    PaymentAlertManager.notifyPayment(
                        context = context,
                        amount = amountDouble,
                        payerName = event.payerName ?: (if (isReceived) "UPI Customer" else payeeName),
                        referenceNumber = event.reference
                    )
                }

                // Section 30: Schedule background sync upload
                PaymentEventSyncWorker.enqueue(context)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing payment notification", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Payment Alerts"
            val descriptionText = "Instant notification on incoming UPI payment credits"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
