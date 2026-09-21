package com.aerotech.upieasy.core.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "upi_easy_session")

class SessionManager(private val context: Context) {

    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_CURRENT_ORG_ID = stringPreferencesKey("current_org_id")
        private val KEY_CURRENT_ORG_NAME = stringPreferencesKey("current_org_name")
        private val KEY_CURRENT_ORG_LEGAL_NAME = stringPreferencesKey("current_org_legal_name")
        private val KEY_CURRENT_ORG_CATEGORY = stringPreferencesKey("current_org_category")
        private val KEY_CURRENT_ORG_PAN = stringPreferencesKey("current_org_pan")
        private val KEY_CURRENT_ORG_GSTIN = stringPreferencesKey("current_org_gstin")
        private val KEY_USER_ROLE = stringPreferencesKey("user_role")
        private val KEY_MOBILE_NUMBER = stringPreferencesKey("mobile_number")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_IS_SETUP_COMPLETE = androidx.datastore.preferences.core.booleanPreferencesKey("is_setup_complete")
        private val KEY_SOUND_NOTIFICATIONS = androidx.datastore.preferences.core.booleanPreferencesKey("sound_notifications")
        private val KEY_BIOMETRIC_LOCK = androidx.datastore.preferences.core.booleanPreferencesKey("biometric_lock")
        private val KEY_HIGH_VALUE_ALERT = androidx.datastore.preferences.core.booleanPreferencesKey("high_value_alert")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = androidx.datastore.preferences.core.booleanPreferencesKey("dynamic_color")
        private val KEY_HAPTIC_FEEDBACK = androidx.datastore.preferences.core.booleanPreferencesKey("haptic_feedback")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    }

    val accessTokenFlow: Flow<String?> = context.dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val currentOrgIdFlow: Flow<String?> = context.dataStore.data.map { it[KEY_CURRENT_ORG_ID] }
    val currentOrgNameFlow: Flow<String?> = context.dataStore.data.map { it[KEY_CURRENT_ORG_NAME] }
    val currentOrgLegalNameFlow: Flow<String?> = context.dataStore.data.map { it[KEY_CURRENT_ORG_LEGAL_NAME] }
    val currentOrgCategoryFlow: Flow<String?> = context.dataStore.data.map { it[KEY_CURRENT_ORG_CATEGORY] }
    val currentOrgPanFlow: Flow<String?> = context.dataStore.data.map { it[KEY_CURRENT_ORG_PAN] }
    val currentOrgGstinFlow: Flow<String?> = context.dataStore.data.map { it[KEY_CURRENT_ORG_GSTIN] }
    val userRoleFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_ROLE] }
    val mobileNumberFlow: Flow<String?> = context.dataStore.data.map { it[KEY_MOBILE_NUMBER] }
    val userEmailFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_EMAIL] }
    val userNameFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_NAME] }
    val isSetupCompleteFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_IS_SETUP_COMPLETE] ?: false }
    val soundNotificationsFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_SOUND_NOTIFICATIONS] ?: true }
    val biometricLockFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_BIOMETRIC_LOCK] ?: false }
    val highValueAlertFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_HIGH_VALUE_ALERT] ?: true }
    val themeModeFlow: Flow<String> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: "SYSTEM" }
    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: false }
    val hapticFeedbackFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_HAPTIC_FEEDBACK] ?: true }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun updateProfile(name: String?, email: String?) {
        context.dataStore.edit { prefs ->
            if (name != null) prefs[KEY_USER_NAME] = name
            if (email != null) prefs[KEY_USER_EMAIL] = email
        }
    }

    suspend fun updateOrganizationName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CURRENT_ORG_NAME] = name
        }
    }

    suspend fun updateOrganizationDetails(
        name: String? = null,
        legalName: String? = null,
        category: String? = null,
        panNumber: String? = null,
        gstin: String? = null
    ) {
        context.dataStore.edit { prefs ->
            if (name != null) prefs[KEY_CURRENT_ORG_NAME] = name
            if (legalName != null) prefs[KEY_CURRENT_ORG_LEGAL_NAME] = legalName
            if (category != null) prefs[KEY_CURRENT_ORG_CATEGORY] = category
            if (panNumber != null) prefs[KEY_CURRENT_ORG_PAN] = panNumber
            if (gstin != null) prefs[KEY_CURRENT_ORG_GSTIN] = gstin
        }
    }

    suspend fun setSoundNotifications(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SOUND_NOTIFICATIONS] = enabled }
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setHighValueAlert(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HIGH_VALUE_ALERT] = enabled }
    }

    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { it[KEY_IS_SETUP_COMPLETE] = complete }
    }

    suspend fun saveSession(
        accessToken: String,
        refreshToken: String,
        userId: String,
        mobileNumber: String,
        email: String? = null,
        fullName: String? = null
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_USER_ID] = userId
            prefs[KEY_MOBILE_NUMBER] = mobileNumber
            if (email != null) prefs[KEY_USER_EMAIL] = email
            if (fullName != null) prefs[KEY_USER_NAME] = fullName
        }
    }

    suspend fun setOrganization(
        orgId: String,
        orgName: String,
        role: String,
        legalName: String? = null,
        category: String? = null,
        panNumber: String? = null,
        gstin: String? = null
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CURRENT_ORG_ID] = orgId
            prefs[KEY_CURRENT_ORG_NAME] = orgName
            prefs[KEY_USER_ROLE] = role
            if (legalName != null) prefs[KEY_CURRENT_ORG_LEGAL_NAME] = legalName
            if (category != null) prefs[KEY_CURRENT_ORG_CATEGORY] = category
            if (panNumber != null) prefs[KEY_CURRENT_ORG_PAN] = panNumber
            if (gstin != null) prefs[KEY_CURRENT_ORG_GSTIN] = gstin
        }
    }

    suspend fun getAccessToken(): String? {
        return context.dataStore.data.first()[KEY_ACCESS_TOKEN]
    }

    suspend fun getRefreshToken(): String? {
        return context.dataStore.data.first()[KEY_REFRESH_TOKEN]
    }

    suspend fun updateAccessToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = token
        }
    }

    suspend fun getCurrentOrgId(): String? {
        return context.dataStore.data.first()[KEY_CURRENT_ORG_ID]
    }

    suspend fun getDeviceId(): String {
        val existing = context.dataStore.data.first()[KEY_DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val newId = "android_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        context.dataStore.edit { it[KEY_DEVICE_ID] = newId }
        return newId
    }

    suspend fun hasActiveSession(): Boolean {
        val token = getAccessToken()
        return !token.isNullOrBlank()
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}
