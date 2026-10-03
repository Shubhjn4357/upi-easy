package com.aerotech.upieasy.feature.notifications

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class PaymentAppDefinition(
    val id: String,
    val displayName: String,
    val packageName: String,
    val supported: Boolean,
    val parserKey: String
)

object SupportedPaymentApps {
    val GOOGLE_PAY = PaymentAppDefinition(
        id = "google_pay",
        displayName = "Google Pay",
        packageName = "com.google.android.apps.nbu.paisa.user",
        supported = true,
        parserKey = "google_pay"
    )

    val PHONEPE = PaymentAppDefinition(
        id = "phonepe",
        displayName = "PhonePe",
        packageName = "com.phonepe.app",
        supported = true,
        parserKey = "phonepe"
    )

    val BHIM = PaymentAppDefinition(
        id = "bhim",
        displayName = "BHIM UPI",
        packageName = "in.org.npci.upiapp",
        supported = true,
        parserKey = "bhim"
    )

    val PAYTM = PaymentAppDefinition(
        id = "paytm",
        displayName = "Paytm",
        packageName = "net.one97.paytm",
        supported = true,
        parserKey = "paytm"
    )

    val ALL = listOf(
        GOOGLE_PAY,
        PHONEPE,
        BHIM,
        PAYTM
    )
}

val supportedPaymentApps = SupportedPaymentApps.ALL

interface PaymentAppDetector {
    fun getInstalledSupportedApps(): List<PaymentAppDefinition>
    fun isInstalled(packageName: String): Boolean
    fun getAppIcon(packageName: String): Drawable?
}

class AndroidPaymentAppDetector(
    private val context: Context
) : PaymentAppDetector {

    override fun getInstalledSupportedApps(): List<PaymentAppDefinition> {
        return supportedPaymentApps.filter { isInstalled(it.packageName) }
    }

    override fun isInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun getAppIcon(packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }
    }
}
