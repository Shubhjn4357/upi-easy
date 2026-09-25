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

@Composable
fun DashboardScreen(
    sessionManager: SessionManager,
    onNavigateToScan: () -> Unit,
    onNavigateToQr: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToUpi: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToStaff: () -> Unit = {},
    onNavigateToLegal: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val apiService = remember { NetworkClient.getApiService(sessionManager) }
    val database = remember { AppDatabase.getInstance(context) }
    val orgRepository = remember { OrganizationRepository(context, apiService, database, sessionManager) }
    val organizations by orgRepository.observeOrganizations().collectAsState(initial = emptyList())
    var showOrgSwitcher by remember { mutableStateOf(false) }

    val currentOrgId by sessionManager.currentOrgIdFlow.collectAsState(initial = null)
    val currentOrgName by sessionManager.currentOrgNameFlow.collectAsState(initial = null)
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
                        // Bento Tile 1: Hero Glass Collection Card with Dynamic Shrink/Scale Animation
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
                                UpieasyHeroCard(
                                    balance = receivedAmount,
                                    transactionCount = receivedCount,
                                    onShowQrClick = onNavigateToQr,
                                    onScanPayClick = onNavigateToScan,
                                    onHistoryClick = onNavigateToTransactions
                                )
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
                                modifier = Modifier.weight(1.2f),
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
                                    .clickable { onNavigateToUpi() },
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
                                    .clickable { onNavigateToTransactions() },
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
                                    .clickable { onNavigateToTransactions() },
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
}


