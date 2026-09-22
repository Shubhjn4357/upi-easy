package com.aerotech.upieasy.feature.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.network.ApiService
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.ui.theme.FailedRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AccountActionButtons(
    onSignOutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    canDeleteAccount: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Sign Out Button
        OutlinedButton(
            onClick = onSignOutClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out", fontWeight = FontWeight.Bold)
        }

        // Delete Account Button (Role-Gated: Strictly only available for OWNER)
        if (canDeleteAccount) {
            Button(
                onClick = onDeleteAccountClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FailedRed.copy(alpha = 0.12f),
                    contentColor = FailedRed
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = FailedRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Account & Data", fontWeight = FontWeight.Bold, color = FailedRed)
            }
        }
    }
}

@Composable
fun SignOutConfirmDialog(
    sessionManager: SessionManager,
    database: AppDatabase,
    apiService: ApiService? = null,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Sign Out of Upieasy?", fontWeight = FontWeight.Bold) },
        text = {
            Text("Are you sure you want to sign out? Your offline cached data will be cleared from this device.")
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    scope.launch {
                        try {
                            val deviceId = sessionManager.getDeviceId()
                            if (!deviceId.isNullOrBlank()) {
                                apiService?.unregisterDevice(mapOf("deviceId" to deviceId))
                            }
                        } catch (_: Exception) {}
                        sessionManager.clearSession()
                        withContext(Dispatchers.IO) {
                            database.clearAllTables()
                        }
                        onLogout()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Sign Out", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun DeleteAccountConfirmDialog(
    apiService: ApiService,
    sessionManager: SessionManager,
    database: AppDatabase,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isDeleting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = {
            Icon(Icons.Default.Warning, contentDescription = null, tint = FailedRed, modifier = Modifier.size(32.dp))
        },
        title = {
            Text("Permanently Delete Account?", fontWeight = FontWeight.Bold, color = FailedRed)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This action is permanent and cannot be undone.")
                Text(
                    "In accordance with Google Play Financial Data Policies, your merchant credentials, local databases, and linked organization data will be purged. Non-repudiation settlement records required by NPCI regulations may be archived by our banking partners per statutory law.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isDeleting = true
                    scope.launch {
                        try {
                            val res = apiService.deleteAccount()
                            if (res.isSuccessful && (res.body()?.success == true || res.code() == 200 || res.code() == 204)) {
                                sessionManager.clearSession()
                                withContext(Dispatchers.IO) {
                                    database.clearAllTables()
                                }
                                onDismiss()
                                onLogout()
                            } else {
                                val errBody = res.errorBody()?.string()
                                val parsedMsg = try {
                                    org.json.JSONObject(errBody ?: "").optString("message", "")
                                } catch (_: Exception) { "" }
                                val displayMsg = if (parsedMsg.isNotBlank()) parsedMsg else (res.body()?.message ?: "Failed to delete account (HTTP ${res.code()})")
                                onError(displayMsg)
                                isDeleting = false
                                onDismiss()
                            }
                        } catch (e: Exception) {
                            onError(e.localizedMessage ?: "Network error during account deletion")
                            isDeleting = false
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = FailedRed),
                enabled = !isDeleting
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Delete Everything", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
