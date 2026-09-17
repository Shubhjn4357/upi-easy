package com.aerospace.upieasy.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.aerospace.upieasy.core.network.DashboardDto
import com.aerospace.upieasy.core.network.NetworkClient
import com.aerospace.upieasy.core.security.SessionManager
import com.aerospace.upieasy.domain.model.Transaction
import com.aerospace.upieasy.ui.components.MetricCard
import com.aerospace.upieasy.ui.components.TransactionRow
import com.aerospace.upieasy.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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
    val currentOrgName by sessionManager.currentOrgNameFlow.collectAsState(initial = "My Business")

    var dashboardData by remember { mutableStateOf<DashboardDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentOrgId) {
        currentOrgId?.let { orgId ->
            isLoading = true
            try {
                val res = apiService.getDashboard(orgId)
                if (res.isSuccessful && res.body()?.success == true) {
                    dashboardData = res.body()?.dashboard
                }
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentOrgName ?: "Business Dashboard",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Live Ledger & UPI Control",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToScan) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = BrandAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
            )
        },
        containerColor = BackgroundLight
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandAccent)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Primary Collection Card
                item {
                    val receivedAmount = dashboardData?.todayReceived?.amount ?: 0.0
                    val receivedCount = dashboardData?.todayReceived?.count ?: 0

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = BrandPrimary)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TODAY'S RECEIVED COLLECTION",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextTertiary,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SuccessGreen.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "+$receivedCount Txns",
                                        color = SuccessGreen,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "₹${String.format("%,.2f", receivedAmount)}",
                                style = MaterialTheme.typography.headlineLarge,
                                color = SurfaceLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 34.sp
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Action Shortcuts
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = onNavigateToQr,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                                ) {
                                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Show QR")
                                }

                                OutlinedButton(
                                    onClick = onNavigateToScan,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SurfaceLight),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(SurfaceLight.copy(alpha = 0.4f)))
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Scan Pay")
                                }
                            }
                        }
                    }
                }

                // Grid Metrics (Pending, Failed, Active UPI)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            title = "Pending",
                            value = "${dashboardData?.pendingCount ?: 0}",
                            subtitle = "Awaiting verification",
                            icon = Icons.Default.HourglassEmpty,
                            iconTint = PendingAmber,
                            modifier = Modifier.weight(1f)
                        )

                        MetricCard(
                            title = "Failed",
                            value = "${dashboardData?.failedCount ?: 0}",
                            subtitle = "Needs reconciliation",
                            icon = Icons.Default.Cancel,
                            iconTint = FailedRed,
                            modifier = Modifier.weight(1f)
                        )

                        MetricCard(
                            title = "Active UPI",
                            value = "${dashboardData?.activeUpiCount ?: 0}",
                            subtitle = "Linked VPAs",
                            icon = Icons.Default.AccountBalanceWallet,
                            iconTint = BrandSecondary,
                            modifier = Modifier.weight(1f)
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
                            color = TextPrimary
                        )

                        TextButton(onClick = onNavigateToTransactions) {
                            Text("See All", color = BrandAccent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Transactions List
                val recent = dashboardData?.recentTransactions ?: emptyList()
                if (recent.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceLight)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = TextTertiary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No recent transactions",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Incoming UPI payments will appear here in real time",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextTertiary
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

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
