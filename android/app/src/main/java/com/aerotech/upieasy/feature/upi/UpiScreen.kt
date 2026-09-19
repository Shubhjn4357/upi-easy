package com.aerotech.upieasy.feature.upi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var showAddDialog by remember { mutableStateOf(false) }

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
            TopAppBar(
                title = {
                    Text(
                        "UPI IDs & Accounts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = BrandPrimary,
                contentColor = SurfaceLight
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add UPI ID")
            }
        },
        containerColor = BackgroundLight
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
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add your business UPI ID (e.g. shop@icici) to accept payments and generate QR codes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(effectiveList) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.vpa,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
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

                                IconButton(onClick = { onNavigateToQrForVpa(item.vpa, item.payeeName) }) {
                                    Icon(Icons.Default.QrCode, contentDescription = "QR Code", tint = BrandAccent)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Payee: ${item.payeeName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )

                            Text(
                                text = "Processed ${item.transactionCount} transactions",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary
                            )

                            if (!item.isDefault) {
                                Spacer(modifier = Modifier.height(10.dp))
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

    if (showAddDialog) {
        var vpaInput by remember { mutableStateOf("") }
        var payeeInput by remember { mutableStateOf("") }
        var setAsDefault by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Link Business UPI ID", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = vpaInput,
                        onValueChange = { vpaInput = it.trim() },
                        label = { Text("UPI VPA (e.g. store@okhdfcbank)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = payeeInput,
                        onValueChange = { payeeInput = it },
                        label = { Text("Registered Payee Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = setAsDefault, onCheckedChange = { setAsDefault = it })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set as default UPI address")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (vpaInput.contains("@") && payeeInput.isNotBlank()) {
                            currentOrgId?.let { orgId ->
                                scope.launch {
                                    apiService.addUpiAccount(
                                        orgId,
                                        AddUpiRequest(vpaInput, payeeInput, setAsDefault)
                                    )
                                    showAddDialog = false
                                    refresh()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Add Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
