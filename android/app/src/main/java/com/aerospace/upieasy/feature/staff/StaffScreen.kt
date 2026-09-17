package com.aerospace.upieasy.feature.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aerospace.upieasy.core.network.InviteStaffRequest
import com.aerospace.upieasy.core.network.NetworkClient
import com.aerospace.upieasy.core.network.StaffMemberDto
import com.aerospace.upieasy.core.security.SessionManager
import com.aerospace.upieasy.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScreen(sessionManager: SessionManager) {
    val scope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(sessionManager) }
    val currentOrgId by sessionManager.currentOrgIdFlow.collectAsState(initial = null)

    var staffList by remember { mutableStateOf<List<StaffMemberDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showInviteDialog by remember { mutableStateOf(false) }

    fun refresh() {
        currentOrgId?.let { orgId ->
            isLoading = true
            scope.launch {
                try {
                    val res = apiService.getStaff(orgId)
                    if (res.isSuccessful && res.body()?.success == true) {
                        staffList = res.body()?.staff ?: emptyList()
                    }
                } catch (_: Exception) {}
                isLoading = false
            }
        }
    }

    LaunchedEffect(currentOrgId) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Staff & Permissions", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showInviteDialog = true },
                containerColor = BrandPrimary,
                contentColor = SurfaceLight
            ) {
                Icon(Icons.Default.Add, contentDescription = "Invite Staff")
            }
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(staffList) { staff ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceLight)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(BrandPrimary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = BrandPrimary)
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = staff.fullName ?: "Staff Member",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "+91 ${staff.mobileNumber}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BrandAccent.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = staff.role,
                                    color = BrandAccent,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInviteDialog) {
        var mobileInput by remember { mutableStateOf("") }
        var nameInput by remember { mutableStateOf("") }
        var selectedRole by remember { mutableStateOf("CASHIER") }

        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = { Text("Invite Staff Member", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = mobileInput,
                        onValueChange = { if (it.length <= 10) mobileInput = it.filter { char -> char.isDigit() } },
                        label = { Text("10-Digit Mobile") },
                        prefix = { Text("+91 ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Assign Role:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)

                    val roles = listOf("MANAGER", "CASHIER", "ACCOUNTANT")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        roles.forEach { role ->
                            FilterChip(
                                selected = selectedRole == role,
                                onClick = { selectedRole = role },
                                label = { Text(role) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (mobileInput.length == 10) {
                            currentOrgId?.let { orgId ->
                                scope.launch {
                                    apiService.inviteStaff(
                                        orgId,
                                        InviteStaffRequest(mobileInput, nameInput.ifBlank { null }, selectedRole)
                                    )
                                    showInviteDialog = false
                                    refresh()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Send Invite")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
