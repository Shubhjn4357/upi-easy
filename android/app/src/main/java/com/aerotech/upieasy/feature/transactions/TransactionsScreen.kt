package com.aerotech.upieasy.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    sessionManager: SessionManager,
    database: AppDatabase
) {
    val scope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(sessionManager) }
    val repository = remember { TransactionRepository(apiService, database) }

    val currentOrgId by sessionManager.currentOrgIdFlow.collectAsState(initial = null)

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<String?>(null) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Transaction Ledger",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
            )
        },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by VPA, RRN, or Note...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
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
                    focusedContainerColor = SurfaceLight,
                    unfocusedContainerColor = SurfaceLight
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
                            selectedContainerColor = BrandPrimary,
                            selectedLabelColor = SurfaceLight
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTransactions, key = { it.id }) { txn ->
                        TransactionRow(
                            transaction = txn,
                            onClick = { selectedTransaction = txn }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    // Detail Bottom Sheet
    selectedTransaction?.let { txn ->
        ModalBottomSheet(
            onDismissRequest = { selectedTransaction = null },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = SurfaceLight
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transaction Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    StatusBadge(status = txn.status)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "₹${String.format("%,.2f", txn.amount)}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (txn.direction == "RECEIVED") SuccessGreen else TextPrimary
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Close")
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
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}
