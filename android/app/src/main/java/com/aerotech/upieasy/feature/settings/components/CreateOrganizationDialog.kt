package com.aerotech.upieasy.feature.settings.components

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.database.entity.OrganizationEntity
import com.aerotech.upieasy.core.network.ApiService
import com.aerotech.upieasy.core.network.CreateOrgRequest
import com.aerotech.upieasy.core.security.SessionManager
import com.aerotech.upieasy.data.repository.OrganizationRepository
import com.aerotech.upieasy.ui.theme.BrandPrimary
import com.aerotech.upieasy.ui.theme.FailedRed
import kotlinx.coroutines.launch

@Composable
fun CreateOrganizationDialog(
    apiService: ApiService,
    sessionManager: SessionManager,
    database: AppDatabase,
    orgRepository: OrganizationRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var businessName by remember { mutableStateOf("") }
    var legalBusinessName by remember { mutableStateOf("") }
    val categories = listOf(
        Pair("RETAIL", "Retail & Store"),
        Pair("FOOD_DINING", "Food & Dining"),
        Pair("SERVICES", "Services"),
        Pair("HEALTHCARE", "Healthcare"),
        Pair("TECH", "Technology"),
        Pair("WHOLESALE", "Wholesale"),
        Pair("OTHER", "Other")
    )
    var selectedCategory by remember { mutableStateOf("RETAIL") }
    var panNumber by remember { mutableStateOf("") }
    var gstin by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        icon = {
            Icon(Icons.Default.Storefront, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(32.dp))
        },
        title = {
            Text("Create New Business / Firm", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Set up a separate merchant organization with its own UPI handles, ledger and team.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Business / Store Name *") },
                    placeholder = { Text("e.g. Acme Supermart") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Category *",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { (catKey, catLabel) ->
                            val isSelected = selectedCategory == catKey
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = catKey },
                                label = { Text(catLabel, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = legalBusinessName,
                    onValueChange = { legalBusinessName = it },
                    label = { Text("Legal Entity Name (Optional)") },
                    placeholder = { Text("e.g. Acme Retail Pvt Ltd") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                OutlinedTextField(
                    value = panNumber,
                    onValueChange = { if (it.length <= 10) panNumber = it.uppercase() },
                    label = { Text("PAN Number (Optional)") },
                    placeholder = { Text("e.g. ABCDE1234F") },
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
                    value = gstin,
                    onValueChange = { if (it.length <= 15) gstin = it.uppercase() },
                    label = { Text("GSTIN Number (Optional)") },
                    placeholder = { Text("e.g. 29ABCDE1234F1Z5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    )
                )

                errorMessage?.let {
                    Text(text = it, color = FailedRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (businessName.trim().length < 2) {
                        errorMessage = "Business name must be at least 2 characters."
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val res = apiService.createOrganization(
                                CreateOrgRequest(
                                    name = businessName.trim(),
                                    legalBusinessName = legalBusinessName.trim().ifBlank { null },
                                    category = selectedCategory,
                                    panNumber = panNumber.trim().ifBlank { null },
                                    gstin = gstin.trim().ifBlank { null }
                                )
                            )
                            if (res.isSuccessful && res.body()?.success == true) {
                                val org = res.body()!!.organization
                                val entity = OrganizationEntity(
                                    id = org.id,
                                    name = org.name,
                                    legalBusinessName = org.legalBusinessName,
                                    category = org.category,
                                    panNumber = org.panNumber,
                                    gstin = org.gstin,
                                    role = "OWNER",
                                    membershipStatus = "ACTIVE",
                                    isCurrent = true
                                )
                                database.organizationDao().insertOrganization(entity)
                                sessionManager.setOrganization(
                                    orgId = org.id,
                                    orgName = org.name,
                                    role = "OWNER",
                                    legalName = org.legalBusinessName,
                                    category = org.category,
                                    panNumber = org.panNumber,
                                    gstin = org.gstin
                                )
                                orgRepository.refreshOrganizations()
                                Toast.makeText(context, "Business '${org.name}' created!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                val errBody = res.errorBody()?.string()
                                val parsedMsg = try {
                                    org.json.JSONObject(errBody ?: "").optString("message", "")
                                } catch (_: Exception) { "" }
                                errorMessage = if (parsedMsg.isNotBlank()) parsedMsg else "Failed to create business (${res.code()})"
                            }
                        } catch (e: Exception) {
                            errorMessage = e.localizedMessage ?: "Failed to connect to backend server"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create Business", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
