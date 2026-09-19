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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalFocusManager
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

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandAccent)
                }
            } else if (staffList.isEmpty()) {
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
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Staff Members Added",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Invite cashiers, branch managers, or accountants to give them secure, role-based store access without bank credentials.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { showInviteBottomSheet = true },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Invite Staff Member", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
                ) {
                    if (!isSelectionMode) {
                        // Bento Role Breakdown Hero Card
                        item {
                            val ownerCount = staffList.count { it.role.equals("OWNER", ignoreCase = true) }
                            val managerCount = staffList.count { it.role.equals("MANAGER", ignoreCase = true) }
                            val cashierCount = staffList.count { it.role.equals("CASHIER", ignoreCase = true) }
                            val accountantCount = staffList.count { it.role.equals("ACCOUNTANT", ignoreCase = true) }

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
                                                    imageVector = Icons.Default.Group,
                                                    contentDescription = null,
                                                    tint = BrandPrimary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "Team & Roles",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${staffList.size} Total Members",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = PastelEmeraldBg
                                        ) {
                                            Text(
                                                text = "RBAC Active",
                                                color = SuccessGreen,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Role Breakdown Badges Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (managerCount > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = PastelIndigoBg,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(text = "$managerCount", fontWeight = FontWeight.Bold, color = BrandPrimary, fontSize = 14.sp)
                                                    Text(text = "Managers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                        if (cashierCount > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = PastelEmeraldBg,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(text = "$cashierCount", fontWeight = FontWeight.Bold, color = SuccessGreen, fontSize = 14.sp)
                                                    Text(text = "Cashiers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                        if (accountantCount > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = PastelAmberBg,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(text = "$accountantCount", fontWeight = FontWeight.Bold, color = AmberAlert, fontSize = 14.sp)
                                                    Text(text = "Accountants", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                        if (ownerCount > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = PastelPurpleBg,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(text = "$ownerCount", fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6), fontSize = 14.sp)
                                                    Text(text = "Owners", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(staffList) { staff ->
                        val isSelected = selectedIds.contains(staff.id)

                        val (roleBg, roleColor) = when (staff.role.uppercase()) {
                            "OWNER" -> Pair(PastelPurpleBg, Color(0xFF8B5CF6))
                            "MANAGER" -> Pair(PastelIndigoBg, BrandPrimary)
                            "CASHIER" -> Pair(PastelEmeraldBg, SuccessGreen)
                            "ACCOUNTANT" -> Pair(PastelAmberBg, AmberAlert)
                            else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        }

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
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(15.dp))
                                            .background(roleBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = (staff.fullName?.take(1) ?: "S").uppercase(),
                                            color = roleColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = staff.fullName ?: "Staff Member",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
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
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = roleBg
                                    ) {
                                        Text(
                                            text = staff.role,
                                            color = roleColor,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    if (!isSelectionMode) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = { staffToDelete = staff },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(PastelPink)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Remove Staff",
                                                tint = FailedRed,
                                                modifier = Modifier.size(17.dp)
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
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    )
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
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )
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
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
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
