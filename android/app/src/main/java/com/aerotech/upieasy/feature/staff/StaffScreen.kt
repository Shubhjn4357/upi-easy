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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import com.aerotech.upieasy.core.util.HapticHelper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalFocusManager
import android.widget.Toast
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.database.entity.OrganizationInviteEntity
import com.aerotech.upieasy.core.network.InvitationDto
import com.aerotech.upieasy.core.network.InviteStaffRequest
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.network.StaffMemberDto
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.data.repository.OrganizationRepository
import com.aerotech.upieasy.ui.components.UpieasyPullToRefreshContainer
import com.aerotech.upieasy.ui.components.SwipeToDeleteContainer
import com.aerotech.upieasy.ui.components.UpieasyConfirmBottomDrawer
import com.aerotech.upieasy.core.ui.StaffScreenSkeleton
import com.aerotech.upieasy.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StaffScreen(
    sessionManager: SessionManager,
    database: AppDatabase = AppDatabase.getInstance(LocalContext.current)
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val apiService = remember { NetworkClient.getApiService(sessionManager) }
    val orgRepository = remember { OrganizationRepository(context, apiService, database, sessionManager) }
    val currentOrgId by sessionManager.currentOrgIdFlow.collectAsState(initial = null)
    val userRole by sessionManager.userRoleFlow.collectAsState(initial = null)
    val isOwner = userRole?.equals("OWNER", ignoreCase = true) == true
    val canManageStaff = isOwner || userRole?.equals("MANAGER", ignoreCase = true) == true

    var staffList by remember { mutableStateOf<List<StaffMemberDto>>(emptyList()) }
    var pendingDeletedIds by remember { mutableStateOf(setOf<String>()) }
    val effectiveList = staffList.filter { it.id !in pendingDeletedIds }
    val visibleStaffList = if (isOwner) effectiveList else effectiveList.filter { !it.role.equals("OWNER", ignoreCase = true) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showInviteBottomSheet by remember { mutableStateOf(false) }

    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var staffToDelete by remember { mutableStateOf<StaffMemberDto?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    // Invitations state
    val incomingInvites by orgRepository.observePendingInvites().collectAsState(initial = emptyList())
    var sentInvites by remember { mutableStateOf<List<InvitationDto>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var actionLoadingId by remember { mutableStateOf<String?>(null) }

    fun refresh(silent: Boolean = false) {
        currentOrgId?.let { orgId ->
            if (!silent && staffList.isEmpty()) {
                isLoading = true
            }
            scope.launch {
                try {
                    val res = apiService.getStaff(orgId)
                    if (res.isSuccessful && res.body()?.success == true) {
                        staffList = res.body()?.staff ?: emptyList()
                        pendingDeletedIds = emptySet()
                    }
                } catch (_: Exception) {}

                try {
                    val invitesRes = apiService.getOrganizationInvites(orgId)
                    if (invitesRes.isSuccessful && invitesRes.body()?.success == true) {
                        val rawInvites = invitesRes.body()?.invitations
                            ?: invitesRes.body()?.invites
                            ?: emptyList()
                        sentInvites = rawInvites.filter { it.status.uppercase() == "PENDING" }
                    }
                } catch (_: Exception) {}

                try {
                    orgRepository.refreshInvitations()
                } catch (_: Exception) {}

                isLoading = false
                isRefreshing = false
            }
        }
    }

    // Auto-refresh: initial fetch + periodic background polling every 5 seconds
    LaunchedEffect(currentOrgId) {
        refresh(silent = false)
        while (isActive) {
            delay(5000)
            if (currentOrgId != null) {
                refresh(silent = true)
            }
        }
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
                Column {
                    TopAppBar(
                        title = { Text("Staff & Permissions", fontWeight = FontWeight.Bold) },
                        actions = {
                            IconButton(onClick = { refresh(silent = false) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Staff & Invites")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.primary,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Text(
                                    text = "Team Members (${effectiveList.size})",
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                        val totalPendingInvites = incomingInvites.size + sentInvites.count { it.status == "PENDING" }
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Invitations",
                                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (totalPendingInvites > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text("$totalPendingInvites")
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isSelectionMode && canManageStaff) {
                FloatingActionButton(
                    onClick = { showInviteBottomSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(bottom = 76.dp)
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

            if (selectedTab == 0) {
            if (isLoading) {
                StaffScreenSkeleton()
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
                            if (canManageStaff) {
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
                }
            } else {
                UpieasyPullToRefreshContainer(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        refresh(silent = true)
                    }
                ) {
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
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
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
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Group,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
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
                                                    text = "${visibleStaffList.size} Active Members",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = "RBAC Active",
                                                color = MaterialTheme.colorScheme.primary,
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
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(text = "$managerCount", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 15.sp)
                                                    Text(text = "Managers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f), fontSize = 10.5.sp)
                                                }
                                            }
                                        }
                                        if (cashierCount > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(text = "$cashierCount", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 15.sp)
                                                    Text(text = "Cashiers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f), fontSize = 10.5.sp)
                                                }
                                            }
                                        }
                                        if (accountantCount > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(text = "$accountantCount", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                                                    Text(text = "Accountants", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 10.5.sp)
                                                }
                                            }
                                        }
                                        if (isOwner && ownerCount > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(text = "$ownerCount", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 15.sp)
                                                    Text(text = "Owners", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), fontSize = 10.5.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(
                        items = visibleStaffList,
                        key = { it.id }
                    ) { staff ->
                        val isSelected = selectedIds.contains(staff.id)
                        val itemScale by animateFloatAsState(
                            targetValue = if (isSelected) 0.98f else 1f,
                            animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
                            label = "itemScale"
                        )
                        val cardBg by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            animationSpec = tween(220),
                            label = "cardBg"
                        )
                        val cardBorder by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else GlassBorderLight,
                            animationSpec = tween(220),
                            label = "cardBorder"
                        )

                        val (roleBg, roleColor) = when (staff.role.uppercase()) {
                            "OWNER" -> Pair(PastelPurpleBg, Color(0xFF8B5CF6))
                            "MANAGER" -> Pair(PastelIndigoBg, BrandPrimary)
                            "CASHIER" -> Pair(PastelEmeraldBg, SuccessGreen)
                            "ACCOUNTANT" -> Pair(PastelAmberBg, AmberAlert)
                            else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        SwipeToDeleteContainer(
                            itemKey = staff.id,
                            enabled = !isSelectionMode,
                            isSwipedOpen = (staffToDelete?.id == staff.id),
                            onDeleteRequest = { staffToDelete = staff }
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItemPlacement()
                                .scale(itemScale)
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
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
                                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.HEAVY)
                                            isSelectionMode = true
                                            selectedIds = setOf(staff.id)
                                        }
                                    }
                                ),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = cardBg
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
                            border = BorderStroke(
                                1.dp,
                                cardBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AnimatedVisibility(
                                    visible = isSelectionMode,
                                    enter = fadeIn() + expandHorizontally(),
                                    exit = fadeOut() + shrinkHorizontally()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                                selectedIds = if (checked) selectedIds + staff.id else selectedIds - staff.id
                                                if (selectedIds.isEmpty()) isSelectionMode = false
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                }

                                if (!isSelectionMode) {
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = staff.fullName ?: "Staff Member",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = roleBg
                                        ) {
                                        Text(
                                            text = staff.role,
                                            color = roleColor,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 1.dp, vertical = 1.dp)
                                        )
                                        }
                                    }
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
                                    

                                    val canDeleteThisStaff = isOwner || (canManageStaff && !staff.role.equals("OWNER", ignoreCase = true) && !staff.role.equals("MANAGER", ignoreCase = true))
                                    if (!isSelectionMode && canDeleteThisStaff) {
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
            } else {
                InvitationsTabContent(
                    incomingInvites = incomingInvites,
                    sentInvites = sentInvites,
                    isRefreshing = isRefreshing,
                    actionLoadingId = actionLoadingId,
                    onRefresh = {
                        isRefreshing = true
                        refresh(silent = true)
                    },
                    onAcceptInvite = { invite ->
                        actionLoadingId = invite.id
                        scope.launch {
                            val res = orgRepository.acceptInvitation(invite.id)
                            actionLoadingId = null
                            if (res.isSuccess) {
                                Toast.makeText(context, "Joined ${res.getOrNull()}!", Toast.LENGTH_LONG).show()
                                refresh(silent = true)
                            } else {
                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Failed to accept invite", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onRejectInvite = { invite ->
                        actionLoadingId = invite.id
                        scope.launch {
                            val res = orgRepository.rejectInvitation(invite.id)
                            actionLoadingId = null
                            if (res.isSuccess) {
                                Toast.makeText(context, "Invitation rejected", Toast.LENGTH_SHORT).show()
                                orgRepository.refreshInvitations()
                                refresh(silent = true)
                            } else {
                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Failed to reject invite", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onCancelInvite = { invite ->
                        actionLoadingId = invite.id
                        // Instantly delete from the UI list so it disappears immediately
                        sentInvites = sentInvites.filter { it.id != invite.id }
                        scope.launch {
                            val res = orgRepository.cancelInvitation(invite.id)
                            actionLoadingId = null
                            if (res.isSuccess) {
                                Toast.makeText(context, "Invitation cancelled", Toast.LENGTH_SHORT).show()
                                refresh(silent = true)
                            } else {
                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Failed to cancel invite", Toast.LENGTH_SHORT).show()
                                refresh(silent = true)
                            }
                        }
                    }
                )
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
                                    val sendRes = orgRepository.sendInvitation(
                                        orgId = orgId,
                                        mobileNumber = mobile ?: "",
                                        name = name,
                                        email = email,
                                        role = selectedRole
                                    )
                                    if (sendRes.isSuccess) {
                                        Toast.makeText(context, "Staff invitation sent successfully", Toast.LENGTH_SHORT).show()
                                        showInviteBottomSheet = false
                                        refresh()
                                    } else {
                                        // Fallback to legacy invite endpoint if needed
                                        val fallbackRes = apiService.inviteStaff(
                                            orgId,
                                            InviteStaffRequest(
                                                mobileNumber = mobile,
                                                email = email,
                                                fullName = name,
                                                role = selectedRole
                                            )
                                        )
                                        if (fallbackRes.isSuccessful && fallbackRes.body()?.success == true) {
                                            Toast.makeText(context, "Staff added successfully", Toast.LENGTH_SHORT).show()
                                            showInviteBottomSheet = false
                                            refresh()
                                        } else {
                                            val errBody = fallbackRes.errorBody()?.string()
                                            val serverMsg = try {
                                                org.json.JSONObject(errBody ?: "").optString("message", "").takeIf { it.isNotEmpty() }
                                            } catch (_: Exception) { null }
                                            errorMessage = serverMsg ?: sendRes.exceptionOrNull()?.message ?: "Failed to send invitation"
                                        }
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
        UpieasyConfirmBottomDrawer(
            visible = staffToDelete != null,
            title = "Remove Staff Member?",
            message = "Are you sure you want to remove ${staff.fullName ?: (staff.mobileNumber ?: "this staff member")} from this organization? Their access will be revoked immediately.",
            confirmText = "Remove",
            cancelText = "Cancel",
            isDestructive = true,
            onConfirm = {
                val member = staffToDelete
                staffToDelete = null
                member?.let { st ->
                    currentOrgId?.let { orgId ->
                        scope.launch {
                            try {
                                apiService.deleteStaff(orgId, st.id)
                                refresh()
                            } catch (_: Exception) {}
                        }
                    }
                }
            },
            onDismiss = { staffToDelete = null }
        )
    }

    // Bulk Delete Confirmation
    if (showBulkDeleteConfirm && selectedIds.isNotEmpty()) {
        UpieasyConfirmBottomDrawer(
            visible = showBulkDeleteConfirm,
            title = "Remove ${selectedIds.size} Members?",
            message = "Are you sure you want to remove ${selectedIds.size} selected staff members? Their access will be revoked immediately.",
            confirmText = "Remove All",
            cancelText = "Cancel",
            isDestructive = true,
            onConfirm = {
                showBulkDeleteConfirm = false
                currentOrgId?.let { orgId ->
                    scope.launch {
                        val idsToDelete = selectedIds.toList()
                        isSelectionMode = false
                        selectedIds = emptySet()
                        idsToDelete.forEach { id ->
                            try {
                                apiService.deleteStaff(orgId, id)
                            } catch (_: Exception) {}
                        }
                        refresh()
                    }
                }
            },
            onDismiss = { showBulkDeleteConfirm = false }
        )
    }
}
}

@Composable
private fun InvitationsTabContent(
    incomingInvites: List<OrganizationInviteEntity>,
    sentInvites: List<InvitationDto>,
    isRefreshing: Boolean,
    actionLoadingId: String?,
    onRefresh: () -> Unit,
    onAcceptInvite: (OrganizationInviteEntity) -> Unit,
    onRejectInvite: (OrganizationInviteEntity) -> Unit,
    onCancelInvite: (InvitationDto) -> Unit,
    modifier: Modifier = Modifier
) {
    UpieasyPullToRefreshContainer(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh
    ) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
        ) {
            // Section 1: Incoming Invitations
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Incoming Invitations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Invites sent to you to join other stores",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (incomingInvites.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${incomingInvites.size} Pending",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (incomingInvites.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.MailOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Pending Invitations",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "When a merchant invites you by mobile number, you will receive an alert and invitation here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(incomingInvites, key = { it.id }) { invite ->
                    val isLoadingThis = actionLoadingId == invite.id
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Business,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = invite.organizationName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (!invite.inviterName.isNullOrBlank()) "Invited by ${invite.inviterName}" else "Store Invitation",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = invite.role,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onRejectInvite(invite) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isLoadingThis,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = FailedRed
                                    ),
                                    border = BorderStroke(1.dp, FailedRed.copy(alpha = 0.5f))
                                ) {
                                    Text("Reject", fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = { onAcceptInvite(invite) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isLoadingThis,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    if (isLoadingThis) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Accept", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            // Section 2: Sent Invitations
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Sent Invitations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Invitations sent to add staff to this store",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (sentInvites.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${sentInvites.size} Total",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (sentInvites.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Sent Invitations",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Use the + button below to invite team members by mobile number.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(sentInvites, key = { it.id }) { invite ->
                    val isLoadingThis = actionLoadingId == invite.id
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = invite.invitedName ?: invite.invitedMobile ?: "Staff Member",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val subDetail = listOfNotNull(invite.invitedMobile, invite.invitedEmail).joinToString(" • ")
                                    if (subDetail.isNotBlank()) {
                                        Text(
                                            text = subDetail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = invite.role,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }

                                    val statusColor = when (invite.status.uppercase()) {
                                        "ACCEPTED" -> SuccessGreen
                                        "REJECTED", "CANCELLED", "EXPIRED" -> FailedRed
                                        else -> PendingAmber
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = statusColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = invite.status,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                if (invite.status == "PENDING") {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    TextButton(
                                        onClick = { onCancelInvite(invite) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        enabled = !isLoadingThis
                                    ) {
                                        if (isLoadingThis) {
                                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                        } else {
                                            Text(
                                                "Cancel",
                                                color = FailedRed,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold
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
}


