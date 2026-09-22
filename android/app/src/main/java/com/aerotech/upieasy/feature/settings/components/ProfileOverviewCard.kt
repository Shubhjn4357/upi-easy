package com.aerotech.upieasy.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerotech.upieasy.ui.theme.*

@Composable
fun ProfileOverviewCard(
    userName: String?,
    userEmail: String?,
    userRole: String?,
    currentOrgName: String?,
    currentOrgLegalName: String?,
    currentOrgCategory: String?,
    currentOrgPan: String?,
    currentOrgGstin: String?,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsBentoCard(modifier = modifier) {
        // User Identity Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PastelIndigoBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (userName?.take(1) ?: userEmail?.take(1) ?: "M").uppercase(),
                    color = BrandPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userName ?: "Verified Merchant",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = userEmail ?: "Google Account Linked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SuccessGreenBg
            ) {
                Text(
                    text = userRole ?: "OWNER",
                    color = SuccessGreen,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        val canEdit = userRole?.uppercase() != "CASHIER"
        if (canEdit) {
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onEditClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Profile Information", fontWeight = FontWeight.SemiBold)
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 14.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // Business Profile Details
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(PastelEmeraldBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Storefront,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentOrgName ?: "Default Store",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    currentOrgCategory?.let { cat ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PastelIndigoBg
                        ) {
                            Text(
                                text = cat.replace("_", " "),
                                color = BrandPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                if (!currentOrgLegalName.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currentOrgLegalName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // PAN and GSTIN Tags
        if (!currentOrgPan.isNullOrBlank() || !currentOrgGstin.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!currentOrgPan.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Badge,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("PAN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                                Text(currentOrgPan, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
                if (!currentOrgGstin.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("GSTIN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                                Text(currentOrgGstin, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
