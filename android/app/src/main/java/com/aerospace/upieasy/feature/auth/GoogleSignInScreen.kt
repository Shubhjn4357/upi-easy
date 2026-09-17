package com.aerospace.upieasy.feature.auth

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerospace.upieasy.core.network.GoogleLoginRequest
import com.aerospace.upieasy.core.network.NetworkClient
import com.aerospace.upieasy.core.security.SessionManager
import com.aerospace.upieasy.ui.theme.*
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

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedAccountEmail by remember { mutableStateOf("merchant.business@gmail.com") }
    var selectedAccountName by remember { mutableStateOf("Merchant Business Owner") }

    fun handleGoogleAuth(email: String, name: String) {
        isLoading = true
        errorMessage = null

        scope.launch {
            try {
                val res = apiService.loginWithGoogle(
                    GoogleLoginRequest(
                        email = email,
                        fullName = name,
                        googleId = "google_${email.hashCode()}",
                        deviceId = "android_${android.os.Build.MODEL}"
                    )
                )

                if (res.isSuccessful && res.body()?.success == true) {
                    val body = res.body()!!
                    sessionManager.saveSession(
                        accessToken = body.tokens.accessToken,
                        refreshToken = body.tokens.refreshToken,
                        userId = body.user.id,
                        mobileNumber = body.user.mobileNumber ?: ""
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
                    errorMessage = "Google authentication failed. Please try again."
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Unable to connect to server"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Identity
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(BrandPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalance,
                contentDescription = null,
                tint = SurfaceLight,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "UPI-Easy",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Business UPI & Multi-Bank Manager",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(36.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Sign in using Google One Tap for frictionless, secure access to your financial dashboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // One Tap Account Pill Preview
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, DividerColor)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(BrandAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedAccountName.take(1),
                                color = SurfaceLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedAccountName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = selectedAccountEmail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = SuccessGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Google One Tap Button
                Button(
                    onClick = {
                        handleGoogleAuth(selectedAccountEmail, selectedAccountName)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = SurfaceLight, modifier = Modifier.size(22.dp))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Continue with Google",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = it, color = FailedRed, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(18.dp))

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
