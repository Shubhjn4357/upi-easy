package com.aerotech.upieasy.feature.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.core.util.BiometricPromptHelper
import com.aerotech.upieasy.data.repository.OrganizationRepository
import com.aerotech.upieasy.feature.settings.components.*
import com.aerotech.upieasy.ui.components.OrganizationSwitcher
import com.aerotech.upieasy.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sessionManager: SessionManager,
    database: AppDatabase,
    onNavigateToLegal: () -> Unit = {},
    onNavigateToPaymentDetection: () -> Unit = {},
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(sessionManager) }
    val orgRepository = remember { OrganizationRepository(context, apiService, database, sessionManager) }
    val organizations by orgRepository.observeOrganizations().collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        orgRepository.refreshOrganizations()
    }

    // Session and Organization State
    val currentOrgId by sessionManager.currentOrgIdFlow.collectAsState(initial = null)
    val currentOrgName by sessionManager.currentOrgNameFlow.collectAsState(initial = null)
    val currentOrgLegalName by sessionManager.currentOrgLegalNameFlow.collectAsState(initial = null)
    val currentOrgCategory by sessionManager.currentOrgCategoryFlow.collectAsState(initial = null)
    val currentOrgPan by sessionManager.currentOrgPanFlow.collectAsState(initial = null)
    val currentOrgGstin by sessionManager.currentOrgGstinFlow.collectAsState(initial = null)
    val userRole by sessionManager.userRoleFlow.collectAsState(initial = null)
    val userEmail by sessionManager.userEmailFlow.collectAsState(initial = null)
    val userName by sessionManager.userNameFlow.collectAsState(initial = null)

    // User Preferences State
    val soundNotifications by sessionManager.soundNotificationsFlow.collectAsState(initial = true)
    val biometricLock by sessionManager.biometricLockFlow.collectAsState(initial = false)
    val highValueAlert by sessionManager.highValueAlertFlow.collectAsState(initial = true)
    val themeMode by sessionManager.themeModeFlow.collectAsState(initial = "SYSTEM")
    val dynamicColor by sessionManager.dynamicColorFlow.collectAsState(initial = false)
    val hapticFeedback by sessionManager.hapticFeedbackFlow.collectAsState(initial = true)

    LaunchedEffect(hapticFeedback) {
        com.aerotech.upieasy.core.util.HapticHelper.isHapticsEnabled = hapticFeedback
    }

    // Dialog & Sheet Visibility
    var showOrganizationSwitcher by remember { mutableStateOf(false) }
    var showEditProfileBottomSheet by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings & Preferences",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Ambient Soft Blurry Glow Spheres
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .offset(x = (-50).dp, y = (-30).dp)
                    .clip(CircleShape)
                    .background(SoftGlowIndigo)
            )
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 60.dp, y = 80.dp)
                    .clip(CircleShape)
                    .background(SoftGlowEmerald)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Bento Tile 1: Profile & Business Overview
                ProfileOverviewCard(
                    userName = userName,
                    userEmail = userEmail,
                    userRole = userRole,
                    currentOrgName = currentOrgName,
                    currentOrgLegalName = currentOrgLegalName,
                    currentOrgCategory = currentOrgCategory,
                    currentOrgPan = currentOrgPan,
                    currentOrgGstin = currentOrgGstin,
                    onEditClick = { showEditProfileBottomSheet = true }
                )

                // Bento Tile: Firms & Organizations
                FirmsSectionCard(
                    currentOrgName = currentOrgName,
                    userRole = userRole,
                    organizationsCount = organizations.size,
                    onSwitchBusinessClick = { showOrganizationSwitcher = true }
                )

                // Bento Tile 2: Appearance & Theme Switcher
                AppearanceCard(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    onThemeModeChange = { newMode ->
                        scope.launch { sessionManager.setThemeMode(newMode) }
                    },
                    onDynamicColorChange = { enabled ->
                        scope.launch { sessionManager.setDynamicColor(enabled) }
                    }
                )

                // Bento Tile 3: Audio, Alerts & Biometric Security
                PreferencesCard(
                    soundNotifications = soundNotifications,
                    highValueAlert = highValueAlert,
                    hapticFeedback = hapticFeedback,
                    biometricLock = biometricLock,
                    onSoundChange = { enabled ->
                        com.aerotech.upieasy.core.util.HapticHelper.performHaptic(context, com.aerotech.upieasy.core.util.HapticHelper.FeedbackType.MEDIUM)
                        scope.launch { sessionManager.setSoundNotifications(enabled) }
                    },
                    onHighValueChange = { enabled ->
                        com.aerotech.upieasy.core.util.HapticHelper.performHaptic(context, com.aerotech.upieasy.core.util.HapticHelper.FeedbackType.MEDIUM)
                        scope.launch { sessionManager.setHighValueAlert(enabled) }
                    },
                    onHapticChange = { enabled ->
                        com.aerotech.upieasy.core.util.HapticHelper.isHapticsEnabled = enabled
                        if (enabled) {
                            com.aerotech.upieasy.core.util.HapticHelper.performHaptic(context, com.aerotech.upieasy.core.util.HapticHelper.FeedbackType.SUCCESS)
                        }
                        scope.launch { sessionManager.setHapticFeedback(enabled) }
                    },
                    onBiometricChange = { enabled ->
                        if (enabled) {
                            val activity = context as? FragmentActivity
                            if (activity != null) {
                                if (BiometricPromptHelper.isBiometricAvailable(context)) {
                                    BiometricPromptHelper.showBiometricPrompt(
                                        activity = activity,
                                        title = "Enable Biometric Lock",
                                        subtitle = "Verify identity to protect your Upieasy account",
                                        onSuccess = {
                                            scope.launch {
                                                sessionManager.setBiometricLock(true)
                                                Toast.makeText(context, "Biometric Lock Enabled", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onError = { err: String ->
                                            Toast.makeText(context, "Biometric error: $err", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    Toast.makeText(context, "Biometric hardware not enrolled or available", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                scope.launch { sessionManager.setBiometricLock(true) }
                            }
                        } else {
                            scope.launch {
                                sessionManager.setBiometricLock(false)
                                Toast.makeText(context, "Biometric Lock Disabled", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                // Bento Tile 4: Data & Cloud Sync
                DataSyncCard()

                // Bento Tile 5: Payment Detection & Listener Access
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPaymentDetection() }
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(BrandPrimary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = BrandPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Payment Detection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "PhonePe & Google Pay notification listener access and app detection status",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Error Message Display
                if (errorMessage != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = FailedRed.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, FailedRed.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = FailedRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Bento Tile 5: Legal & Privacy Compliance Section
                LegalSectionCard(onNavigateToLegal = onNavigateToLegal)

                Spacer(modifier = Modifier.height(4.dp))

                // Account Actions (Sign Out & Delete Account - Strictly OWNER only)
                val isOwner = userRole?.uppercase() == "OWNER"
                AccountActionButtons(
                    onSignOutClick = { showSignOutConfirm = true },
                    onDeleteAccountClick = { showDeleteDialog = true },
                    canDeleteAccount = isOwner
                )

                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }

    // Modal Bottom Sheet: Edit Profile & Business Details
    if (showEditProfileBottomSheet) {
        EditProfileBottomSheet(
            userName = userName,
            userEmail = userEmail,
            currentOrgId = currentOrgId,
            currentOrgName = currentOrgName,
            currentOrgLegalName = currentOrgLegalName,
            currentOrgCategory = currentOrgCategory,
            currentOrgPan = currentOrgPan,
            currentOrgGstin = currentOrgGstin,
            apiService = apiService,
            sessionManager = sessionManager,
            onDismiss = { showEditProfileBottomSheet = false }
        )
    }

    // Dialog: Delete Account Confirmation
    if (showDeleteDialog) {
        DeleteAccountConfirmDialog(
            apiService = apiService,
            sessionManager = sessionManager,
            database = database,
            onDismiss = { showDeleteDialog = false },
            onLogout = onLogout,
            onError = { err -> errorMessage = err }
        )
    }

    // Dialog: Sign Out Confirmation
    if (showSignOutConfirm) {
        SignOutConfirmDialog(
            sessionManager = sessionManager,
            database = database,
            apiService = apiService,
            onDismiss = { showSignOutConfirm = false },
            onLogout = onLogout
        )
    }

    // Modal Bottom Sheet: Organization / Firm Switcher
    if (showOrganizationSwitcher) {
        OrganizationSwitcher(
            organizations = organizations,
            activeOrganizationId = currentOrgId,
            onOrganizationSelected = { orgId ->
                scope.launch {
                    orgRepository.switchOrganization(orgId)
                }
                showOrganizationSwitcher = false
            },
            onCreateFirmClick = {
                showOrganizationSwitcher = false
            },
            onDismissRequest = {
                showOrganizationSwitcher = false
            }
        )
    }
}
