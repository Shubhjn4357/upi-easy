package com.aerotech.upieasy.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerotech.upieasy.core.network.DashboardDto
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.domain.model.Transaction
import com.aerotech.upieasy.ui.components.MetricCard
import com.aerotech.upieasy.ui.components.PayouHeroCard
import com.aerotech.upieasy.ui.components.PayouQuickActionButton
import com.aerotech.upieasy.ui.components.PayouTopBar
import com.aerotech.upieasy.ui.components.TransactionRow
import com.aerotech.upieasy.ui.theme.*
import kotlinx.coroutines.launch

import com.aerotech.upieasy.ui.components.PayouNotificationsSheet
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
    onNavigateToStaff: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val apiService = remember { NetworkClient.getApiService(sessionManager) }

    val currentOrgId by sessionManager.currentOrgIdFlow.collectAsState(initial = null)
    val currentOrgName by sessionManager.currentOrgNameFlow.collectAsState(initial = "UPI-Easy Store")
    val userName by sessionManager.userNameFlow.collectAsState(initial = "Merchant")

    val alertHistory by PaymentAlertManager.alertHistory.collectAsState()
    var showNotificationsSheet by remember { mutableStateOf(false) }

    var dashboardData by remember { mutableStateOf<DashboardDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    fun refresh() {
        scope.launch {
            isLoading = true
            var orgId = currentOrgId ?: sessionManager.getCurrentOrgId()
            if (orgId.isNullOrBlank()) {
                try {
                    val orgRes = apiService.getOrganizations()
                    if (orgRes.isSuccessful && orgRes.body()?.success == true) {
                        val firstOrg = orgRes.body()?.organizations?.firstOrNull()
                        if (firstOrg != null) {
                            sessionManager.setOrganization(firstOrg.id, firstOrg.name, firstOrg.role)
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
        }
    }

    LaunchedEffect(currentOrgId) {
        refresh()
    }

    if (showNotificationsSheet) {
        PayouNotificationsSheet(
            onDismiss = { showNotificationsSheet = false },
            alertHistory = alertHistory,
            onClearAll = { PaymentAlertManager.clearAlertHistory() }
        )
    }

    Scaffold(
        topBar = {
            PayouTopBar(
                brandTitle = "UPIEasy",
                subtitle = currentOrgName ?: "Merchant Dashboard",
                avatarInitial = (userName?.take(1) ?: "M").uppercase(),
                onNotificationClick = { showNotificationsSheet = true },
                onProfileClick = onNavigateToSettings
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Background blurry ambient glow spheres
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

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
                ) {
                    // Bento Tile 1: Hero Glass Collection Card
                    item {
                        val receivedAmount = dashboardData?.todayReceived?.amount ?: 0.0
                        val receivedCount = dashboardData?.todayReceived?.count ?: 0

                        PayouHeroCard(
                            balance = receivedAmount,
                            transactionCount = receivedCount,
                            onShowQrClick = onNavigateToQr,
                            onScanPayClick = onNavigateToScan,
                            onHistoryClick = onNavigateToTransactions,
                            onAddUpiClick = onNavigateToUpi
                        )
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

                    // Bento Tile 3: Quick Action Grid (PayOu Inspired 4x2 Frosted Grid)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                            border = BorderStroke(1.dp, GlassBorderLight),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    PayouQuickActionButton(
                                        icon = Icons.Default.QrCode2,
                                        label = "Show QR",
                                        backgroundColor = PastelGreen,
                                        iconTint = PastelGreenIcon,
                                        onClick = onNavigateToQr,
                                        modifier = Modifier.weight(1f)
                                    )
                                    PayouQuickActionButton(
                                        icon = Icons.Default.QrCodeScanner,
                                        label = "Scan Pay",
                                        backgroundColor = PastelBlue,
                                        iconTint = PastelBlueIcon,
                                        onClick = onNavigateToScan,
                                        modifier = Modifier.weight(1f)
                                    )
                                    PayouQuickActionButton(
                                        icon = Icons.Default.AccountBalanceWallet,
                                        label = "Add UPI",
                                        backgroundColor = PastelYellow,
                                        iconTint = PastelYellowIcon,
                                        onClick = onNavigateToUpi,
                                        modifier = Modifier.weight(1f)
                                    )
                                    PayouQuickActionButton(
                                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                        label = "Ledger",
                                        backgroundColor = PastelPurple,
                                        iconTint = PastelPurpleIcon,
                                        onClick = onNavigateToTransactions,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    PayouQuickActionButton(
                                        icon = Icons.Default.Group,
                                        label = "Staff",
                                        backgroundColor = PastelPink,
                                        iconTint = PastelPinkIcon,
                                        onClick = onNavigateToStaff,
                                        modifier = Modifier.weight(1f)
                                    )
                                    PayouQuickActionButton(
                                        icon = Icons.Default.NotificationsActive,
                                        label = "Alerts",
                                        backgroundColor = PastelCyan,
                                        iconTint = PastelCyanIcon,
                                        onClick = { showNotificationsSheet = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                    PayouQuickActionButton(
                                        icon = Icons.Default.AccountBalance,
                                        label = "Banks",
                                        backgroundColor = PastelYellow,
                                        iconTint = PastelYellowIcon,
                                        onClick = onNavigateToUpi,
                                        modifier = Modifier.weight(1f)
                                    )
                                    PayouQuickActionButton(
                                        icon = Icons.Default.Settings,
                                        label = "Settings",
                                        backgroundColor = PastelBlue,
                                        iconTint = PastelBlueIcon,
                                        onClick = onNavigateToSettings,
                                        modifier = Modifier.weight(1f)
                                    )
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
