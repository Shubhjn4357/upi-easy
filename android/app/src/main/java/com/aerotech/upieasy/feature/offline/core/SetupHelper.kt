package com.aerotech.upieasy.feature.offline.core

import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

object SetupHelper {
    private const val PREFS = "upieasy_offline_prefs"
    private const val KEY_USER_REPORTED_USSD_NOT_WORKING = "user_reported_ussd_not_working"
    private const val KEY_PREFERRED_RAIL = "preferred_offline_rail" // "123PAY" or "USSD"

    data class SimInfo(
        val subscriptionId: Int,
        val slotIndex: Int,
        val carrierName: String,
        val isJio: Boolean
    )

    fun getDetectedSims(context: Context): List<SimInfo> {
        val list = mutableListOf<SimInfo>()
        try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val activeList = subManager?.activeSubscriptionInfoList
            if (!activeList.isNullOrEmpty()) {
                for (info in activeList) {
                    val name = info.carrierName?.toString() ?: "SIM ${info.simSlotIndex + 1}"
                    val isJio = name.contains("jio", ignoreCase = true)
                    list.add(
                        SimInfo(
                            subscriptionId = info.subscriptionId,
                            slotIndex = info.simSlotIndex,
                            carrierName = name,
                            isJio = isJio
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission READ_PHONE_STATE not granted
        }

        if (list.isEmpty()) {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simName = tm?.simOperatorName?.ifBlank { null } ?: tm?.networkOperatorName?.ifBlank { null } ?: "Default SIM"
            val isJio = simName.contains("jio", ignoreCase = true)
            list.add(
                SimInfo(
                    subscriptionId = -1,
                    slotIndex = 0,
                    carrierName = simName,
                    isJio = isJio
                )
            )
        }
        return list
    }

    fun isPrimarySimUssdCapable(context: Context): Boolean {
        val sims = getDetectedSims(context)
        val primary = sims.firstOrNull() ?: return true
        return !primary.isJio
    }

    fun hasUserReportedUssdNotWorking(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_USER_REPORTED_USSD_NOT_WORKING, false)
    }

    fun setUserReportedUssdNotWorking(context: Context, reported: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_USER_REPORTED_USSD_NOT_WORKING, reported)
            .apply()
    }

    fun isScanToPayUssdAvailable(context: Context): Boolean {
        return isPrimarySimUssdCapable(context) && !hasUserReportedUssdNotWorking(context)
    }

    fun getScanToPayBlockedMessage(context: Context): String? {
        if (isScanToPayUssdAvailable(context)) return null
        if (!isPrimarySimUssdCapable(context)) {
            return "Scan to pay via USSD is not supported on Jio (VoLTE network). Please use UPI 123Pay IVR."
        }
        return "Scan to pay via *99# USSD is disabled or unavailable on this device."
    }

    fun getPreferredRail(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PREFERRED_RAIL, if (isPrimarySimUssdCapable(context)) "123PAY" else "123PAY") ?: "123PAY"
    }

    fun setPreferredRail(context: Context, rail: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PREFERRED_RAIL, rail)
            .apply()
    }
}
