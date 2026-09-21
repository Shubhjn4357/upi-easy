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

val supportedPaymentApps = listOf(
    PaymentAppDefinition(
        id = "phonepe",
        displayName = "PhonePe",
        packageName = "com.phonepe.app",
        supported = true,
        parserKey = "phonepe"
    ),
    PaymentAppDefinition(
        id = "google_pay",
        displayName = "Google Pay",
        packageName = "com.google.android.apps.nbu.paisa.user",
        supported = true,
        parserKey = "google_pay"
    )
)

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
