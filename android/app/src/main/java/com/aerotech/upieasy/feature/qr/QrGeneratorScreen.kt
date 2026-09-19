package com.aerotech.upieasy.feature.qr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
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
import com.aerotech.upieasy.core.util.QrCodeGenerator
import com.aerotech.upieasy.core.util.UpiPaymentDetails
import com.aerotech.upieasy.core.util.UpiUriHelper
import com.aerotech.upieasy.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrGeneratorScreen(
    initialVpa: String = "business@bank",
    initialPayeeName: String = "Merchant Store",
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    var vpa by remember { mutableStateOf(initialVpa) }
    var payeeName by remember { mutableStateOf(initialPayeeName) }
    var amountText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var refNoText by remember { mutableStateOf("") }

    val upiUri = remember(vpa, payeeName, amountText, descriptionText, refNoText) {
        val amount = amountText.toDoubleOrNull()
        UpiUriHelper.buildUri(
            UpiPaymentDetails(
                payeeVpa = vpa,
                payeeName = payeeName,
                amount = amount,
                transactionNote = descriptionText.ifBlank { null },
                referenceId = refNoText.ifBlank { null }
            )
        )
    }

    val qrBitmap = remember(upiUri) {
        QrCodeGenerator.generateQrBitmap(upiUri, size = 600)
    }

    val quickAmounts = listOf(50, 100, 200, 500, 1000, 2000)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dynamic UPI QR Generator", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Real High-Resolution Dynamic QR Display Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = payeeName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = vpa,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Real Dynamic Rendered QR Code
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Dynamic NPCI UPI QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (amountText.isNotBlank()) {
                        Text(
                            text = "₹${amountText}",
                            style = MaterialTheme.typography.headlineLarge,
                            color = SuccessGreen,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (descriptionText.isNotBlank()) {
                        Text(
                            text = "Note: $descriptionText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "SCAN & PAY VIA ANY UPI APP",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Custom Amount Input
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { char -> char.isDigit() || char == '.' } },
                label = { Text("Custom Amount") },
                placeholder = { Text("0.00") },
                prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                trailingIcon = {
                    if (amountText.isNotEmpty()) {
                        IconButton(onClick = { amountText = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Amount")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Amount Suggestion Chips
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

            Spacer(modifier = Modifier.height(14.dp))

            // Description / Note Input
            OutlinedTextField(
                value = descriptionText,
                onValueChange = { descriptionText = it },
                label = { Text("Description / Item Note (Optional)") },
                placeholder = { Text("e.g. Table 4 Order, Groceries, Billing") },
                leadingIcon = {
                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (descriptionText.isNotEmpty()) {
                        IconButton(onClick = { descriptionText = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Note")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Reference No / Bill No Input
            OutlinedTextField(
                value = refNoText,
                onValueChange = { refNoText = it },
                label = { Text("Bill / Order Reference No. (Optional)") },
                placeholder = { Text("e.g. INV-2026-001") },
                leadingIcon = {
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (refNoText.isNotEmpty()) {
                        IconButton(onClick = { refNoText = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Ref")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Action Buttons: Copy Link & Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("UPI Payment URI", upiUri)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "UPI payment link copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Link", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            val amtStr = if (amountText.isNotBlank()) " (₹$amountText)" else ""
                            val noteStr = if (descriptionText.isNotBlank()) " - $descriptionText" else ""
                            putExtra(Intent.EXTRA_TEXT, "Pay $payeeName$amtStr$noteStr via UPI:\n$upiUri")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share UPI QR Payment Link"))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share QR", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
