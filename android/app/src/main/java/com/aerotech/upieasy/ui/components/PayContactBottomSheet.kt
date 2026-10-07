package com.aerotech.upieasy.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.core.util.HapticHelper
import com.aerotech.upieasy.core.util.PaymentLauncher
import com.aerotech.upieasy.core.util.UpiPaymentDetails
import com.aerotech.upieasy.core.util.UpiUriHelper
import com.aerotech.upieasy.feature.offline.core.CallManager
import com.aerotech.upieasy.feature.offline.core.OfflinePaymentSessionManager
import com.aerotech.upieasy.feature.offline.core.OfflineUpiBindingManager
import com.aerotech.upieasy.feature.offline.core.PhoneNumberUtils
import com.aerotech.upieasy.ui.theme.FailedRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PhoneContactItem(
    val name: String,
    val rawNumber: String,
    val normalizedNumber: String
)

private val AvatarColors = listOf(
    Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899),
    Color(0xFF06B6D4), Color(0xFF10B981), Color(0xFFF59E0B),
    Color(0xFF3B82F6), Color(0xFF14B8A6)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayContactBottomSheet(
    onDismiss: () -> Unit,
    sessionManager: SessionManager = SessionManager(LocalContext.current),
    database: AppDatabase = AppDatabase.getInstance(LocalContext.current),
    onPaymentInitiated: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val sessionManagerInstance = remember { OfflinePaymentSessionManager.getInstance(context) }
    val callManager = remember { CallManager(context) }

    var searchQuery by remember { mutableStateOf("") }
    var contactsList by remember { mutableStateOf<List<PhoneContactItem>>(emptyList()) }
    var isLoadingContacts by remember { mutableStateOf(false) }

    var selectedContact by remember { mutableStateOf<PhoneContactItem?>(null) }
    var transferAmount by remember { mutableStateOf("") }
    var transferNote by remember { mutableStateOf("") }
    var selectedSubscriptionId by remember { mutableStateOf<Int?>(null) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }

    // Multi-SIM detection
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

    // Permission launcher for offline telephony
    val telephonyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val callGranted = permissions[Manifest.permission.CALL_PHONE] == true
        val stateGranted = permissions[Manifest.permission.READ_PHONE_STATE] == true
        val smsGranted = permissions[Manifest.permission.RECEIVE_SMS] == true
        if (callGranted && stateGranted && smsGranted) {
            Toast.makeText(context, "Telephony permissions enabled for offline payments", Toast.LENGTH_SHORT).show()
        }
    }

    // Read contacts permission launcher
    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPermission = granted
    }

    // Query device contacts asynchronously
    LaunchedEffect(hasContactsPermission) {
        if (hasContactsPermission) {
            isLoadingContacts = true
            withContext(Dispatchers.IO) {
                val list = mutableListOf<PhoneContactItem>()
                val seenNumbers = mutableSetOf<String>()
                try {
                    val cursor = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                        ),
                        null,
                        null,
                        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                    )
                    cursor?.use { c ->
                        val nameCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        while (c.moveToNext()) {
                            val name = if (nameCol != -1) c.getString(nameCol) ?: "" else ""
                            val num = if (numCol != -1) c.getString(numCol) ?: "" else ""
                            val normalized = PhoneNumberUtils.normalize(num)
                            if (normalized.length == 10 && !seenNumbers.contains(normalized)) {
                                seenNumbers.add(normalized)
                                list.add(PhoneContactItem(name = name.ifBlank { "Contact" }, rawNumber = num, normalizedNumber = normalized))
                            }
                        }
                    }
                } catch (_: Exception) {}
                contactsList = list
                isLoadingContacts = false
            }
        }
    }

    fun hasTelephonyPermissions(): Boolean {
        val call = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val state = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val sms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        return call && state && sms
    }

    fun launchOnlineUpiPayment(phone: String, name: String, amt: Double) {
        val details = UpiPaymentDetails(
            payeeVpa = "$phone@upi",
            payeeName = name.ifBlank { "Mobile $phone" },
            amount = if (amt > 0) amt else null,
            transactionNote = transferNote.ifBlank { "Payment via UPI-Easy" }
        )
        val uri = UpiUriHelper.buildUri(details)
        val intent = PaymentLauncher.createPaymentIntent(uri)
        try {
            context.startActivity(intent)
            onPaymentInitiated()
            onDismiss()
        } catch (_e: Exception) {
            Toast.makeText(context, "No UPI application found on device", Toast.LENGTH_LONG).show()
        }
    }

    fun executeOfflinePay(phone: String, name: String, amt: Long) {
        if (!hasTelephonyPermissions()) {
            telephonyPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.RECEIVE_SMS
                )
            )
            return
        }

        if (amt <= 0L) {
            Toast.makeText(context, "Please enter an amount to transfer", Toast.LENGTH_SHORT).show()
            return
        }

        if (amt > 4999L) {
            Toast.makeText(context, "Offline payment limit is ₹4,999 per transaction", Toast.LENGTH_LONG).show()
            return
        }

        if (!Settings.canDrawOverlays(context)) {
            showOverlayPermissionDialog = true
            return
        }

        // Bind offline UPI ID if not already bound
        scope.launch {
            try {
                OfflineUpiBindingManager.ensureOfflineUpiBound(
                    context = context,
                    sessionManager = sessionManager,
                    database = database
                )
            } catch (_: Exception) {}
        }

        val sessionSuccess = sessionManagerInstance.startSession(
            phoneNumber = phone,
            amount = amt.toString(),
            payeeName = name.ifBlank { "Mobile $phone" }
        )

        if (sessionSuccess) {
            callManager.initiateUPI123Call(
                phoneNumber = phone,
                amount = amt.toString(),
                subscriptionId = selectedSubscriptionId
            )
            onPaymentInitiated()
            onDismiss()
        } else {
            Toast.makeText(context, "Another payment session is already active", Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        windowInsets = WindowInsets.navigationBars
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // STEP 1: Search contact or input custom contact
            if (selectedContact == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "To Mobile Number",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Search contact or enter custom mobile number",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search Bar Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search name, mobile number (+91)...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                val cleanedDigits = searchQuery.filter { it.isDigit() }.takeLast(10)
                val isCustomNumberTyped = cleanedDigits.length == 10

                // If user typed a 10-digit number: prominent direct payment card
                if (isCustomNumberTyped) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                                selectedContact = PhoneContactItem(
                                    name = "Mobile +91 $cleanedDigits",
                                    rawNumber = cleanedDigits,
                                    normalizedNumber = cleanedDigits
                                )
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pay to +91 $cleanedDigits",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Send via UPI App or Offline 123Pay",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // If contacts permission not yet granted, show permission request button
                if (!hasContactsPermission) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Contacts,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Access Phone Contacts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Find and pay friends directly from your contact list",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Allow Contacts Access")
                            }
                        }
                    }
                } else {
                    // Filter contacts list based on search query
                    val filteredContacts = remember(contactsList, searchQuery) {
                        if (searchQuery.isBlank()) contactsList.take(30)
                        else contactsList.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.normalizedNumber.contains(searchQuery)
                        }.take(40)
                    }

                    if (isLoadingContacts) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        }
                    } else if (filteredContacts.isEmpty() && !isCustomNumberTyped) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No matching contacts found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Phone Contacts",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredContacts, key = { it.normalizedNumber }) { contact ->
                                val avatarColor = remember(contact.name) {
                                    val idx = kotlin.math.abs(contact.name.hashCode()) % AvatarColors.size
                                    AvatarColors[idx]
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                            selectedContact = contact
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Avatar Circle
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(avatarColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = (contact.name.take(1)).uppercase(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = contact.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "+91 ${contact.normalizedNumber}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = "UPI",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // STEP 2: Transfer Details & Direct Action Buttons
                val contact = selectedContact!!

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedContact = null },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to contacts",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Transfer to Contact",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selected Contact Summary Header
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = contact.name.take(1).uppercase(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "+91 ${contact.normalizedNumber} • ${contact.normalizedNumber}@upi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Transfer Amount Input
                val isOverOfflineLimit = (transferAmount.toLongOrNull() ?: 0L) > 4999L

                OutlinedTextField(
                    value = transferAmount,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        transferAmount = digits
                    },
                    label = { Text("Transfer Amount") },
                    placeholder = { Text("0") },
                    leadingIcon = {
                        Text(
                            text = "₹",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 14.dp, end = 4.dp)
                        )
                    },
                    supportingText = {
                        if (isOverOfflineLimit) {
                            Text(
                                text = "Offline payments capped at ₹4,999 (UPI Apps have standard limits)",
                                color = FailedRed,
                                fontSize = 11.sp
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Amount Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(100, 200, 500, 1000, 2000).forEach { chipAmt ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    transferAmount = chipAmt.toString()
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                }
                        ) {
                            Text(
                                text = "+₹$chipAmt",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Optional Transfer Note
                OutlinedTextField(
                    value = transferNote,
                    onValueChange = { transferNote = it },
                    label = { Text("Add a note (Optional)") },
                    placeholder = { Text("e.g. Dinner, Rent, Grocery") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Multi-SIM selection if multiple SIMs available
                if (availableSims.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Payment SIM (For Offline Pay)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                color = if (isSimSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(
                                    width = if (isSimSelected) 1.5.dp else 1.dp,
                                    color = if (isSimSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
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
                                        tint = if (isSimSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SIM ${sim.simSlotIndex + 1} (${sim.carrierName ?: "Cellular"})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSimSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // DUAL DIRECT ACTION BUTTONS (UPI App vs Offline Pay)
                val amtDouble = transferAmount.toDoubleOrNull() ?: 0.0
                val amtLong = transferAmount.toLongOrNull() ?: 0L

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Button 1: Direct Pay with any installed UPI App
                    Button(
                        onClick = {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                            launchOnlineUpiPayment(contact.normalizedNumber, contact.name, amtDouble)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (amtDouble > 0) "Pay ₹${String.format("%,.0f", amtDouble)} with UPI App" else "Pay with UPI App",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    // Button 2: Direct Offline Pay (UPI 123Pay Call)
                    Button(
                        onClick = {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                            executeOfflinePay(contact.normalizedNumber, contact.name, amtLong)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Default.Call, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (amtLong > 0) "Offline Pay ₹$amtLong (No Internet)" else "Offline Pay (No Internet)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    // Overlay Permission Explanation Dialog
    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            title = { Text("Allow Screen Guide", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "UPI-Easy displays a payment guide on your screen during the call so you can view payee details while you enter your UPI PIN.\n\nPlease enable 'Allow display over other apps' in Settings."
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
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPermissionDialog = false }) {
                    Text("Not Now")
                }
            }
        )
    }
}
