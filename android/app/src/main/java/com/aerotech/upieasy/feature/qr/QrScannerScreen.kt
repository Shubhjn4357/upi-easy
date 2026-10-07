package com.aerotech.upieasy.feature.qr

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
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aerotech.upieasy.core.util.*
import com.aerotech.upieasy.feature.offline.core.CallManager
import com.aerotech.upieasy.feature.offline.core.CurrencyFormat
import com.aerotech.upieasy.feature.offline.core.OfflinePaymentSessionManager
import com.aerotech.upieasy.feature.offline.core.PhoneNumberUtils
import com.aerotech.upieasy.ui.theme.FailedRed
import com.aerotech.upieasy.ui.theme.SuccessGreen
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private val NeonLime = Color(0xFFB8FF00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTransactions: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current

    val sessionManagerInstance = remember { OfflinePaymentSessionManager.getInstance(context) }
    val callManager = remember { CallManager(context) }

    var scannedDetails by remember { mutableStateOf<UpiPaymentDetails?>(null) }
    var scannedRawUri by remember { mutableStateOf<String?>(null) }
    var cameraPermissionGranted by remember { mutableStateOf(context.hasCameraPermission()) }
    var triggerPermission by remember { mutableStateOf(true) }
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControlRef by remember { mutableStateOf<CameraControl?>(null) }

    // Manual Entry Bottom Sheet State
    var showManualEntrySheet by remember { mutableStateOf(false) }
    var manualMode by remember { mutableIntStateOf(0) } // 0: Mobile Number, 1: UPI ID (VPA)
    var manualMobileNumber by remember { mutableStateOf("") }
    var manualVpa by remember { mutableStateOf("") }
    var manualPayeeName by remember { mutableStateOf("") }
    var manualAmountText by remember { mutableStateOf("") }

    // Payment Selection State in Confirmation Sheet
    var selectedPaymentMode by remember { mutableStateOf("ONLINE") } // "ONLINE" or "OFFLINE"
    var offlineSubRail by remember { mutableIntStateOf(0) } // 0: 123Pay Call, 1: *99# USSD
    var customAmountText by remember { mutableStateOf("") }
    var selectedSubscriptionId by remember { mutableStateOf<Int?>(null) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }

    // Dual-SIM detection
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

    // Contact Picker for Manual Mobile Entry
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
                            manualPayeeName = name
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
                                            manualMobileNumber = normalized
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

    // Telephony permissions launcher for offline calls
    val offlinePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val callGranted = permissions[Manifest.permission.CALL_PHONE] == true
        val stateGranted = permissions[Manifest.permission.READ_PHONE_STATE] == true
        val smsGranted = permissions[Manifest.permission.RECEIVE_SMS] == true
        if (callGranted && stateGranted && smsGranted) {
            Toast.makeText(context, "Telephony permissions enabled for offline payments", Toast.LENGTH_SHORT).show()
        }
    }

    fun hasOfflinePermissions(): Boolean {
        val call = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val state = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val sms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        return call && state && sms
    }

    // Request camera permission on first entry
    CameraPermissionEffect(
        trigger = triggerPermission,
        onGranted = {
            cameraPermissionGranted = true
            triggerPermission = false
        },
        onDismissed = {
            triggerPermission = false
            if (!context.hasCameraPermission()) {
                onNavigateBack()
            }
        }
    )

    // Gallery Picker for QR Image Scanning
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputImage = InputImage.fromFilePath(context, uri)
                val scanner = BarcodeScanning.getClient()
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        var found = false
                        for (barcode in barcodes) {
                            val rawValue = barcode.rawValue ?: continue
                            val parsed = UpiUriHelper.parseUri(rawValue)
                            if (parsed != null) {
                                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.SUCCESS)
                                scannedDetails = parsed
                                scannedRawUri = rawValue
                                customAmountText = parsed.amount?.let { if (it > 0) it.toInt().toString() else "" } ?: ""
                                selectedPaymentMode = "ONLINE"
                                found = true
                                break
                            }
                        }
                        if (!found) {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.ERROR)
                            Toast.makeText(context, "No valid UPI QR found in selected image", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Failed to read image", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(context, "Error processing image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (!cameraPermissionGranted) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = NeonLime)
                Text(
                    "Awaiting camera permission…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }
        return
    }

    // Infinite transition for the animated laser scanning line
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Camera Preview View
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraExecutor = Executors.newSingleThreadExecutor()
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val barcodeScanner = BarcodeScanning.getClient()

                        @androidx.annotation.OptIn(ExperimentalGetImage::class)
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null && scannedDetails == null && !showManualEntrySheet) {
                                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                        barcodeScanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                for (barcode in barcodes) {
                                                    val rawValue = barcode.rawValue ?: continue
                                                    val parsed = UpiUriHelper.parseUri(rawValue)
                                                    if (parsed != null) {
                                                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.SUCCESS)
                                                        scannedDetails = parsed
                                                        scannedRawUri = rawValue
                                                        customAmountText = parsed.amount?.let { if (it > 0) it.toInt().toString() else "" } ?: ""
                                                        selectedPaymentMode = "ONLINE"
                                                        break
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                }
                            }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                            cameraControlRef = camera.cameraControl
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Neon Lime Rounded Viewfinder & Cutout Overlay
            val boxSize = 290.dp
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val rectSize = boxSize.toPx()
                val left = (w - rectSize) / 2f
                val top = (h - rectSize) / 2f - 40.dp.toPx()
                val cornerRadius = 32.dp.toPx()
                val armLength = 54.dp.toPx()
                val strokeWidth = 7.dp.toPx()

                // 1. Semi-transparent dark vignette mask around the cutout
                val path = Path().apply {
                    addRect(Rect(0f, 0f, w, h))
                    addRoundRect(
                        RoundRect(
                            rect = Rect(left, top, left + rectSize, top + rectSize),
                            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                        )
                    )
                    fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                }
                drawPath(path, color = Color.Black.copy(alpha = 0.58f))

                // 2. Neon Lime Glowing Rounded Corner Brackets
                val lime = NeonLime

                // Top-Left Corner
                val tlPath = Path().apply {
                    moveTo(left, top + armLength)
                    lineTo(left, top + cornerRadius)
                    quadraticBezierTo(left, top, left + cornerRadius, top)
                    lineTo(left + armLength, top)
                }
                drawPath(tlPath, lime, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

                // Top-Right Corner
                val trPath = Path().apply {
                    moveTo(left + rectSize - armLength, top)
                    lineTo(left + rectSize - cornerRadius, top)
                    quadraticBezierTo(left + rectSize, top, left + rectSize, top + cornerRadius)
                    lineTo(left + rectSize, top + armLength)
                }
                drawPath(trPath, lime, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

                // Bottom-Left Corner
                val blPath = Path().apply {
                    moveTo(left, top + rectSize - armLength)
                    lineTo(left, top + rectSize - cornerRadius)
                    quadraticBezierTo(left, top + rectSize, left + cornerRadius, top + rectSize)
                    lineTo(left + armLength, top + rectSize)
                }
                drawPath(blPath, lime, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

                // Bottom-Right Corner
                val brPath = Path().apply {
                    moveTo(left + rectSize - armLength, top + rectSize)
                    lineTo(left + rectSize - cornerRadius, top + rectSize)
                    quadraticBezierTo(left + rectSize, top + rectSize, left + rectSize, top + rectSize - cornerRadius)
                    lineTo(left + rectSize, top + rectSize - armLength)
                }
                drawPath(brPath, lime, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

                // 3. Smooth animated neon scanning laser line
                val currentY = top + (rectSize * laserProgress)
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            lime.copy(alpha = 0.85f),
                            Color.White,
                            lime.copy(alpha = 0.85f),
                            Color.Transparent
                        )
                    ),
                    start = Offset(left + 16.dp.toPx(), currentY),
                    end = Offset(left + rectSize - 16.dp.toPx(), currentY),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Top Bar: Back, Torch, Upload
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                        onNavigateBack()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Flash / Torch Toggle
                    IconButton(
                        onClick = {
                            isFlashOn = !isFlashOn
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                            cameraControlRef?.enableTorch(isFlashOn)
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isFlashOn) NeonLime.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(
                            if (isFlashOn) Icons.Default.Lightbulb else Icons.Outlined.Lightbulb,
                            contentDescription = "Flashlight",
                            tint = if (isFlashOn) NeonLime else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Upload / Pick Image from Gallery
                    IconButton(
                        onClick = {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = "Upload QR",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Bottom Section: Manual Entry Button, BHIM UPI badge, subtitle
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // "Or Enter Manually" Quick Pill Button
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.70f),
                    border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable {
                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                        showManualEntrySheet = true
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dialpad,
                            contentDescription = null,
                            tint = NeonLime,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Or Enter UPI ID / Mobile Manually",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // BHIM | UPI Emblem Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BHIM",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(14.dp)
                                .background(Color.White.copy(alpha = 0.4f))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "UPI",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Box(modifier = Modifier.size(4.dp).background(Color(0xFFFF9933), CircleShape))
                                Box(modifier = Modifier.size(4.dp).background(Color(0xFF138808), CircleShape))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Scan any UPI QR code or pay manually",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // =========================================================================
        // 1. MANUAL ENTRY MODAL BOTTOM SHEET
        // =========================================================================
        if (showManualEntrySheet) {
            ModalBottomSheet(
                onDismissRequest = { showManualEntrySheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Enter Payee Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Pay via mobile number or UPI ID",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Mode Segmented Pill (Mobile Number vs UPI ID)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (manualMode == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (manualMode == 0) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.SELECTION)
                                    manualMode = 0
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = if (manualMode == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Mobile Number",
                                    fontWeight = if (manualMode == 0) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (manualMode == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (manualMode == 1) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (manualMode == 1) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.SELECTION)
                                    manualMode = 1
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AlternateEmail,
                                    contentDescription = null,
                                    tint = if (manualMode == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "UPI ID / VPA",
                                    fontWeight = if (manualMode == 1) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (manualMode == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (manualMode == 0) {
                        // Recipient Phone Number
                        OutlinedTextField(
                            value = manualMobileNumber,
                            onValueChange = { input ->
                                val cleaned = input.filter { it.isDigit() }
                                if (cleaned.length <= 10) manualMobileNumber = cleaned
                            },
                            label = { Text("Recipient Mobile Number") },
                            placeholder = { Text("10-digit mobile number") },
                            leadingIcon = {
                                Text(
                                    text = "+91",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
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
                                            offlinePermissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Contacts,
                                        contentDescription = "Pick Contact",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Recipient UPI ID (VPA)
                        OutlinedTextField(
                            value = manualVpa,
                            onValueChange = { manualVpa = it.trim() },
                            label = { Text("Recipient UPI ID / VPA") },
                            placeholder = { Text("name@bank or 9876543210@upi") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AlternateEmail,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Optional Payee Name
                    OutlinedTextField(
                        value = manualPayeeName,
                        onValueChange = { manualPayeeName = it },
                        label = { Text("Payee Name (Optional)") },
                        placeholder = { Text("e.g. Ramesh Kirana") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Amount Field
                    OutlinedTextField(
                        value = manualAmountText,
                        onValueChange = { input ->
                            val cleaned = input.filter { it.isDigit() }
                            manualAmountText = cleaned
                        },
                        label = { Text("Transfer Amount (₹)") },
                        placeholder = { Text("e.g. 500") },
                        leadingIcon = {
                            Text(
                                text = "₹",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 14.dp, end = 4.dp)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Amount Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(100, 500, 1000, 2000).forEach { chipAmt ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        manualAmountText = chipAmt.toString()
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

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            val vpa = if (manualMode == 0) {
                                val norm = PhoneNumberUtils.normalize(manualMobileNumber)
                                if (norm.length != 10) {
                                    Toast.makeText(context, "Please enter a valid 10-digit mobile number", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                "$norm@upi"
                            } else {
                                val raw = manualVpa.trim()
                                if (raw.isBlank() || !raw.contains("@")) {
                                    Toast.makeText(context, "Please enter a valid UPI VPA (e.g. name@bank)", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                raw
                            }

                            val amt = manualAmountText.toDoubleOrNull()
                            val name = manualPayeeName.ifBlank {
                                if (manualMode == 0) "Mobile +91 $manualMobileNumber" else vpa
                            }

                            val details = UpiPaymentDetails(
                                payeeVpa = vpa,
                                payeeName = name,
                                amount = amt
                            )

                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.SUCCESS)
                            showManualEntrySheet = false
                            scannedDetails = details
                            scannedRawUri = UpiUriHelper.buildUri(details)
                            customAmountText = manualAmountText
                            selectedPaymentMode = "ONLINE"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = "Proceed to Payment",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // =========================================================================
        // 2. PAYMENT CONFIRMATION BOTTOM SHEET WITH CLEAN CARD OPTIONS
        // =========================================================================
        scannedDetails?.let { details ->
            val payeePhoneCandidate = remember(details.payeeVpa) {
                // If VPA is formatted as 9876543210@upi or starts with 10 digits
                val prefix = details.payeeVpa.substringBefore("@")
                if (prefix.length == 10 && prefix.all { it.isDigit() }) prefix else null
            }

            ModalBottomSheet(
                onDismissRequest = {
                    scannedDetails = null
                    scannedRawUri = null
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header: Payee Verified Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = SuccessGreen.copy(alpha = 0.15f),
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Verified Merchant / Payee",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }

                        IconButton(
                            onClick = {
                                scannedDetails = null
                                scannedRawUri = null
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Payee Overview Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = details.payeeName.ifBlank { "UPI Merchant" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (payeePhoneCandidate != null) Icons.Default.Phone else Icons.Default.AlternateEmail,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (payeePhoneCandidate != null) "+91 $payeePhoneCandidate" else details.payeeVpa,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Amount Section
                            val isPresetAmount = details.amount != null && details.amount > 0.0
                            Spacer(modifier = Modifier.height(12.dp))

                            if (isPresetAmount) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Text(
                                            text = "Transfer Amount",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "₹${CurrencyFormat.inr(details.amount!!)}",
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    details.transactionNote?.let { note ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        ) {
                                            Text(
                                                text = note,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Editable Amount input if QR doesn't specify an amount
                                OutlinedTextField(
                                    value = customAmountText,
                                    onValueChange = { input ->
                                        val cleaned = input.filter { it.isDigit() }
                                        customAmountText = cleaned
                                    },
                                    label = { Text("Enter Transfer Amount") },
                                    placeholder = { Text("0") },
                                    leadingIcon = {
                                        Text(
                                            text = "₹",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 14.dp, end = 4.dp)
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(100, 200, 500, 1000).forEach { chipAmt ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    customAmountText = chipAmt.toString()
                                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                                }
                                        ) {
                                            Text(
                                                text = "+₹$chipAmt",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val resolvedAmount = (if (details.amount != null && details.amount > 0.0) details.amount else customAmountText.toDoubleOrNull()) ?: 0.0

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Pay With",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // =================================================================
                    // DIRECT ACTION 1: PAY VIA ONLINE UPI APP
                    // =================================================================
                    Button(
                        onClick = {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                            val finalDetails = if (details.amount != null && details.amount > 0.0) {
                                details
                            } else {
                                details.copy(amount = resolvedAmount)
                            }

                            val uriToLaunch = scannedRawUri ?: UpiUriHelper.buildUri(finalDetails)
                            val intent = PaymentLauncher.createPaymentIntent(uriToLaunch)
                            try {
                                context.startActivity(intent)
                                scannedDetails = null
                                onNavigateBack()
                            } catch (e: Exception) {
                                Toast.makeText(context, "No UPI application found on device", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (resolvedAmount > 0) "Pay ₹${CurrencyFormat.inr(resolvedAmount)} via UPI App" else "Pay via UPI App",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // OR DIVIDER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = "OR PAY WITHOUT INTERNET",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // =================================================================
                    // DIRECT ACTION 2: OFFLINE PAY (UPI 123PAY / USSD)
                    // =================================================================
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = SuccessGreen.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(SuccessGreen.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Offline Pay (123Pay)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = SuccessGreen.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "NO INTERNET",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = SuccessGreen,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Works via telephone line or *99# USSD",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }

                            // Sub-rail selection: Voice Call vs *99# Code
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(3.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (offlineSubRail == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                                    shadowElevation = if (offlineSubRail == 0) 2.dp else 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.SELECTION)
                                            offlineSubRail = 0
                                        }
                                ) {
                                    Text(
                                        text = "Voice Call",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (offlineSubRail == 0) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        color = if (offlineSubRail == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (offlineSubRail == 1) MaterialTheme.colorScheme.surface else Color.Transparent,
                                    shadowElevation = if (offlineSubRail == 1) 2.dp else 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.SELECTION)
                                            offlineSubRail = 1
                                        }
                                ) {
                                    Text(
                                        text = "*99# Code",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (offlineSubRail == 1) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        color = if (offlineSubRail == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }

                            // Multi-SIM selection if available
                            if (availableSims.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    availableSims.forEach { sim ->
                                        val isSimSelected = selectedSubscriptionId == sim.subscriptionId
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSimSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
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
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.SimCard,
                                                    contentDescription = null,
                                                    tint = if (isSimSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "SIM ${sim.simSlotIndex + 1} (${sim.carrierName?.toString() ?: "Cellular"})",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSimSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSimSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Direct button to initiate offline payment
                            Button(
                                onClick = {
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                                    if (!hasOfflinePermissions()) {
                                        offlinePermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.CALL_PHONE,
                                                Manifest.permission.READ_PHONE_STATE,
                                                Manifest.permission.RECEIVE_SMS
                                            )
                                        )
                                        return@Button
                                    }

                                    val amtLong = resolvedAmount.toLong()
                                    if (amtLong <= 0L) {
                                        Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (amtLong > 4999L) {
                                        Toast.makeText(context, "UPI 123Pay limit is ₹4,999 per offline transaction", Toast.LENGTH_LONG).show()
                                        return@Button
                                    }

                                    if (offlineSubRail == 0) {
                                        // 123Pay IVR Call
                                        val phoneToUse = payeePhoneCandidate ?: run {
                                            val norm = PhoneNumberUtils.normalize(details.payeeVpa.substringBefore("@"))
                                            if (norm.length == 10) norm else null
                                        }

                                        if (phoneToUse == null) {
                                            Toast.makeText(
                                                context,
                                                "Payee UPI ID does not have a 10-digit mobile number. Please switch to *99# Code option.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            return@Button
                                        }

                                        if (!Settings.canDrawOverlays(context)) {
                                            showOverlayPermissionDialog = true
                                            return@Button
                                        }

                                        val sessionSuccess = sessionManagerInstance.startSession(
                                            phoneNumber = phoneToUse,
                                            amount = amtLong.toString(),
                                            payeeName = details.payeeName.ifBlank { "Mobile $phoneToUse" },
                                            payeeUpiId = details.payeeVpa
                                        )

                                        if (sessionSuccess) {
                                            callManager.initiateUPI123Call(
                                                phoneNumber = phoneToUse,
                                                amount = amtLong.toString(),
                                                subscriptionId = selectedSubscriptionId
                                            )
                                            scannedDetails = null
                                            onNavigateBack()
                                        } else {
                                            Toast.makeText(context, "Another payment session is already active", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        // *99# USSD
                                        val targetVpa = details.payeeVpa
                                        val sessionSuccess = sessionManagerInstance.startSession(
                                            phoneNumber = targetVpa,
                                            amount = amtLong.toString(),
                                            payeeName = details.payeeName.ifBlank { targetVpa },
                                            rail = "USSD",
                                            payeeUpiId = details.payeeVpa
                                        )

                                        if (sessionSuccess) {
                                            callManager.initiateUSSDCall(
                                                ussdCode = "*99*1*3#",
                                                subscriptionId = selectedSubscriptionId
                                            )
                                            scannedDetails = null
                                            onNavigateBack()
                                        } else {
                                            Toast.makeText(context, "Another payment session is already active", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (offlineSubRail == 0) Icons.Default.Call else Icons.Default.Dialpad,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (offlineSubRail == 0) {
                                            if (resolvedAmount > 0) "Offline Pay (₹${CurrencyFormat.inr(resolvedAmount)})" else "Offline Pay"
                                        } else {
                                            if (resolvedAmount > 0) "Dial *99# Code (₹${CurrencyFormat.inr(resolvedAmount)})" else "Dial *99# Code"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    // Overlay Permission Explanation Dialog
    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            title = {
                Text(
                    text = "Allow Screen Guide",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "UPI-Easy displays a payment guide on your screen during the call so you can view payee details while you enter your UPI PIN.\n\nPlease enable 'Allow display over other apps' in Settings."
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
