package com.aerotech.upieasy.core.security

import com.aerotech.upieasy.BuildConfig
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Handles mutual cryptographic authentication between the Android application and the UPI-Easy backend.
 *
 * Security architecture:
 * 1. The shared secret key is stored locally in local.properties (preventing public git repository leakage).
 * 2. During Gradle compilation, the key is XOR-obfuscated with mask 0x5A into a byte array
 *    (preventing static reverse-engineering via plaintext DEX string dumps).
 * 3. At runtime, getSecretKey() de-obfuscates the byte array in memory on-demand.
 * 4. generateSignature(timestamp) computes a SHA-256 digest of "${timestamp}${secretKey}" which
 *    the server validates within a replay window.
 */
object RequestAuthenticator {
    // The key is hidden as a byte array XORed with a mask
    private const val MASK: Byte = 0x5A

    /**
     * Reconstruct the secret key at runtime by XOR de-masking.
     * Prevents static extraction of plaintext secret key from DEX bytecode.
     */
    fun getSecretKey(): String {
        return try {
            val obfuscated = BuildConfig.OBFUSCATED_SECRET_KEY
            if (obfuscated.isEmpty()) return ""
            val decrypted = ByteArray(obfuscated.size)
            for (i in obfuscated.indices) {
                decrypted[i] = (obfuscated[i].toInt() xor MASK.toInt()).toByte()
            }
            String(decrypted, StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Generates a SHA-256 signature matching the server verification algorithm:
     * serverSignature = crypto.createHash('sha256').update(`${timestamp}${secretKey}`).digest('hex')
     *
     * @param timestampSeconds Unix timestamp in seconds
     */
    fun generateSignature(timestampSeconds: Long): String {
        val secretKey = getSecretKey()
        val input = "$timestampSeconds$secretKey"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
