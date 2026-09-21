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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.database.UpiAccountEntity
import com.aerotech.upieasy.core.network.AddUpiRequest
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.network.UpiAccountDto
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.core.util.HapticHelper
import com.aerotech.upieasy.ui.components.UpieasyPullToRefreshContainer
import com.aerotech.upieasy.ui.components.SwipeToDeleteContainer
import com.aerotech.upieasy.ui.components.UpieasyConfirmBottomDrawer
import com.aerotech.upieasy.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val localAccounts by remember(currentOrgId) {
        if (!currentOrgId.isNullOrBlank()) {
            database.upiDao().getUpiAccountsFlow(currentOrgId!!)
        } else {
            database.upiDao().getAllUpiAccountsFlow()
        }
    }.collectAsState(initial = emptyList())

    var upiList by remember { mutableStateOf<List<UpiAccountDto>>(emptyList()) }
    var pendingDeletedIds by remember { mutableStateOf(setOf<String>()) }
    var isLoading by remember { mutableStateOf(false) }
    var showAddBottomSheet by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var accountToDelete by remember { mutableStateOf<UpiAccountDto?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

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
                            sessionManager.setOrganization(
                                orgId = firstOrg.id,
                                orgName = firstOrg.name,
                                role = firstOrg.role,
                                legalName = firstOrg.legalBusinessName,
                                category = firstOrg.category,
                                panNumber = firstOrg.panNumber,
                                gstin = firstOrg.gstin
                            )
                            orgId = firstOrg.id
                        }
                    }
                } catch (_: Exception) {}
            }

            if (!orgId.isNullOrBlank()) {
                try {
                    val res = apiService.getUpiAccounts(orgId)
                    if (res.isSuccessful && res.body()?.success == true) {
                        val accounts = res.body()?.upiAccounts ?: emptyList()
                        upiList = accounts
                        withContext(Dispatchers.IO) {
                            if (accounts.isNotEmpty()) {
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
                        }
                    }
                } catch (_: Exception) {}
            }
            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(currentOrgId) {
        refresh()
    }

    val sourceList = if (upiList.isNotEmpty()) {
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

    val effectiveList = remember(sourceList, pendingDeletedIds) {
        sourceList.filter { it.id !in pendingDeletedIds }
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
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(bottom = 76.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add UPI ID")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
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

            if (isLoading && effectiveList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandAccent)
                }
            } else if (effectiveList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                        border = BorderStroke(1.dp, GlassBorderLight),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(PastelIndigoBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No UPI Accounts Linked",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Link your business UPI VPA (e.g. store@okhdfcbank) to accept customer payments with 0% MDR direct settlement.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { showAddBottomSheet = true },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add UPI Account", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                UpieasyPullToRefreshContainer(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        refresh()
                    }
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
                    ) {
                    if (!isSelectionMode) {
                        // Bento Overview Hero Card
                        item {
                            val defaultAcc = effectiveList.firstOrNull { it.isDefault } ?: effectiveList.firstOrNull()
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                                border = BorderStroke(1.dp, GlassBorderLight),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(PastelIndigoBg),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AccountBalanceWallet,
                                                    contentDescription = null,
                                                    tint = BrandPrimary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "Linked Accounts",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${effectiveList.size} Active VPAs",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SuccessGreenBg
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(SuccessGreen)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "0% MDR Active",
                                                    color = SuccessGreen,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    if (defaultAcc != null) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = "Default:",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = defaultAcc.vpa,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { onNavigateToQrForVpa(defaultAcc.vpa, defaultAcc.payeeName) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.QrCode,
                                                        contentDescription = "Show QR",
                                                        tint = BrandPrimary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(
                        items = effectiveList,
                        key = { it.id }
                    ) { item ->
                        val isSelected = selectedIds.contains(item.id)

                        SwipeToDeleteContainer(
                            itemKey = item.id,
                            enabled = !isSelectionMode,
                            isSwipedOpen = (accountToDelete?.id == item.id),
                            onDeleteRequest = { accountToDelete = item }
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItemPlacement()
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
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else GlassBorderLight
                            )
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
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
                                            Spacer(modifier = Modifier.width(6.dp))
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(if (item.isDefault) SuccessGreenBg else PastelBlueBg),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AccountBalance,
                                                    contentDescription = null,
                                                    tint = if (item.isDefault) SuccessGreen else BrandPrimary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(14.dp))
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
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = SuccessGreenBg
                                                    ) {
                                                        Text(
                                                            text = "DEFAULT",
                                                            color = SuccessGreen,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                                            IconButton(
                                                onClick = { onNavigateToQrForVpa(item.vpa, item.payeeName) },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            ) {
                                                Icon(Icons.Default.QrCode, contentDescription = "QR Code", tint = BrandPrimary, modifier = Modifier.size(18.dp))
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            IconButton(
                                                onClick = { accountToDelete = item },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(PastelPink)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = FailedRed, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Processed ${item.transactionCount} transactions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )

                                    if (!item.isDefault && !isSelectionMode) {
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    val orgId = currentOrgId ?: sessionManager.getCurrentOrgId()
                                                    if (!orgId.isNullOrBlank()) {
                                                        apiService.setDefaultUpi(orgId, item.id)
                                                        refresh()
                                                    }
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Make Default", color = BrandPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
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
            val focusManager = LocalFocusManager.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
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
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
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
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
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

                        isSubmitting = true
                        scope.launch {
                            try {
                                val orgId = currentOrgId ?: sessionManager.getCurrentOrgId()
                                if (!orgId.isNullOrBlank()) {
                                    val res = apiService.addUpiAccount(
                                        orgId,
                                        AddUpiRequest(vpaInput.trim(), payeeInput.trim(), setAsDefault)
                                    )
                                    if (res.isSuccessful && res.body()?.success == true) {
                                        showAddBottomSheet = false
                                        refresh()
                                    } else {
                                        errorMessage = res.body()?.message ?: "Failed to add UPI ID"
                                    }
                                } else {
                                    errorMessage = "Organization not found"
                                }
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage ?: "Network error"
                            } finally {
                                isSubmitting = false
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
        UpieasyConfirmBottomDrawer(
            visible = accountToDelete != null,
            title = "Delete UPI Account?",
            message = "Are you sure you want to remove ${account.vpa}? Linked static QR codes will also be removed.",
            confirmText = "Delete",
            cancelText = "Cancel",
            isDestructive = true,
            onConfirm = {
                val target = account
                accountToDelete = null // Dismiss dialog immediately
                pendingDeletedIds = pendingDeletedIds + target.id // Instant optimistic UI removal
                upiList = upiList.filter { it.id != target.id }
                Toast.makeText(context, "${target.vpa} removed", Toast.LENGTH_SHORT).show()

                scope.launch(Dispatchers.IO) {
                    try {
                        database.upiDao().deleteUpiAccount(target.id)
                        val orgId = currentOrgId ?: sessionManager.getCurrentOrgId()
                        if (!orgId.isNullOrBlank()) {
                            val res = apiService.deleteUpiAccount(orgId, target.id)
                            if (!res.isSuccessful) {
                                withContext(Dispatchers.Main) {
                                    pendingDeletedIds = pendingDeletedIds - target.id
                                    val err = res.errorBody()?.string()
                                    val msg = try { org.json.JSONObject(err ?: "").optString("message").takeIf { it.isNotEmpty() } } catch (_: Exception) { null }
                                    Toast.makeText(context, msg ?: "Server sync failed (${res.code()})", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Background network error; item remains deleted locally
                    }
                }
            },
            onDismiss = { accountToDelete = null }
        )
    }

    // Bulk Delete Confirmation
    if (showBulkDeleteConfirm && selectedIds.isNotEmpty()) {
        UpieasyConfirmBottomDrawer(
            visible = showBulkDeleteConfirm,
            title = "Delete ${selectedIds.size} UPI Accounts?",
            message = "Are you sure you want to delete all selected UPI accounts?",
            confirmText = "Delete All",
            cancelText = "Cancel",
            isDestructive = true,
            onConfirm = {
                val toDelete = selectedIds.toList()
                showBulkDeleteConfirm = false // Dismiss immediately
                isSelectionMode = false
                selectedIds = emptySet()
                pendingDeletedIds = pendingDeletedIds + toDelete // Instant optimistic removal
                upiList = upiList.filter { it.id !in toDelete }
                Toast.makeText(context, "${toDelete.size} UPI IDs deleted", Toast.LENGTH_SHORT).show()

                scope.launch(Dispatchers.IO) {
                    val orgId = currentOrgId ?: sessionManager.getCurrentOrgId()
                    toDelete.forEach { id ->
                        try {
                            database.upiDao().deleteUpiAccount(id)
                            if (!orgId.isNullOrBlank()) {
                                apiService.deleteUpiAccount(orgId, id)
                            }
                        } catch (_: Exception) {}
                    }
                }
            },
            onDismiss = { showBulkDeleteConfirm = false }
        )
    }
}
