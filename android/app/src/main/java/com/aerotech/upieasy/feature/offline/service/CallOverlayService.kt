package com.aerotech.upieasy.feature.offline.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import com.aerotech.upieasy.R
import com.aerotech.upieasy.feature.offline.core.CallManager
import com.aerotech.upieasy.feature.offline.core.CurrencyFormat
import com.aerotech.upieasy.feature.offline.core.OfflinePaymentSessionManager
import com.aerotech.upieasy.feature.offline.core.PhoneNumberUtils
import com.aerotech.upieasy.feature.offline.model.OfflinePaymentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Floating in-call assistance overlay for offline UPI 123Pay IVR calls.
 * Displays amount, payee, progress, and end call button.
 */
class CallOverlayService : Service() {

    companion object {
        private const val TAG = "CallOverlayService"
        const val EXTRA_PHONE = "extra_phone"
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_PAYEE = "extra_payee"

        fun show(context: Context, phone: String, amount: String, payee: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Cannot draw overlays: permission not granted")
                return
            }
            val intent = Intent(context, CallOverlayService::class.java).apply {
                putExtra(EXTRA_PHONE, phone)
                putExtra(EXTRA_AMOUNT, amount)
                putExtra(EXTRA_PAYEE, payee)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start CallOverlayService", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, CallOverlayService::class.java))
            } catch (_: Exception) {}
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val phone = intent?.getStringExtra(EXTRA_PHONE) ?: ""
        val amount = intent?.getStringExtra(EXTRA_AMOUNT) ?: ""
        val payee = intent?.getStringExtra(EXTRA_PAYEE) ?: ""

        if (overlayView == null) {
            setupOverlay(phone, amount, payee)
        } else {
            updateDetails(phone, amount, payee)
        }

        return START_NOT_STICKY
    }

    private fun setupOverlay(phone: String, amount: String, payee: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.call_overlay_upieasy, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        updateDetails(phone, amount, payee)

        overlayView?.findViewById<ImageButton>(R.id.btnEndCall)?.setOnClickListener {
            Log.d(TAG, "User tapped End Call in overlay")
            CallManager(this).hangupCall()
            OfflinePaymentSessionManager.getInstance(this).onUserCancelled()
            removeOverlay()
            stopSelf()
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view", e)
            stopSelf()
            return
        }

        // Monitor payment state to auto-dismiss on completion
        serviceScope.launch {
            OfflinePaymentSessionManager.getInstance(this@CallOverlayService).paymentState.collectLatest { state ->
                when (state) {
                    is OfflinePaymentState.InProgress -> {
                        overlayView?.findViewById<TextView>(R.id.statusText)?.text = "Call in progress..."
                        overlayView?.findViewById<ProgressBar>(R.id.progressBar)?.progress = (state.progress * 100).toInt()
                    }
                    is OfflinePaymentState.WaitingForVerification -> {
                        overlayView?.findViewById<TextView>(R.id.statusText)?.text = "Awaiting bank SMS..."
                        overlayView?.findViewById<ProgressBar>(R.id.progressBar)?.progress = 90
                        overlayView?.findViewById<TextView>(R.id.instructionText)?.text =
                            "Call completed. Waiting for bank SMS confirmation (up to 10 mins)..."
                        // Keep open for 5 seconds so user sees it then dismiss overlay to foreground app
                        mainHandler.postDelayed({
                            removeOverlay()
                            stopSelf()
                        }, 5000)
                    }
                    is OfflinePaymentState.Success,
                    is OfflinePaymentState.Failed,
                    is OfflinePaymentState.Cancelled,
                    is OfflinePaymentState.Timeout -> {
                        removeOverlay()
                        stopSelf()
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun updateDetails(phone: String, amount: String, payee: String) {
        val view = overlayView ?: return
        val formattedAmt = "₹${CurrencyFormat.inr(amount)}"
        view.findViewById<TextView>(R.id.amountText)?.text = formattedAmt

        val recipientLabel = if (payee.isNotBlank()) {
            "$payee (${PhoneNumberUtils.formatPhoneForDisplay(phone)})"
        } else {
            PhoneNumberUtils.formatPhoneForDisplay(phone)
        }
        view.findViewById<TextView>(R.id.recipientText)?.text = recipientLabel
    }

    private fun removeOverlay() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view", e)
            } finally {
                overlayView = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        removeOverlay()
    }
}
