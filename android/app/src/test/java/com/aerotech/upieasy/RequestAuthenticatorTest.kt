package com.aerotech.upieasy

import com.aerotech.upieasy.core.security.RequestAuthenticator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class RequestAuthenticatorTest {

    @Test
    fun getSecretKey_isNotPlainTextAndDeobfuscatesCorrectly() {
        val secretKey = RequestAuthenticator.getSecretKey()
        assertTrue("Secret key must not be blank", secretKey.isNotBlank())
        assertTrue("Secret key must have length >= 16", secretKey.length >= 16)
    }

    @Test
    fun generateSignature_matchesStandardSha256Digest() {
        val timestamp = 1775000000L
        val secretKey = RequestAuthenticator.getSecretKey()
        val expectedInput = "$timestamp$secretKey"

        val md = MessageDigest.getInstance("SHA-256")
        val expectedDigest = md.digest(expectedInput.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val actualSignature = RequestAuthenticator.generateSignature(timestamp)
        assertEquals(expectedDigest, actualSignature)
    }

    @Test
    fun generateSignature_changesWhenTimestampChanges() {
        val sig1 = RequestAuthenticator.generateSignature(1000L)
        val sig2 = RequestAuthenticator.generateSignature(1001L)
        assertNotEquals(sig1, sig2)
    }
}
