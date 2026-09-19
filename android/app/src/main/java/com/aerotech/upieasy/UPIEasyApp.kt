package com.aerotech.upieasy

import android.app.Application
import android.util.Log
import androidx.work.*
import com.aerotech.upieasy.core.sync.SyncWorker
import java.util.concurrent.TimeUnit

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import com.aerotech.upieasy.core.util.PaymentAlertManager

class UPIEasyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("UPIEasyFatal", "FATAL EXCEPTION on thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        PaymentAlertManager.init(this)
        scheduleBackgroundSync()
        registerNetworkAutoSync()
    }

    private fun registerNetworkAutoSync() {
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && connectivityManager != null) {
                connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        Log.i("UPIEasyApp", "Network connectivity restored - triggering auto-sync")
                        triggerImmediateSync(this@UPIEasyApp)
                    }
                })
            }
        } catch (e: Exception) {
            Log.w("UPIEasyApp", "Could not register default network callback", e)
        }
    }

    private fun scheduleBackgroundSync() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "UPIEasyDeltaSync",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        } catch (e: Throwable) {
            Log.e("UPIEasyApp", "Failed to schedule background sync on startup: ${e.message}", e)
        }
    }

    companion object {
        fun triggerImmediateSync(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "UPIEasyImmediateSync",
                    ExistingWorkPolicy.REPLACE,
                    syncRequest
                )
            } catch (e: Exception) {
                Log.e("UPIEasyApp", "Failed to trigger immediate sync", e)
            }
        }
    }
}
