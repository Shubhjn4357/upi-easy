package com.aerotech.upieasy.core.network

import com.aerotech.upieasy.core.security.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        val token = runBlocking { sessionManager.getAccessToken() }
        val orgId = runBlocking { sessionManager.getCurrentOrgId() }

        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        if (!orgId.isNullOrBlank()) {
            builder.header("X-Organization-Id", orgId)
        }

        return chain.proceed(builder.build())
    }
}

class TokenAuthenticator(
    private val sessionManager: SessionManager,
    private val baseUrl: String
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 3) {
            return null
        }

        val refreshToken = runBlocking { sessionManager.getRefreshToken() }
        if (refreshToken.isNullOrBlank()) {
            return null
        }

        synchronized(this) {
            val currentToken = runBlocking { sessionManager.getAccessToken() }
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")

            if (!currentToken.isNullOrBlank() && currentToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val newAccessToken = refreshAccessToken(baseUrl, refreshToken) ?: return null

            runBlocking {
                sessionManager.updateAccessToken(newAccessToken)
            }

            return response.request.newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private fun refreshAccessToken(baseUrl: String, refreshToken: String): String? {
        return try {
            val json = JSONObject().put("refreshToken", refreshToken).toString()
            val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/api/v1/auth/refresh")
                .post(requestBody)
                .build()

            val tempClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val resp = tempClient.newCall(request).execute()
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string() ?: return null
                val jsonResp = JSONObject(bodyStr)
                if (jsonResp.optBoolean("success", false)) {
                    val tokens = jsonResp.optJSONObject("tokens")
                    tokens?.optString("accessToken")
                } else null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

object NetworkClient {
    val DEFAULT_BASE_URL: String = try {
        val url = com.aerotech.upieasy.BuildConfig.API_BASE_URL
        if (!url.isNullOrBlank()) {
            if (!url.endsWith("/")) "$url/" else url
        } else {
            ""
        }
    } catch (_: Throwable) {
        ""
    }

    @Volatile
    private var apiService: ApiService? = null
    @Volatile
    private var currentBaseUrl: String? = null

    fun getApiService(sessionManager: SessionManager, baseUrl: String = DEFAULT_BASE_URL): ApiService {
        val resolvedUrl = if (baseUrl.isNotBlank()) {
            baseUrl
        } else {
            try {
                sessionManager.context.getString(com.aerotech.upieasy.R.string.api_base_url)
            } catch (_: Exception) {
                ""
            }
        }
        val normalizedBaseUrl = if (!resolvedUrl.endsWith("/")) "$resolvedUrl/" else resolvedUrl
        if (apiService != null && currentBaseUrl == normalizedBaseUrl) {
            return apiService!!
        }

        return synchronized(this) {
            if (apiService != null && currentBaseUrl == normalizedBaseUrl) {
                return apiService!!
            }

            val logging = HttpLoggingInterceptor().apply {
                level = if (com.aerotech.upieasy.BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
                // Redact sensitive headers
                redactHeader("Authorization")
                redactHeader("X-Webhook-Signature")
            }

            val okHttpClient = OkHttpClient.Builder()
                .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
                .addInterceptor(AuthInterceptor(sessionManager))
                .authenticator(TokenAuthenticator(sessionManager, normalizedBaseUrl))
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val instance = retrofit.create(ApiService::class.java)
            apiService = instance
            currentBaseUrl = normalizedBaseUrl
            instance
        }
    }
}
