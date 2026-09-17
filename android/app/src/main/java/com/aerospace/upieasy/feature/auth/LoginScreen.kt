package com.aerospace.upieasy.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerospace.upieasy.core.network.NetworkClient
import com.aerospace.upieasy.core.network.RequestOtpRequest
import com.aerospace.upieasy.core.network.VerifyOtpRequest
import com.aerospace.upieasy.core.security.SessionManager
import com.aerospace.upieasy.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    sessionManager: SessionManager,
    onLoginSuccess: () -> Unit
) {
    var mobileNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }
    var devOtpPreview by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(sessionManager) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Branding
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(BrandPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalance,
                contentDescription = null,
                tint = SurfaceLight,
                modifier = Modifier.size(36.dp)
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
            text = "Business UPI & Multiple Bank Ledger",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(36.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                if (!isOtpSent) {
                    Text(
                        text = "Merchant Mobile Sign In",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Enter your 10-digit registered mobile number to receive a secure login OTP.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = { if (it.length <= 10) mobileNumber = it.filter { char -> char.isDigit() } },
                        label = { Text("Mobile Number") },
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = TextSecondary)
                        },
                        prefix = { Text("+91 ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (mobileNumber.length == 10) {
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        val res = apiService.requestOtp(RequestOtpRequest(mobileNumber))
                                        if (res.isSuccessful && res.body()?.success == true) {
                                            isOtpSent = true
                                            devOtpPreview = res.body()?.devOtpPreview
                                        } else {
                                            errorMessage = "Failed to send OTP. Please check the number."
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = e.localizedMessage ?: "Network error"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = mobileNumber.length == 10 && !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = SurfaceLight, modifier = Modifier.size(24.dp))
                        } else {
                            Text("Send OTP", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                } else {
                    // OTP Verification Step
                    Text(
                        text = "Enter Verification Code",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Code sent to +91 $mobileNumber",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    devOtpPreview?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Dev OTP Preview: $it",
                            style = MaterialTheme.typography.labelMedium,
                            color = BrandAccent
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = otp,
                        onValueChange = { if (it.length <= 6) otp = it.filter { char -> char.isDigit() } },
                        label = { Text("6-Digit OTP") },
                        leadingIcon = {
                            Icon(Icons.Default.Security, contentDescription = null, tint = TextSecondary)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (otp.length == 6) {
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        val res = apiService.verifyOtp(
                                            VerifyOtpRequest(mobileNumber, otp, "android-device")
                                        )
                                        if (res.isSuccessful && res.body()?.success == true) {
                                            val body = res.body()!!
                                            sessionManager.saveSession(
                                                accessToken = body.tokens.accessToken,
                                                refreshToken = body.tokens.refreshToken,
                                                userId = body.user.id,
                                                mobileNumber = body.user.mobileNumber
                                            )
                                            onLoginSuccess()
                                        } else {
                                            errorMessage = "Invalid OTP entered."
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = e.localizedMessage ?: "Verification error"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = otp.length == 6 && !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = SurfaceLight, modifier = Modifier.size(24.dp))
                        } else {
                            Text("Verify & Continue", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { isOtpSent = false; otp = "" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change Mobile Number", color = TextSecondary)
                    }
                }

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = it, color = FailedRed, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
