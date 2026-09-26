package com.aerotech.upieasy.feature.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.util.UUID

data class GoogleAuthResult(
    val idToken: String,
    val nonce: String? = null,
    val profilePictureUri: String? = null
)

class GoogleSignInManager(private val context: Context) {

    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Generates a cryptographically secure random nonce string.
     */
    fun generateSecureRandomNonce(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }

    /**
     * Executes Credential Manager Sign in with Google flow.
     * 1. Attempts GetGoogleIdOption (filterByAuthorizedAccounts = false) for seamless 1-tap sheet.
     * 2. If NoCredentialException or custom credential error occurs (e.g. account not previously authorized),
     *    falls back to GetSignInWithGoogleOption explicit account picker.
     */
    suspend fun signIn(activityContext: Context, serverClientId: String): Result<GoogleAuthResult> {
        val targetActivity = activityContext.findActivity()
            ?: return Result.failure(IllegalStateException("Unable to resolve foreground Activity for Google Sign-In"))

        val cleanClientId = serverClientId.trim().replace("\"", "").replace("'", "")
        if (cleanClientId.isBlank()) {
            return Result.failure(IllegalStateException("Google Web Client ID is not configured. Please ensure GOOGLE_WEB_CLIENT_ID is set in your build or environment."))
        }

        val nonce = generateSecureRandomNonce()
        val credentialManager = CredentialManager.create(targetActivity)

        // Attempt 1: GetGoogleIdOption
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(cleanClientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .setNonce(nonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = targetActivity,
                request = request
            )
            return extractIdToken(result, nonce)
        } catch (e: GetCredentialCancellationException) {
            Log.i("GoogleSignInManager", "Google Sign-In cancelled by user")
            return Result.failure(e)
        } catch (e: Exception) {
            Log.w("GoogleSignInManager", "GetGoogleIdOption failed (${e.javaClass.simpleName}): ${e.message}. Attempting GetSignInWithGoogleOption fallback...", e)
        }

        // Attempt 2: Fallback to GetSignInWithGoogleOption (standard button flow)
        return try {
            val buttonOption = GetSignInWithGoogleOption.Builder(cleanClientId)
                .setNonce(nonce)
                .build()

            val fallbackRequest = GetCredentialRequest.Builder()
                .addCredentialOption(buttonOption)
                .build()

            val fallbackResult = credentialManager.getCredential(
                context = targetActivity,
                request = fallbackRequest
            )
            extractIdToken(fallbackResult, nonce)
        } catch (e: GetCredentialCancellationException) {
            Log.i("GoogleSignInManager", "Google Sign-In cancelled by user during fallback")
            Result.failure(e)
        } catch (e: NoCredentialException) {
            Log.w("GoogleSignInManager", "No Google account found on device", e)
            Result.failure(
                IllegalStateException("No Google account found on this device. Please sign in to a Google account under Android Settings > Accounts.", e)
            )
        } catch (e: GetCredentialException) {
            Log.e("GoogleSignInManager", "Credential Manager sign-in failed (${e.javaClass.simpleName}): ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("GoogleSignInManager", "Unexpected sign-in exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun extractIdToken(
        result: GetCredentialResponse,
        nonce: String?
    ): Result<GoogleAuthResult> {
        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                Result.success(
                    GoogleAuthResult(
                        idToken = googleIdTokenCredential.idToken,
                        nonce = nonce,
                        profilePictureUri = googleIdTokenCredential.profilePictureUri?.toString()
                    )
                )
            } catch (e: GoogleIdTokenParsingException) {
                Result.failure(e)
            }
        }
        return Result.failure(
            IllegalStateException("Unexpected credential type returned: ${credential::class.java.name}")
        )
    }
}
