package com.aerotech.upieasy.feature.auth

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerotech.upieasy.BuildConfig
import com.aerotech.upieasy.core.network.GoogleLoginRequest
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun GoogleSignInScreen(
    sessionManager: SessionManager,
    onNavigateToSetup: () -> Unit,
    onNavigateToMain: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(sessionManager) }
    val googleSignInManager = remember { GoogleSignInManager(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun handleGoogleAuth() {
        isLoading = true
        errorMessage = null

        scope.launch {
            try {
                val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
                val authResult = googleSignInManager.signIn(context, webClientId)

                authResult.fold(
                    onSuccess = { googleAuth ->
                        val res = apiService.loginWithGoogle(
                            GoogleLoginRequest(
                                idToken = googleAuth.idToken,
                                nonce = googleAuth.nonce,
                                deviceId = "android_${android.os.Build.MODEL}",
                                deviceModel = android.os.Build.MODEL,
                                osVersion = "Android ${android.os.Build.VERSION.RELEASE}"
                            )
                        )

                        if (res.isSuccessful && res.body()?.success == true) {
                            val body = res.body()!!
                            sessionManager.saveSession(
                                accessToken = body.tokens.accessToken,
                                refreshToken = body.tokens.refreshToken,
                                userId = body.user.id,
                                mobileNumber = body.user.mobileNumber ?: "",
                                email = body.user.email,
                                fullName = body.user.fullName
                            )

                            body.defaultOrg?.let { org ->
                                sessionManager.setOrganization(org.id, org.name, org.role)
                            }

                            sessionManager.setSetupComplete(body.isSetupComplete)

                            if (body.isSetupComplete) {
                                onNavigateToMain()
                            } else {
                                onNavigateToSetup()
                            }
                        } else {
                            val errorText = res.errorBody()?.string() ?: "Google authentication failed on server."
                            errorMessage = errorText
                        }
                    },
                    onFailure = { error ->
                        Log.w("GoogleSignInScreen", "Credential Manager sign-in failed: ${error.message}", error)
                        errorMessage = error.localizedMessage ?: "Google Sign-In was cancelled or unavailable on this device."
                    }
                )
            } catch (e: Exception) {
                Log.e("GoogleSignInScreen", "Unexpected error during Google Sign-In", e)
                errorMessage = e.localizedMessage ?: "Unable to connect to UPI-Easy authentication service"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Branding Logo
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(BrandPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalance,
                contentDescription = null,
                tint = SurfaceLight,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "UPI-Easy",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Business UPI & Smart Multi-Bank Manager",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Main Login Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Welcome Merchant",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Sign in securely using your Google account to access your transactions, UPI soundbox, and QR codes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Feature Highlights
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceCard)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeaturePill(icon = Icons.Default.AccountBalance, title = "Multi-Bank UPI Aggregation")
                    FeaturePill(icon = Icons.Default.QrCodeScanner, title = "CameraX Instant QR Generator & Scanner")
                    FeaturePill(icon = Icons.Default.NotificationsActive, title = "Real-time Notification Sync & Room DB")
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Real Sign in with Google Button triggering Android Credential Manager
                Button(
                    onClick = { handleGoogleAuth() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = SurfaceLight,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                modifier = Modifier.size(26.dp),
                                shape = CircleShape,
                                color = Color.White
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "G",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = BrandPrimary,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Sign in with Google",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SurfaceLight
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = SurfaceLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = FailedRed.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, FailedRed.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = FailedRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage!!,
                                color = FailedRed,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Encrypted session • Bank credentials never stored",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(BrandAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrandAccent,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
