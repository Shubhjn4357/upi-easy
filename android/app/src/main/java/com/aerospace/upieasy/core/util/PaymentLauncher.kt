package com.aerospace.upieasy.core.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

data class UpiAppInfo(
    val packageName: String,
    val appName: String
)

object PaymentLauncher {

    fun getInstalledUpiApps(context: Context): List<UpiAppInfo> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("upi://pay?pa=test@upi&pn=Test&cu=INR")
        }

        val packageManager = context.packageManager
        val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

        return resolveInfos.map {
            UpiAppInfo(
                packageName = it.activityInfo.packageName,
                appName = it.loadLabel(packageManager).toString()
            )
        }
    }

    fun createPaymentIntent(uriString: String, targetPackage: String? = null): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(uriString)
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }
        }
    }
}
