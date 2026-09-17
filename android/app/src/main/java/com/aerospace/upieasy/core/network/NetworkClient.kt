package com.aerospace.upieasy.core.network

import com.aerospace.upieasy.core.security.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
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

object NetworkClient {
    // 10.0.2.2 points to host machine from Android Emulator.
    // For physical devices on local Wi-Fi, change to machine's LAN IP.
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/"

    @Volatile
    private var apiService: ApiService? = null

    fun getApiService(sessionManager: SessionManager, baseUrl: String = DEFAULT_BASE_URL): ApiService {
        return apiService ?: synchronized(this) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
                // Redact sensitive headers
                redactHeader("Authorization")
                redactHeader("X-Webhook-Signature")
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(sessionManager))
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val instance = retrofit.create(ApiService::class.java)
            apiService = instance
            instance
        }
    }
}
