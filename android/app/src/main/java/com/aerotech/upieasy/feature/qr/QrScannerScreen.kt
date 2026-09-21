package com.aerotech.upieasy.feature.qr

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.Lightbulb
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aerotech.upieasy.core.util.*
import com.aerotech.upieasy.ui.theme.SuccessGreen
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private val NeonLime = Color(0xFFB8FF00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var scannedDetails by remember { mutableStateOf<UpiPaymentDetails?>(null) }
    var scannedRawUri by remember { mutableStateOf<String?>(null) }
    var cameraPermissionGranted by remember { mutableStateOf(context.hasCameraPermission()) }
    var triggerPermission by remember { mutableStateOf(true) }
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControlRef by remember { mutableStateOf<CameraControl?>(null) }

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
                                    if (mediaImage != null && scannedDetails == null) {
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

                // 2. Neon Lime Glowing Rounded Corner Brackets (Exact match to Image 1)
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

            // Bottom Section: BHIM UPI badge, subtitle, and Brand Bar (Matches Image 1)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // BHIM | UPI Emblem Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
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
                            // Orange and Green accent indicator
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Box(modifier = Modifier.size(4.dp).background(Color(0xFFFF9933), CircleShape))
                                Box(modifier = Modifier.size(4.dp).background(Color(0xFF138808), CircleShape))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Scan any UPI QR code to pay",
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

            }
        }

        // Payment Confirmation Bottom Sheet
        scannedDetails?.let { details ->
            ModalBottomSheet(
                onDismissRequest = { scannedDetails = null; scannedRawUri = null },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Payee Verified",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = details.payeeName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = details.payeeVpa,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    details.amount?.let { amt ->
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "₹${String.format("%,.2f", amt)}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = SuccessGreen
                        )
                    }

                    details.transactionNote?.let { note ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Note: $note",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                            val uriToLaunch = scannedRawUri ?: UpiUriHelper.buildUri(details)
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
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Pay via Authorized UPI App", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
