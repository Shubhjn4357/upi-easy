package com.aerotech.upieasy.feature.auth

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.SecureRandom

data class GoogleAuthResult(
    val idToken: String,
    val nonce: String,
    val profilePictureUri: String? = null
)

class GoogleSignInManager(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)

    /**
     * Generates a cryptographically secure random nonce encoded as URL-safe Base64.
     * Prevents token replay attacks as recommended by Google Identity documentation.
     */
    fun generateSecureRandomNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(
            randomBytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
    }

    /**
     * Executes Credential Manager Sign in with Google flow.
     * Follows official Google guidance:
     * 1. First attempts filterByAuthorizedAccounts = true for seamless 1-tap sign-in.
     * 2. On NoCredentialException or fallback, retries with filterByAuthorizedAccounts = false
     *    to show the standard Google account selection dialog.
     */
    suspend fun signIn(activityContext: Context, serverClientId: String): Result<GoogleAuthResult> {
        val nonce = generateSecureRandomNonce()

        return try {
            // Attempt 1: Filter by authorized accounts
            val initialOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .setNonce(nonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(initialOption)
                .build()

            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            extractIdToken(result, nonce)
        } catch (e: NoCredentialException) {
            Log.d("GoogleSignInManager", "No authorized accounts found, falling back to full account picker")
            signInWithAnyGoogleAccount(activityContext, serverClientId, nonce)
        } catch (e: GetCredentialCancellationException) {
            Log.i("GoogleSignInManager", "User cancelled Google Sign-In")
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.w("GoogleSignInManager", "Authorized account fetch failed (${e.message}), attempting fallback", e)
            signInWithAnyGoogleAccount(activityContext, serverClientId, nonce)
        } catch (e: Exception) {
            Log.e("GoogleSignInManager", "Unexpected sign-in exception", e)
            Result.failure(e)
        }
    }

    private suspend fun signInWithAnyGoogleAccount(
        activityContext: Context,
        serverClientId: String,
        nonce: String
    ): Result<GoogleAuthResult> {
        return try {
            val fallbackOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .setNonce(nonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(fallbackOption)
                .build()

            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            extractIdToken(result, nonce)
        } catch (e: Exception) {
            Log.e("GoogleSignInManager", "Full Google Sign-In account picker failed", e)
            Result.failure(e)
        }
    }

    private fun extractIdToken(
        result: GetCredentialResponse,
        nonce: String
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
