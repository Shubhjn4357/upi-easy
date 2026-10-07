package com.aerotech.upieasy.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerotech.upieasy.core.util.HapticHelper

@Composable
fun FirstTimeConsentBottomSheet(
    onAcknowledge: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var hasReachedBottom by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    // Non-dismissible on back press until acknowledged
    BackHandler(enabled = true) {
        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
    }

    // Hard Scroll Acceptance Rule: Detect when the user reaches the absolute bottom
    val isAtBottom by remember {
        derivedStateOf {
            scrollState.maxValue > 0 && (scrollState.value >= scrollState.maxValue - 28 || !scrollState.canScrollForward)
        }
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom && !hasReachedBottom) {
            hasReachedBottom = true
            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.SUCCESS)
        }
    }

    AnimatedVisibility(
        visible = isVisible && !isDismissing,
        enter = fadeIn(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(180))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 24.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Drawer Drag Handle Pill (Visual Indicator)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Box(modifier = Modifier.size(width = 38.dp, height = 4.dp))
                        }
                    }

                    // Header Bar
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Welcome to UPIEasy",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Operational Transparency & Consent",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = "Required",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Scrollable Consent Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Introduction Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Before you begin using UPIEasy for merchant payments, sound announcements, and offline transactions, please review how our system operates, what data is accessed, and our commitment to your privacy.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 21.sp
                            )
                        }
                    }

                    // Section 1: How App Works
                    ConsentSectionCard(
                        icon = Icons.Default.Payment,
                        title = "1. How UPIEasy Operates",
                        badge = "Operational Model",
                        items = listOf(
                            ConsentItem(
                                title = "Dual Payment Engines (Online & Offline)",
                                description = "UPIEasy facilitates both high-speed Online UPI app launches (Google Pay, PhonePe, Paytm, BHIM) and RBI / NPCI-authorized Offline UPI (UPI 123Pay) via secure bank IVR calling and *99# USSD telecommunication."
                            ),
                            ConsentItem(
                                title = "Direct Bank-to-Bank Transfers",
                                description = "All funds settle directly between verified bank accounts. UPIEasy operates purely as an on-device utility application and does NOT hold, intermediate, or escrow funds."
                            ),
                            ConsentItem(
                                title = "Automated Merchant Ledger",
                                description = "The app records cash receipts, offline payments, and instant audio notifications into an on-device accounting ledger to simplify daily shop and business reconciliations."
                            )
                        )
                    )

                    // Section 2: What Data It Records & Permissions
                    ConsentSectionCard(
                        icon = Icons.Default.ManageAccounts,
                        title = "2. Data We Access & Record",
                        badge = "Device Permissions",
                        items = listOf(
                            ConsentItem(
                                title = "Automated Bank SMS & Notification Alerts",
                                description = "We parse incoming bank SMS messages and notification badges exclusively to detect and announce genuine payment credits in real time. We strictly ignore and never store personal conversations, OTPs, or non-financial messages."
                            ),
                            ConsentItem(
                                title = "Contact Book (On-Device Pay Contact)",
                                description = "Queried strictly when you choose 'Pay Contact' to select phone numbers. Your contact book is processed locally on your phone and is NEVER uploaded, scraped, or transferred to remote servers."
                            ),
                            ConsentItem(
                                title = "Telephony & Dual-SIM Details",
                                description = "Detects SIM 1 and SIM 2 carriers so offline payments and UPI binding correctly route through the phone number registered with your banking institution."
                            ),
                            ConsentItem(
                                title = "Merchant Business Profile",
                                description = "Stores your authorized organization identifier, business legal name, and tax identifiers (PAN/GSTIN) locally to authenticate authorized multi-user staff and generate branded payment QR codes."
                            )
                        )
                    )

                    // Section 3: Privacy Terms & Zero PIN Storage
                    ConsentSectionCard(
                        icon = Icons.Default.Security,
                        title = "3. Security & Zero PIN Storage",
                        badge = "Strict Security",
                        items = listOf(
                            ConsentItem(
                                title = "Zero UPI PIN Interception / Storage",
                                description = "UPIEasy NEVER requests, intercepts, logs, or stores your 4-digit or 6-digit UPI MPIN or banking passwords. All PIN entry occurs strictly within official NPCI keyboard overlays or your bank's encrypted telecom sessions."
                            ),
                            ConsentItem(
                                title = "Hardware-Backed Local Encryption",
                                description = "Ledger databases and sensitive session tokens are protected on-device with Android Keystore cryptographic keys and AES-256 standard encryption."
                            ),
                            ConsentItem(
                                title = "No 3rd-Party Selling or Ad Tracking",
                                description = "Your financial transaction data belongs strictly to you. We do not sell, rent, or monetize your transactional records with advertisers or third-party brokers."
                            ),
                            ConsentItem(
                                title = "Full Data Control & Erasure",
                                description = "You retain complete authority to clear cached data, disconnect banks, export ledgers, or wipe your profile anytime from the Settings menu."
                            )
                        )
                    )

                    // Compliance Notice
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gavel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Regulatory Compliance",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Operated in conformity with the Reserve Bank of India (RBI) Master Directions for Digital Payments, NPCI UPI 123Pay guidelines, and the Digital Personal Data Protection (DPDP) Act.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    // End of document anchor
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "—— End of Disclosure Document ——",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Sticky Bottom Action Bar with Hard Scroll Rule
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Dynamic Scroll Prompt
                        AnimatedVisibility(
                            visible = !hasReachedBottom,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .padding(bottom = 12.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Scroll to bottom to read full consent",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.surface
                                )
                            }
                        }

                        // Acknowledge Button
                        Button(
                            onClick = {
                                if (hasReachedBottom && !isDismissing) {
                                    isDismissing = true
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.SUCCESS)
                                    onAcknowledge()
                                }
                            },
                            enabled = hasReachedBottom,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            AnimatedContent(targetState = hasReachedBottom, label = "button_state") { isEnabled ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isEnabled) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "I Acknowledge & Agree",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Read to bottom to unlock",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "By clicking I Acknowledge, you agree to UPIEasy's Terms of Use & Privacy Policy",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun FirstTimeConsentDialog(
    onAcknowledge: () -> Unit
) = FirstTimeConsentBottomSheet(onAcknowledge = onAcknowledge)

private data class ConsentItem(
    val title: String,
    val description: String
)

@Composable
private fun ConsentSectionCard(
    icon: ImageVector,
    title: String,
    badge: String,
    items: List<ConsentItem>
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { item ->
                    Column {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "• ",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                            Column {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
