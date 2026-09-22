package com.aerotech.upieasy.feature.bank

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.ui.theme.BrandPrimary
import com.aerotech.upieasy.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

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
    var isSaving by remember { mutableStateOf(false) }
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
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = BrandPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Loading bank accounts...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

    // Add Bank Account Dialog
    if (showAddDialog && isOwner) {
        AddBankAccountDialog(
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
                    if (!account.isDefault) {
                        OutlinedButton(
                            onClick = onSetDefault,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Set Default", style = MaterialTheme.typography.labelSmall)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBankAccountDialog(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (bankName: String, holderName: String, accountNumber: String, ifsc: String, accountType: String) -> Unit
) {
    var bankName by remember { mutableStateOf("") }
    var holderName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var ifsc by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf("CURRENT") }
    var expanded by remember { mutableStateOf(false) }

    val isValid = bankName.isNotBlank() && holderName.isNotBlank()
        && accountNumber.length >= 9 && ifsc.length == 11

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Link Bank Account", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Bank Name") },
                    placeholder = { Text("e.g. HDFC Bank") },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = holderName,
                    onValueChange = { holderName = it },
                    label = { Text("Account Holder Name") },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it.filter { c -> c.isDigit() } },
                    label = { Text("Account Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = ifsc,
                    onValueChange = { ifsc = it.uppercase().take(11) },
                    label = { Text("IFSC Code") },
                    placeholder = { Text("HDFC0001234") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = accountType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf("CURRENT", "SAVINGS", "OVERDRAFT").forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    accountType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(bankName, holderName, accountNumber, ifsc, accountType) },
                enabled = isValid && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text("Linking...")
                } else {
                    Text("Link Account")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
}
