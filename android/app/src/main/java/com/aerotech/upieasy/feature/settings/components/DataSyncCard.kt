package com.aerotech.upieasy.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerotech.upieasy.ui.theme.BrandPrimary
import com.aerotech.upieasy.ui.theme.PastelIndigoBg
import com.aerotech.upieasy.ui.theme.SuccessGreen

@Composable
fun DataSyncCard(
    modifier: Modifier = Modifier
) {
    SettingsBentoCard(modifier = modifier) {
        Text(
            text = "Data & Cloud Sync",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(PastelIndigoBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = BrandPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Offline Cache",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Automatic reconciliation when device reconnects",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Text(
                text = "Active",
                color = SuccessGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
