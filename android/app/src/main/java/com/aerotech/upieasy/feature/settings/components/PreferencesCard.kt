package com.aerotech.upieasy.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerotech.upieasy.ui.theme.*

@Composable
fun PreferencesCard(
    soundNotifications: Boolean,
    highValueAlert: Boolean,
    hapticFeedback: Boolean,
    biometricLock: Boolean,
    onSoundChange: (Boolean) -> Unit,
    onHighValueChange: (Boolean) -> Unit,
    onHapticChange: (Boolean) -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsBentoCard(modifier = modifier) {
        // Section: Audio & Payment Alerts
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Audio & Payment Alerts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        SettingToggleRow(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            title = "Voice Payment Alerts",
            subtitle = "Announces incoming payments via TTS soundbox",
            checked = soundNotifications,
            onCheckedChange = onSoundChange,
            iconTint = BrandPrimary,
            iconBg = PastelIndigoBg
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        SettingToggleRow(
            icon = Icons.Default.NotificationsActive,
            title = "High-Value Transaction Alerts",
            subtitle = "Prominent popups for payments above ₹5,000",
            checked = highValueAlert,
            onCheckedChange = onHighValueChange,
            iconTint = SuccessGreen,
            iconBg = PastelEmeraldBg
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        SettingToggleRow(
            icon = Icons.Default.Vibration,
            title = "Touch & Haptic Feedback",
            subtitle = "Tactile response on button clicks and tab switching",
            checked = hapticFeedback,
            onCheckedChange = onHapticChange,
            iconTint = BrandPrimary,
            iconBg = PastelIndigoBg
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // Section: Security & Privacy
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Security & Biometrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        SettingToggleRow(
            icon = Icons.Default.Fingerprint,
            title = "Biometric App Lock",
            subtitle = "Require fingerprint or face scan on app launch",
            checked = biometricLock,
            onCheckedChange = onBiometricChange,
            iconTint = BrandPrimary,
            iconBg = PastelIndigoBg
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Security Status Pill
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(SuccessGreenBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "Hardware-Backed Keystore Active",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Encrypted credential vault & biometric Scan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
