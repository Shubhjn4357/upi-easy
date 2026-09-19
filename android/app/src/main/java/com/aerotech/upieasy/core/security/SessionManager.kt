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
        private val KEY_USER_ROLE = stringPreferencesKey("user_role")
        private val KEY_MOBILE_NUMBER = stringPreferencesKey("mobile_number")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_IS_SETUP_COMPLETE = androidx.datastore.preferences.core.booleanPreferencesKey("is_setup_complete")
        private val KEY_SOUND_NOTIFICATIONS = androidx.datastore.preferences.core.booleanPreferencesKey("sound_notifications")
        private val KEY_BIOMETRIC_LOCK = androidx.datastore.preferences.core.booleanPreferencesKey("biometric_lock")
        private val KEY_HIGH_VALUE_ALERT = androidx.datastore.preferences.core.booleanPreferencesKey("high_value_alert")
    }

    val accessTokenFlow: Flow<String?> = context.dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val currentOrgIdFlow: Flow<String?> = context.dataStore.data.map { it[KEY_CURRENT_ORG_ID] }
    val currentOrgNameFlow: Flow<String?> = context.dataStore.data.map { it[KEY_CURRENT_ORG_NAME] }
    val userRoleFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_ROLE] }
    val userEmailFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_EMAIL] }
    val userNameFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_NAME] }
    val isSetupCompleteFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_IS_SETUP_COMPLETE] ?: false }
    val soundNotificationsFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_SOUND_NOTIFICATIONS] ?: true }
    val biometricLockFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_BIOMETRIC_LOCK] ?: false }
    val highValueAlertFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_HIGH_VALUE_ALERT] ?: true }

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

    suspend fun setOrganization(orgId: String, orgName: String, role: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CURRENT_ORG_ID] = orgId
            prefs[KEY_CURRENT_ORG_NAME] = orgName
            prefs[KEY_USER_ROLE] = role
        }
    }

    suspend fun getAccessToken(): String? {
        return context.dataStore.data.first()[KEY_ACCESS_TOKEN]
    }

    suspend fun getCurrentOrgId(): String? {
        return context.dataStore.data.first()[KEY_CURRENT_ORG_ID]
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}
