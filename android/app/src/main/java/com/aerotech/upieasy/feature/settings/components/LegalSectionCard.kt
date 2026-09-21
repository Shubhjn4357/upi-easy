package com.aerotech.upieasy.feature.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerotech.upieasy.ui.theme.*

@Composable
fun LegalSectionCard(
    onNavigateToLegal: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsBentoCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Gavel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Legal & Compliance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "NPCI / RBI Compliant",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        SettingNavigationRow(
            icon = Icons.Default.Policy,
            title = "Terms of Service & Privacy Policy",
            subtitle = "Google Play Financial Data Disclosures",
            onClick = onNavigateToLegal,
            iconTint = MaterialTheme.colorScheme.primary,
            iconBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        SettingNavigationRow(
            icon = Icons.Default.SupportAgent,
            title = "Grievance Officer & Security Reporting",
            subtitle = "Direct contact for regulatory and compliance queries",
            onClick = onNavigateToLegal,
            iconTint = MaterialTheme.colorScheme.secondary,
            iconBg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Software Architecture Disclosure
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "UPI-Easy v1.0.0 (Build neo) • Offline Cache",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                fontSize = 11.sp
            )
        }
    }
}
