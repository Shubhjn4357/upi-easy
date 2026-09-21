package com.aerotech.upieasy.core.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

object HapticHelper {

    @Volatile
    var isHapticsEnabled: Boolean = true

    enum class FeedbackType {
        LIGHT,
        MEDIUM,
        HEAVY,
        SUCCESS,
        ERROR,
        SELECTION
    }

    fun performHaptic(context: Context, type: FeedbackType = FeedbackType.LIGHT) {
        if (!isHapticsEnabled) return

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = when (type) {
                    FeedbackType.LIGHT, FeedbackType.SELECTION -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    FeedbackType.MEDIUM -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    FeedbackType.HEAVY -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    FeedbackType.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30), intArrayOf(0, 180, 0, 240), -1)
                    FeedbackType.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 40, 40, 40, 40, 50), intArrayOf(0, 255, 0, 255, 0, 255), -1)
                }
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (type) {
                    FeedbackType.LIGHT, FeedbackType.SELECTION -> VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE)
                    FeedbackType.MEDIUM -> VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
                    FeedbackType.HEAVY -> VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                    FeedbackType.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30), -1)
                    FeedbackType.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 40, 40, 40, 40, 50), -1)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                when (type) {
                    FeedbackType.LIGHT, FeedbackType.SELECTION -> vibrator.vibrate(12)
                    FeedbackType.MEDIUM -> vibrator.vibrate(25)
                    FeedbackType.HEAVY -> vibrator.vibrate(50)
                    FeedbackType.SUCCESS -> vibrator.vibrate(longArrayOf(0, 30, 60, 30), -1)
                    FeedbackType.ERROR -> vibrator.vibrate(longArrayOf(0, 40, 40, 40, 40, 50), -1)
                }
            }
        } catch (_: Exception) {}
    }

    fun performViewHaptic(view: View, type: FeedbackType = FeedbackType.LIGHT) {
        if (!isHapticsEnabled) return
        try {
            val constant = when (type) {
                FeedbackType.LIGHT, FeedbackType.SELECTION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) HapticFeedbackConstants.KEYBOARD_TAP else HapticFeedbackConstants.VIRTUAL_KEY
                FeedbackType.MEDIUM -> HapticFeedbackConstants.VIRTUAL_KEY
                FeedbackType.HEAVY -> HapticFeedbackConstants.LONG_PRESS
                FeedbackType.SUCCESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
                FeedbackType.ERROR -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            }
            view.performHapticFeedback(constant)
        } catch (_: Exception) {}
    }
}
