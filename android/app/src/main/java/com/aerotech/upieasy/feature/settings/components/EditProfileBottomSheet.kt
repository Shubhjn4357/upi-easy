package com.aerotech.upieasy.feature.settings.components

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerotech.upieasy.core.network.ApiService
import com.aerotech.upieasy.core.network.UpdateOrganizationRequest
import com.aerotech.upieasy.core.network.UpdateProfileRequest
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.ui.theme.BrandPrimary
import com.aerotech.upieasy.ui.theme.FailedRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileBottomSheet(
    userName: String?,
    userEmail: String?,
    currentOrgId: String?,
    currentOrgName: String?,
    currentOrgLegalName: String?,
    currentOrgCategory: String?,
    currentOrgPan: String?,
    currentOrgGstin: String?,
    apiService: ApiService,
    sessionManager: SessionManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var editedName by remember { mutableStateOf(userName ?: "") }
    var editedBusinessName by remember(currentOrgName) { mutableStateOf(currentOrgName ?: "") }
    var editedLegalName by remember(currentOrgLegalName) { mutableStateOf(currentOrgLegalName ?: "") }
    val categories = listOf(
        Pair("RETAIL", "Retail & Store"),
        Pair("FOOD_DINING", "Food & Dining"),
        Pair("SERVICES", "Services"),
        Pair("HEALTHCARE", "Healthcare"),
        Pair("TECH", "Technology"),
        Pair("WHOLESALE", "Wholesale"),
        Pair("EDUCATION", "Education"),
        Pair("OTHER", "Other")
    )
    var editedCategory by remember(currentOrgCategory) { mutableStateOf(currentOrgCategory ?: "RETAIL") }
    var editedPan by remember(currentOrgPan) { mutableStateOf(currentOrgPan ?: "") }
    var editedGstin by remember(currentOrgGstin) { mutableStateOf(currentOrgGstin ?: "") }
    var editedEmail by remember { mutableStateOf(userEmail ?: "") }
    var isUpdating by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Edit Profile & Business Info",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = editedName,
                onValueChange = {
                    editedName = it
                    updateError = null
                },
                label = { Text("Full Name") },
                placeholder = { Text("Your Name") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = editedBusinessName,
                onValueChange = {
                    editedBusinessName = it
                    updateError = null
                },
                label = { Text("Business / Store Name") },
                placeholder = { Text("My Retail Store") },
                leadingIcon = {
                    Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = editedLegalName,
                onValueChange = {
                    editedLegalName = it
                    updateError = null
                },
                label = { Text("Legal Entity Name (Optional)") },
                placeholder = { Text("e.g. My Store Technologies Pvt Ltd") },
                leadingIcon = {
                    Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            // Category Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Business Category",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { (catKey, catLabel) ->
                        val isSelected = editedCategory == catKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { editedCategory = catKey },
                            label = { Text(catLabel, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandPrimary.copy(alpha = 0.15f),
                                selectedLabelColor = BrandPrimary
                            )
                        )
                    }
                }
            }

            // PAN Number
            OutlinedTextField(
                value = editedPan,
                onValueChange = {
                    if (it.length <= 10) editedPan = it.uppercase()
                    updateError = null
                },
                label = { Text("PAN Number (Optional)") },
                placeholder = { Text("ABCDE1234F") },
                leadingIcon = {
                    Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next
                )
            )

            // GSTIN
            OutlinedTextField(
                value = editedGstin,
                onValueChange = {
                    if (it.length <= 15) editedGstin = it.uppercase()
                    updateError = null
                },
                label = { Text("GSTIN Number (Optional)") },
                placeholder = { Text("29ABCDE1234F1Z5") },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = editedEmail,
                onValueChange = {
                    editedEmail = it.trim()
                    updateError = null
                },
                label = { Text("Email Address") },
                placeholder = { Text("name@example.com") },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                )
            )

            if (updateError != null) {
                Text(text = updateError!!, color = FailedRed, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    if (editedName.trim().isBlank()) {
                        updateError = "Full name cannot be empty"
                        return@Button
                    }
                    if (editedBusinessName.trim().isBlank()) {
                        updateError = "Business name cannot be empty"
                        return@Button
                    }
                    if (editedEmail.isNotBlank() && !editedEmail.contains("@")) {
                        updateError = "Please enter a valid email address"
                        return@Button
                    }

                    isUpdating = true
                    scope.launch {
                        try {
                            val resProfile = apiService.updateProfile(
                                UpdateProfileRequest(
                                    fullName = editedName.trim(),
                                    email = editedEmail.trim().ifBlank { null }
                                )
                            )
                            if (resProfile.isSuccessful && resProfile.body()?.success == true) {
                                sessionManager.updateProfile(editedName.trim(), editedEmail.trim().ifBlank { null })
                            }

                            currentOrgId?.let { orgId ->
                                val resOrg = apiService.updateOrganization(
                                    orgId = orgId,
                                    request = UpdateOrganizationRequest(
                                        name = editedBusinessName.trim(),
                                        legalBusinessName = editedLegalName.trim().ifBlank { null },
                                        category = editedCategory,
                                        panNumber = editedPan.trim().ifBlank { null },
                                        gstin = editedGstin.trim().ifBlank { null }
                                    )
                                )
                                if (resOrg.isSuccessful && resOrg.body()?.success == true) {
                                    sessionManager.updateOrganizationDetails(
                                        name = editedBusinessName.trim(),
                                        legalName = editedLegalName.trim().ifBlank { null },
                                        category = editedCategory,
                                        panNumber = editedPan.trim().ifBlank { null },
                                        gstin = editedGstin.trim().ifBlank { null }
                                    )
                                }
                            }

                            Toast.makeText(context, "Profile and Business updated successfully", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } catch (e: Exception) {
                            updateError = e.localizedMessage ?: "Network error"
                        } finally {
                            isUpdating = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isUpdating
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
