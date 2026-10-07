package com.aerotech.upieasy.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerotech.upieasy.core.util.HapticHelper

enum class TourHighlightShape {
    Rounded,
    Circle
}

data class TourGuideStep(
    val index: Int,
    val title: String,
    val description: String,
    val targetListItemIndex: Int,
    val shape: TourHighlightShape = TourHighlightShape.Rounded
)

/**
 * Modifier extension to register an element's screen bounds for the tour guide.
 */
fun Modifier.tourAnchor(
    stepIndex: Int,
    onPositioned: (Int, Rect) -> Unit
): Modifier = this.onGloballyPositioned { coordinates: LayoutCoordinates ->
    if (coordinates.isAttached) {
        onPositioned(stepIndex, coordinates.boundsInRoot())
    }
}

/**
 * Interactive full-screen tour guide overlay for Dashboard.
 * - Highlights the target component with a smooth cut-out and pulsing glow
 * - Allows user to tap anywhere on the screen, on the card, or on Next to proceed to the next component
 * - Fully resolves being stuck on the first component
 * - Auto-scrolls to the target component
 */
@Composable
fun DashboardTourGuideOverlay(
    visible: Boolean,
    currentStep: Int,
    steps: List<TourGuideStep>,
    boundsMap: Map<Int, Rect>,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible || steps.isEmpty()) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val safeStepIndex = currentStep.coerceIn(0, steps.size - 1)
    val step = steps[safeStepIndex]
    val isLastStep = safeStepIndex == steps.size - 1
    val isFirstStep = safeStepIndex == 0

    val rawTargetBounds = boundsMap[safeStepIndex]

    // Pulsing animation for the spotlight border
    val infiniteTransition = rememberInfiniteTransition(label = "pulseSpotlight")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Advance handler
    fun advanceTour() {
        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
        if (isLastStep) {
            HapticHelper.performHaptic(context, HapticHelper.FeedbackType.SUCCESS)
            onDismiss()
        } else {
            onNext()
        }
    }

    // Main overlay capturing taps across the whole screen to advance to next component
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tapping anywhere advances to the next component!
                advanceTour()
            }
    ) {
        // Spotlight Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hasValidBounds = rawTargetBounds != null &&
                    rawTargetBounds.width > 0f &&
                    rawTargetBounds.height > 0f &&
                    rawTargetBounds.top < screenHeightPx &&
                    rawTargetBounds.bottom > 0f

            val scrimColor = Color.Black.copy(alpha = 0.72f)

            if (hasValidBounds) {
                val targetBounds = rawTargetBounds!!
                val paddingPx = with(density) { 8.dp.toPx() }
                val cornerRadiusPx = with(density) { 18.dp.toPx() }

                val adjustedRect = Rect(
                    left = (targetBounds.left - paddingPx).coerceAtLeast(0f),
                    top = (targetBounds.top - paddingPx).coerceAtLeast(0f),
                    right = (targetBounds.right + paddingPx).coerceAtMost(screenWidthPx),
                    bottom = (targetBounds.bottom + paddingPx).coerceAtMost(screenHeightPx)
                )

                val path = Path().apply {
                    addRect(Rect(0f, 0f, size.width, size.height))
                    if (step.shape == TourHighlightShape.Circle) {
                        val radius = maxOf(adjustedRect.width, adjustedRect.height) / 2f
                        val center = adjustedRect.center
                        addOval(
                            Rect(
                                left = center.x - radius,
                                top = center.y - radius,
                                right = center.x + radius,
                                bottom = center.y + radius
                            )
                        )
                    } else {
                        addRoundRect(
                            RoundRect(
                                rect = adjustedRect,
                                radiusX = cornerRadiusPx,
                                radiusY = cornerRadiusPx
                            )
                        )
                    }
                    fillType = PathFillType.EvenOdd
                }

                drawPath(path = path, color = scrimColor)

                // Glowing outline around the spotlight target
                val borderStroke = Stroke(width = with(density) { 2.5.dp.toPx() })
                val glowColor = Color(0xFF6C63FF).copy(alpha = pulseAlpha)

                if (step.shape == TourHighlightShape.Circle) {
                    val radius = maxOf(adjustedRect.width, adjustedRect.height) / 2f
                    val center = adjustedRect.center
                    drawCircle(
                        color = glowColor,
                        radius = radius,
                        center = center,
                        style = borderStroke
                    )
                } else {
                    drawRoundRect(
                        color = glowColor,
                        topLeft = adjustedRect.topLeft,
                        size = adjustedRect.size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        style = borderStroke
                    )
                }
            } else {
                // If bounds are not ready yet, draw smooth scrim
                drawRect(color = scrimColor)
            }
        }

        // Floating Tooltip Card
        val targetCenterY = rawTargetBounds?.let { (it.top + it.bottom) / 2f } ?: (screenHeightPx / 2f)
        val isTargetInTopHalf = targetCenterY < (screenHeightPx * 0.48f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = if (isTargetInTopHalf) Alignment.BottomCenter else Alignment.TopCenter
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Tapping the card also advances to the next step
                        advanceTour()
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header Row: Step counter pill + Skip / Close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tour Tip ${safeStepIndex + 1} of ${steps.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // Close / Skip Button
                        TextButton(
                            onClick = {
                                HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                onDismiss()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Skip",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Title
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Message
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Bottom Navigation Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hint text
                        Text(
                            text = if (isLastStep) "Tap Done to finish" else "Tap anywhere to continue",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isFirstStep) {
                                OutlinedButton(
                                    onClick = {
                                        HapticHelper.performHaptic(context, HapticHelper.FeedbackType.LIGHT)
                                        onBack()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Back", fontSize = 13.sp)
                                }
                            }

                            Button(
                                onClick = { advanceTour() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                if (isLastStep) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Got It!", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                } else {
                                    Text("Next", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Next",
                                        modifier = Modifier.size(16.dp)
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
