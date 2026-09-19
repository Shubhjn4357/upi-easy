package com.aerotech.upieasy.core.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

// ─────────────────────────────────────────────────────────
// Check helpers
// ─────────────────────────────────────────────────────────

fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

fun Context.hasNotificationPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    else true   // Pre-Android 13 doesn't need explicit notification permission

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

// ─────────────────────────────────────────────────────────
// Reusable "need permission" bottom sheet composable
// ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRationaleSheet(
    title: String,
    reason: String,
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (permanentlyDenied) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open App Settings", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            } else {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Permission", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Not Now")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────
// Camera permission gating composable
// Usage: wrap your QR scanner screen with this
// ─────────────────────────────────────────────────────────

/**
 * Requests CAMERA permission if not granted. Shows a rationale sheet with proper messaging.
 * Calls [onGranted] when permission is available, otherwise shows explainer.
 *
 * @param trigger        If true, triggers the permission request flow immediately.
 * @param onGranted      Called once camera permission is confirmed.
 * @param onDismissed    Called if the user declines/dismisses the rationale.
 */
@Composable
fun CameraPermissionEffect(
    trigger: Boolean,
    onGranted: () -> Unit,
    onDismissed: () -> Unit = {}
) {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onGranted()
        } else {
            // After denial, check if the user can be asked again
            val canAsk = context.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            permanentlyDenied = !canAsk
            showRationale = true
        }
    }

    LaunchedEffect(trigger) {
        if (!trigger) return@LaunchedEffect
        if (context.hasCameraPermission()) {
            onGranted()
        } else {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (showRationale) {
        PermissionRationaleSheet(
            title = "Camera Access Needed",
            reason = "UPI-Easy needs camera access to scan QR codes for payments. Without it, you won't be able to scan and pay.",
            permanentlyDenied = permanentlyDenied,
            onRequestPermission = {
                showRationale = false
                launcher.launch(Manifest.permission.CAMERA)
            },
            onOpenSettings = {
                showRationale = false
                openAppSettings(context)
            },
            onDismiss = {
                showRationale = false
                onDismissed()
            }
        )
    }
}

// ─────────────────────────────────────────────────────────
// Notification permission gating composable (Android 13+)
// ─────────────────────────────────────────────────────────

@Composable
fun NotificationPermissionEffect(
    trigger: Boolean,
    onGranted: () -> Unit = {},
    onDismissed: () -> Unit = {}
) {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        LaunchedEffect(trigger) { if (trigger) onGranted() }
        return
    }

    var showRationale by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onGranted()
        } else {
            val canAsk = context.shouldShowRequestPermissionRationale(
                Manifest.permission.POST_NOTIFICATIONS
            )
            permanentlyDenied = !canAsk
            showRationale = true
        }
    }

    LaunchedEffect(trigger) {
        if (!trigger) return@LaunchedEffect
        if (context.hasNotificationPermission()) {
            onGranted()
        } else {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showRationale) {
        PermissionRationaleSheet(
            title = "Enable Payment Notifications",
            reason = "Allow UPI-Easy to send real-time payment alerts so you never miss an incoming payment — even when the app is in the background.",
            permanentlyDenied = permanentlyDenied,
            onRequestPermission = {
                showRationale = false
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onOpenSettings = {
                showRationale = false
                openAppSettings(context)
            },
            onDismiss = {
                showRationale = false
                onDismissed()
            }
        )
    }
}
