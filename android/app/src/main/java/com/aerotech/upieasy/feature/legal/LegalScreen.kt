package com.aerotech.upieasy.feature.legal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerotech.upieasy.ui.components.GlassCard
import com.aerotech.upieasy.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    onNavigateBack: () -> Unit,
    initialTab: Int = 0
) {
    val tabs = listOf(
        "About Role",
        "Privacy Policy",
        "Terms",
        "UPI Disclaimer",
        "Refunds",
        "Data Deletion",
        "Grievance"
    )
    var selectedTab by remember { mutableStateOf(initialTab) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Legal & Regulatory Disclosures", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Horizontal scrollable frosted tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.background,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) BrandPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    0 -> AboutRoleSection()
                    1 -> PrivacyPolicySection()
                    2 -> TermsSection()
                    3 -> UpiDisclaimerSection()
                    4 -> RefundPolicySection()
                    5 -> DataRetentionSection()
                    6 -> GrievanceSection()
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun AboutRoleSection() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // High-level Bento Overview
        GlassCard(
            backgroundColor = BrandPrimary.copy(alpha = 0.08f),
            borderColor = BrandPrimary.copy(alpha = 0.25f)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BrandPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("What UPI-Easy Is", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandPrimary)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "UPI-Easy is an independent merchant software application providing tools for managing authorized UPI identifiers, dynamic QR codes, transaction records, soundbox alerts, and staff permissions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Regulatory Boundary Card (CRITICAL)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, FailedRed.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = FailedRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Important Regulatory Clarification", fontWeight = FontWeight.Bold, color = FailedRed)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• UPI-Easy is NOT a bank, Payment System Operator (PSO), PSP Bank, Payment Aggregator, or wallet issuer.\n" +
                            "• UPI-Easy does NOT hold customer or merchant funds, nor does it process or authorize interbank settlements.\n" +
                            "• All payments occur directly between customer and merchant bank accounts via NPCI-authorized UPI applications.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Zero Banking Credentials Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SuccessGreenBg)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = SuccessGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Zero Banking Credentials Architecture", fontWeight = FontWeight.Bold, color = SuccessGreen)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "UPI-Easy will NEVER request, read, or store your UPI PIN, banking password, ATM PIN, CVV, or OTPs. Banking authentication happens strictly within bank apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PrivacyPolicySection() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Digital Personal Data Protection (DPDP) Compliance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Itemized Data Collection & Purpose (DPDP 2025 Rules)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                LegalItemRow("Mobile Number & Email", "Account creation, OTP login, and multi-device authentication.")
                LegalItemRow("Merchant Business Name", "Display on dynamic QR codes and merchant receipts.")
                LegalItemRow("Authorized UPI VPA", "Configuring QR codes and linking transactions (e.g. store@bank).")
                LegalItemRow("Transaction History", "Offline ledgering, sales summaries, and business bookkeeping.")
                LegalItemRow("Device ID & FCM Token", "Instant payment soundbox audio announcements & security logs.")
                LegalItemRow("Staff Role Assignment", "Role-based access control (Manager, Cashier, Accountant).")
                LegalItemRow("Camera (Optional)", "Scanning customer UPI QR codes when scanner is triggered.")
            }
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = FailedRedBg),
            border = BorderStroke(1.dp, FailedRed.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Information NEVER Collected or Processed", fontWeight = FontWeight.Bold, color = FailedRed)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "❌ UPI PIN\n❌ Net Banking Passwords\n❌ Debit/Credit Card CVV or PIN\n❌ Banking Transaction OTPs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun TermsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Terms of Service Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LegalItemRow("1. Eligibility", "Users must be at least 18 years old and authorized to conduct commercial business under Indian laws.")
                LegalItemRow("2. Merchant Representation", "You represent that any UPI VPA entered is legitimately assigned to your business. Deceptive VPAs are strictly prohibited.")
                LegalItemRow("3. Staff Control", "Organization owners control staff permissions. Actions performed by staff members are recorded in organizational audit logs.")
                LegalItemRow("4. Non-Custodial Limitation", "UPI-Easy does not hold funds or guarantee settlement. Merchants must verify critical settlements via bank account statements.")
                LegalItemRow("5. Governing Law", "Subject to the jurisdiction of the courts of Bengaluru, Karnataka, India.")
            }
        }
    }
}

@Composable
private fun UpiDisclaimerSection() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("NPCI & UPI Ecosystem Regulatory Disclaimer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "UPI-Easy is an independent commercial software product. UPI is a registered trademark of National Payments Corporation of India (NPCI).\n\n" +
                            "UPI-Easy is NOT an official NPCI or RBI application and does not claim official certification unless documented. Payments are executed directly via NPCI rails between participating PSP banks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RefundPolicySection() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Refund & Dispute Policy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "• UPI-Easy does not hold money and cannot unilaterally issue monetary refunds or reversals.\n" +
                            "• Failed or uncredited UPI debits are automatically reversed by customer & merchant banks within NPCI TAT (T+1 to T+2 days).\n" +
                            "• Customers must raise transaction disputes within their respective UPI apps using the UPI Reference Number (RRN / UTR).\n" +
                            "• Merchants can issue direct commercial refunds through their linked bank accounts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DataRetentionSection() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Account Deletion & Data Retention Policy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "You can delete your account at any time via Settings → Delete Account & Data.\n\n" +
                            "• Immediate Deletion: Name, phone, email, Google ID, device tokens, and local offline database tables are erased immediately.\n" +
                            "• Statutory Retention: Under Section 128 of the Companies Act and Goods & Services Tax (GST) Act, certain anonymized commercial transaction records and security audit logs are retained for statutory periods (up to 7 years) to prevent tax fraud.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GrievanceSection() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Grievance Redressal & Responsible Disclosure", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LegalItemRow("Grievance Officer", "Grievance Officer, Legal & Compliance")
                LegalItemRow("Grievance Email", "grievance@upieasy.com")
                LegalItemRow("Support Email", "support@upieasy.com")
                LegalItemRow("Security Vulnerabilities", "security@upieasy.com")
                LegalItemRow("Office Address", "Aerotech Innovations, Technology Hub, Bengaluru, Karnataka 560100, India")
            }
        }
    }
}

@Composable
private fun LegalItemRow(label: String, description: String) {
    Column {
        Text(text = label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
