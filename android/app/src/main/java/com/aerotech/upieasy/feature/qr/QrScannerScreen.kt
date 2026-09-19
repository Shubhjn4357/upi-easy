package com.aerotech.upieasy.feature.qr

import android.content.Intent
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.aerotech.upieasy.core.util.PaymentLauncher
import com.aerotech.upieasy.core.util.UpiPaymentDetails
import com.aerotech.upieasy.core.util.UpiUriHelper
import com.aerotech.upieasy.ui.theme.*
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var scannedDetails by remember { mutableStateOf<UpiPaymentDetails?>(null) }
    var scannedRawUri by remember { mutableStateOf<String?>(null) }

    // Infinite transition for the animated laser scanning line
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan UPI QR Code", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Camera Preview
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
                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Dynamic Reticle Overlay with animated scanning laser
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                val boxSize = 270.dp
                Box(
                    modifier = Modifier
                        .size(boxSize)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                ) {
                    // Animated laser line
                    val laserColor = MaterialTheme.colorScheme.primary
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val currentY = size.height * laserProgress
                        // Laser line
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    laserColor.copy(alpha = 0.8f),
                                    Color.White,
                                    laserColor.copy(alpha = 0.8f),
                                    Color.Transparent
                                )
                            ),
                            start = Offset(0f, currentY),
                            end = Offset(size.width, currentY),
                            strokeWidth = 4.dp.toPx()
                        )

                        // Laser subtle glow gradient
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    laserColor.copy(alpha = 0.25f),
                                    Color.Transparent
                                ),
                                startY = currentY,
                                endY = (currentY - 40.dp.toPx()).coerceAtLeast(0f)
                            ),
                            topLeft = Offset(0f, (currentY - 40.dp.toPx()).coerceAtLeast(0f)),
                            size = androidx.compose.ui.geometry.Size(size.width, 40.dp.toPx())
                        )
                    }

                    // Corner brackets
                    val cornerLength = 28.dp
                    val strokeW = 4.dp
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cLen = cornerLength.toPx()
                        val sw = strokeW.toPx()
                        val col = laserColor

                        // Top-Left
                        drawLine(col, Offset(0f, 0f), Offset(cLen, 0f), sw)
                        drawLine(col, Offset(0f, 0f), Offset(0f, cLen), sw)

                        // Top-Right
                        drawLine(col, Offset(size.width, 0f), Offset(size.width - cLen, 0f), sw)
                        drawLine(col, Offset(size.width, 0f), Offset(size.width, cLen), sw)

                        // Bottom-Left
                        drawLine(col, Offset(0f, size.height), Offset(cLen, size.height), sw)
                        drawLine(col, Offset(0f, size.height), Offset(0f, size.height - cLen), sw)

                        // Bottom-Right
                        drawLine(col, Offset(size.width, size.height), Offset(size.width - cLen, size.height), sw)
                        drawLine(col, Offset(size.width, size.height), Offset(size.width, size.height - cLen), sw)
                    }
                }
            }

            // Bottom guide text
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Align QR code inside the frame to scan",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
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
