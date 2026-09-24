package com.aerotech.upieasy.core.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-performance smooth shimmer animation modifier for Material 3.
 */
@Composable
fun Modifier.shimmerEffect(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnim - 300f, y = translateAnim - 300f),
        end = Offset(x = translateAnim, y = translateAnim)
    )

    return this.background(brush)
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .shimmerEffect()
    )
}

@Composable
fun SkeletonCircle(
    size: Dp = 44.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .shimmerEffect()
    )
}

// -------------------------------------------------------------
// SKELETON SCREENS MATCHING EXACT PAGE LAYOUTS
// -------------------------------------------------------------

@Composable
fun DashboardSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Balance Card Skeleton
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth().height(160.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                SkeletonBox(Modifier.width(130.dp).height(18.dp))
                SkeletonBox(Modifier.width(220.dp).height(38.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkeletonBox(Modifier.width(90.dp).height(24.dp))
                    SkeletonBox(Modifier.width(90.dp).height(24.dp))
                }
            }
        }

        // Quick Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                SkeletonBox(Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp))
            }
        }

        // Bento 2x2 Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBox(Modifier.weight(1f).height(105.dp), shape = RoundedCornerShape(18.dp))
            SkeletonBox(Modifier.weight(1f).height(105.dp), shape = RoundedCornerShape(18.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBox(Modifier.weight(1f).height(105.dp), shape = RoundedCornerShape(18.dp))
            SkeletonBox(Modifier.weight(1f).height(105.dp), shape = RoundedCornerShape(18.dp))
        }

        // Recent Transactions Section Header
        SkeletonBox(Modifier.width(160.dp).height(22.dp))

        // Transaction items
        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkeletonCircle(size = 40.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBox(Modifier.width(140.dp).height(16.dp))
                        SkeletonBox(Modifier.width(90.dp).height(12.dp))
                    }
                }
                SkeletonBox(Modifier.width(65.dp).height(20.dp))
            }
        }
    }
}

@Composable
fun UpiScreenSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Overview Card
        SkeletonBox(Modifier.fillMaxWidth().height(130.dp), shape = RoundedCornerShape(22.dp))

        // Section Title
        SkeletonBox(Modifier.width(140.dp).height(20.dp))

        // UPI Cards
        repeat(3) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().height(110.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SkeletonCircle(size = 40.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SkeletonBox(Modifier.width(130.dp).height(18.dp))
                                SkeletonBox(Modifier.width(180.dp).height(14.dp))
                            }
                        }
                        SkeletonBox(Modifier.width(55.dp).height(24.dp), shape = RoundedCornerShape(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonBox(Modifier.width(80.dp).height(26.dp), shape = RoundedCornerShape(8.dp))
                        SkeletonBox(Modifier.width(80.dp).height(26.dp), shape = RoundedCornerShape(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun StaffScreenSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Info / Invite Banner
        SkeletonBox(Modifier.fillMaxWidth().height(70.dp), shape = RoundedCornerShape(16.dp))

        // Staff Tabs
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBox(Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp))
            SkeletonBox(Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp))
        }

        // Staff List Cards
        repeat(4) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().height(88.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SkeletonCircle(size = 44.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SkeletonBox(Modifier.width(120.dp).height(18.dp))
                                SkeletonBox(Modifier.width(50.dp).height(16.dp), shape = RoundedCornerShape(6.dp))
                            }
                            SkeletonBox(Modifier.width(100.dp).height(13.dp))
                        }
                    }
                    SkeletonBox(Modifier.size(32.dp), shape = CircleShape)
                }
            }
        }
    }
}

@Composable
fun RolesPermissionsSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SkeletonBox(Modifier.fillMaxWidth().height(65.dp), shape = RoundedCornerShape(16.dp))

        repeat(4) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().height(140.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SkeletonCircle(size = 40.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                SkeletonBox(Modifier.width(100.dp).height(18.dp))
                                SkeletonBox(Modifier.width(160.dp).height(12.dp))
                            }
                        }
                        SkeletonBox(Modifier.size(30.dp), shape = CircleShape)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) {
                            SkeletonBox(Modifier.width(70.dp).height(24.dp), shape = RoundedCornerShape(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BankAccountsSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SkeletonBox(Modifier.fillMaxWidth().height(70.dp), shape = RoundedCornerShape(16.dp))

        repeat(2) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().height(180.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SkeletonCircle(size = 42.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                SkeletonBox(Modifier.width(120.dp).height(18.dp))
                                SkeletonBox(Modifier.width(90.dp).height(12.dp))
                            }
                        }
                        SkeletonBox(Modifier.width(60.dp).height(24.dp), shape = RoundedCornerShape(8.dp))
                    }

                    SkeletonBox(Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SkeletonBox(Modifier.weight(1f).height(38.dp), shape = RoundedCornerShape(10.dp))
                        SkeletonBox(Modifier.weight(1f).height(38.dp), shape = RoundedCornerShape(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionsSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search & Filter Bar
        SkeletonBox(Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) {
                SkeletonBox(Modifier.width(75.dp).height(32.dp), shape = RoundedCornerShape(16.dp))
            }
        }

        Spacer(Modifier.height(4.dp))

        // Transaction Rows
        repeat(6) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().height(72.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SkeletonCircle(size = 40.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SkeletonBox(Modifier.width(130.dp).height(16.dp))
                            SkeletonBox(Modifier.width(90.dp).height(12.dp))
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SkeletonBox(Modifier.width(70.dp).height(18.dp))
                        SkeletonBox(Modifier.width(45.dp).height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun QrScreenSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        SkeletonBox(Modifier.width(180.dp).height(24.dp))
        SkeletonBox(Modifier.width(120.dp).height(14.dp))

        // QR Code Box
        SkeletonBox(Modifier.size(240.dp), shape = RoundedCornerShape(24.dp))

        // VPA selector
        SkeletonBox(Modifier.width(220.dp).height(44.dp), shape = RoundedCornerShape(14.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonBox(Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp))
            SkeletonBox(Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp))
        }
    }
}

@Composable
fun SettingsSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile Card Skeleton
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth().height(100.dp)
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SkeletonCircle(size = 52.dp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBox(Modifier.width(140.dp).height(20.dp))
                    SkeletonBox(Modifier.width(180.dp).height(14.dp))
                }
            }
        }

        // Setting Section Skeletons
        repeat(5) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SkeletonCircle(size = 36.dp)
                        SkeletonBox(Modifier.width(130.dp).height(16.dp))
                    }
                    SkeletonBox(Modifier.size(18.dp), shape = CircleShape)
                }
            }
        }
    }
}
