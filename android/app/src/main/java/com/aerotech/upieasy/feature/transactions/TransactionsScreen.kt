package com.aerotech.upieasy.feature.transactions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.data.repository.TransactionRepository
import com.aerotech.upieasy.domain.model.Transaction
import com.aerotech.upieasy.ui.components.StatusBadge
import com.aerotech.upieasy.ui.components.TransactionRow
import com.aerotech.upieasy.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    sessionManager: SessionManager,
    database: AppDatabase
) {
    val context = LocalContext.current
    val apiService = remember { NetworkClient.getApiService(sessionManager) }
    val repository = remember { TransactionRepository(apiService, database) }

    val currentOrgId by sessionManager.currentOrgIdFlow.collectAsState(initial = null)

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<String?>(null) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    // Multi-select contextual state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTxnIds by remember { mutableStateOf(setOf<String>()) }

    val transactionsList by repository.getTransactionsFlow(currentOrgId ?: "").collectAsState(initial = emptyList())

    LaunchedEffect(currentOrgId) {
        currentOrgId?.let {
            repository.refreshTransactions(it)
        }
    }

    val filteredTransactions = remember(transactionsList, searchQuery, selectedStatus) {
        transactionsList.filter { txn ->
            val matchesStatus = selectedStatus == null || txn.status.equals(selectedStatus, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    txn.payeeName.contains(searchQuery, ignoreCase = true) ||
                    (txn.payerName?.contains(searchQuery, ignoreCase = true) == true) ||
                    (txn.referenceNumber?.contains(searchQuery, ignoreCase = true) == true) ||
                    (txn.note?.contains(searchQuery, ignoreCase = true) == true)
            matchesStatus && matchesSearch
        }
    }

    val selectedTransactions = remember(transactionsList, selectedTxnIds) {
        transactionsList.filter { selectedTxnIds.contains(it.id) }
    }
    val selectedTotalAmount = remember(selectedTransactions) {
        selectedTransactions.sumOf { it.amount }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "${selectedTxnIds.size} Selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Total: ₹${String.format("%,.2f", selectedTotalAmount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SuccessGreen
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedTxnIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit Selection")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val textToCopy = selectedTransactions.joinToString("\n") {
                                    "${it.payeeName} | ₹${it.amount} | Ref: ${it.referenceNumber ?: it.id}"
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Transactions", textToCopy)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied ${selectedTxnIds.size} transactions", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy IDs")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            "Transaction Ledger",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (!isSelectionMode) {
                // Bento Glass Summary Card
                val totalVolume = remember(transactionsList) { transactionsList.filter { it.status.equals("SUCCESS", ignoreCase = true) }.sumOf { it.amount } }
                val successCount = remember(transactionsList) { transactionsList.count { it.status.equals("SUCCESS", ignoreCase = true) } }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                    border = BorderStroke(1.dp, GlassBorderLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Settled Volume",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₹${String.format("%,.2f", totalVolume)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SuccessGreenBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$successCount Verified",
                                    color = SuccessGreen,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search by VPA, RRN, or Note...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Filter Chips
                val statusFilters = listOf("ALL", "SUCCESS", "PENDING", "FAILED")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(statusFilters) { status ->
                        val isSelected = (status == "ALL" && selectedStatus == null) || (selectedStatus == status)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedStatus = if (status == "ALL") null else status
                            },
                            label = { Text(status) },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Transactions List
            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No matching transactions found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(filteredTransactions, key = { it.id }) { txn ->
                        val isSelected = selectedTxnIds.contains(txn.id)
                        TransactionRow(
                            transaction = txn,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedTxnIds = if (isSelected) {
                                        selectedTxnIds - txn.id
                                    } else {
                                        selectedTxnIds + txn.id
                                    }
                                    if (selectedTxnIds.isEmpty()) isSelectionMode = false
                                } else {
                                    selectedTransaction = txn
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedTxnIds = setOf(txn.id)
                                }
                            },
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onCheckedChange = { checked ->
                                selectedTxnIds = if (checked) selectedTxnIds + txn.id else selectedTxnIds - txn.id
                                if (selectedTxnIds.isEmpty()) isSelectionMode = false
                            }
                        )
                    }
                }
            }
        }
    }

    // Detail Bottom Sheet
    selectedTransaction?.let { txn ->
        ModalBottomSheet(
            onDismissRequest = { selectedTransaction = null },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transaction Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StatusBadge(status = txn.status)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "₹${String.format("%,.2f", txn.amount)}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (txn.direction == "RECEIVED") SuccessGreen else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                DetailItem(label = "Direction", value = txn.direction)
                DetailItem(label = "Payee VPA", value = txn.payeeVpa)
                DetailItem(label = "Payer VPA", value = txn.payerVpa ?: "Direct UPI")
                DetailItem(label = "Bank RRN / Reference", value = txn.referenceNumber ?: "Pending generation")
                DetailItem(label = "Payment Method", value = txn.paymentMethod)
                DetailItem(label = "Transaction ID", value = txn.id)
                if (!txn.note.isNullOrBlank()) {
                    DetailItem(label = "Note", value = txn.note)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { selectedTransaction = null },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
