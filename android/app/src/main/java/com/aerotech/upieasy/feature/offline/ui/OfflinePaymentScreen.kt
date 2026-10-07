package com.aerotech.upieasy.feature.offline.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.database.entity.TransactionEntity
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.core.util.HapticHelper
import com.aerotech.upieasy.feature.offline.core.CallManager
import com.aerotech.upieasy.feature.offline.core.CurrencyFormat
import com.aerotech.upieasy.feature.offline.core.OfflinePaymentSessionManager
import com.aerotech.upieasy.feature.offline.core.PhoneNumberUtils
import com.aerotech.upieasy.feature.offline.model.OfflinePaymentState
import com.aerotech.upieasy.ui.theme.*

private val PeriwinkleBlue: Color
    @Composable
    get() = MaterialTheme.colorScheme.primary
private val DarkCardSurface = Color(0xFF0F0F11)
private val ActionButtonBlue: Color
    @Composable
    get() = MaterialTheme.colorScheme.primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflinePaymentScreen(
    sessionManager: SessionManager,
    database: AppDatabase,
    onNavigateBack: () -> Unit,
    onNavigateToScan: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val sessionManagerInstance = remember { OfflinePaymentSessionManager.getInstance(context) }
    val callManager = remember { CallManager(context) }

    // Session State & Recent transactions
    val paymentState by sessionManagerInstance.paymentState.collectAsState()
    val recentOfflineTxns by database.transactionDao().getOfflineTransactionsFlow().collectAsState(initial = emptyList())
    val upiAccounts by database.upiDao().getAllUpiAccountsFlow().collectAsState(initial = emptyList())

    // Permissions & Roles
    val userRole by sessionManager.userRoleFlow.collectAsState(initial = null)
    val userPermissions by sessionManager.userPermissionsFlow.collectAsState(initial = emptySet())
    val isOwner = userRole?.equals("OWNER", ignoreCase = true) == true || userPermissions.contains("*")
    val canInitiatePayment = isOwner || userRole?.uppercase() in listOf("MANAGER", "CASHIER") || userPermissions.contains("transactions.create")

    // Contact Payment Bottom Sheet state
    var showPayContactSheet by remember { mutableStateOf(false) }
    var contactPhone by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var contactAmount by remember { mutableStateOf("") }
    var selectedSubscriptionId by remember { mutableStateOf<Int?>(null) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var showBankSwitchDialog by remember { mutableStateOf(false) }

    // Telephony SIM detection
    val availableSims = remember {
        val list = mutableListOf<SubscriptionInfo>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            try {
                sm?.activeSubscriptionInfoList?.let { list.addAll(it) }
            } catch (_: Exception) {}
        }
        list
    }

    LaunchedEffect(availableSims) {
        if (availableSims.isNotEmpty() && selectedSubscriptionId == null) {
            selectedSubscriptionId = availableSims[0].subscriptionId
        }
    }

    // Contact picker launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        contactUri?.let { uri ->
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val idIndex = c.getColumnIndex(ContactsContract.Contacts._ID)
                        val nameIndex = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        val contactId = if (idIndex != -1) c.getString(idIndex) else null
                        val name = if (nameIndex != -1) c.getString(nameIndex) else null
                        if (!name.isNullOrBlank()) {
                            contactName = name
                        }

                        if (!contactId.isNullOrBlank()) {
                            val phoneCursor = context.contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null,
                                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                arrayOf(contactId),
                                null
                            )
                            phoneCursor?.use { pc ->
                                if (pc.moveToFirst()) {
                                    val numIndex = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                    if (numIndex != -1) {
                                        val rawNumber = pc.getString(numIndex)
                                        val normalized = PhoneNumberUtils.normalize(rawNumber)
                                        if (normalized.isNotBlank()) {
                                            contactPhone = normalized
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Could not read contact: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val callGranted = permissions[Manifest.permission.CALL_PHONE] == true
        val stateGranted = permissions[Manifest.permission.READ_PHONE_STATE] == true
        val smsGranted = permissions[Manifest.permission.RECEIVE_SMS] == true
        if (callGranted && stateGranted && smsGranted) {
            Toast.makeText(context, "Permissions enabled for offline payments", Toast.LENGTH_SHORT).show()
        }
    }

    fun hasRequiredPermissions(): Boolean {
        val call = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val state = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val sms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        return call && state && sms
    }

    fun executeContactPayment() {
        if (!hasRequiredPermissions()) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.RECEIVE_SMS
                )
            )
            return
        }

        val amt = contactAmount.toLongOrNull() ?: 0L
        if (amt <= 0L) {
            Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }
        if (amt > 4999L) {
            Toast.makeText(context, "Offline payment limit is ₹4,999", Toast.LENGTH_LONG).show()
            return
        }

        val normPhone = PhoneNumberUtils.normalize(contactPhone)
        if (normPhone.length != 10) {
            Toast.makeText(context, "Please enter a valid 10-digit mobile number", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Settings.canDrawOverlays(context)) {
            showOverlayPermissionDialog = true
            return
        }

        val sessionSuccess = sessionManagerInstance.startSession(
            phoneNumber = normPhone,
            amount = amt.toString(),
            payeeName = contactName.ifBlank { "Mobile $normPhone" }
        )

        if (sessionSuccess) {
            showPayContactSheet = false
            callManager.initiateUPI123Call(
                phoneNumber = normPhone,
                amount = amt.toString(),
                subscriptionId = selectedSubscriptionId
            )
        } else {
            Toast.makeText(context, "Another payment session is already active", Toast.LENGTH_SHORT).show()
        }
    }

    // Active Bank Name / Carrier display
    val connectedBankName = remember(upiAccounts, availableSims, selectedSubscriptionId) {
        val activeSim = availableSims.find { it.subscriptionId == selectedSubscriptionId } ?: availableSims.firstOrNull()
        val defaultUpi = upiAccounts.firstOrNull { it.isDefault } ?: upiAccounts.firstOrNull()

        when {
            defaultUpi != null -> defaultUpi.payeeName.ifBlank { "HDFC Bank" }
            activeSim != null -> "${activeSim.carrierName ?: "SIM"} Bank Link"
            else -> "HDFC Bank"
        }
    }

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // =========================================================================
            // 1. TOP PERIWINKLE CARD (Exact match to screenshot)
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = PeriwinkleBlue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // Header Row: Back button (if back-stack exists), App Title, and Settings Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                    onNavigateBack()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.22f))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "UPIEasy",
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Payments Without Internet",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Circular Settings Button (Exact match)
                        IconButton(
                            onClick = {
                                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                showBankSwitchDialog = true
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Connected Bank Inner Pill Card (Exact match)
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                showBankSwitchDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Connected Bank",
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = connectedBankName,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Circular Wallet / Bank Badge
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.32f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Live Payment Session Banner (if an offline payment is active)
            if (paymentState !is OfflinePaymentState.Idle) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    LivePaymentStatusCard(
                        state = paymentState,
                        onCancelSession = {
                            sessionManagerInstance.cancelSession("Cancelled by user")
                            callManager.hangupCall()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.7f))

            // =========================================================================
            // 2. CENTER SECTION: SCAN QR CODE & PAY CONTACT (Exact match to screenshot)
            // =========================================================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Action 1: Large Circular Scan QR Code Button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                        onNavigateToScan()
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(ActionButtonBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR Code",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Scan QR Code",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // "OR" Divider Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.22f))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1C1C1E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "OR",
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.22f))
                    )
                }

                // Action 2: Squircle Pay Contact Button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                        showPayContactSheet = true
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(ActionButtonBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Pay Contact",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Pay Contact",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // =========================================================================
            // 3. BOTTOM SECTION: RECENT PAYMENTS CARD (Exact match to screenshot)
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
                color = DarkCardSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp)
                ) {
                    // Header Row: History Icon, Title, and "View All" link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Recent Payments",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Your latest transactions",
                                    color = Color.White.copy(alpha = 0.50f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // "View All" clickable link
                        Text(
                            text = "View All",
                            color = PeriwinkleBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                    onNavigateToTransactions()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Minimalist History Center Action / List
                    if (recentOfflineTxns.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = {
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                    onNavigateToTransactions()
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1C1C1E))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Transactions History",
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    } else {
                        // Display latest 2 transactions with clean minimalist rows
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            recentOfflineTxns.take(2).forEach { txn ->
                                MinimalRecentTxnRow(
                                    transaction = txn,
                                    onClick = {
                                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                        onNavigateToTransactions()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // =============================================================================
    // PAY CONTACT / NUMBER BOTTOM SHEET
    // =============================================================================
    if (showPayContactSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPayContactSheet = false },
            containerColor = DarkCardSurface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pay Contact",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    IconButton(
                        onClick = { showPayContactSheet = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = "Transfer without internet via 10-digit mobile number",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Mobile Number field with contact picker button
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { input ->
                        val cleaned = input.filter { it.isDigit() }
                        if (cleaned.length <= 10) contactPhone = cleaned
                    },
                    label = { Text("Mobile Number", color = Color.White.copy(alpha = 0.7f)) },
                    placeholder = { Text("10-digit phone number", color = Color.White.copy(alpha = 0.4f)) },
                    leadingIcon = {
                        Text(
                            text = "+91",
                            fontWeight = FontWeight.Bold,
                            color = PeriwinkleBlue,
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                    contactPickerLauncher.launch(null)
                                } else {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Contacts,
                                contentDescription = "Pick Contact",
                                tint = PeriwinkleBlue
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PeriwinkleBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Payee Name (Optional)
                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text("Payee Name (Optional)", color = Color.White.copy(alpha = 0.7f)) },
                    placeholder = { Text("e.g. Ramesh", color = Color.White.copy(alpha = 0.4f)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PeriwinkleBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Amount field
                val isOverLimit = (contactAmount.toLongOrNull() ?: 0L) > 4999L
                OutlinedTextField(
                    value = contactAmount,
                    onValueChange = { input ->
                        val cleaned = input.filter { it.isDigit() }
                        contactAmount = cleaned
                    },
                    label = { Text("Transfer Amount", color = Color.White.copy(alpha = 0.7f)) },
                    placeholder = { Text("₹ 1 to ₹ 4,999", color = Color.White.copy(alpha = 0.4f)) },
                    leadingIcon = {
                        Text(
                            text = "₹",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOverLimit) FailedRed else PeriwinkleBlue,
                            modifier = Modifier.padding(start = 14.dp, end = 4.dp)
                        )
                    },
                    isError = isOverLimit,
                    supportingText = {
                        Text(
                            text = if (isOverLimit) "Max limit is ₹4,999 per offline payment" else "Offline limit: ₹4,999",
                            color = if (isOverLimit) FailedRed else Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PeriwinkleBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Amount chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(100, 500, 1000, 2000).forEach { chipAmt ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    contactAmount = chipAmt.toString()
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                }
                        ) {
                            Text(
                                text = "+₹$chipAmt",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                // Multi-SIM selection if available
                if (availableSims.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Payment SIM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        availableSims.forEach { sim ->
                            val isSimSelected = selectedSubscriptionId == sim.subscriptionId
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSimSelected) PeriwinkleBlue.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(
                                    width = if (isSimSelected) 1.5.dp else 1.dp,
                                    color = if (isSimSelected) PeriwinkleBlue else Color.White.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.SELECTION)
                                        selectedSubscriptionId = sim.subscriptionId
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SimCard,
                                        contentDescription = null,
                                        tint = if (isSimSelected) PeriwinkleBlue else Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SIM ${sim.simSlotIndex + 1} (${sim.carrierName ?: "Cellular"})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary Call to Pay Button
                val amtDisplay = contactAmount.toLongOrNull() ?: 0L
                Button(
                    onClick = {
                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                        focusManager.clearFocus()
                        executeContactPayment()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PeriwinkleBlue,
                        contentColor = Color.White
                    ),
                    enabled = paymentState is OfflinePaymentState.Idle && canInitiatePayment
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (amtDisplay > 0) "Call to Pay (₹$amtDisplay)" else "Call to Pay",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // =============================================================================
    // BANK / SIM SELECTION DIALOG
    // =============================================================================
    if (showBankSwitchDialog) {
        AlertDialog(
            onDismissRequest = { showBankSwitchDialog = false },
            containerColor = DarkCardSurface,
            title = {
                Text(
                    text = "Connected Account",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Offline payments use your cellular telephony link with whole rupees and a ₹4,999 limit.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    if (availableSims.isNotEmpty()) {
                        Text(
                            text = "Select Default SIM:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        availableSims.forEach { sim ->
                            val isSelected = selectedSubscriptionId == sim.subscriptionId
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) PeriwinkleBlue.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(1.dp, if (isSelected) PeriwinkleBlue else Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.SELECTION)
                                        selectedSubscriptionId = sim.subscriptionId
                                        showBankSwitchDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SimCard,
                                        contentDescription = null,
                                        tint = if (isSelected) PeriwinkleBlue else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "SIM ${sim.simSlotIndex + 1}: ${sim.carrierName ?: "Cellular"}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBankSwitchDialog = false }) {
                    Text("Done", color = PeriwinkleBlue, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // =============================================================================
    // OVERLAY PERMISSION EXPLANATION DIALOG
    // =============================================================================
    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            containerColor = DarkCardSurface,
            title = {
                Text(
                    text = "Allow Screen Guide",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "UPI-Easy displays a payment guide on your screen during the call so you can view payee details while you enter your UPI PIN.\n\nPlease enable 'Allow display over other apps' in Settings.",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverlayPermissionDialog = false
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PeriwinkleBlue)
                ) {
                    Text("Open Settings", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPermissionDialog = false }) {
                    Text("Not Now", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }
}

@Composable
fun MinimalRecentTxnRow(
    transaction: TransactionEntity,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.05f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = PeriwinkleBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = transaction.payeeName.ifBlank { transaction.payeeVpa.ifBlank { "Offline Payee" } },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = CurrencyFormat.formatTimestamp(transaction.occurredAt),
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = "₹${CurrencyFormat.inr(transaction.amount)}",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun LivePaymentStatusCard(
    state: OfflinePaymentState,
    onCancelSession: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PeriwinkleBlue.copy(alpha = 0.20f)),
        border = BorderStroke(1.dp, PeriwinkleBlue.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = PeriwinkleBlue
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Payment in Progress",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                TextButton(
                    onClick = {
                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.HEAVY)
                        onCancelSession()
                    }
                ) {
                    Text("Cancel", color = FailedRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val statusText = when (state) {
                is OfflinePaymentState.Initiating -> "Starting call session..."
                is OfflinePaymentState.InProgress -> "Call connected: Please enter your UPI PIN on your phone keypad."
                is OfflinePaymentState.WaitingForVerification -> "Call completed! Waiting for bank confirmation..."
                is OfflinePaymentState.Success -> "Payment Verified! Bank confirmation received."
                is OfflinePaymentState.Failed -> "Payment failed or disconnected."
                is OfflinePaymentState.NeedsReview -> "Payment requires review. Please check bank statement."
                is OfflinePaymentState.Cancelled -> "Session cancelled."
                is OfflinePaymentState.Timeout -> "Verification timed out."
                else -> ""
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}
