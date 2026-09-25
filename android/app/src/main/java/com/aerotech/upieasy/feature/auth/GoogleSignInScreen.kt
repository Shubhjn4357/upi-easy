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
import com.aerotech.upieasy.core.network.UpdateProfileRequest
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
    var pendingInvite by remember { mutableStateOf<com.aerotech.upieasy.core.network.InvitationDto?>(null) }
    var isAcceptingInvite by remember { mutableStateOf(false) }

    fun handleGoogleAuth() {
        isLoading = true
        errorMessage = null

        scope.launch {
            try {
                val webClientId = if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
                    BuildConfig.GOOGLE_WEB_CLIENT_ID
                } else {
                    context.getString(com.aerotech.upieasy.R.string.default_web_client_id)
                }
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

                        val resBody = res.body()
                        if (res.isSuccessful && resBody?.success == true) {
                            val body = resBody
                            val resolvedAvatar = body.user.avatarUrl?.takeIf { it.isNotBlank() } ?: googleAuth.profilePictureUri
                            sessionManager.saveSession(
                                accessToken = body.tokens.accessToken,
                                refreshToken = body.tokens.refreshToken,
                                userId = body.user.id,
                                mobileNumber = body.user.mobileNumber ?: "",
                                email = body.user.email,
                                fullName = body.user.fullName,
                                avatarUrl = resolvedAvatar
                            )

                            // Proactively sync avatar to backend if missing on profile
                            if (body.user.avatarUrl.isNullOrBlank() && !googleAuth.profilePictureUri.isNullOrBlank()) {
                                try {
                                    apiService.updateProfile(UpdateProfileRequest(avatarUrl = googleAuth.profilePictureUri))
                                } catch (_: Exception) {}
                            }

                            body.defaultOrg?.let { org ->
                                sessionManager.setOrganization(
                                    orgId = org.id,
                                    orgName = org.name,
                                    role = org.role,
                                    legalName = org.legalBusinessName,
                                    category = org.category,
                                    panNumber = org.panNumber,
                                    gstin = org.gstin
                                )
                            }

                            sessionManager.setSetupComplete(body.isSetupComplete)

                            if (body.isSetupComplete) {
                                onNavigateToMain()
                            } else {
                                // Check if user has any pending invites
                                try {
                                    val inviteRes = apiService.getMyInvitations()
                                    if (inviteRes.isSuccessful && inviteRes.body()?.success == true) {
                                        val invites = inviteRes.body()?.invitations ?: inviteRes.body()?.invites ?: emptyList()
                                        val pending = invites.firstOrNull { it.status.equals("PENDING", ignoreCase = true) }
                                        if (pending != null) {
                                            pendingInvite = pending
                                            return@launch
                                        }
                                    }
                                } catch (_: Exception) {}
                                onNavigateToSetup()
                            }
                        } else {
                            val errorText = res.errorBody()?.string() ?: "Google authentication failed on server."
                            errorMessage = errorText
                        }
                    },
                    onFailure = { error ->
                        Log.w("GoogleSignInScreen", "Credential Manager sign-in failed: ${error.message}", error)
                        val msg = when {
                            error is androidx.credentials.exceptions.GetCredentialCancellationException ->
                                "Sign-in was cancelled. If you selected an account, Google closed the request because this Gmail is not registered in Google Cloud Console 'Test users' or the Android SHA-1 fingerprint is missing."
                            error is androidx.credentials.exceptions.NoCredentialException || error.message?.contains("no credential", ignoreCase = true) == true ->
                                "No Google account found on this device. Please ensure a Google account is added in Android Settings > Accounts."
                            else -> error.localizedMessage ?: "Google Sign-In was unavailable on this device."
                        }
                        errorMessage = msg
                    }
                )
            } catch (e: Exception) {
                Log.e("GoogleSignInScreen", "Unexpected error during Google Sign-In", e)
                val msg = when {
                    e is androidx.credentials.exceptions.NoCredentialException || e.message?.contains("no credential", ignoreCase = true) == true ->
                        "No Google account or matching credentials found on this device. Please check Settings > Accounts."
                    else -> e.localizedMessage ?: "Unable to connect to UPI-Easy authentication service"
                }
                errorMessage = msg
            } finally {
                isLoading = false
            }
        }
    }

    // Pending Invitation Popup Modal
    pendingInvite?.let { invite ->
        com.aerotech.upieasy.ui.components.UpieasyPendingInviteModal(
            visible = true,
            orgName = invite.organizationName ?: "Workspace",
            role = invite.role,
            invitedBy = invite.inviterName,
            isProcessing = isAcceptingInvite,
            onAccept = {
                scope.launch {
                    isAcceptingInvite = true
                    try {
                        val acceptRes = apiService.acceptInvitation(invite.id)
                        if (acceptRes.isSuccessful && acceptRes.body()?.success == true) {
                            val orgRes = apiService.getOrganizations()
                            if (orgRes.isSuccessful && orgRes.body()?.success == true) {
                                val orgs = orgRes.body()!!.organizations
                                val target = orgs.find { it.id == invite.organizationId } ?: orgs.firstOrNull()
                                if (target != null) {
                                    sessionManager.setOrganization(
                                        orgId = target.id,
                                        orgName = target.name,
                                        role = target.role,
                                        legalName = target.legalBusinessName,
                                        category = target.category,
                                        panNumber = target.panNumber,
                                        gstin = target.gstin
                                    )
                                    sessionManager.setSetupComplete(true)
                                    pendingInvite = null
                                    onNavigateToMain()
                                    return@launch
                                }
                            }
                        } else {
                            errorMessage = acceptRes.body()?.message ?: "Failed to accept invitation"
                        }
                    } catch (e: Exception) {
                        errorMessage = e.localizedMessage ?: "Network error accepting invitation"
                    } finally {
                        isAcceptingInvite = false
                    }
                }
            },
            onDecline = {
                scope.launch {
                    try {
                        apiService.rejectInvitation(invite.id)
                    } catch (_: Exception) {}
                    pendingInvite = null
                    onNavigateToSetup()
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Background blurry ambient glow spheres adapting to dynamic theme colors
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = (-60).dp, y = (-40).dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
        )
        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 60.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Branding Logo with dynamic primary container
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "UPI-Easy",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Business UPI & Smart Multi-Bank Manager",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Main Login Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
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
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Sign in securely using your Google account to access your transactions, UPI soundbox, and QR codes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Feature Highlights adapting to dynamic colors
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FeaturePill(icon = Icons.Default.AccountBalance, title = "Multi-Bank UPI Aggregation")
                        FeaturePill(icon = Icons.Default.QrCodeScanner, title = "Instant QR Generator & Scanner")
                        FeaturePill(icon = Icons.Default.NotificationsActive, title = "Real-time Notification Sync")
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Real Sign in with Google Button adapting to dynamic colors
                    Button(
                        onClick = { handleGoogleAuth() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
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
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "G",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Sign in with Google",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Encrypted session • Bank credentials never stored",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
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
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
