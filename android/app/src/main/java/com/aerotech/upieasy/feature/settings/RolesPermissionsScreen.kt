package com.aerotech.upieasy.feature.settings

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.network.PermissionDto
import com.aerotech.upieasy.core.network.RoleDto
import com.aerotech.upieasy.core.network.UpdateRolePermissionsRequest
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.core.ui.RolesPermissionsSkeleton
import com.aerotech.upieasy.ui.theme.BrandPrimary
import com.aerotech.upieasy.ui.theme.SuccessGreen
import com.aerotech.upieasy.ui.theme.FailedRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RolesPermissionsScreen(
    sessionManager: SessionManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(sessionManager) }

    val orgId by sessionManager.currentOrgIdFlow.collectAsState(initial = null)
    val userRole by sessionManager.userRoleFlow.collectAsState(initial = null)
    val isOwner = userRole?.equals("OWNER", ignoreCase = true) == true

    var roles by remember { mutableStateOf<List<RoleDto>>(emptyList()) }
    var allPermissions by remember { mutableStateOf<List<PermissionDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var editingRole by remember { mutableStateOf<RoleDto?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    suspend fun loadRoles() {
        val id = orgId ?: return
        isLoading = true
        try {
            val res = apiService.getRolesAndPermissions(id)
            if (res.isSuccessful) {
                roles = res.body()?.roles ?: emptyList()
                allPermissions = res.body()?.permissions ?: emptyList()
            }
        } catch (_: Exception) {}
        finally { isLoading = false }
    }

    LaunchedEffect(orgId) { loadRoles() }

    // Permission categories for grouped display
    val permissionCategories = allPermissions.groupBy { it.category }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Roles & Permissions",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                RolesPermissionsSkeleton()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Info card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BrandPrimary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = BrandPrimary)
                        Text(
                            if (isOwner)
                                "As the Owner, you can configure what each role can access. OWNER always has full access."
                            else
                                "Only organization Owners can modify role permissions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(roles) { role ->
                RoleCard(
                    role = role,
                    allPermissions = allPermissions,
                    permissionCategories = permissionCategories,
                    isOwner = isOwner,
                    onEditClick = { if (role.name != "OWNER") editingRole = role }
                )
            }
        }
    }

    // Edit Role Permissions Bottom Sheet
    editingRole?.let { role ->
        EditRolePermissionsSheet(
            role = role,
            allPermissions = allPermissions,
            permissionCategories = permissionCategories,
            isSaving = isSaving,
            onDismiss = { editingRole = null },
            onSave = { selectedPermIds ->
                scope.launch {
                    isSaving = true
                    try {
                        val id = orgId ?: return@launch
                        val res = apiService.updateRolePermissions(
                            id,
                            role.id,
                            UpdateRolePermissionsRequest(permissionIds = selectedPermIds)
                        )
                        if (res.isSuccessful) {
                            editingRole = null
                            loadRoles()
                            Toast.makeText(context, "Permissions updated for ${role.name}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to update permissions", Toast.LENGTH_SHORT).show()
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
private fun RoleCard(
    role: RoleDto,
    allPermissions: List<PermissionDto>,
    permissionCategories: Map<String, List<PermissionDto>>,
    isOwner: Boolean,
    onEditClick: () -> Unit
) {
    val isOwnerRole = role.name == "OWNER"

    val roleColors = mapOf(
        "OWNER" to BrandPrimary,
        "MANAGER" to MaterialTheme.colorScheme.secondary,
        "CASHIER" to SuccessGreen,
        "ACCOUNTANT" to MaterialTheme.colorScheme.tertiary
    )
    val roleColor = roleColors[role.name] ?: BrandPrimary

    val roleIcons = mapOf(
        "OWNER" to Icons.Default.AdminPanelSettings,
        "MANAGER" to Icons.Default.ManageAccounts,
        "CASHIER" to Icons.Default.PointOfSale,
        "ACCOUNTANT" to Icons.Default.Calculate
    )
    val roleIcon = roleIcons[role.name] ?: Icons.Default.Group

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(roleColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(roleIcon, contentDescription = null, tint = roleColor)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            role.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            role.description ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isOwner && !isOwnerRole) {
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit permissions", tint = BrandPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isOwnerRole) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandPrimary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Stars, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(16.dp))
                        Text(
                            "Full access to all modules — cannot be restricted",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandPrimary
                        )
                    }
                }
            } else {
                // Show permission chips grouped by category
                permissionCategories.forEach { (category, perms) ->
                    val grantedPerms = perms.filter { p -> role.permissions.contains(p.id) }
                    if (grantedPerms.isNotEmpty() || perms.isNotEmpty()) {
                        Text(
                            formatCategoryName(category),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            perms.forEach { perm ->
                                val hasIt = role.permissions.contains(perm.id)
                                val chipLabel = formatPermissionAction(perm.name)
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            chipLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = if (hasIt) SuccessGreen else FailedRed
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (hasIt) Icons.Default.Check else Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = if (hasIt) SuccessGreen else FailedRed
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatPermissionAction(permName: String): String {
    val action = permName.substringAfter(".", permName).lowercase()
    return when (action) {
        "read" -> "Read"
        "export" -> "Export"
        "create" -> "Create"
        "refund" -> "Refund"
        "ingest" -> "Ingest"
        "manage" -> "Manage"
        else -> action.replaceFirstChar { it.uppercase() }
    }
}

private fun formatPermissionDescription(permName: String, fallbackDesc: String?): String {
    return when (permName) {
        "transactions.read" -> "View payment transactions & settlement history"
        "transactions.export" -> "Export reports to Excel & CSV spreadsheets"
        "transactions.create" -> "Record manual payments & initiate transactions"
        "transactions.refund" -> "Process payment refunds back to customers"
        "payment_events.ingest" -> "Auto-detect and capture payment notifications & SMS"
        "accounts.read" -> "View settlement bank accounts"
        "accounts.manage" -> "Add, update, or remove linked bank accounts"
        "upi.read" -> "View active UPI IDs and VPAs"
        "upi.manage" -> "Configure and manage business UPI handles"
        "qr.create" -> "Generate custom counter and customer QR codes"
        "staff.read" -> "View team members and staff list"
        "staff.manage" -> "Invite staff, assign roles, or remove members"
        "reports.read" -> "Access sales reports and business analytics"
        "organization.manage" -> "Manage organization settings and business profile"
        else -> fallbackDesc ?: ""
    }
}

private fun formatCategoryName(category: String): String {
    return when (category.lowercase()) {
        "transactions" -> "Transactions & Ledger"
        "accounts" -> "Bank Accounts"
        "upi" -> "UPI Handles"
        "qr" -> "QR Codes"
        "staff" -> "Staff & Team"
        "reports" -> "Reports & Analytics"
        "organization" -> "Business Settings"
        else -> category.replaceFirstChar { it.uppercase() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRolePermissionsSheet(
    role: RoleDto,
    allPermissions: List<PermissionDto>,
    permissionCategories: Map<String, List<PermissionDto>>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = remember { mutableStateListOf<String>().apply { addAll(role.permissions) } }

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Edit Permissions — ${role.name}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        role.description ?: "Configure access permissions for this role",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, enabled = !isSaving) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Render all permission categories cleanly
            permissionCategories.forEach { (category, perms) ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            formatCategoryName(category),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = BrandPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        perms.forEachIndexed { index, perm ->
                            val isChecked = selected.contains(perm.id)
                            val actionName = formatPermissionAction(perm.name)
                            val description = formatPermissionDescription(perm.name, perm.description)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        actionName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) selected.add(perm.id)
                                        else selected.remove(perm.id)
                                    },
                                    enabled = !isSaving
                                )
                            }
                            if (index < perms.size - 1) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onSave(selected.toList()) },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Saving Changes...", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Permissions", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
