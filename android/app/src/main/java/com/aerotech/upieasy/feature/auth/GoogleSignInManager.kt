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
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.MessageDigest
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
     * Generates a cryptographically secure SHA-256 hashed nonce string.
     */
    fun generateSecureRandomNonce(): Pair<String, String> {
        val rawNonce = UUID.randomUUID().toString()
        val md = MessageDigest.getInstance("SHA-256")
        val hashedNonce = md.digest(rawNonce.toByteArray()).fold("") { str, it -> str + "%02x".format(it) }
        return Pair(rawNonce, hashedNonce)
    }

    /**
     * Executes Credential Manager Sign in with Google flow.
     * Uses GetGoogleIdOption with filterByAuthorizedAccounts = false and autoSelectEnabled = false.
     * This directly opens the native Google bottom drawer (bottom sheet) allowing the user
     * to select their Gmail account.
     */
    suspend fun signIn(activityContext: Context, serverClientId: String): Result<GoogleAuthResult> {
        val targetActivity = activityContext.findActivity()
            ?: return Result.failure(IllegalStateException("Unable to resolve foreground Activity for Google Sign-In"))

        val (rawNonce, hashedNonce) = generateSecureRandomNonce()
        val cleanClientId = serverClientId.trim()
        val credentialManager = CredentialManager.create(targetActivity)

        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(cleanClientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .setNonce(hashedNonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = targetActivity,
                request = request
            )
            extractIdToken(result, rawNonce)
        } catch (e: GetCredentialCancellationException) {
            Log.w("GoogleSignInManager", "Google Sign-In cancelled or rejected by Google Play Services: ${e.message}", e)
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
