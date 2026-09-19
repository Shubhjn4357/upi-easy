package com.aerotech.upieasy.feature.upi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.database.UpiAccountEntity
import com.aerotech.upieasy.core.network.AddUpiRequest
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.network.UpiAccountDto
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UpiScreen(
    sessionManager: SessionManager,
    onNavigateToQrForVpa: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val database = remember { AppDatabase.getInstance(context) }
    val apiService = remember { NetworkClient.getApiService(sessionManager) }
    val currentOrgId by sessionManager.currentOrgIdFlow.collectAsState(initial = null)

    val localAccounts by database.upiDao().getUpiAccountsFlow(currentOrgId ?: "").collectAsState(initial = emptyList())
    var upiList by remember { mutableStateOf<List<UpiAccountDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddBottomSheet by remember { mutableStateOf(false) }

    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var accountToDelete by remember { mutableStateOf<UpiAccountDto?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    fun refresh() {
        currentOrgId?.let { orgId ->
            isLoading = true
            scope.launch {
                try {
                    val res = apiService.getUpiAccounts(orgId)
                    if (res.isSuccessful && res.body()?.success == true) {
                        val accounts = res.body()?.upiAccounts ?: emptyList()
                        upiList = accounts
                        val entities = accounts.map { dto ->
                            UpiAccountEntity(
                                id = dto.id,
                                organizationId = orgId,
                                vpa = dto.vpa,
                                payeeName = dto.payeeName,
                                merchantCategoryCode = dto.merchantCategoryCode,
                                isDefault = dto.isDefault,
                                status = dto.status,
                                transactionCount = dto.transactionCount
                            )
                        }
                        database.upiDao().insertUpiAccounts(entities)
                    }
                } catch (_: Exception) {}
                isLoading = false
            }
        }
    }

    val effectiveList = remember(upiList, localAccounts) {
        if (upiList.isNotEmpty()) {
            upiList
        } else {
            localAccounts.map { entity ->
                UpiAccountDto(
                    id = entity.id,
                    vpa = entity.vpa,
                    payeeName = entity.payeeName,
                    merchantCategoryCode = entity.merchantCategoryCode,
                    isDefault = entity.isDefault,
                    status = entity.status,
                    transactionCount = entity.transactionCount
                )
            }
        }
    }

    LaunchedEffect(currentOrgId) {
        refresh()
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            "${selectedIds.size} Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit Selection")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    showBulkDeleteConfirm = true
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = FailedRed)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            "UPI IDs & Accounts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = { showAddBottomSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add UPI ID")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandAccent)
            }
        } else if (effectiveList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No UPI IDs added yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add your business UPI ID (e.g. store@okhdfcbank) to accept payments and generate dynamic QR codes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(effectiveList) { item ->
                    val isSelected = selectedIds.contains(item.id)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) {
                                            selectedIds - item.id
                                        } else {
                                            selectedIds + item.id
                                        }
                                        if (selectedIds.isEmpty()) isSelectionMode = false
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedIds = setOf(item.id)
                                    }
                                }
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isSelectionMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id
                                                if (selectedIds.isEmpty()) isSelectionMode = false
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.vpa,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (item.isDefault) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(BrandAccent.copy(alpha = 0.15f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "DEFAULT",
                                                        color = BrandAccent,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Payee: ${item.payeeName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (!isSelectionMode) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { onNavigateToQrForVpa(item.vpa, item.payeeName) }) {
                                            Icon(Icons.Default.QrCode, contentDescription = "QR Code", tint = BrandAccent)
                                        }
                                        IconButton(onClick = { accountToDelete = item }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = FailedRed)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Processed ${item.transactionCount} transactions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )

                            if (!item.isDefault && !isSelectionMode) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        currentOrgId?.let { orgId ->
                                            scope.launch {
                                                apiService.setDefaultUpi(orgId, item.id)
                                                refresh()
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Make Default", color = BrandAccent, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add UPI Bottom Sheet
    if (showAddBottomSheet) {
        var vpaInput by remember { mutableStateOf("") }
        var payeeInput by remember { mutableStateOf("") }
        var setAsDefault by remember { mutableStateOf(false) }
        var isSubmitting by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        ModalBottomSheet(
            onDismissRequest = { showAddBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Link Business UPI ID",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enter your UPI VPA to accept payments and print dynamic QR codes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = vpaInput,
                    onValueChange = {
                        vpaInput = it.trim()
                        errorMessage = null
                    },
                    label = { Text("UPI VPA (e.g. merchant@icici)") },
                    placeholder = { Text("merchant@okhdfcbank") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = payeeInput,
                    onValueChange = {
                        payeeInput = it
                        errorMessage = null
                    },
                    label = { Text("Registered Payee Name") },
                    placeholder = { Text("Acme Retail Store") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = setAsDefault,
                        onCheckedChange = { setAsDefault = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Set as primary UPI address for QR codes",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = FailedRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        if (!vpaInput.contains("@") || vpaInput.length < 5) {
                            errorMessage = "Please enter a valid UPI VPA (e.g. store@okhdfcbank)"
                            return@Button
                        }
                        if (payeeInput.trim().isBlank()) {
                            errorMessage = "Please enter payee name"
                            return@Button
                        }

                        currentOrgId?.let { orgId ->
                            isSubmitting = true
                            scope.launch {
                                try {
                                    val res = apiService.addUpiAccount(
                                        orgId,
                                        AddUpiRequest(vpaInput, payeeInput.trim(), setAsDefault)
                                    )
                                    if (res.isSuccessful && res.body()?.success == true) {
                                        showAddBottomSheet = false
                                        refresh()
                                    } else {
                                        errorMessage = res.body()?.message ?: "Failed to add UPI ID"
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.localizedMessage ?: "Network error"
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Add UPI Account", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Single Delete Confirmation
    accountToDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Delete UPI Account") },
            text = { Text("Are you sure you want to remove ${account.vpa}? Linked static QR codes will also be removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        currentOrgId?.let { orgId ->
                            scope.launch {
                                try {
                                    apiService.deleteUpiAccount(orgId, account.id)
                                    refresh()
                                } catch (_: Exception) {}
                                accountToDelete = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FailedRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bulk Delete Confirmation
    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete ${selectedIds.size} UPI Accounts") },
            text = { Text("Are you sure you want to delete all selected UPI accounts?") },
            confirmButton = {
                Button(
                    onClick = {
                        currentOrgId?.let { orgId ->
                            scope.launch {
                                selectedIds.forEach { id ->
                                    try {
                                        apiService.deleteUpiAccount(orgId, id)
                                    } catch (_: Exception) {}
                                }
                                isSelectionMode = false
                                selectedIds = emptySet()
                                showBulkDeleteConfirm = false
                                refresh()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FailedRed)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
