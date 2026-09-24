package com.aerotech.upieasy.feature.setup

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.aerotech.upieasy.core.network.AppSetupRequest
import com.aerotech.upieasy.core.network.NetworkClient
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DiscoveredUpi(
    val vpa: String,
    val provider: String,
    val app: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSetupScreen(
    sessionManager: SessionManager,
    onSetupComplete: () -> Unit,
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(sessionManager) }

    val savedUserName by sessionManager.userNameFlow.collectAsState(initial = "")
    val savedMobileNumber by sessionManager.mobileNumberFlow.collectAsState(initial = "")

    // Form inputs
    var businessName by remember { mutableStateOf("") }
    var legalBusinessName by remember { mutableStateOf("") }
    val categories = listOf(
        Pair("RETAIL", "Retail & Store"),
        Pair("FOOD_DINING", "Food & Dining"),
        Pair("SERVICES", "Services"),
        Pair("HEALTHCARE", "Healthcare"),
        Pair("TECH", "Technology"),
        Pair("WHOLESALE", "Wholesale"),
        Pair("EDUCATION", "Education"),
        Pair("OTHER", "Other")
    )
    var selectedCategory by remember { mutableStateOf("RETAIL") }
    var panNumber by remember { mutableStateOf("") }
    var gstin by remember { mutableStateOf("") }
    var mobileNumber by remember { mutableStateOf("") }
    var primaryVpa by remember { mutableStateOf("") }
    var payeeName by remember { mutableStateOf(savedUserName ?: "") }
    var bankName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var ifscCode by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSessionExpired by remember { mutableStateOf(false) }

    LaunchedEffect(savedMobileNumber) {
        val mobile = savedMobileNumber
        if (mobileNumber.isBlank() && !mobile.isNullOrBlank()) {
            mobileNumber = mobile.filter { it.isDigit() }.takeLast(10)
        }
    }

    // Auto-skip onboarding if user already has an active firm or pending invitation (e.g. invited staff)
    LaunchedEffect(Unit) {
        try {
            // 1. Check if user already belongs to an organization
            val orgRes = apiService.getOrganizations()
            if (orgRes.isSuccessful && orgRes.body()?.success == true) {
                val orgs = orgRes.body()!!.organizations
                if (orgs.isNotEmpty()) {
                    val firstOrg = orgs[0]
                    sessionManager.setOrganization(
                        orgId = firstOrg.id,
                        orgName = firstOrg.name,
                        role = firstOrg.role,
                        legalName = firstOrg.legalBusinessName,
                        category = firstOrg.category,
                        panNumber = firstOrg.panNumber,
                        gstin = firstOrg.gstin
                    )
                    sessionManager.setSetupComplete(true)
                    onSetupComplete()
                    return@LaunchedEffect
                }
            } else if (orgRes.code() == 401) {
                errorMessage = "Your login session has expired or is invalid. Please sign in again."
                isSessionExpired = true
            }

            // 2. Check if user has any pending invitations to auto-accept
            val inviteRes = apiService.getMyInvitations()
            if (inviteRes.isSuccessful && inviteRes.body()?.success == true) {
                val invites = inviteRes.body()?.invitations ?: inviteRes.body()?.invites ?: emptyList()
                val pendingInvite = invites.firstOrNull { it.status.equals("PENDING", ignoreCase = true) }
                if (pendingInvite != null) {
                    val acceptRes = apiService.acceptInvitation(pendingInvite.id)
                    if (acceptRes.isSuccessful && acceptRes.body()?.success == true) {
                        val orgRes2 = apiService.getOrganizations()
                        if (orgRes2.isSuccessful && orgRes2.body()?.success == true) {
                            val orgs2 = orgRes2.body()!!.organizations
                            val target = orgs2.find { it.id == pendingInvite.organizationId } ?: orgs2.firstOrNull()
                            if (target != null) {
                                sessionManager.setOrganization(
                                    orgId = target.id,
                                    orgName = target.name,
                                    role = target.role,
                                    legalName = target.legalBusinessName,
                                    category = target.category,
                                    panNumber = target.panNumber,
                                    gstin = target.gstin
                                )
                                sessionManager.setSetupComplete(true)
                                onSetupComplete()
                                return@LaunchedEffect
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // UPI Discovery Bottom Sheet
    var showUpiDrawer by remember { mutableStateOf(false) }
    var isSearchingUpi by remember { mutableStateOf(false) }
    var selectedDiscoveredVpa by remember { mutableStateOf("") }

    // Permissions state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            android.os.Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    fun openUpiDiscovery() {
        if (mobileNumber.length == 10) {
            isSearchingUpi = true
            showUpiDrawer = true
            scope.launch {
                delay(600) // Smooth discovery feel
                isSearchingUpi = false
                selectedDiscoveredVpa = "${mobileNumber}@okhdfcbank"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Business Setup & Onboarding", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign Out / Switch Account",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
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

            val focusManager = LocalFocusManager.current
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header Description Bento Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                    border = BorderStroke(1.dp, GlassBorderLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PastelIndigoBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Merchant Store Setup",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Zero-fee direct bank settlements",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Configure your store details and primary UPI address to generate payment QR codes and start tracking customer payments.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Step 1: Permissions Bento Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                    border = BorderStroke(1.dp, GlassBorderLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "1. App Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Camera permission row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (hasCameraPermission) SuccessGreenBg else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = if (hasCameraPermission) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Camera Access", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text("Required for scanning QR codes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                        }

                        if (hasCameraPermission) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = SuccessGreen)
                        } else {
                            TextButton(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text("Grant", color = BrandAccent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Notification permission row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (hasNotificationPermission) SuccessGreenBg else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = if (hasNotificationPermission) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Instant Alerts", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text("Notifies when UPI payments arrive", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                        }

                        if (hasNotificationPermission) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = SuccessGreen)
                        } else {
                            TextButton(onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= 33) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }) {
                                Text("Grant", color = BrandAccent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Step 2: Business & UPI Details Form
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "2. Business & UPI Credentials",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = { Text("Store / Brand Name *") },
                        placeholder = { Text("e.g. Sri Krishna Supermarket") },
                        leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = legalBusinessName,
                        onValueChange = { legalBusinessName = it },
                        label = { Text("Legal Entity Name (Optional)") },
                        placeholder = { Text("e.g. Sri Krishna Retail Pvt Ltd") },
                        leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    // Business Category Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Business Category *",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { (catKey, catLabel) ->
                                val isSelected = selectedCategory == catKey
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = catKey },
                                    label = { Text(catLabel, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandPrimary.copy(alpha = 0.15f),
                                        selectedLabelColor = BrandPrimary
                                    )
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }.takeLast(10)
                            mobileNumber = digits
                            if (mobileNumber.length == 10) {
                                openUpiDiscovery()
                            }
                        },
                        label = { Text("Business Mobile Number *") },
                        prefix = { Text("+91 ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        trailingIcon = {
                            if (mobileNumber.length == 10) {
                                TextButton(onClick = { openUpiDiscovery() }) {
                                    Text("Find UPI", color = BrandAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = primaryVpa,
                        onValueChange = { primaryVpa = it.trim() },
                        label = { Text("Primary Business UPI ID *") },
                        placeholder = { Text("e.g. storename@okhdfcbank or 9876543210@paytm") },
                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingIcon = {
                            if (mobileNumber.length == 10) {
                                IconButton(onClick = { openUpiDiscovery() }) {
                                    Icon(Icons.Default.Search, contentDescription = "Discover UPI", tint = BrandAccent)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = payeeName,
                        onValueChange = { payeeName = it },
                        label = { Text("Registered Payee Name *") },
                        placeholder = { Text("Exact name registered with bank") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                }
            }

            // Step 3: Statutory & Tax Identifiers (Optional)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "3. Tax & Legal Compliance (Optional)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Add PAN or GSTIN for compliant merchant settlements and official invoices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = panNumber,
                        onValueChange = {
                            if (it.length <= 10) panNumber = it.uppercase()
                        },
                        label = { Text("PAN Number (Optional)") },
                        placeholder = { Text("e.g. ABCDE1234F") },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        )
                    )

                    OutlinedTextField(
                        value = gstin,
                        onValueChange = {
                            if (it.length <= 15) gstin = it.uppercase()
                        },
                        label = { Text("GSTIN Number (Optional)") },
                        placeholder = { Text("e.g. 29ABCDE1234F1Z5") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        )
                    )
                }
            }

            // Step 4: Linked Bank Account (Optional)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "4. Bank Account for Reconciliation (Optional)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("Bank Name") },
                        placeholder = { Text("e.g. HDFC Bank, ICICI Bank, SBI") },
                        leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { accountNumber = it.filter { char -> char.isDigit() } },
                        label = { Text("Account Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = ifscCode,
                        onValueChange = { ifscCode = it.uppercase().trim() },
                        label = { Text("IFSC Code") },
                        placeholder = { Text("e.g. HDFC0001234") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        )
                    )
                }
            }

            errorMessage?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = FailedRed.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, FailedRed.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = msg, color = FailedRed, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        if (isSessionExpired) {
                            Button(
                                onClick = onSignOut,
                                colors = ButtonDefaults.buttonColors(containerColor = FailedRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign In Again", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Submit Button
            val bName = businessName.trim()
            val vpa = primaryVpa.trim()
            val phone = mobileNumber.trim()
            val pName = payeeName.trim().ifBlank { bName }

            Button(
                onClick = {
                    if (bName.length < 2) {
                        errorMessage = "Please enter your store or brand name."
                        return@Button
                    }
                    if (phone.length != 10) {
                        errorMessage = "Please enter a valid 10-digit mobile number."
                        return@Button
                    }
                    if (!vpa.contains("@") || vpa.length < 3) {
                        errorMessage = "Please enter a valid UPI address (e.g. store@okhdfcbank)."
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val res = apiService.completeSetup(
                                AppSetupRequest(
                                    businessName = bName,
                                    legalBusinessName = legalBusinessName.trim().ifBlank { null },
                                    category = selectedCategory,
                                    panNumber = panNumber.trim().ifBlank { null },
                                    gstin = gstin.trim().ifBlank { null },
                                    mobileNumber = phone,
                                    primaryVpa = vpa,
                                    payeeName = pName,
                                    bankName = bankName.ifBlank { null },
                                    accountNumber = accountNumber.ifBlank { null },
                                    ifscCode = ifscCode.ifBlank { null }
                                )
                            )

                            if (res.isSuccessful && res.body()?.success == true) {
                                val data = res.body()!!
                                sessionManager.setOrganization(
                                    orgId = data.organization.id,
                                    orgName = data.organization.name,
                                    role = "OWNER",
                                    legalName = data.organization.legalBusinessName,
                                    category = data.organization.category,
                                    panNumber = data.organization.panNumber,
                                    gstin = data.organization.gstin
                                )
                                sessionManager.setSetupComplete(true)
                                onSetupComplete()
                            } else {
                                val errBody = res.errorBody()?.string()
                                val parsedMsg = try {
                                    val jsonObj = org.json.JSONObject(errBody ?: "")
                                    val errObj = jsonObj.optJSONObject("error")
                                    if (errObj != null) {
                                        val msg = errObj.optString("message", "")
                                        val details = errObj.optJSONObject("details")?.toString() ?: ""
                                        if (msg.isNotBlank() && details.isNotBlank() && details != "{}") "$msg: $details" else msg
                                    } else {
                                        jsonObj.optString("message", "")
                                    }
                                } catch (_: Exception) { "" }

                                if (res.code() == 401) {
                                    errorMessage = if (parsedMsg.isNotBlank()) "Authentication error: $parsedMsg. Please sign in again." else "Session expired or invalid (401). Please sign in again."
                                    isSessionExpired = true
                                } else {
                                    errorMessage = if (parsedMsg.isNotBlank()) parsedMsg else (res.body()?.message ?: "Setup failed (${res.code()}). Please check your UPI address and details.")
                                }
                            }
                        } catch (e: Exception) {
                            errorMessage = e.localizedMessage ?: "Failed to connect to backend server"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = SurfaceLight, modifier = Modifier.size(24.dp))
                } else {
                    Text("Complete Setup & Open Dashboard", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

    // Discovered UPI Drawer (Bottom Sheet)
    if (showUpiDrawer) {
        val discoveredList = remember(mobileNumber) {
            listOf(
                DiscoveredUpi("${mobileNumber}@okhdfcbank", "HDFC Bank", "Google Pay"),
                DiscoveredUpi("${mobileNumber}@okaxis", "Axis Bank", "Google Pay"),
                DiscoveredUpi("${mobileNumber}@oksbi", "State Bank of India", "Google Pay"),
                DiscoveredUpi("${mobileNumber}@okicici", "ICICI Bank", "Google Pay"),
                DiscoveredUpi("${mobileNumber}@ybl", "Yes Bank", "PhonePe"),
                DiscoveredUpi("${mobileNumber}@ibl", "ICICI Bank", "PhonePe"),
                DiscoveredUpi("${mobileNumber}@paytm", "Paytm Payments", "Paytm"),
                DiscoveredUpi("${mobileNumber}@upi", "NPCI Direct", "BHIM UPI")
            )
        }

        ModalBottomSheet(
            onDismissRequest = { showUpiDrawer = false },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Linked UPI IDs Found",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Discovered for +91 $mobileNumber",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSearchingUpi) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }

                Text(
                    text = "Select the UPI handle you want to link as your primary payment receiver:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(discoveredList) { item ->
                        val isSelected = selectedDiscoveredVpa == item.vpa
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedDiscoveredVpa = item.vpa },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedDiscoveredVpa = item.vpa }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = item.vpa,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${item.app} • ${item.provider}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BrandAccent.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = item.app,
                                        color = BrandAccent,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (selectedDiscoveredVpa.isNotBlank()) {
                            primaryVpa = selectedDiscoveredVpa
                            if (payeeName.isBlank() && businessName.isNotBlank()) {
                                payeeName = businessName
                            }
                            showUpiDrawer = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = selectedDiscoveredVpa.isNotBlank()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Selected UPI ID", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}
