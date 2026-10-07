package com.aerotech.upieasy.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerotech.upieasy.core.network.DashboardDto
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.domain.model.Transaction
import com.aerotech.upieasy.ui.components.UpieasyHeroCard
import com.aerotech.upieasy.ui.components.UpieasyPullToRefreshContainer
import com.aerotech.upieasy.ui.components.UpieasyQuickActionButton
import com.aerotech.upieasy.ui.components.UpieasyTopBar
import com.aerotech.upieasy.ui.components.TransactionRow
import com.aerotech.upieasy.ui.components.UpieasyConfirmBottomDrawer
import com.aerotech.upieasy.ui.theme.*
import kotlinx.coroutines.launch

import com.aerotech.upieasy.core.ui.DashboardSkeleton
import com.aerotech.upieasy.ui.components.UpieasyNotificationsSheet
import com.aerotech.upieasy.ui.components.OrganizationSwitcher
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.data.repository.OrganizationRepository
import com.aerotech.upieasy.core.util.PaymentAlertManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import com.aerotech.upieasy.ui.components.DashboardTourGuideOverlay
import com.aerotech.upieasy.ui.components.TourGuideStep
import com.aerotech.upieasy.ui.components.TourHighlightShape
import com.aerotech.upieasy.ui.components.tourAnchor
import kotlinx.coroutines.delay
import com.aerotech.upieasy.core.util.HapticHelper
import com.aerotech.upieasy.feature.offline.core.CallManager
import com.aerotech.upieasy.feature.offline.core.OfflinePaymentSessionManager
import com.aerotech.upieasy.feature.offline.model.OfflinePaymentState
import com.aerotech.upieasy.feature.offline.ui.LivePaymentStatusCard
import com.aerotech.upieasy.ui.components.PayContactBottomSheet

@Composable
fun DashboardScreen(
    sessionManager: SessionManager,
    onNavigateToScan: () -> Unit,
    onNavigateToQr: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToUpi: () -> Unit,
    onNavigateToBankAccounts: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToStaff: () -> Unit = {},
    onNavigateToLegal: () -> Unit = {},
    onNavigateToOfflinePayment: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val apiService = remember { NetworkClient.getApiService(sessionManager) }
    val database = remember { AppDatabase.getInstance(context) }
    val orgRepository = remember { OrganizationRepository(context, apiService, database, sessionManager) }
    val organizations by orgRepository.observeOrganizations().collectAsState(initial = emptyList())
    var showOrgSwitcher by remember { mutableStateOf(false) }

    val upiAccounts by database.upiDao().getAllUpiAccountsFlow().collectAsState(initial = emptyList())
    val sessionManagerInstance = remember { OfflinePaymentSessionManager.getInstance(context) }
    val callManager = remember { CallManager(context) }
    val paymentState by sessionManagerInstance.paymentState.collectAsState()
    var showPayContactSheet by remember { mutableStateOf(false) }

    val currentOrgId by sessionManager.currentOrgIdFlow.collectAsState(initial = null)
    val currentOrgName by sessionManager.currentOrgNameFlow.collectAsState(initial = null)

    val connectedBankName = remember(upiAccounts, currentOrgName) {
        val defaultUpi = upiAccounts.firstOrNull { it.isDefault } ?: upiAccounts.firstOrNull()
        when {
            defaultUpi != null -> defaultUpi.payeeName.ifBlank { defaultUpi.vpa }
            !currentOrgName.isNullOrBlank() -> "$currentOrgName Account"
            else -> "Connected Bank"
        }
    }

    val userName by sessionManager.userNameFlow.collectAsState(initial = null)
    val userAvatarUrl by sessionManager.userAvatarUrlFlow.collectAsState(initial = null)
    val themeMode by sessionManager.themeModeFlow.collectAsState(initial = "SYSTEM")
    val userRole by sessionManager.userRoleFlow.collectAsState(initial = "OWNER")
    val userPermissions by sessionManager.userPermissionsFlow.collectAsState(initial = emptySet())

    val isOwner = userRole?.equals("OWNER", ignoreCase = true) == true || userPermissions.contains("*")
    fun can(permission: String): Boolean = isOwner || userPermissions.contains(permission)
    fun canModule(module: String): Boolean = isOwner || userPermissions.any { it.startsWith("$module.") }

    val alertHistory by PaymentAlertManager.alertHistory.collectAsState()
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    var dashboardData by remember { mutableStateOf<DashboardDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    val isTourCompleted by sessionManager.dashboardTourCompletedFlow.collectAsState(initial = true)
    var hasCheckedTour by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasCheckedTour = true
    }

    val listState = rememberLazyListState()
    val collapseProgress by remember {
        derivedStateOf {
            val index = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            if (index > 0) 1f
            else (offset / 240f).coerceIn(0f, 1f)
        }
    }

    fun refresh() {
        scope.launch {
            var orgId = currentOrgId ?: sessionManager.getCurrentOrgId()
            if (orgId.isNullOrBlank()) {
                try {
                    val orgRes = apiService.getOrganizations()
                    if (orgRes.isSuccessful && orgRes.body()?.success == true) {
                        val firstOrg = orgRes.body()?.organizations?.firstOrNull()
                        if (firstOrg != null) {
                            sessionManager.setOrganization(
                                orgId = firstOrg.id,
                                orgName = firstOrg.name,
                                role = firstOrg.role,
                                legalName = firstOrg.legalBusinessName,
                                category = firstOrg.category,
                                panNumber = firstOrg.panNumber,
                                gstin = firstOrg.gstin,
                                permissions = firstOrg.permissions
                            )
                            orgId = firstOrg.id
                        }
                    }
                } catch (_: Exception) {}
            }

            if (!orgId.isNullOrBlank()) {
                try {
                    val res = apiService.getDashboard(orgId)
                    if (res.isSuccessful && res.body()?.success == true) {
                        dashboardData = res.body()?.dashboard
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(currentOrgId) {
        isLoading = true
        refresh()
    }

    if (showNotificationsSheet) {
        UpieasyNotificationsSheet(
            onDismiss = { showNotificationsSheet = false },
            alertHistory = alertHistory,
            onClearAll = { PaymentAlertManager.clearAlertHistory() },
            onDeleteAlert = { PaymentAlertManager.removeAlert(it) }
        )
    }

    val tourSteps = remember {
        listOf(
            TourGuideStep(
                index = 0,
                title = "Business Pulse & Linked Bank",
                description = "View today's collected revenue, total payment count, and your connected bank account or active UPI ID.",
                targetListItemIndex = 0,
                shape = TourHighlightShape.Rounded
            ),
            TourGuideStep(
                index = 1,
                title = "Scan QR (Online & Offline)",
                description = "Scan any UPI QR code. Pay instantly via installed apps (GPay, PhonePe, Paytm) or seamlessly initiate Offline 123Pay without internet.",
                targetListItemIndex = 1,
                shape = TourHighlightShape.Circle
            ),
            TourGuideStep(
                index = 2,
                title = "Pay Contact / Mobile Number",
                description = "Search phonebook contacts or enter any mobile number directly to pay online or trigger Offline 123Pay telecom calls.",
                targetListItemIndex = 1,
                shape = TourHighlightShape.Rounded
            ),
            TourGuideStep(
                index = 3,
                title = "Receive via Store QR",
                description = "Display and share your official store QR code to accept customer payments right on your counter.",
                targetListItemIndex = 1,
                shape = TourHighlightShape.Rounded
            ),
            TourGuideStep(
                index = 4,
                title = "Business Ledger History",
                description = "Access your verified bookkeeping ledger, transaction receipts, and live soundbox audio alerts.",
                targetListItemIndex = 1,
                shape = TourHighlightShape.Rounded
            )
        )
    }

    var isTourActive by remember { mutableStateOf(false) }
    var currentTourStep by remember { mutableIntStateOf(0) }
    val tourBoundsMap = remember { mutableStateMapOf<Int, Rect>() }

    LaunchedEffect(isLoading, isTourCompleted, hasCheckedTour) {
        if (!isLoading && hasCheckedTour && !isTourCompleted) {
            delay(600)
            isTourActive = true
            currentTourStep = 0
        }
    }

    // Auto-scroll list to bring the highlighted target into view smoothly
    LaunchedEffect(currentTourStep, isTourActive) {
        if (isTourActive && currentTourStep in tourSteps.indices) {
            val targetItem = tourSteps[currentTourStep].targetListItemIndex
            listState.animateScrollToItem(targetItem)
        }
    }

        Scaffold(
            topBar = {
            val unreadCount = remember(alertHistory) { alertHistory.size }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1f - (collapseProgress * 0.04f)
                    }
            ) {
                UpieasyTopBar(
                    brandTitle = "UPIEasy",
                    subtitle = currentOrgName ?: "Merchant Dashboard",
                    avatarInitial = (userName?.take(1) ?: "M").uppercase(),
                    notificationCount = unreadCount,
                    onNotificationClick = { showNotificationsSheet = true },
                    onProfileClick = onNavigateToSettings,
                    onOrganizationClick = { showOrgSwitcher = true },
                    onMenuThemeClick = {
                        val nextTheme = when (themeMode) {
                            "LIGHT" -> "DARK"
                            "DARK" -> "SYSTEM"
                            else -> "LIGHT"
                        }
                        scope.launch { sessionManager.setThemeMode(nextTheme) }
                    },
                    onMenuLegalClick = onNavigateToLegal,
                    onMenuLogoutClick = { showSignOutConfirm = true },
                    userName = userName,
                    organizationName = currentOrgName,
                    avatarUrl = userAvatarUrl
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                DashboardSkeleton()
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Background blurry ambient glow spheres
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .offset(x = (-50).dp, y = (-30).dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                )
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 60.dp, y = 80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f))
                )

                UpieasyPullToRefreshContainer(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        refresh()
                    }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
                    ) {
                        // Bento Tile 1: Signature Top Rounded Periwinkle Card with Connected Bank Pill & Collections
                        item {
                            val receivedAmount = dashboardData?.todayReceived?.amount ?: 0.0
                            val receivedCount = dashboardData?.todayReceived?.count ?: 0

                            val heroScale = 1f - (collapseProgress * 0.06f)
                            val heroAlpha = 1f - (collapseProgress * 0.12f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = heroScale
                                        scaleY = heroScale
                                        alpha = heroAlpha
                                    }
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(32.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .tourAnchor(0) { idx, rect -> tourBoundsMap[idx] = rect }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(22.dp)
                                    ) {
                                        // Header Row: App Title & Subtitle + Organization Switcher Button
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "UPIEasy",
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontSize = 28.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    letterSpacing = (-0.5).sp
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = currentOrgName ?: "Unified Payments & Inflows",
                                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }

                                            // Circular Organization Switcher / Settings Button
                                            IconButton(
                                                onClick = {
                                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                                    showOrgSwitcher = true
                                                },
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.SwapHoriz,
                                                    contentDescription = "Switch Organization",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(18.dp))

                                        // Connected Bank Inner Pill Card (Signature Offline Pay Style)
                                        Surface(
                                            shape = RoundedCornerShape(22.dp),
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                                    if (can("accounts.read") || can("accounts.manage")) {
                                                        onNavigateToBankAccounts()
                                                    } else {
                                                        onNavigateToUpi()
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Connected Bank & UPI",
                                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = connectedBankName,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                }

                                                // Circular Wallet / Bank Badge
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.30f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AccountBalanceWallet,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(18.dp))

                                        // Today's Inflows Summary Pill inside top card
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Today's Collection",
                                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "₹${String.format("%,.2f", receivedAmount)}",
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                                            ) {
                                                Text(
                                                    text = "$receivedCount payments",
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Live Payment Banner (if an offline payment is active)
                        if (paymentState !is OfflinePaymentState.Idle) {
                            item {
                                LivePaymentStatusCard(
                                    state = paymentState,
                                    onCancelSession = {
                                        sessionManagerInstance.cancelSession("Cancelled by user")
                                        callManager.hangupCall()
                                    }
                                )
                            }
                        }

                        // Bento Tile 2: Center Hero Controls (Iconic Offline Pay: Circular Scan QR + Squircle Pay Contact)
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(26.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp, horizontal = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Action 1: Circular Scan QR Code Button
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable {
                                                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                                                if (can("transactions.create")) onNavigateToScan()
                                            }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(68.dp)
                                                    .tourAnchor(1) { idx, rect -> tourBoundsMap[idx] = rect }
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.QrCodeScanner,
                                                    contentDescription = "Scan QR Code",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Scan QR Code",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Online / Offline",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        }

                                        // "OR" Divider Pill
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "OR",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        }

                                        // Action 2: Squircle Pay Contact Button
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable {
                                                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                                                showPayContactSheet = true
                                            }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(68.dp)
                                                    .tourAnchor(2) { idx, rect -> tourBoundsMap[idx] = rect }
                                                    .clip(RoundedCornerShape(22.dp))
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = "Pay Contact",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Pay Contact",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Search or Number",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Secondary quick links: My QR Code & Ledger
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                            modifier = Modifier
                                                .weight(1f)
                                                .tourAnchor(3) { idx, rect -> tourBoundsMap[idx] = rect }
                                                .clickable {
                                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                                    if (can("qr.create") || can("upi.read")) onNavigateToQr()
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.QrCode,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Receive (My QR)",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                            modifier = Modifier
                                                .weight(1f)
                                                .tourAnchor(4) { idx, rect -> tourBoundsMap[idx] = rect }
                                                .clickable {
                                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                                    if (can("transactions.read")) onNavigateToTransactions()
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Ledger History",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    // Bento Row: Asymmetric Dual Cards (Settlements & Active Accounts)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Bento Sub-Tile A: 0% MDR Free Direct Settlement
                            Card(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .then(if (can("accounts.read") || can("accounts.manage")) Modifier.clickable { onNavigateToBankAccounts() } else Modifier),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                                border = BorderStroke(1.dp, GlassBorderLight),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = SuccessGreenBg
                                        ) {
                                            Text(
                                                text = "0% MDR",
                                                color = SuccessGreen,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Icon(
                                            Icons.Default.Verified,
                                            contentDescription = null,
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Direct Bank Settlements",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Direct to account",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Bento Sub-Tile B: Active UPI Accounts
                            Card(
                                modifier = Modifier
                                    .weight(0.9f)
                                    .then(if (can("upi.read") || can("upi.manage")) Modifier.clickable { onNavigateToUpi() } else Modifier),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                                border = BorderStroke(1.dp, GlassBorderLight),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(PastelPurple),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.AccountBalanceWallet,
                                                contentDescription = null,
                                                tint = PastelPurpleIcon,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "${dashboardData?.activeUpiCount ?: 0} Active",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Linked VPAs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Bento Tile 3: Dynamic Adaptable Bento Quick Action Operations
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Bolt,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Quick Operations",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    ) {
                                        Text(
                                            text = "Action Suite",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                val permittedQuickActions = remember(userRole, userPermissions) {
                                    val list = mutableListOf<Triple<String, androidx.compose.ui.graphics.vector.ImageVector, () -> Unit>>()
                                    if (can("qr.create") || can("upi.read")) {
                                        list.add(Triple("Show QR", Icons.Default.QrCode2, onNavigateToQr))
                                    }
                                    if (can("transactions.create")) {
                                        list.add(Triple("Scan Pay", Icons.Default.QrCodeScanner, onNavigateToScan))
                                    }
                                    if (can("upi.manage")) {
                                        list.add(Triple("Add UPI", Icons.Default.AccountBalanceWallet, onNavigateToUpi))
                                    }
                                    if (can("accounts.read") || can("accounts.manage")) {
                                        list.add(Triple("Bank", Icons.Default.AccountBalance, onNavigateToBankAccounts))
                                    }
                                    if (can("transactions.read")) {
                                        list.add(Triple("Ledger", Icons.AutoMirrored.Filled.ReceiptLong, onNavigateToTransactions))
                                    }
                                    if (can("staff.read") || can("staff.manage")) {
                                        list.add(Triple("Staff", Icons.Default.Group, onNavigateToStaff))
                                    }
                                    list.add(Triple("Alerts", Icons.Default.NotificationsActive, { showNotificationsSheet = true }))
                                    if (can("organization.manage")) {
                                        list.add(Triple("Soundbox", Icons.Default.VolumeUp, onNavigateToSettings))
                                    }
                                    list.add(Triple("Settings", Icons.Default.Settings, onNavigateToSettings))
                                    list
                                }

                                permittedQuickActions.chunked(4).forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowItems.forEach { (label, icon, onClick) ->
                                            val (bgColor, iconTint) = when (label) {
                                                "Show QR" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) to MaterialTheme.colorScheme.primary
                                                "Scan Pay" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f) to MaterialTheme.colorScheme.tertiary
                                                "Add UPI" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) to MaterialTheme.colorScheme.secondary
                                                "Bank" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f) to MaterialTheme.colorScheme.tertiary
                                                "Ledger" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) to MaterialTheme.colorScheme.primary
                                                "Staff" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f) to MaterialTheme.colorScheme.tertiary
                                                "Alerts" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) to MaterialTheme.colorScheme.primary
                                                "Soundbox" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f) to MaterialTheme.colorScheme.secondary
                                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) to MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                            UpieasyQuickActionButton(
                                                icon = icon,
                                                label = label,
                                                backgroundColor = bgColor,
                                                iconTint = iconTint,
                                                onClick = onClick,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        // Fill remaining slots in row for alignment
                                        repeat(4 - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bento Tile 4: Dual Metric Review Cards (Pending & Disputed)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (can("transactions.read")) Modifier.clickable { onNavigateToTransactions() } else Modifier),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                                border = BorderStroke(1.dp, PendingAmber.copy(alpha = 0.3f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(PendingAmberBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = PendingAmber, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "${dashboardData?.pendingCount ?: 0}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text("Pending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (can("transactions.read")) Modifier.clickable { onNavigateToTransactions() } else Modifier),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                                border = BorderStroke(1.dp, FailedRed.copy(alpha = 0.3f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(FailedRedBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Cancel, contentDescription = null, tint = FailedRed, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "${dashboardData?.failedCount ?: 0}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text("Disputed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    // Recent Transactions Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Transactions",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            TextButton(onClick = onNavigateToTransactions) {
                                Text("See All", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                // Recent Transactions List
                val recent = dashboardData?.recentTransactions ?: emptyList()
                if (recent.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No recent transactions",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Incoming UPI payments will appear here in real time",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                } else {
                    items(recent) { dto ->
                        TransactionRow(
                            transaction = Transaction(
                                id = dto.id,
                                organizationId = dto.organizationId,
                                bankAccountId = dto.bankAccountId,
                                upiAccountId = dto.upiAccountId,
                                type = dto.type,
                                direction = dto.direction,
                                amount = dto.amount,
                                currency = dto.currency,
                                status = dto.status,
                                paymentMethod = dto.paymentMethod,
                                referenceNumber = dto.referenceNumber,
                                payerName = dto.payerName,
                                payerVpa = dto.payerVpa,
                                payeeName = dto.payeeName,
                                payeeVpa = dto.payeeVpa,
                                note = dto.note,
                                occurredAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }
    }
}
}

    if (showSignOutConfirm) {
        UpieasyConfirmBottomDrawer(
            visible = showSignOutConfirm,
            title = "Sign Out of UPIEasy?",
            message = "Are you sure you want to sign out? Your offline cache will be cleared from this device and you will need to log back in.",
            confirmText = "Sign Out",
            cancelText = "Cancel",
            isDestructive = true,
            onConfirm = {
                showSignOutConfirm = false
                onLogout()
            },
            onDismiss = {
                showSignOutConfirm = false
            }
        )
    }

    if (showOrgSwitcher) {
        OrganizationSwitcher(
            organizations = organizations,
            activeOrganizationId = currentOrgId,
            onOrganizationSelected = { id ->
                scope.launch {
                    orgRepository.switchOrganization(id)
                    refresh()
                }
            },
            onCreateFirmClick = onNavigateToSettings,
            onDismissRequest = { showOrgSwitcher = false }
        )
    }

    if (showPayContactSheet) {
        PayContactBottomSheet(
            sessionManager = sessionManager,
            database = database,
            onDismiss = { showPayContactSheet = false }
        )
    }

    DashboardTourGuideOverlay(
        visible = isTourActive,
        currentStep = currentTourStep,
        steps = tourSteps,
        boundsMap = tourBoundsMap,
        onNext = {
            if (currentTourStep < tourSteps.size - 1) {
                currentTourStep++
            } else {
                isTourActive = false
                scope.launch { sessionManager.setDashboardTourCompleted(true) }
            }
        },
        onBack = {
            if (currentTourStep > 0) {
                currentTourStep--
            }
        },
        onDismiss = {
            isTourActive = false
            scope.launch { sessionManager.setDashboardTourCompleted(true) }
        }
    )
}


