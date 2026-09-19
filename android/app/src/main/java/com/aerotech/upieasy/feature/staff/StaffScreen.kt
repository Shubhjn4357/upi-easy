package com.aerotech.upieasy.feature.staff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.aerotech.upieasy.core.network.InviteStaffRequest
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.network.StaffMemberDto
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StaffScreen(sessionManager: SessionManager) {
    val scope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(sessionManager) }
    val currentOrgId by sessionManager.currentOrgIdFlow.collectAsState(initial = null)

    var staffList by remember { mutableStateOf<List<StaffMemberDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showInviteBottomSheet by remember { mutableStateOf(false) }

    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var staffToDelete by remember { mutableStateOf<StaffMemberDto?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

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
                    title = { Text("Staff & Permissions", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = { showInviteBottomSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Invite Staff")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandAccent)
            }
        } else if (staffList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No staff members yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Invite cashiers, managers, or accountants to give them role-based access.",
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(staffList) { staff ->
                    val isSelected = selectedIds.contains(staff.id)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) {
                                            selectedIds - staff.id
                                        } else {
                                            selectedIds + staff.id
                                        }
                                        if (selectedIds.isEmpty()) isSelectionMode = false
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedIds = setOf(staff.id)
                                    }
                                }
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) selectedIds + staff.id else selectedIds - staff.id
                                        if (selectedIds.isEmpty()) isSelectionMode = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = staff.fullName ?: "Staff Member",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!staff.mobileNumber.isNullOrBlank()) {
                                    Text(
                                        text = "+91 ${staff.mobileNumber}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!staff.email.isNullOrBlank()) {
                                    Text(
                                        text = staff.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
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

                                if (!isSelectionMode) {
                                    IconButton(onClick = { staffToDelete = staff }) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Remove Staff",
                                            tint = FailedRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Invite Staff Bottom Sheet
    if (showInviteBottomSheet) {
        var mobileInput by remember { mutableStateOf("") }
        var emailInput by remember { mutableStateOf("") }
        var nameInput by remember { mutableStateOf("") }
        var selectedRole by remember { mutableStateOf("CASHIER") }
        var isSubmitting by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        val roles = listOf("CASHIER", "MANAGER", "ACCOUNTANT")
        val scrollState = rememberScrollState()

        ModalBottomSheet(
            onDismissRequest = { showInviteBottomSheet = false },
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
                    text = "Invite Staff Member",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Provide a 10-digit mobile number or email to invite your team.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = mobileInput,
                    onValueChange = {
                        if (it.length <= 10) mobileInput = it.filter { char -> char.isDigit() }
                        errorMessage = null
                    },
                    label = { Text("10-Digit Mobile Number (Optional)") },
                    prefix = { Text("+91 ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = {
                        emailInput = it.trim()
                        errorMessage = null
                    },
                    label = { Text("Email Address (Optional)") },
                    placeholder = { Text("staff@example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        errorMessage = null
                    },
                    label = { Text("Full Name (Optional)") },
                    placeholder = { Text("John Doe") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Assign Role",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        roles.forEach { role ->
                            FilterChip(
                                selected = selectedRole == role,
                                onClick = { selectedRole = role },
                                label = {
                                    Text(
                                        role,
                                        fontWeight = if (selectedRole == role) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
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
                        val mobile = mobileInput.trim().ifBlank { null }
                        val email = emailInput.trim().ifBlank { null }
                        val name = nameInput.trim().ifBlank { null }

                        if (mobile == null && email == null) {
                            errorMessage = "Please enter either a 10-digit mobile number or email address."
                            return@Button
                        }
                        if (mobile != null && mobile.length != 10) {
                            errorMessage = "Mobile number must be exactly 10 digits."
                            return@Button
                        }
                        if (email != null && !email.contains("@")) {
                            errorMessage = "Please enter a valid email address."
                            return@Button
                        }

                        currentOrgId?.let { orgId ->
                            isSubmitting = true
                            scope.launch {
                                try {
                                    val res = apiService.inviteStaff(
                                        orgId,
                                        InviteStaffRequest(
                                            mobileNumber = mobile,
                                            email = email,
                                            fullName = name,
                                            role = selectedRole
                                        )
                                    )
                                    if (res.isSuccessful && res.body()?.success == true) {
                                        showInviteBottomSheet = false
                                        refresh()
                                    } else {
                                        // Parse the error body for a real error message
                                        val errBody = res.errorBody()?.string()
                                        val serverMsg = try {
                                            org.json.JSONObject(errBody ?: "").optString("message", "").takeIf { it.isNotEmpty() }
                                        } catch (_: Exception) { null }
                                        errorMessage = serverMsg
                                            ?: res.body()?.message
                                            ?: "Failed to add staff member (HTTP ${res.code()})"
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
                        Text("Send Staff Invite", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Single Delete Confirmation
    staffToDelete?.let { staff ->
        AlertDialog(
            onDismissRequest = { staffToDelete = null },
            title = { Text("Remove Staff Member") },
            text = { Text("Are you sure you want to remove ${staff.fullName ?: staff.mobileNumber} from this organization?") },
            confirmButton = {
                Button(
                    onClick = {
                        currentOrgId?.let { orgId ->
                            scope.launch {
                                try {
                                    apiService.deleteStaff(orgId, staff.id)
                                    refresh()
                                } catch (_: Exception) {}
                                staffToDelete = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FailedRed)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { staffToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bulk Delete Confirmation
    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Remove ${selectedIds.size} Staff Members") },
            text = { Text("Are you sure you want to remove all selected staff members?") },
            confirmButton = {
                Button(
                    onClick = {
                        currentOrgId?.let { orgId ->
                            scope.launch {
                                selectedIds.forEach { id ->
                                    try {
                                        apiService.deleteStaff(orgId, id)
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
                    Text("Remove All")
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
