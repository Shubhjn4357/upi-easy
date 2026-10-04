package com.aerotech.upieasy.feature.offline.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Manages cellular calling for offline UPI (UPI 123Pay IVR and *99# USSD).
 */
class CallManager(private val context: Context) {

    companion object {
        private const val TAG = "CallManager"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var originalCallVolume: Int = 0
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var isAudioMuted = false
    private val audioLock = Any()

    fun initiateUPI123Call(phoneNumber: String, amount: String, subscriptionId: Int? = null): Boolean {
        return try {
            val result = Upi123CallStringBuilder.build(
                Upi123CallStringBuilder.DEFAULT_UPI_SERVICE_NUMBER,
                phoneNumber,
                amount
            )
            val callString = when (result) {
                is Upi123CallStringBuilder.Result.Invalid -> {
                    val msg = Upi123CallStringBuilder.messageFor(result.reason)
                    Log.e(TAG, "UPI123 call rejected: $msg")
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    return false
                }
                is Upi123CallStringBuilder.Result.Valid -> result.callString
            }

            val hasCallPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            val intentAction = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(intentAction).apply {
                data = Uri.parse(callString)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (subscriptionId != null && subscriptionId >= 0) {
                    putExtra("subscription", subscriptionId)
                    putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", subscriptionId)
                }
            }
            context.startActivity(intent)
            Log.d(TAG, "UPI 123Pay call launched: $callString")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch UPI 123Pay call", e)
            Toast.makeText(context, "Could not start payment call: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun initiateUSSDCall(ussdCode: String = "*99#", subscriptionId: Int? = null): Boolean {
        return try {
            val encodedUssd = Uri.encode(ussdCode)
            val hasCallPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            val intentAction = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(intentAction).apply {
                data = Uri.parse("tel:$encodedUssd")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (subscriptionId != null && subscriptionId >= 0) {
                    putExtra("subscription", subscriptionId)
                }
            }
            context.startActivity(intent)
            Log.d(TAG, "USSD call initiated: $ussdCode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start USSD call", e)
            Toast.makeText(context, "Could not start USSD call: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun muteCallAudio(): Boolean {
        synchronized(audioLock) {
            return try {
                if (isAudioMuted) return true
                originalCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                previousAudioMode = audioManager.mode

                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, 0)
                isAudioMuted = true
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error muting audio", e)
                false
            }
        }
    }

    fun restoreCallAudio(): Boolean {
        synchronized(audioLock) {
            return try {
                if (!isAudioMuted) return true
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, originalCallVolume, 0)
                audioManager.mode = previousAudioMode
                isAudioMuted = false
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring audio", e)
                false
            }
        }
    }

    fun hangupCall(): Boolean {
        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                tm?.endCall() ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call", e)
            false
        }
    }

    fun cleanup() {
        restoreCallAudio()
    }
}
