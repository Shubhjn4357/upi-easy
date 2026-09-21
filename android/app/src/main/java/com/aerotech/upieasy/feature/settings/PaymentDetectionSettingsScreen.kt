package com.aerotech.upieasy.feature.settings

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.database.entity.PaymentAccountEntity
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.feature.notifications.AndroidPaymentAppDetector
import com.aerotech.upieasy.feature.notifications.PaymentNotificationListenerService
import com.aerotech.upieasy.feature.notifications.supportedPaymentApps
import com.aerotech.upieasy.ui.theme.BrandPrimary
import com.aerotech.upieasy.ui.theme.SuccessGreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

fun isNotificationAccessGranted(context: Context): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val component = ComponentName(context, PaymentNotificationListenerService::class.java)
            notificationManager.isNotificationListenerAccessGranted(component)
        } else {
            val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            val packageName = context.packageName
            enabledListeners != null && enabledListeners.contains(packageName)
        }
    } catch (_: Exception) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentDetectionSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager(context) }
    val database = remember { AppDatabase.getInstance(context) }
    val detector = remember { AndroidPaymentAppDetector(context) }

    var isListenerGranted by remember { mutableStateOf(isNotificationAccessGranted(context)) }
    var accounts by remember { mutableStateOf<List<PaymentAccountEntity>>(emptyList()) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Re-check permission on lifecycle resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isListenerGranted = isNotificationAccessGranted(context)
                scope.launch {
                    val orgId = sessionManager.getCurrentOrgId()
                    if (orgId != null) {
                        accounts = database.paymentAccountDao().getAccountsForOrgSync(orgId)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        val orgId = sessionManager.getCurrentOrgId()
        if (orgId != null) {
            accounts = database.paymentAccountDao().getAccountsForOrgSync(orgId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Detection", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Access Status Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isListenerGranted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                border = BorderStroke(
                    1.dp,
                    if (isListenerGranted) Color(0xFFA5D6A7) else Color(0xFFFFCC80)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isListenerGranted) SuccessGreen else Color(0xFFE65100)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isListenerGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isListenerGranted) "Notification Access Active" else "Notification Access Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isListenerGranted) Color(0xFF1B5E20) else Color(0xFFBF360C)
                        )
                        Text(
                            text = if (isListenerGranted)
                                "UPI-Easy is actively observing payment notifications on this device."
                            else
                                "Grant Android notification access to observe incoming PhonePe and Google Pay payments.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!isListenerGranted) {
                Button(
                    onClick = { showPermissionDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enable Notification Access")
                }
            }

            // Supported Payment Apps Status
            Text(
                text = "Supported UPI Apps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            supportedPaymentApps.forEach { app ->
                val isInstalled = detector.isInstalled(app.packageName)
                val appAccounts = accounts.filter { it.paymentAppPackage == app.packageName }
                val isEnabled = appAccounts.any { it.detectionEnabled && it.status == "ACTIVE" }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (app.id == "phonepe") Color(0xFF5F259F) else Color(0xFF1A73E8)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = app.displayName,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = when {
                                    !isInstalled -> "Not installed on this device"
                                    isEnabled && isListenerGranted -> "● Active (Observing payments)"
                                    isEnabled && !isListenerGranted -> "○ Permission needed"
                                    else -> "○ No active payment account configured"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    !isInstalled -> MaterialTheme.colorScheme.error
                                    isEnabled && isListenerGranted -> SuccessGreen
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )

                            // Show last detected time if available
                            val lastDetected = appAccounts.mapNotNull { it.lastNotificationDetectedAt }.maxOrNull()
                            if (lastDetected != null) {
                                val timeStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(lastDetected))
                                Text(
                                    text = "Last payment observed: $timeStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Legal & Architectural Disclosure Banner
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Privacy & Verification Notice",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = BrandPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• Payment detection is based on notifications generated by the selected UPI app.\n" +
                                "• Notifications can be delayed, altered, or silenced by the operating system.\n" +
                                "• UPI-Easy records these events as 'Observed' signals until verified against authoritative bank statements or provider feeds.\n" +
                                "• UPI-Easy never accesses passwords, PINs, OTPs, or private databases of other applications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }

    // Educational Onboarding Dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = BrandPrimary) },
            title = { Text("Why is Notification Access Needed?") },
            text = {
                Text(
                    "UPI-Easy uses Android's official NotificationListenerService to observe payment arrival alerts posted by PhonePe and Google Pay.\n\n" +
                            "• No access to other apps' private data\n" +
                            "• No SMS scraping or private API abuse\n" +
                            "• Only supported payment notifications are processed\n\n" +
                            "Tap 'Open Settings' to enable UPI-Easy in Android's notification access list."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
