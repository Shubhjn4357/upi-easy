package com.aerotech.upieasy.core.network

import com.aerotech.upieasy.core.security.RequestAuthenticator
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp Interceptor that automatically stamps outgoing requests with:
 * - x-app-timestamp: Unix timestamp in seconds
 * - x-app-signature: SHA-256 hex digest of "${timestamp}${secretKey}"
 *
 * Ensures only authentic instances of UPI-Easy can invoke backend APIs.
 */
class AppSignatureInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val timestampSeconds = System.currentTimeMillis() / 1000
        val signature = RequestAuthenticator.generateSignature(timestampSeconds)

        val builder = original.newBuilder()
            .header("x-app-timestamp", timestampSeconds.toString())
            .header("x-app-signature", signature)

        return chain.proceed(builder.build())
    }
}
