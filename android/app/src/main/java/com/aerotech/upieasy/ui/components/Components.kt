package com.aerotech.upieasy.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerotech.upieasy.core.ui.UserAvatar
import com.aerotech.upieasy.core.util.HapticHelper
import com.aerotech.upieasy.core.util.PaymentAlert
import com.aerotech.upieasy.domain.model.Transaction
import com.aerotech.upieasy.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun StatusBadge(status: String) {
    val (bg, textColor) = when (status.uppercase()) {
        "SUCCESS" -> Pair(SuccessGreenBg, SuccessGreen)
        "PENDING" -> Pair(PendingAmberBg, PendingAmber)
        "FAILED" -> Pair(FailedRedBg, FailedRed)
        "UNKNOWN" -> Pair(Color(0xFFEDE7F6), Color(0xFF5E35B1))
        "OBSERVED" -> Pair(Color(0xFFEDE7F6), Color(0xFF5E35B1))
        else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = status.uppercase(),
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

/**
 * Observed Payment Badge: Displays OBSERVED status and originating UPI app (PhonePe / Google Pay)
 * per Section 38 & 88 of specification.
 */
@Composable
fun ObservedPaymentBadge(
    verificationStatus: String,
    eventSource: String? = null,
    modifier: Modifier = Modifier
) {
    if (verificationStatus.equals("OBSERVED", ignoreCase = true)) {
        val sourceLabel = when {
            eventSource?.contains("PHONEPE", ignoreCase = true) == true -> "PhonePe"
            eventSource?.contains("GPAY", ignoreCase = true) == true -> "Google Pay"
            eventSource?.contains("GOOGLE", ignoreCase = true) == true -> "Google Pay"
            else -> "Notification"
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFEDE7F6))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF673AB7))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "OBSERVED • $sourceLabel",
                color = Color(0xFF512DA8),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }
}


/**
 * Glassmorphic Card: Translucent frosted glass surface with subtle border glow and soft elevation.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = GlassSurfaceLight,
    borderColor: Color = GlassBorderLight,
    borderWidth: androidx.compose.ui.unit.Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(borderWidth, borderColor),
        shadowElevation = 3.dp
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

/**
 * Bento Grid Tile: Asymmetric soft card with interactive feedback and optional blurry glow accent.
 */
@Composable
fun BentoTile(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(22.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = GlassBorderLight,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Surface(
        modifier = modifier.then(clickModifier),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/**
 * Upieasy TopBar: Bold brand title, store subtitle, notification bell with badge, and profile avatar.
 */
@Composable
fun UpieasyTopBar(
    brandTitle: String = "UPIEasy",
    subtitle: String = "Merchant Dashboard",
    avatarInitial: String = "M",
    notificationCount: Int = 0,
    onNotificationClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onOrganizationClick: (() -> Unit)? = null,
    onMenuThemeClick: () -> Unit = {},
    onMenuLegalClick: () -> Unit = {},
    onMenuLogoutClick: () -> Unit = {},
    userName: String? = null,
    organizationName: String? = null,
    avatarUrl: String? = null
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = brandTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = (-0.5).sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (onOrganizationClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOrganizationClick() }
                        .padding(vertical = 2.dp)
                } else Modifier
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (onOrganizationClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (onOrganizationClick != null) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Switch Firm",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Notification icon button with unclipped badge (hidden if 0)
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                            onNotificationClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = "Notifications",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (notificationCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-2).dp, y = 2.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(FailedRed)
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }
            }

            // User avatar with tap dropdown menu
            val isDark = isSystemInDarkTheme()
            val avatarGradientColors = if (isDark) {
                listOf(Color(0xFF1E1B4B), Color(0xFF2E1065))
            } else {
                listOf(Color(0xFF1E3A8A), Color(0xFF6D28D9))
            }

            Box {
                UserAvatar(
                    avatarUrl = avatarUrl,
                    name = userName ?: avatarInitial,
                    size = 40.dp,
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (isDark) Color(0xFF818CF8).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.6f),
                            CircleShape
                        )
                        .clickable {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                            showMenu = true
                        }
                )

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .width(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            text = userName ?: "Merchant",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!organizationName.isNullOrBlank()) {
                            Text(
                                text = organizationName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    DropdownMenuItem(
                        text = { Text("Settings & Profile") },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                            onProfileClick()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Toggle Theme") },
                        leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                            onMenuThemeClick()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Legal & Terms") },
                        leadingIcon = { Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                            onMenuLegalClick()
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    DropdownMenuItem(
                        text = { Text("Sign Out", color = FailedRed, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = FailedRed, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                            onMenuLogoutClick()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Upieasy Hero Card: Electric gradient, live collection balance, and quick circular glass action buttons.
 */
@Composable
fun UpieasyHeroCard(
    balance: Double,
    transactionCount: Int,
    onShowQrClick: () -> Unit,
    onScanPayClick: () -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val heroGradientColors = if (isDark) {
        listOf(
            Color(0xFF0F172A), // Deep midnight slate
            Color(0xFF1E1B4B), // Rich dark indigo
            Color(0xFF2E1065)  // Deep royal violet
        )
    } else {
        listOf(
            Color(0xFFFFFFFF), // Crisp pure white
            Color(0xFFF7F8FE), // Soft frosted porcelain
            Color(0xFFEEF2FF)  // Soft ambient pastel indigo
        )
    }

    val cardBorder = if (isDark) {
        BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.35f))
    } else {
        BorderStroke(1.dp, GlassBorderLight)
    }

    val titleColor = if (isDark) Color.White.copy(alpha = 0.85f) else TextSecondary
    val balanceColor = if (isDark) Color.White else TextPrimary
    val walletBoxBg = if (isDark) Color.White.copy(alpha = 0.18f) else PastelIndigoBg
    val walletIconTint = if (isDark) Color.White else BrandPrimary

    val chipBg = if (isDark) Color.White.copy(alpha = 0.18f) else SuccessGreenBg
    val chipTextColor = if (isDark) Color.White else SuccessGreen

    val specularBubble1 = if (isDark) Color.White.copy(alpha = 0.08f) else BrandPrimary.copy(alpha = 0.04f)
    val specularBubble2 = if (isDark) Color.White.copy(alpha = 0.05f) else BrandSecondary.copy(alpha = 0.03f)

    val actionButtonBg = if (isDark) Color.White.copy(alpha = 0.18f) else PastelIndigoBg
    val actionButtonBorder = if (isDark) null else BorderStroke(1.dp, BrandPrimary.copy(alpha = 0.12f))
    val actionButtonIconTint = if (isDark) Color.White else BrandPrimary
    val actionButtonTextColor = if (isDark) Color.White else TextPrimary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 6.dp else 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = heroGradientColors,
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
                .padding(22.dp)
        ) {
            // Ambient specular glass overlays
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .offset(x = 180.dp, y = (-50).dp)
                    .clip(CircleShape)
                    .background(specularBubble1)
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .offset(x = (-30).dp, y = 70.dp)
                    .clip(CircleShape)
                    .background(specularBubble2)
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(walletBoxBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = walletIconTint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Today's Collection",
                            style = MaterialTheme.typography.bodyMedium,
                            color = titleColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = chipBg
                    ) {
                        Text(
                            text = "$transactionCount Txns",
                            color = chipTextColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "₹${String.format("%,.2f", balance)}",
                    style = MaterialTheme.typography.headlineLarge,
                    color = balanceColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 36.sp,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Circular Glass Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroCircularButton(
                        icon = Icons.Default.QrCode,
                        label = "Show QR",
                        onClick = onShowQrClick,
                        backgroundColor = actionButtonBg,
                        border = actionButtonBorder,
                        iconTint = actionButtonIconTint,
                        textColor = actionButtonTextColor
                    )
                    HeroCircularButton(
                        icon = Icons.Default.QrCodeScanner,
                        label = "Scan Pay",
                        onClick = onScanPayClick,
                        backgroundColor = actionButtonBg,
                        border = actionButtonBorder,
                        iconTint = actionButtonIconTint,
                        textColor = actionButtonTextColor
                    )
                    HeroCircularButton(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        label = "Ledger",
                        onClick = onHistoryClick,
                        backgroundColor = actionButtonBg,
                        border = actionButtonBorder,
                        iconTint = actionButtonIconTint,
                        textColor = actionButtonTextColor
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCircularButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    backgroundColor: Color = Color.White.copy(alpha = 0.22f),
    border: BorderStroke? = null,
    iconTint: Color = Color.White,
    textColor: Color = Color.White
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable {
            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
            onClick()
        }
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .then(if (border != null) Modifier.border(border, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp
        )
    }
}

/**
 * Quick Action Squircle: Pastel rounded tile with icon and text below.
 */
@Composable
fun UpieasyQuickActionButton(
    icon: ImageVector,
    label: String,
    backgroundColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Upieasy Stat Card: Used for Pending, Failed, Active UPI metrics.
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconTint: Color = BrandAccent,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .heightIn(min = 108.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        border = BorderStroke(1.dp, GlassBorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Filter Chip (Square Squircle style from Image 2):
 * Used in Transactions / Ledger filtering.
 */
@Composable
fun UpieasySquircleFilter(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surface
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isSelected) 2.dp else 0.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Upieasy Pill Button: Full-width modern pill action button.
 */
@Composable
fun UpieasyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = SuccessGreen,
    contentColor: Color = Color.White,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        enabled = enabled
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * TransactionRow: Displays transaction info with clean, adaptable Material 3 layout.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    transaction: Transaction,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    val isInflow = transaction.direction == "RECEIVED"
    val formattedDate = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        .format(Date(transaction.occurredAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.5.dp),
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else GlassBorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onCheckedChange
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                // Direction Indicator Circle
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isInflow) SuccessGreenBg else PastelPink),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isInflow) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (isInflow) SuccessGreen else FailedRed,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isInflow) (transaction.payerName ?: transaction.payerVpa ?: "Direct UPI")
                    else transaction.payeeName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Ref: ${transaction.referenceNumber ?: transaction.id.takeLast(8)} • $formattedDate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isInflow) "+" else "-"}₹${String.format("%,.2f", transaction.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isInflow) SuccessGreen else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (transaction.verificationStatus.equals("OBSERVED", ignoreCase = true)) {
                    ObservedPaymentBadge(
                        verificationStatus = transaction.verificationStatus,
                        eventSource = transaction.eventSource
                    )
                } else {
                    StatusBadge(status = transaction.status)
                }
            }
        }
    }
}

/**
 * UpieasyNotificationsSheet: Bottom sheet displaying recent payment alerts, soundbox events, and system notifications.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpieasyNotificationsSheet(
    onDismiss: () -> Unit,
    alertHistory: List<PaymentAlert>,
    onClearAll: () -> Unit,
    onDeleteAlert: (PaymentAlert) -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Notifications & Alerts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Swipe left to dismiss individual alerts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (alertHistory.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (alertHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No new payment alerts",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Incoming UPI payment notifications and voice alerts will appear here in real time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
                ) {
                    items(alertHistory.size, key = { alertHistory[it].id }) { index ->
                        val alert = alertHistory[index]
                        val formattedDate = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                            .format(Date(alert.timestamp))

                        SwipeToDeleteContainer(
                            itemKey = alert.id,
                            onDelete = { onDeleteAlert(alert) }
                        ) {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                border = BorderStroke(1.dp, GlassBorderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(SuccessGreenBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Received from ${alert.payerName ?: "UPI Customer"}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${if (!alert.referenceNumber.isNullOrBlank()) "UTR: ${alert.referenceNumber} • " else ""}$formattedDate",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text(
                                        text = "+₹${String.format("%,.2f", alert.amount)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SuccessGreen
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

/**
 * SwipeToDeleteContainer: Reusable swipe-left to delete container with snapping,
 * fixed-point haptic feedback, and coordination with screen-level confirmation bottom drawers.
 */
@Composable
fun SwipeToDeleteContainer(
    itemKey: Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSwipedOpen: Boolean = false,
    onDeleteRequest: (() -> Unit)? = null,
    onDelete: () -> Unit = {},
    confirmTitle: String = "Confirm Deletion",
    confirmMessage: String = "Are you sure you want to delete this item? This action cannot be undone.",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var isDeleted by remember(itemKey) { mutableStateOf(false) }
    var offsetX by remember(itemKey) { mutableFloatStateOf(0f) }
    var hasHapticTriggered by remember(itemKey) { mutableStateOf(false) }
    val maxSwipe = with(LocalDensity.current) { (-100).dp.toPx() }
    val dismissThreshold = with(LocalDensity.current) { (-70).dp.toPx() }

    // Synchronize with external isSwipedOpen state:
    // If another item is opened or confirmation is dismissed, smoothly reset offset to 0f
    LaunchedEffect(isSwipedOpen) {
        if (!isSwipedOpen && offsetX != 0f) {
            offsetX = 0f
            hasHapticTriggered = false
        } else if (isSwipedOpen && offsetX == 0f) {
            offsetX = maxSwipe
        }
    }

    val animatedOffset by animateFloatAsState(
        targetValue = if (isDeleted) -1200f else offsetX,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "swipeOffset"
    )

    AnimatedVisibility(
        visible = !isDeleted,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically(
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(durationMillis = 200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
        ) {
            // Background Delete Action revealed on swipe left
            if (offsetX < 0) {
                val deleteFraction = (-offsetX / -dismissThreshold).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(end = 22.dp)
                        .clickable {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                            if (onDeleteRequest != null) {
                                onDeleteRequest()
                            } else {
                                onDelete()
                            }
                        },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.graphicsLayer {
                            scaleX = 0.82f + (deleteFraction * 0.28f)
                            scaleY = 0.82f + (deleteFraction * 0.28f)
                            alpha = deleteFraction
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Delete",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Foreground swipeable card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                    .pointerInput(itemKey, enabled) {
                        if (!enabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = {
                                hasHapticTriggered = false
                            },
                            onDragEnd = {
                                if (offsetX <= dismissThreshold) {
                                    // Snap to open delete action and trigger confirmation bottom drawer
                                    offsetX = maxSwipe
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.HEAVY)
                                    if (onDeleteRequest != null) {
                                        onDeleteRequest()
                                    } else {
                                        onDelete()
                                    }
                                } else {
                                    offsetX = 0f
                                }
                            },
                            onDragCancel = {
                                if (!isSwipedOpen) {
                                    offsetX = 0f
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val newOffset = (offsetX + dragAmount).coerceIn(maxSwipe * 1.3f, 0f)
                                if (newOffset <= dismissThreshold && !hasHapticTriggered) {
                                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                                    hasHapticTriggered = true
                                } else if (newOffset > dismissThreshold) {
                                    hasHapticTriggered = false
                                }
                                offsetX = newOffset
                            }
                        )
                    }
            ) {
                content()
            }
        }
    }
}

/**
 * UpieasyConfirmBottomDrawer: Reusable Material 3 ModalBottomSheet for confirmation prompts
 * (delete confirmation, sign out confirmation, etc.).
 * Renders in a top-level Android Window/Dialog layer so it floats above bottom navigation bars,
 * handles back gestures, and never scrolls with underlying LazyColumn items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpieasyConfirmBottomDrawer(
    visible: Boolean,
    title: String,
    message: String,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    isDestructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (visible) {
        val context = LocalContext.current
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() },
            windowInsets = WindowInsets.navigationBars
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp)
            ) {
                // Warning / Action Icon Circle
                Surface(
                    shape = CircleShape,
                    color = if (isDestructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isDestructive) Icons.Default.DeleteOutline else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(26.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(cancelText, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            HapticHelper.performHaptic(
                                context,
                                if (isDestructive) HapticHelper.FeedbackType.HEAVY else HapticHelper.FeedbackType.MEDIUM
                            )
                            onConfirm()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            contentColor = if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(confirmText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * UpieasyPullToRefreshContainer: Smooth, physics-based swipe-down to refresh container.
 */
@Composable
fun UpieasyPullToRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var pullOffset by remember { mutableFloatStateOf(0f) }
    var hasHapticFired by remember { mutableStateOf(false) }
    val pullTrigger = with(LocalDensity.current) { 70.dp.toPx() }
    val maxPull = with(LocalDensity.current) { 110.dp.toPx() }

    val animatedPullOffset by animateFloatAsState(
        targetValue = if (isRefreshing) pullTrigger else pullOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pullOffset"
    )

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && available.y < 0 && pullOffset > 0f) {
                    val consumed = available.y.coerceAtLeast(-pullOffset)
                    pullOffset += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.Drag && available.y > 0 && !isRefreshing) {
                    val pullFraction = 1f - (pullOffset / maxPull).coerceIn(0f, 1f)
                    val added = available.y * 0.45f * pullFraction
                    val newOffset = (pullOffset + added).coerceIn(0f, maxPull)
                    if (newOffset >= pullTrigger && !hasHapticFired) {
                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.MEDIUM)
                        hasHapticFired = true
                    }
                    pullOffset = newOffset
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullOffset >= pullTrigger && !isRefreshing) {
                    HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                    onRefresh()
                }
                pullOffset = 0f
                hasHapticFired = false
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = animatedPullOffset
                }
        ) {
            content()
        }

        if (animatedPullOffset > 4f || isRefreshing) {
            val progress = (animatedPullOffset / pullTrigger).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .graphicsLayer {
                        translationY = (animatedPullOffset * 0.7f) - 20.dp.toPx()
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Pull to refresh",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer {
                                        rotationZ = progress * 180f
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
