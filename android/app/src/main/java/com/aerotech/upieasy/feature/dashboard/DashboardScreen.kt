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

@Composable
fun DashboardScreen(
    sessionManager: SessionManager,
    onNavigateToScan: () -> Unit,
    onNavigateToQr: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToUpi: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(sessionManager) }

    val currentOrgId by sessionManager.currentOrgIdFlow.collectAsState(initial = null)
    val currentOrgName by sessionManager.currentOrgNameFlow.collectAsState(initial = "UPI-Easy Store")
    val userName by sessionManager.userNameFlow.collectAsState(initial = "Merchant")

    var dashboardData by remember { mutableStateOf<DashboardDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    fun refresh() {
        currentOrgId?.let { orgId ->
            isLoading = true
            scope.launch {
                try {
                    val res = apiService.getDashboard(orgId)
                    if (res.isSuccessful && res.body()?.success == true) {
                        dashboardData = res.body()?.dashboard
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(currentOrgId) {
        refresh()
    }

    Scaffold(
        topBar = {
            PayouTopBar(
                brandTitle = "UPIEasy",
                subtitle = currentOrgName ?: "Merchant Dashboard",
                avatarInitial = (userName?.take(1) ?: "M").uppercase(),
                onNotificationClick = { /* Notifications */ },
                onProfileClick = { /* Settings / Profile */ }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // PayOu Inspired Hero Balance Card
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

                // Quick Action Grid (PayOu Inspired 4x2 Grid)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    }
                }

                // Grid Metrics (Pending, Failed, Active UPI)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = "Pending",
                            value = "${dashboardData?.pendingCount ?: 0}",
                            subtitle = "In Review",
                            icon = Icons.Default.HourglassEmpty,
                            iconTint = PendingAmber,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToTransactions
                        )

                        MetricCard(
                            title = "Failed",
                            value = "${dashboardData?.failedCount ?: 0}",
                            subtitle = "Disputed",
                            icon = Icons.Default.Cancel,
                            iconTint = FailedRed,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToTransactions
                        )

                        MetricCard(
                            title = "Active UPI",
                            value = "${dashboardData?.activeUpiCount ?: 0}",
                            subtitle = "Linked VPAs",
                            icon = Icons.Default.AccountBalanceWallet,
                            iconTint = BrandSecondary,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToUpi
                        )
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
