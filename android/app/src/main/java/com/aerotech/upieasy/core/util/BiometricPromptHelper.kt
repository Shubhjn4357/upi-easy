package com.aerotech.upieasy.core.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricPromptHelper {

    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun canAuthenticate(context: Context): Boolean = isBiometricAvailable(context)

    fun showPrompt(
        activity: FragmentActivity,
        title: String = "Biometric Authentication",
        subtitle: String = "Verify your identity to proceed",
        negativeButtonText: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = showBiometricPrompt(activity, title, subtitle, negativeButtonText, onSuccess, onError)

    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Biometric Authentication",
        subtitle: String = "Verify your identity to proceed",
        negativeButtonText: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // User canceled or other non-fatal errors
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Authentication failed. Try again.")
                }
            }
        )

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (negativeButtonText != null) {
            promptInfoBuilder.setNegativeButtonText(negativeButtonText)
        } else {
            promptInfoBuilder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        }

        prompt.authenticate(promptInfoBuilder.build())
    }
}
