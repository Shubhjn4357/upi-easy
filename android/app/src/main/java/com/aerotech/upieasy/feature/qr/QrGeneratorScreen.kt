package com.aerotech.upieasy.feature.qr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.horizontalScroll
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.core.util.QrCodeGenerator
import com.aerotech.upieasy.core.util.UpiPaymentDetails
import com.aerotech.upieasy.core.util.UpiUriHelper
import com.aerotech.upieasy.ui.components.PayouButton
import com.aerotech.upieasy.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrGeneratorScreen(
    sessionManager: SessionManager? = null,
    database: AppDatabase? = null,
    initialVpa: String? = null,
    initialPayeeName: String? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    val currentOrgId by (sessionManager?.currentOrgIdFlow?.collectAsState(initial = null) ?: remember { mutableStateOf(null) })
    val currentOrgName by (sessionManager?.currentOrgNameFlow?.collectAsState(initial = null) ?: remember { mutableStateOf(null) })
    val userName by (sessionManager?.userNameFlow?.collectAsState(initial = null) ?: remember { mutableStateOf(null) })

    val localAccounts by (database?.upiDao()?.getUpiAccountsFlow(currentOrgId ?: "")?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) })

    val defaultAccount = remember(localAccounts) {
        localAccounts.firstOrNull { it.isDefault } ?: localAccounts.firstOrNull()
    }

    var vpa by remember(initialVpa, defaultAccount) {
        mutableStateOf(initialVpa ?: defaultAccount?.vpa ?: "merchant@upi")
    }
    var payeeName by remember(initialPayeeName, defaultAccount, currentOrgName, userName) {
        mutableStateOf(
            initialPayeeName
                ?: defaultAccount?.payeeName
                ?: currentOrgName
                ?: userName
                ?: "Merchant Store"
        )
    }

    var isSetAmountEnabled by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var refNoText by remember { mutableStateOf("") }

    // QR Theme Frame Color Palette (From Image 1)
    val themeColors = listOf(
        Pair("Emerald", Color(0xFF00C07F)),
        Pair("RoyalBlue", Color(0xFF2563EB)),
        Pair("SunsetYellow", Color(0xFFF59E0B)),
        Pair("Purple", Color(0xFF8B5CF6)),
        Pair("Pink", Color(0xFFEC4899)),
        Pair("SlateDark", Color(0xFF0F172A))
    )
    var selectedThemeColor by remember { mutableStateOf(themeColors[0].second) }

    val upiUri = remember(vpa, payeeName, isSetAmountEnabled, amountText, descriptionText, refNoText) {
        val amount = if (isSetAmountEnabled) amountText.toDoubleOrNull() else null
        UpiUriHelper.buildUri(
            UpiPaymentDetails(
                payeeVpa = vpa,
                payeeName = payeeName,
                amount = amount,
                transactionNote = if (isSetAmountEnabled) descriptionText.ifBlank { null } else null,
                referenceId = if (isSetAmountEnabled) refNoText.ifBlank { null } else null
            )
        )
    }

    val qrBitmap = remember(upiUri) {
        QrCodeGenerator.generateQrBitmap(upiUri, size = 600)
    }

    val customCardBitmap = remember(upiUri, payeeName, vpa, isSetAmountEnabled, amountText, descriptionText, selectedThemeColor) {
        val amt = if (isSetAmountEnabled && amountText.isNotBlank()) amountText else null
        val desc = if (isSetAmountEnabled && descriptionText.isNotBlank()) descriptionText else null
        QrCodeGenerator.generateCustomQrCardBitmap(
            content = upiUri,
            payeeName = payeeName,
            payeeVpa = vpa,
            amount = amt,
            description = desc,
            themeColor = selectedThemeColor.toArgb()
        )
    }

    val quickAmounts = listOf(50, 100, 200, 500, 1000, 2000)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receive Payment", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val amtStr = if (isSetAmountEnabled && amountText.isNotBlank()) " (₹$amountText)" else ""
                            QrCodeGenerator.shareQr(
                                context = context,
                                bitmap = customCardBitmap ?: qrBitmap,
                                textMessage = "Pay $payeeName$amtStr via UPI:\n$upiUri",
                                title = "Share Payment Link & Custom QR"
                            )
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary)
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Main QR Card (Image 1 Style)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                    border = BorderStroke(1.dp, GlassBorderLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    // Payee Header Row (Avatar, Name, VPA, Copy Icon)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(selectedThemeColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (payeeName.take(1)).uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = selectedThemeColor
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = payeeName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = vpa,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy VPA",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("VPA", vpa))
                                            Toast.makeText(context, "UPI ID copied", Toast.LENGTH_SHORT).show()
                                        }
                                )
                            }
                        }
                    }

                    // Multiple Linked UPI Account Selector
                    if (localAccounts.size > 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            localAccounts.forEach { acc ->
                                val isSelected = acc.vpa == vpa
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        vpa = acc.vpa
                                        payeeName = acc.payeeName
                                    },
                                    label = { Text(acc.vpa, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = selectedThemeColor.copy(alpha = 0.15f),
                                        selectedLabelColor = selectedThemeColor
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // QR Code Frame with Theme-Colored Rounded Border (Image 1)
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .border(BorderStroke(5.dp, selectedThemeColor), RoundedCornerShape(26.dp))
                            .background(Color.White)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "NPCI UPI QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                            // Centered mini wallet badge (from Image 1)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(selectedThemeColor)
                                    .border(BorderStroke(2.dp, Color.White), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            CircularProgressIndicator(color = selectedThemeColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Bank Account Info Pill (Image 1)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Receive money in verified UPI account",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (isSetAmountEnabled && amountText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "₹${amountText}",
                            style = MaterialTheme.typography.headlineLarge,
                            color = SuccessGreen,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Color Theme Palette Dots (From Image 1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                themeColors.forEach { (_, color) ->
                    val isSelected = selectedThemeColor == color
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(if (isSelected) 36.dp else 28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { selectedThemeColor = color }
                            .then(
                                if (isSelected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.background), CircleShape)
                                else Modifier
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Set Amount Toggle Switch (From Image 1)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Set Amount",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Lock a fixed amount & description on QR",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isSetAmountEnabled,
                        onCheckedChange = { isSetAmountEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SuccessGreen
                        )
                    )
                }
            }

            // Expandable Inputs when Set Amount is active
            AnimatedVisibility(visible = isSetAmountEnabled) {
                val focusManager = LocalFocusManager.current
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { char -> char.isDigit() || char == '.' } },
                        label = { Text("Custom Amount") },
                        placeholder = { Text("0.00") },
                        prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        )
                    )

                    // Quick Amounts Row
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(quickAmounts) { amt ->
                            SuggestionChip(
                                onClick = {
                                    val current = amountText.toDoubleOrNull() ?: 0.0
                                    amountText = String.format("%.0f", current + amt)
                                },
                                label = { Text("+₹$amt", fontWeight = FontWeight.SemiBold) },
                                shape = RoundedCornerShape(18.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = descriptionText,
                        onValueChange = { descriptionText = it },
                        label = { Text("Description / Item Note (Optional)") },
                        placeholder = { Text("e.g. Table 4 Order, Groceries") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        )
                    )

                    OutlinedTextField(
                        value = refNoText,
                        onValueChange = { refNoText = it },
                        label = { Text("Bill / Order Reference No. (Optional)") },
                        placeholder = { Text("e.g. INV-9284") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Request Payment Button (Image 1 Style)
            PayouButton(
                text = "Request Payment",
                icon = Icons.Default.Share,
                onClick = {
                    val amtStr = if (isSetAmountEnabled && amountText.isNotBlank()) " (₹$amountText)" else ""
                    val noteStr = if (isSetAmountEnabled && descriptionText.isNotBlank()) " - $descriptionText" else ""
                    QrCodeGenerator.shareQr(
                        context = context,
                        bitmap = customCardBitmap ?: qrBitmap,
                        textMessage = "Pay $payeeName$amtStr$noteStr via UPI:\n$upiUri",
                        title = "Share Payment Request & Custom QR"
                    )
                },
                containerColor = SuccessGreen
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
}
