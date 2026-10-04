package com.aerotech.upieasy.feature.offline.core

import android.content.Context
import android.util.Log
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.database.entity.TransactionEntity
import com.aerotech.upieasy.feature.offline.model.OfflinePaymentState
import com.aerotech.upieasy.feature.offline.model.SimpleTransaction
import com.aerotech.upieasy.feature.offline.model.TimeoutType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Coordinates offline payment state lifecycle, telephony events, bank confirmation SMS,
 * and local Room database persistence for UPI-Easy.
 */
class OfflinePaymentSessionManager private constructor(
    private val context: Context,
    private val coordinator: CallStateSource = CallStateCoordinator.getInstance(context),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: () -> Long = System::currentTimeMillis,
    private val verificationDeadlineMs: Long = DEFAULT_VERIFICATION_DEADLINE_MS,
    private val minRealCallDurationMs: Long = DEFAULT_MIN_REAL_CALL_DURATION_MS
) {

    companion object {
        private const val TAG = "OfflinePaymentSessionMgr"
        const val DEFAULT_VERIFICATION_DEADLINE_MS = 10 * 60 * 1000L // 10 minutes
        const val DEFAULT_MIN_REAL_CALL_DURATION_MS = 5_000L // Minimum 5s call duration
        private const val COORDINATOR_TAG = "offline-payment-session"

        @Volatile
        private var instance: OfflinePaymentSessionManager? = null

        fun getInstance(context: Context): OfflinePaymentSessionManager {
            return instance ?: synchronized(this) {
                instance ?: OfflinePaymentSessionManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val db = AppDatabase.getInstance(context)
    private val _paymentState = MutableStateFlow<OfflinePaymentState>(OfflinePaymentState.Idle)
    val paymentState: StateFlow<OfflinePaymentState> = _paymentState.asStateFlow()

    private val sessionLock = Any()
    @Volatile private var collectJob: Job? = null
    @Volatile private var watchdogJob: Job? = null
    @Volatile private var pendingInsertJob: Job? = null
    private var coordinatorAcquired = false

    fun begin(
        phoneNumber: String,
        amount: String,
        payeeName: String = "",
        payeeUpiId: String? = null,
        orgId: String = "offline_org",
        rail: String = "123PAY"
    ): String {
        val initiating: OfflinePaymentState.Initiating
        val supersededTxnId: String?

        synchronized(sessionLock) {
            val previous = _paymentState.value
            supersededTxnId = if (previous.isInProgress()) {
                Log.w(TAG, "begin() supersedes existing in-flight session")
                cleanupLocked()
                previous.getTransactionIdValue()
            } else {
                null
            }
            initiating = OfflinePaymentState.Initiating(
                phoneNumber = phoneNumber,
                amount = amount,
                payeeName = payeeName
            )
            _paymentState.value = initiating

            if (!coordinatorAcquired) {
                coordinator.acquire(COORDINATOR_TAG)
                coordinatorAcquired = true
            }
        }

        val txnId = initiating.transactionId
        val now = clock()

        if (supersededTxnId != null) {
            val previousInsert = pendingInsertJob
            scope.launch {
                previousInsert?.join()
                try {
                    db.transactionDao().deleteTransaction(supersededTxnId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error cleaning superseded txn", e)
                }
            }
        }

        pendingInsertJob = scope.launch(Dispatchers.IO) {
            try {
                val entity = TransactionEntity(
                    id = txnId,
                    organizationId = orgId,
                    bankAccountId = null,
                    upiAccountId = null,
                    type = "PAYMENT",
                    direction = "DEBIT",
                    amount = amount.toDoubleOrNull() ?: 0.0,
                    currency = "INR",
                    status = "PENDING",
                    paymentMethod = if (rail == "USSD") "OFFLINE_USSD" else "OFFLINE_123PAY",
                    referenceNumber = null,
                    payerName = "Merchant Self",
                    payerVpa = null,
                    payeeName = payeeName.ifBlank { "Recipient ($phoneNumber)" },
                    payeeVpa = payeeUpiId ?: "$phoneNumber@upi",
                    note = "Offline payment via $rail",
                    occurredAt = now,
                    syncStatus = "LOCAL_ONLY",
                    verificationStatus = "UNVERIFIED",
                    eventSource = if (rail == "USSD") "OFFLINE_USSD" else "OFFLINE_123PAY"
                )
                db.transactionDao().insertTransaction(entity)
                Log.d(TAG, "Recorded PENDING offline transaction $txnId in Room")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert pending offline transaction", e)
            }
        }

        collectJob = scope.launch {
            coordinator.sessionEvents.collect { event -> onCallSessionEvent(event) }
        }

        watchdogJob = scope.launch {
            delay(verificationDeadlineMs)
            onVerificationDeadline()
        }

        Log.d(TAG, "Started offline payment session: $txnId")
        return txnId
    }

    private fun onCallSessionEvent(event: CallSessionEvent) {
        when (event) {
            is CallSessionEvent.Started -> {
                synchronized(sessionLock) {
                    val current = _paymentState.value
                    if (current is OfflinePaymentState.Initiating) {
                        _paymentState.value = OfflinePaymentState.InProgress(
                            step = "In payment call — follow voice prompts",
                            progress = 0.35f,
                            phoneNumber = current.phoneNumber,
                            amount = current.amount,
                            payeeName = current.payeeName,
                            transactionId = current.transactionId
                        )
                        Log.d(TAG, "Call connected: state moved to InProgress")
                    }
                }
            }
            is CallSessionEvent.Ended -> {
                val callDuration = event.durationMs
                Log.d(TAG, "Call ended with duration: ${callDuration}ms")

                if (callDuration < minRealCallDurationMs) {
                    Log.w(TAG, "Call disconnected under ${minRealCallDurationMs}ms: marking Cancelled")
                    finishSession("CANCELLED") { phone, amount, payee, txnId ->
                        OfflinePaymentState.Cancelled(phone, amount, payee, txnId, "Call disconnected prematurely")
                    }
                } else {
                    synchronized(sessionLock) {
                        val current = _paymentState.value
                        when (current) {
                            is OfflinePaymentState.InProgress -> {
                                _paymentState.value = OfflinePaymentState.WaitingForVerification(
                                    timeout = verificationDeadlineMs,
                                    phoneNumber = current.phoneNumber,
                                    amount = current.amount,
                                    payeeName = current.payeeName,
                                    transactionId = current.transactionId
                                )
                                Log.d(TAG, "Call completed normally: WaitingForVerification (awaiting bank SMS)")
                            }
                            is OfflinePaymentState.Initiating -> {
                                _paymentState.value = OfflinePaymentState.WaitingForVerification(
                                    timeout = verificationDeadlineMs,
                                    phoneNumber = current.phoneNumber,
                                    amount = current.amount,
                                    payeeName = current.payeeName,
                                    transactionId = current.transactionId
                                )
                                Log.d(TAG, "Call completed normally: WaitingForVerification (awaiting bank SMS)")
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    fun onSmsConfirmed(parsed: SimpleTransaction): String? {
        if (parsed.transactionType == "CREDIT") {
            Log.d(TAG, "Ignoring incoming CREDIT SMS during offline outgoing DEBIT session")
            return null
        }

        val activeTxnId: String
        val phone: String
        val amt: String
        val payee: String
        val insertJob: Job?

        synchronized(sessionLock) {
            val current = _paymentState.value
            if (!current.isInProgress()) {
                Log.d(TAG, "SMS arrived but no session is currently in progress")
                return null
            }
            activeTxnId = current.getTransactionIdValue() ?: return null
            phone = current.phoneNumber
            amt = current.amount
            payee = current.payeeName
            insertJob = pendingInsertJob

            cleanupLocked()

            val isFailed = parsed.status.equals("FAILED", ignoreCase = true)
            if (isFailed) {
                _paymentState.value = OfflinePaymentState.Failed(
                    error = "Your bank reported the payment as failed",
                    phoneNumber = phone,
                    amount = amt,
                    payeeName = payee,
                    transactionId = activeTxnId
                )
            } else {
                _paymentState.value = OfflinePaymentState.Success(
                    transactionId = activeTxnId,
                    phoneNumber = phone,
                    amount = amt,
                    payeeName = payee,
                    bankReference = parsed.transactionId,
                    timestamp = clock()
                )
            }
        }

        scope.launch(Dispatchers.IO) {
            insertJob?.join()
            try {
                val isFailed = parsed.status.equals("FAILED", ignoreCase = true)
                val status = if (isFailed) "FAILED" else "SUCCESS"
                val verificationStatus = if (isFailed) "CONFLICT" else "VERIFIED"
                val syncStatus = if (isFailed) "LOCAL_ONLY" else "QUEUED"

                db.transactionDao().updateStatusAndRef(
                    id = activeTxnId,
                    status = status,
                    referenceNumber = parsed.transactionId
                )
                Log.d(TAG, "Updated offline transaction $activeTxnId to $status in database")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating transaction in database", e)
            }
        }

        return activeTxnId
    }

    fun onUserCancelled() {
        finishSession("CANCELLED") { phone, amount, payee, txnId ->
            OfflinePaymentState.Cancelled(phone, amount, payee, txnId, "Payment cancelled by user")
        }
    }

    fun onDialFailed(reason: String) {
        finishSession("CANCELLED") { phone, amount, payee, txnId ->
            OfflinePaymentState.Cancelled(phone, amount, payee, txnId, reason)
        }
    }

    private fun onVerificationDeadline() {
        Log.w(TAG, "Payment verification window expired with no bank SMS confirmation")
        finishSession("TIMEOUT") { phone, amount, payee, txnId ->
            OfflinePaymentState.Timeout(TimeoutType.VERIFICATION, phone, amount, payee, txnId)
        }
    }

    private fun finishSession(
        finalStatus: String,
        stateFactory: (String, String, String, String) -> OfflinePaymentState
    ) {
        val phone: String
        val amt: String
        val payee: String
        val txnId: String
        val insertJob: Job?

        synchronized(sessionLock) {
            val current = _paymentState.value
            if (!current.isInProgress()) return

            phone = current.phoneNumber
            amt = current.amount
            payee = current.payeeName
            txnId = current.getTransactionIdValue() ?: return
            insertJob = pendingInsertJob

            cleanupLocked()
            _paymentState.value = stateFactory(phone, amt, payee, txnId)
        }

        scope.launch(Dispatchers.IO) {
            insertJob?.join()
            try {
                if (finalStatus == "TIMEOUT" || finalStatus == "CANCELLED") {
                    db.transactionDao().deleteTransaction(txnId)
                } else {
                    db.transactionDao().updateStatus(txnId, finalStatus)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error finalizing session transaction", e)
            }
        }
    }

    private fun cleanupLocked() {
        collectJob?.cancel()
        collectJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        if (coordinatorAcquired) {
            coordinator.release(COORDINATOR_TAG)
            coordinatorAcquired = false
        }
    }

    fun resetToIdle() {
        synchronized(sessionLock) {
            cleanupLocked()
            _paymentState.value = OfflinePaymentState.Idle
        }
    }

    fun startSession(
        phoneNumber: String,
        amount: String,
        payeeName: String = "",
        payeeUpiId: String? = null,
        rail: String = "123PAY"
    ): Boolean {
        begin(phoneNumber, amount, payeeName, payeeUpiId, rail = rail)
        return true
    }

    fun cancelSession(reason: String = "Cancelled by user") {
        onUserCancelled()
    }
}

private val OfflinePaymentState.phoneNumber: String
    get() = when (this) {
        is OfflinePaymentState.Initiating -> phoneNumber
        is OfflinePaymentState.InProgress -> phoneNumber
        is OfflinePaymentState.WaitingForVerification -> phoneNumber
        is OfflinePaymentState.Success -> phoneNumber
        is OfflinePaymentState.Failed -> phoneNumber
        is OfflinePaymentState.NeedsReview -> phoneNumber
        is OfflinePaymentState.Cancelled -> phoneNumber
        is OfflinePaymentState.Timeout -> phoneNumber
        is OfflinePaymentState.Idle -> ""
    }

private val OfflinePaymentState.amount: String
    get() = when (this) {
        is OfflinePaymentState.Initiating -> amount
        is OfflinePaymentState.InProgress -> amount
        is OfflinePaymentState.WaitingForVerification -> amount
        is OfflinePaymentState.Success -> amount
        is OfflinePaymentState.Failed -> amount
        is OfflinePaymentState.NeedsReview -> amount
        is OfflinePaymentState.Cancelled -> amount
        is OfflinePaymentState.Timeout -> amount
        is OfflinePaymentState.Idle -> ""
    }

private val OfflinePaymentState.payeeName: String
    get() = when (this) {
        is OfflinePaymentState.Initiating -> payeeName
        is OfflinePaymentState.InProgress -> payeeName
        is OfflinePaymentState.WaitingForVerification -> payeeName
        is OfflinePaymentState.Success -> payeeName
        is OfflinePaymentState.Failed -> payeeName
        is OfflinePaymentState.NeedsReview -> payeeName
        is OfflinePaymentState.Cancelled -> payeeName
        is OfflinePaymentState.Timeout -> payeeName
        is OfflinePaymentState.Idle -> ""
    }
