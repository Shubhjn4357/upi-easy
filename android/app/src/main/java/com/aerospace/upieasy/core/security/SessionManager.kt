package com.aerospace.upieasy.core.security

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
        private val KEY_IS_SETUP_COMPLETE = androidx.datastore.preferences.core.booleanPreferencesKey("is_setup_complete")
    }

    val accessTokenFlow: Flow<String?> = context.dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val currentOrgIdFlow: Flow<String?> = context.dataStore.data.map { it[KEY_CURRENT_ORG_ID] }
    val currentOrgNameFlow: Flow<String?> = context.dataStore.data.map { it[KEY_CURRENT_ORG_NAME] }
    val userRoleFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_ROLE] }
    val isSetupCompleteFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_IS_SETUP_COMPLETE] ?: false }

    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { it[KEY_IS_SETUP_COMPLETE] = complete }
    }

    suspend fun saveSession(
        accessToken: String,
        refreshToken: String,
        userId: String,
        mobileNumber: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_USER_ID] = userId
            prefs[KEY_MOBILE_NUMBER] = mobileNumber
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
