package com.aerotech.upieasy.feature.transactions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Intent
import androidx.compose.ui.graphics.Brush
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.core.ui.UserAvatar
import com.aerotech.upieasy.core.util.HapticHelper
import com.aerotech.upieasy.core.util.StatementExporter
import com.aerotech.upieasy.data.repository.TransactionRepository
import com.aerotech.upieasy.domain.model.Transaction
import com.aerotech.upieasy.core.ui.TransactionsSkeleton
import com.aerotech.upieasy.ui.components.UpieasyPullToRefreshContainer
import com.aerotech.upieasy.ui.components.SwipeToDeleteContainer
import com.aerotech.upieasy.ui.components.UpieasyConfirmBottomDrawer
import com.aerotech.upieasy.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// Thread-safe cached date formatters for high-performance 120Hz smooth scrolling
private val monthHeaderFormatter = SimpleDateFormat("MMMM ''yy", Locale.ENGLISH)
private val transactionTimeFormatter = SimpleDateFormat("d MMM • h:mm a", Locale.ENGLISH)
private val monthCache = ConcurrentHashMap<Long, String>()
private val timeCache = ConcurrentHashMap<Long, String>()

private fun formatMonthHeader(timestamp: Long): String {
    val monthBucket = timestamp / (1000L * 60 * 60 * 24 * 25)
    return monthCache.getOrPut(monthBucket) {
        monthHeaderFormatter.format(Date(timestamp))
    }
}

private fun formatTransactionTime(timestamp: Long): String {
    val minuteBucket = timestamp / (1000L * 60)
    return timeCache.getOrPut(minuteBucket) {
        val raw = transactionTimeFormatter.format(Date(timestamp))
        val parts = raw.split(" ")
        if (parts.isNotEmpty()) {
            val day = parts[0]
            val suffix = when {
                day.endsWith("1") && day != "11" -> "st"
                day.endsWith("2") && day != "12" -> "nd"
                day.endsWith("3") && day != "13" -> "rd"
                else -> "th"
            }
            raw.replaceFirst(day, "$day$suffix")
        } else raw
    }
}

private data class MonthGroup(
    val monthName: String,
    val totalAmount: Double,
    val transactions: List<Transaction>
)

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
    val currentOrgName by sessionManager.currentOrgNameFlow.collectAsState(initial = null)
    val userName by sessionManager.userNameFlow.collectAsState(initial = null)
    val userEmail by sessionManager.userEmailFlow.collectAsState(initial = null)
    val userRole by sessionManager.userRoleFlow.collectAsState(initial = null)
    val userAvatarUrl by sessionManager.userAvatarUrlFlow.collectAsState(initial = null)

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var amountRangeSlider by remember { mutableStateOf(0f..50000f) }
    var selectedAmountRange by remember { mutableStateOf<String?>(null) }
    var selectedDatePreset by remember { mutableStateOf<String?>(null) }

    var showFilterSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var txnToDelete by remember { mutableStateOf<Transaction?>(null) }
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var isInitialLoading by remember { mutableStateOf(true) }

    val transactionsList by repository.getTransactionsFlow(currentOrgId ?: "").collectAsState(initial = emptyList())

    LaunchedEffect(currentOrgId) {
        currentOrgId?.let {
            repository.refreshTransactions(it)
            isInitialLoading = false
        }
    }

    // High performance memoized search & multi-criteria filter calculation
    val filteredTransactions = remember(
        transactionsList,
        searchQuery,
        selectedStatus,
        selectedCategory,
        selectedAmountRange,
        amountRangeSlider,
        selectedDatePreset
    ) {
        val now = System.currentTimeMillis()
        transactionsList.filter { txn ->
            val matchesStatus = selectedStatus == null || txn.status.equals(selectedStatus, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    txn.payeeName.contains(searchQuery, ignoreCase = true) ||
                    (txn.payerName?.contains(searchQuery, ignoreCase = true) == true) ||
                    (txn.referenceNumber?.contains(searchQuery, ignoreCase = true) == true) ||
                    (txn.note?.contains(searchQuery, ignoreCase = true) == true)

            val cat = when {
                (txn.note?.lowercase() ?: "").contains("recharge") || txn.payeeName.lowercase().contains("jio") || txn.payeeName.lowercase().contains("airtel") || txn.payeeName.lowercase().contains("bill") -> "Bills & Utilities"
                (txn.note?.lowercase() ?: "").contains("food") || txn.payeeName.lowercase().contains("cafe") || txn.payeeName.lowercase().contains("dining") -> "Food & Dining"
                (txn.note?.lowercase() ?: "").contains("retail") || txn.payeeName.lowercase().contains("store") || txn.payeeName.lowercase().contains("mart") -> "Retail & Store"
                else -> "Miscellaneous"
            }
            val matchesCategory = selectedCategory == null || cat.equals(selectedCategory, ignoreCase = true)

            val matchesAmount = (txn.amount >= amountRangeSlider.start) &&
                    (amountRangeSlider.endInclusive >= 50000f || txn.amount <= amountRangeSlider.endInclusive)

            val matchesDate = when (selectedDatePreset) {
                "TODAY" -> (now - txn.occurredAt) < 24L * 3600 * 1000
                "THIS_WEEK" -> (now - txn.occurredAt) < 7L * 24 * 3600 * 1000
                "THIS_MONTH" -> formatMonthHeader(txn.occurredAt) == formatMonthHeader(now)
                else -> true
            }

            matchesStatus && matchesSearch && matchesCategory && matchesAmount && matchesDate
        }
    }

    // High performance monthly grouping (Matches Image 2)
    val monthlyGroups = remember(filteredTransactions) {
        val groups = LinkedHashMap<String, MutableList<Transaction>>()
        filteredTransactions.forEach { txn ->
            val header = formatMonthHeader(txn.occurredAt)
            groups.getOrPut(header) { mutableListOf() }.add(txn)
        }
        groups.map { (month, txns) ->
            val total = txns.filter { it.status.equals("SUCCESS", ignoreCase = true) }.sumOf { it.amount }
            MonthGroup(month, total, txns)
        }
    }

    Scaffold(
        topBar = {
            // Header 
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Profile Avatar with Google profile picture fallback to vibrant initials
                    UserAvatar(
                        avatarUrl = userAvatarUrl,
                        name = userName,
                        size = 42.dp,
                        modifier = Modifier.clickable {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                            showProfileSheet = true
                        }
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Text(
                        text = "Payment history",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Download Statement Button
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                            showExportSheet = true
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Download Statement",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar & Filter Controls Row 
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        if (it.isNotEmpty()) {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    placeholder = {
                        Text(
                            "Search your payments",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                searchQuery = ""
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                )

                Spacer(modifier = Modifier.width(10.dp))

                val hasActiveFilters = selectedStatus != null || selectedCategory != null || selectedAmountRange != null || selectedDatePreset != null

                // Filter Button with Sliders Icon
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (hasActiveFilters) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (hasActiveFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                            showFilterSheet = true
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filter",
                            tint = if (hasActiveFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Active Filter Pill indicator row if any filter applied
            val hasActiveFilters = selectedStatus != null || selectedCategory != null || selectedAmountRange != null || selectedDatePreset != null
            if (hasActiveFilters) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    selectedStatus?.let { st ->
                        item {
                            FilterChip(
                                selected = true,
                                onClick = {
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                    selectedStatus = null
                                },
                                label = { Text("Status: $st ✕", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                    selectedCategory?.let { cat ->
                        item {
                            FilterChip(
                                selected = true,
                                onClick = {
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                    selectedCategory = null
                                },
                                label = { Text("Cat: $cat ✕", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                    selectedAmountRange?.let { amt ->
                        item {
                            FilterChip(
                                selected = true,
                                onClick = {
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                    selectedAmountRange = null
                                },
                                label = { Text("Amount: $amt ✕", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                    selectedDatePreset?.let { dt ->
                        item {
                            FilterChip(
                                selected = true,
                                onClick = {
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                    selectedDatePreset = null
                                },
                                label = { Text("Date: $dt ✕", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Grouped Transactions LazyColumn 
            if (isInitialLoading && transactionsList.isEmpty()) {
                TransactionsSkeleton()
            } else if (monthlyGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            "No transactions found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (searchQuery.isNotEmpty()) "Try adjusting your search query" else "Transactions will appear here as payments arrive",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                UpieasyPullToRefreshContainer(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            currentOrgId?.let { repository.refreshTransactions(it) }
                            isRefreshing = false
                        }
                    }
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 110.dp)
                    ) {
                        monthlyGroups.forEach { monthGroup ->
                            // Month Header Tile
                            item(key = "header_${monthGroup.monthName}") {
                                MonthHeaderSection(
                                    monthName = monthGroup.monthName,
                                    totalAmount = monthGroup.totalAmount,
                                    onArrowClick = {
                                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                        Toast.makeText(context, "Monthly statement for ${monthGroup.monthName}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            // Transaction Items in Month 
                            items(
                                items = monthGroup.transactions,
                                key = { it.id }
                            ) { txn ->
                                SwipeToDeleteContainer(
                                    itemKey = txn.id,
                                    isSwipedOpen = (txnToDelete?.id == txn.id),
                                    onDeleteRequest = { txnToDelete = txn }
                                ) {
                                    TransactionCardItem(
                                        transaction = txn,
                                        onClick = {
                                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                            selectedTransaction = txn
                                        }
                                    )
                                }
                            }

                            item(key = "spacer_${monthGroup.monthName}") {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Redesigned Comprehensive Filter Bottom Sheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter Payment History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    TextButton(onClick = {
                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                        selectedStatus = null
                        selectedCategory = null
                        selectedAmountRange = null
                        amountRangeSlider = 0f..50000f
                        selectedDatePreset = null
                    }) {
                        Text("Reset All", color = FailedRed, fontWeight = FontWeight.SemiBold)
                    }
                }

                // 1. Status Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Payment Status", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    val statuses = listOf("ALL" to "All", "SUCCESS" to "Success", "PENDING" to "Pending", "FAILED" to "Failed")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        statuses.forEach { (code, label) ->
                            val isSelected = (code == "ALL" && selectedStatus == null) || (selectedStatus == code)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                    selectedStatus = if (code == "ALL") null else code
                                },
                                label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                // 2. Category Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Category", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    val categories = listOf("Bills & Utilities", "Food & Dining", "Retail & Store", "Miscellaneous")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            val isAllCat = selectedCategory == null
                            FilterChip(
                                selected = isAllCat,
                                onClick = {
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                    selectedCategory = null
                                },
                                label = { Text("All Categories", fontSize = 12.sp, fontWeight = if (isAllCat) FontWeight.Bold else FontWeight.Normal) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                        items(categories) { cat ->
                            val isSelected = selectedCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                    selectedCategory = if (isSelected) null else cat
                                },
                                label = { Text(cat, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                // 3. Amount Range Section with RangeSlider and Quick Presets
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Amount Range",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "₹${amountRangeSlider.start.toInt()} – ${if (amountRangeSlider.endInclusive >= 50000f) "₹50,000+" else "₹${amountRangeSlider.endInclusive.toInt()}"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    RangeSlider(
                        value = amountRangeSlider,
                        onValueChange = { range ->
                            amountRangeSlider = range
                            selectedAmountRange = "CUSTOM"
                        },
                        valueRange = 0f..50000f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("₹0", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("₹50,000+", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    val amountRanges = listOf(
                        null to "Any Amount",
                        "UNDER_500" to "Under ₹500",
                        "500_2000" to "₹500 - ₹2,000",
                        "2000_10000" to "₹2,000 - ₹10,000",
                        "ABOVE_10000" to "Above ₹10,000"
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(amountRanges) { (rangeKey, rangeLabel) ->
                            val isSelected = selectedAmountRange == rangeKey || (rangeKey == null && selectedAmountRange == null && amountRangeSlider.start == 0f && amountRangeSlider.endInclusive >= 50000f)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                    selectedAmountRange = rangeKey
                                    amountRangeSlider = when (rangeKey) {
                                        "UNDER_500" -> 0f..500f
                                        "500_2000" -> 500f..2000f
                                        "2000_10000" -> 2000f..10000f
                                        "ABOVE_10000" -> 10000f..50000f
                                        else -> 0f..50000f
                                    }
                                },
                                label = { Text(rangeLabel, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                // 4. Date Presets Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Date Period", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    val datePresets = listOf(
                        null to "All Time",
                        "TODAY" to "Today",
                        "THIS_WEEK" to "This Week",
                        "THIS_MONTH" to "This Month"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        datePresets.forEach { (presetKey, presetLabel) ->
                            val isSelected = selectedDatePreset == presetKey
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                    selectedDatePreset = presetKey
                                },
                                label = { Text(presetLabel, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                        showFilterSheet = false
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Apply Filters (${filteredTransactions.size} Results)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }

    // Statement Export Modal Bottom Sheet (PDF & CSV)
    if (showExportSheet) {
        var exportFormat by remember { mutableStateOf("PDF") }
        var exportRange by remember { mutableStateOf("THIS_MONTH") }

        val exportTransactions = remember(transactionsList, exportRange) {
            val now = System.currentTimeMillis()
            transactionsList.filter { txn ->
                when (exportRange) {
                    "THIS_MONTH" -> formatMonthHeader(txn.occurredAt) == formatMonthHeader(now)
                    "LAST_30" -> (now - txn.occurredAt) <= 30L * 24 * 3600 * 1000
                    else -> true
                }
            }
        }
        val exportTotal = remember(exportTransactions) {
            exportTransactions.filter { it.status.equals("SUCCESS", ignoreCase = true) }.sumOf { it.amount }
        }

        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Export Statement", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Download and share payment records", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                        showExportSheet = false
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Format selection tabs
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Statement Format", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (exportFormat == "PDF") MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (exportFormat == "PDF") MaterialTheme.colorScheme.primary else Color.Transparent),
                            modifier = Modifier.weight(1f).clickable {
                                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                exportFormat = "PDF"
                            }
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = if (exportFormat == "PDF") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("PDF Statement", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Formatted doc", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (exportFormat == "CSV") MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (exportFormat == "CSV") MaterialTheme.colorScheme.primary else Color.Transparent),
                            modifier = Modifier.weight(1f).clickable {
                                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                exportFormat = "CSV"
                            }
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TableChart, contentDescription = null, tint = if (exportFormat == "CSV") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("CSV Sheet", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Excel compatible", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Range selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Date Range", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    val ranges = listOf("THIS_MONTH" to "This Month", "LAST_30" to "Past 30 Days", "ALL" to "All Time")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ranges.forEach { (rKey, rLabel) ->
                            val isSelected = exportRange == rKey
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                    exportRange = rKey
                                },
                                label = { Text(rLabel, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                // Summary metrics card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${exportTransactions.size} Records", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Inflow", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${String.format("%,.2f", exportTotal)}", fontWeight = FontWeight.ExtraBold, color = SuccessGreen, fontSize = 16.sp)
                        }
                    }
                }

                Button(
                    onClick = {
                        try {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                            val merchantName = userName ?: "Merchant"
                            val orgName = currentOrgName ?: "UPIEasy Merchant"

                            val file = if (exportFormat == "CSV") {
                                StatementExporter.generateCsv(context, exportTransactions)
                            } else {
                                StatementExporter.generatePdf(
                                    context = context,
                                    merchantName = merchantName,
                                    orgName = orgName,
                                    transactions = exportTransactions,
                                    totalAmount = exportTotal
                                )
                            }

                            val mimeType = if (exportFormat == "CSV") "text/csv" else "application/pdf"
                            StatementExporter.shareFile(
                                context = context,
                                file = file,
                                mimeType = mimeType,
                                subject = "UPIEasy Payment Statement (${if (exportFormat == "CSV") "CSV" else "PDF"})"
                            )

                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.SUCCESS)
                            showExportSheet = false
                            Toast.makeText(context, "$exportFormat statement generated successfully", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export & Share Statement", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }

    // User Profile Bottom Sheet
    if (showProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProfileSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(
                            avatarUrl = userAvatarUrl,
                            name = userName,
                            size = 52.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = userName ?: "Merchant",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = userEmail ?: "No email linked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = {
                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                        showProfileSheet = false
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                DetailRow(label = "Merchant Org", value = currentOrgName ?: "Primary Store")
                userRole?.let { DetailRow(label = "Store Role", value = it.uppercase()) }
                currentOrgId?.let { DetailRow(label = "Organization ID", value = it) }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                        showProfileSheet = false
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Transaction Details Modal Bottom Sheet
    selectedTransaction?.let { txn ->
        ModalBottomSheet(
            onDismissRequest = { selectedTransaction = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Payment Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (txn.status.uppercase()) {
                            "SUCCESS" -> SuccessGreenBg
                            "PENDING" -> PendingAmberBg
                            else -> FailedRedBg
                        }
                    ) {
                        Text(
                            text = txn.status.uppercase(),
                            color = when (txn.status.uppercase()) {
                                "SUCCESS" -> SuccessGreen
                                "PENDING" -> PendingAmber
                                else -> FailedRed
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "₹${String.format("%,.2f", txn.amount)}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                DetailRow(label = "Payee Name", value = txn.payeeName)
                DetailRow(label = "UPI Handle", value = txn.payeeVpa)
                txn.payerName?.let { DetailRow(label = "Payer", value = it) }
                txn.referenceNumber?.let { DetailRow(label = "Ref / RRN", value = it) }
                txn.note?.let { DetailRow(label = "Note", value = it) }
                DetailRow(label = "Date & Time", value = formatTransactionTime(txn.occurredAt))

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val textToCopy = "Ref: ${txn.referenceNumber ?: txn.id}\nAmount: ₹${txn.amount}\nDate: ${formatTransactionTime(txn.occurredAt)}"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Payment Details", textToCopy))
                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                        Toast.makeText(context, "Receipt copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Receipt Details", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (txnToDelete != null) {
        val txn = txnToDelete!!
        UpieasyConfirmBottomDrawer(
            visible = true,
            title = "Delete Transaction Record?",
            message = "Are you sure you want to remove this transaction record (₹${String.format("%,.2f", txn.amount)}) from your ledger history?",
            confirmText = "Delete",
            cancelText = "Cancel",
            isDestructive = true,
            onConfirm = {
                val idToDelete = txn.id
                txnToDelete = null
                scope.launch {
                    database.transactionDao().deleteTransaction(idToDelete)
                }
            },
            onDismiss = {
                txnToDelete = null
            }
        )
    }
}

@Composable
private fun MonthHeaderSection(
    monthName: String,
    totalAmount: Double,
    onArrowClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = monthName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp
            )

            // Small rounded square open statement arrow button [ ↗ ]
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onArrowClick() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.NorthEast,
                        contentDescription = "Month Statement",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "You have collected ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Text(
                text = "₹${String.format("%,.2f", totalAmount)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
            Text(
                text = " in this month",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun TransactionCardItem(
    transaction: Transaction,
    onClick: () -> Unit
) {
    val isFailed = transaction.status.equals("FAILED", ignoreCase = true)
    val firstLetter = transaction.payeeName.take(1).uppercase()

    // Determine category based on note or payee name
    val categoryLabel = remember(transaction) {
        val note = transaction.note?.lowercase() ?: ""
        val name = transaction.payeeName.lowercase()
        when {
            note.contains("recharge") || name.contains("jio") || name.contains("airtel") || name.contains("bill") -> "Bills & Utilities"
            note.contains("food") || name.contains("cafe") || name.contains("dining") -> "Food & Dining"
            note.contains("retail") || name.contains("store") -> "Retail & Store"
            else -> "Miscellaneous"
        }
    }

    val squircleBg = remember(transaction) {
        when (firstLetter) {
            "J" -> Color(0xFF0F285A) // Dark Blue for Jio / Telecom
            "B" -> Color(0xFFD6E4FF) // Soft Light Blue
            "S" -> Color(0xFFFFE2CC) // Soft Peach
            "P" -> Color(0xFFE8FAF3) // Emerald Light
            else -> PastelIndigoBg
        }
    }

    val squircleTextColor = remember(transaction) {
        if (firstLetter == "J") Color.White else BrandPrimary
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Brand/Payee Squircle Logo
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(squircleBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = firstLetter,
                    color = squircleTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center: Payee Title, Date & Time, and Category Pill
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.payeeName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = formatTransactionTime(transaction.occurredAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Category pill with outline border
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = categoryLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right: Amount, Account / Status line, and Cashback / Settled badge
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${String.format("%,.0f", transaction.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                if (isFailed) {
                    Text(
                        text = "Failed",
                        color = FailedRed,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "From",
                            style = MaterialTheme.typography.bodySmall.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                lineHeight = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0083CA)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "₹",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Green settlement / incentive pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SuccessGreenBg)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚡ 0.12%",
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
