package com.aerotech.upieasy.feature.bank

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aerotech.upieasy.core.network.AddBankAccountRequest
import com.aerotech.upieasy.core.network.BankAccountDto
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.network.UpdateBankAccountRequest
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.core.ui.BankAccountsSkeleton
import com.aerotech.upieasy.ui.theme.BrandPrimary
import com.aerotech.upieasy.ui.theme.SuccessGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankAccountsScreen(
    sessionManager: SessionManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(sessionManager) }

    val orgId by sessionManager.currentOrgIdFlow.collectAsState(initial = null)
    val userRole by sessionManager.userRoleFlow.collectAsState(initial = null)
    val isOwner = userRole?.equals("OWNER", ignoreCase = true) == true

    var bankAccounts by remember { mutableStateOf<List<BankAccountDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<BankAccountDto?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }
    var deletingId by remember { mutableStateOf<String?>(null) }

    suspend fun loadAccounts() {
        val id = orgId ?: return
        isLoading = true
        try {
            val res = apiService.getBankAccounts(id)
            if (res.isSuccessful) {
                bankAccounts = res.body()?.accounts ?: emptyList()
            }
        } catch (_: Exception) {}
        finally { isLoading = false }
    }

    LaunchedEffect(orgId) { loadAccounts() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settlement Bank Accounts",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isOwner) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add bank account")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            when {
                isLoading -> {
                    BankAccountsSkeleton()
                }
                bankAccounts.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(BrandPrimary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = BrandPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No Bank Accounts Linked",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Connect a verified settlement bank account to receive automated daily payouts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                        )
                        if (isOwner) {
                            Button(
                                onClick = { showAddDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Link Bank Account")
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(bankAccounts) { account ->
                            BankAccountCard(
                                account = account,
                                isOwner = isOwner,
                                isDeleting = deletingId == account.id,
                                onEdit = { editingAccount = account },
                                onSetDefault = {
                                    scope.launch {
                                        try {
                                            val id = orgId ?: return@launch
                                            apiService.setDefaultBankAccount(id, account.id)
                                            loadAccounts()
                                            Toast.makeText(context, "Default account updated", Toast.LENGTH_SHORT).show()
                                        } catch (_: Exception) {}
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        deletingId = account.id
                                        try {
                                            val id = orgId ?: return@launch
                                            val res = apiService.deleteBankAccount(id, account.id)
                                            if (res.isSuccessful) {
                                                loadAccounts()
                                                Toast.makeText(context, "Bank account removed", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (_: Exception) {}
                                        finally { deletingId = null }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Bank Account Bottom Sheet (Drawer)
    if (editingAccount != null && isOwner) {
        EditBankAccountBottomSheet(
            account = editingAccount!!,
            isSaving = isUpdating,
            onDismiss = { editingAccount = null },
            onSubmit = { bankName, holderName, accountNumber, ifsc, accountType ->
                scope.launch {
                    isUpdating = true
                    try {
                        val id = orgId ?: return@launch
                        val res = apiService.updateBankAccount(
                            orgId = id,
                            accountId = editingAccount!!.id,
                            request = UpdateBankAccountRequest(
                                bankName = bankName,
                                accountHolderName = holderName,
                                accountNumber = accountNumber,
                                ifscCode = ifsc.uppercase(),
                                accountType = accountType
                            )
                        )
                        if (res.isSuccessful) {
                            editingAccount = null
                            loadAccounts()
                            Toast.makeText(context, "Bank account updated successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to update bank account", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isUpdating = false
                    }
                }
            }
        )
    }

    // Add Bank Account Bottom Sheet (Drawer)
    if (showAddDialog && isOwner) {
        AddBankAccountBottomSheet(
            isSaving = isSaving,
            onDismiss = { showAddDialog = false },
            onSubmit = { bankName, holderName, accountNumber, ifsc, accountType ->
                scope.launch {
                    isSaving = true
                    try {
                        val id = orgId ?: return@launch
                        val res = apiService.addBankAccount(
                            id,
                            AddBankAccountRequest(
                                bankName = bankName,
                                accountHolderName = holderName,
                                accountNumber = accountNumber,
                                ifscCode = ifsc.uppercase(),
                                accountType = accountType
                            )
                        )
                        if (res.isSuccessful) {
                            showAddDialog = false
                            loadAccounts()
                            Toast.makeText(context, "Bank account linked!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to link account", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isSaving = false
                    }
                }
            }
        )
    }
}

@Composable
private fun BankAccountCard(
    account: BankAccountDto,
    isOwner: Boolean,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (account.isDefault) 4.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            account.bankName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (account.isDefault) {
                            Text(
                                "Default Settlement Account",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuccessGreen
                            )
                        }
                    }
                }
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            account.accountType,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Account Holder", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(account.accountHolderName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Account Number", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(account.accountNumberMasked, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("IFSC Code", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(account.ifscCode, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }

            if (isOwner) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", style = MaterialTheme.typography.labelSmall)
                    }
                    if (!account.isDefault) {
                        OutlinedButton(
                            onClick = onSetDefault,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Default", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !isDeleting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("Remove", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove Bank Account?") },
            text = { Text("Are you sure you want to remove ${account.bankName} (${account.accountNumberMasked}) from your settlement accounts?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private data class BankBranchDetails(
    val bank: String,
    val branch: String,
    val address: String,
    val city: String,
    val state: String
)

private val POPULAR_INDIAN_BANKS = listOf(
    "State Bank of India",
    "HDFC Bank",
    "ICICI Bank",
    "Axis Bank",
    "Kotak Mahindra Bank",
    "Punjab National Bank",
    "Bank of Baroda",
    "Canara Bank",
    "Union Bank of India",
    "IndusInd Bank",
    "Yes Bank",
    "IDFC FIRST Bank",
    "Federal Bank",
    "Indian Bank",
    "Central Bank of India"
)

private suspend fun fetchBankDetailsByIfsc(ifsc: String): BankBranchDetails? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://ifsc.razorpay.com/$ifsc")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.requestMethod = "GET"
        if (conn.responseCode == 200) {
            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(jsonStr)
            BankBranchDetails(
                bank = obj.optString("BANK", ""),
                branch = obj.optString("BRANCH", ""),
                address = obj.optString("ADDRESS", ""),
                city = obj.optString("CITY", ""),
                state = obj.optString("STATE", "")
            )
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBankAccountBottomSheet(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (bankName: String, holderName: String, accountNumber: String, ifsc: String, accountType: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var bankName by remember { mutableStateOf("") }
    var holderName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var ifsc by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf("CURRENT") }
    var bankDropdownExpanded by remember { mutableStateOf(false) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    var isSearchingIfsc by remember { mutableStateOf(false) }
    var ifscDetails by remember { mutableStateOf<BankBranchDetails?>(null) }
    var ifscSearchAttempted by remember { mutableStateOf(false) }

    val filteredBanks = remember(bankName) {
        if (bankName.isBlank()) POPULAR_INDIAN_BANKS.take(6)
        else POPULAR_INDIAN_BANKS.filter { it.contains(bankName, ignoreCase = true) }
    }

    // Auto-discover bank details when 11-digit IFSC code is entered
    LaunchedEffect(ifsc) {
        if (ifsc.length == 11) {
            isSearchingIfsc = true
            ifscSearchAttempted = true
            val details = fetchBankDetailsByIfsc(ifsc)
            ifscDetails = details
            if (details != null && bankName.isBlank()) {
                bankName = details.bank
            }
            isSearchingIfsc = false
        } else {
            ifscDetails = null
            ifscSearchAttempted = false
        }
    }

    val isValid = bankName.isNotBlank() && holderName.isNotBlank()
        && accountNumber.length >= 9 && ifsc.length == 11

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Link Bank Account",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Add settlement account for payouts & reconciliation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, enabled = !isSaving) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 1. Bank Name with suggestions
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ExposedDropdownMenuBox(
                    expanded = bankDropdownExpanded,
                    onExpandedChange = { bankDropdownExpanded = !bankDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { newText ->
                            bankName = newText
                            bankDropdownExpanded = newText.isNotBlank() && POPULAR_INDIAN_BANKS.any { it.contains(newText, ignoreCase = true) }
                        },
                        label = { Text("Bank Name") },
                        placeholder = { Text("e.g. HDFC Bank, SBI") },
                        leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, tint = BrandPrimary) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (bankName.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            bankName = ""
                                            bankDropdownExpanded = false
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Clear bank name",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = bankDropdownExpanded)
                            }
                        },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (filteredBanks.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = bankDropdownExpanded,
                            onDismissRequest = { bankDropdownExpanded = false }
                        ) {
                            filteredBanks.forEach { bank ->
                                DropdownMenuItem(
                                    text = { Text(bank, fontWeight = FontWeight.Medium) },
                                    leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        bankName = bank
                                        bankDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Quick selection chips for top banks
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("HDFC Bank", "SBI", "ICICI Bank", "Axis Bank", "Kotak").forEach { quickBank ->
                        SuggestionChip(
                            onClick = {
                                bankName = if (quickBank == "SBI") "State Bank of India" else quickBank
                                bankDropdownExpanded = false
                            },
                            label = { Text(quickBank, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // 2. Account Holder Name
            OutlinedTextField(
                value = holderName,
                onValueChange = { holderName = it },
                label = { Text("Account Holder Name") },
                placeholder = { Text("As per bank records") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // 3. Account Number
            OutlinedTextField(
                value = accountNumber,
                onValueChange = { accountNumber = it.filter { c -> c.isDigit() } },
                label = { Text("Account Number") },
                placeholder = { Text("e.g. 50100234567890") },
                leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // 4. IFSC Code with automatic Bank Address Finder
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = ifsc,
                    onValueChange = { ifsc = it.uppercase().take(11) },
                    label = { Text("IFSC Code (11 characters)") },
                    placeholder = { Text("HDFC0001234") },
                    leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
                    trailingIcon = {
                        if (isSearchingIfsc) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else if (ifscDetails != null) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = SuccessGreen)
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Auto-found Address Card
                if (ifscDetails != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SuccessGreen.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "${ifscDetails!!.bank} — ${ifscDetails!!.branch} Branch",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${ifscDetails!!.address}, ${ifscDetails!!.city}, ${ifscDetails!!.state}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (ifscSearchAttempted && !isSearchingIfsc && ifsc.length == 11) {
                    Text(
                        "Branch details not found online. You can still link this account.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            // 5. Account Type
            ExposedDropdownMenuBox(
                expanded = typeDropdownExpanded,
                onExpandedChange = { typeDropdownExpanded = !typeDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = accountType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Account Type") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = typeDropdownExpanded,
                    onDismissRequest = { typeDropdownExpanded = false }
                ) {
                    listOf("CURRENT", "SAVINGS", "OVERDRAFT").forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                accountType = type
                                typeDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Submit Button
            Button(
                onClick = { onSubmit(bankName, holderName, accountNumber, ifsc, accountType) },
                enabled = isValid && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Linking Account...", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.AddLink, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Link Bank Account", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditBankAccountBottomSheet(
    account: BankAccountDto,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (bankName: String, holderName: String, accountNumber: String?, ifsc: String, accountType: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var bankName by remember { mutableStateOf(account.bankName) }
    var holderName by remember { mutableStateOf(account.accountHolderName) }
    var accountNumber by remember { mutableStateOf("") }
    var ifsc by remember { mutableStateOf(account.ifscCode) }
    var accountType by remember { mutableStateOf(account.accountType) }
    var bankDropdownExpanded by remember { mutableStateOf(false) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    var isSearchingIfsc by remember { mutableStateOf(false) }
    var ifscDetails by remember { mutableStateOf<BankBranchDetails?>(null) }
    var ifscSearchAttempted by remember { mutableStateOf(false) }

    val filteredBanks = remember(bankName) {
        if (bankName.isBlank()) POPULAR_INDIAN_BANKS.take(6)
        else POPULAR_INDIAN_BANKS.filter { it.contains(bankName, ignoreCase = true) }
    }

    LaunchedEffect(ifsc) {
        if (ifsc.length == 11) {
            isSearchingIfsc = true
            ifscSearchAttempted = true
            val details = fetchBankDetailsByIfsc(ifsc)
            ifscDetails = details
            if (details != null && bankName.isBlank()) {
                bankName = details.bank
            }
            isSearchingIfsc = false
        } else {
            ifscDetails = null
            ifscSearchAttempted = false
        }
    }

    val isValid = bankName.isNotBlank() && holderName.isNotBlank() && ifsc.length == 11
        && (accountNumber.isBlank() || accountNumber.length >= 9)

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Edit Bank Account",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Update settlement destination details",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, enabled = !isSaving) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Bank Name with suggestions and backspace fix
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ExposedDropdownMenuBox(
                    expanded = bankDropdownExpanded,
                    onExpandedChange = { bankDropdownExpanded = !bankDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { newText ->
                            bankName = newText
                            bankDropdownExpanded = newText.isNotBlank() && POPULAR_INDIAN_BANKS.any { it.contains(newText, ignoreCase = true) }
                        },
                        label = { Text("Bank Name") },
                        placeholder = { Text("e.g. HDFC Bank, SBI") },
                        leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, tint = BrandPrimary) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (bankName.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            bankName = ""
                                            bankDropdownExpanded = false
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                    }
                                }
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = bankDropdownExpanded)
                            }
                        },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (filteredBanks.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = bankDropdownExpanded,
                            onDismissRequest = { bankDropdownExpanded = false }
                        ) {
                            filteredBanks.forEach { bank ->
                                DropdownMenuItem(
                                    text = { Text(bank, fontWeight = FontWeight.Medium) },
                                    leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        bankName = bank
                                        bankDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Account Holder Name
            OutlinedTextField(
                value = holderName,
                onValueChange = { holderName = it },
                label = { Text("Account Holder Name") },
                placeholder = { Text("As per bank records") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Account Number (optional update)
            OutlinedTextField(
                value = accountNumber,
                onValueChange = { accountNumber = it.filter { c -> c.isDigit() } },
                label = { Text("Account Number") },
                placeholder = { Text("Leave empty to keep ${account.accountNumberMasked}") },
                supportingText = { Text("Current: ${account.accountNumberMasked}") },
                leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // IFSC Code
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = ifsc,
                    onValueChange = { ifsc = it.uppercase().take(11) },
                    label = { Text("IFSC Code (11 characters)") },
                    placeholder = { Text("HDFC0001234") },
                    leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
                    trailingIcon = {
                        if (isSearchingIfsc) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else if (ifscDetails != null) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = SuccessGreen)
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (ifscDetails != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SuccessGreen.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "${ifscDetails!!.bank} — ${ifscDetails!!.branch} Branch",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${ifscDetails!!.address}, ${ifscDetails!!.city}, ${ifscDetails!!.state}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Account Type
            ExposedDropdownMenuBox(
                expanded = typeDropdownExpanded,
                onExpandedChange = { typeDropdownExpanded = !typeDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = accountType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Account Type") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = typeDropdownExpanded,
                    onDismissRequest = { typeDropdownExpanded = false }
                ) {
                    listOf("CURRENT", "SAVINGS", "OVERDRAFT").forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                accountType = type
                                typeDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = { onSubmit(bankName, holderName, accountNumber.ifBlank { null }, ifsc, accountType) },
                enabled = isValid && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Updating...", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Changes", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

