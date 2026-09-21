package com.aerotech.upieasy

import com.aerotech.upieasy.core.database.entity.PaymentAccountEntity
import com.aerotech.upieasy.feature.notifications.PaymentAccountResolver
import com.aerotech.upieasy.feature.notifications.PaymentMatchStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PaymentAccountResolverTest {

    private lateinit var resolver: PaymentAccountResolver

    @Before
    fun setUp() {
        resolver = PaymentAccountResolver()
    }

    @Test
    fun testUnmatchedWhenNoAccountsConfigured() {
        val result = resolver.resolve(
            sourcePackage = "com.phonepe.app",
            payerVpa = null,
            availableAccounts = emptyList()
        )

        assertEquals(PaymentMatchStatus.UNMATCHED, result.status)
        assertNull(result.paymentAccountId)
    }

    @Test
    fun testMatchedWhenSingleAccountConfigured() {
        val account = PaymentAccountEntity(
            id = "pa_phonepe_1",
            organizationId = "org_1",
            label = "Main PhonePe",
            upiId = "store@ybl",
            paymentAppId = "phonepe",
            paymentAppPackage = "com.phonepe.app",
            detectionEnabled = true
        )

        val result = resolver.resolve(
            sourcePackage = "com.phonepe.app",
            payerVpa = null,
            availableAccounts = listOf(account)
        )

        assertEquals(PaymentMatchStatus.MATCHED, result.status)
        assertEquals("pa_phonepe_1", result.paymentAccountId)
    }

    @Test
    fun testAmbiguousWhenMultipleAccountsWithoutDistinctVpa() {
        val account1 = PaymentAccountEntity(
            id = "pa_phonepe_1",
            organizationId = "org_1",
            label = "Counter 1",
            upiId = "store1@ybl",
            paymentAppId = "phonepe",
            paymentAppPackage = "com.phonepe.app",
            detectionEnabled = true
        )
        val account2 = PaymentAccountEntity(
            id = "pa_phonepe_2",
            organizationId = "org_1",
            label = "Counter 2",
            upiId = "store2@ybl",
            paymentAppId = "phonepe",
            paymentAppPackage = "com.phonepe.app",
            detectionEnabled = true
        )

        val result = resolver.resolve(
            sourcePackage = "com.phonepe.app",
            payerVpa = null, // Notification has no receiving VPA
            availableAccounts = listOf(account1, account2)
        )

        // Section 18: Never guess between multiple accounts!
        assertEquals(PaymentMatchStatus.AMBIGUOUS, result.status)
        assertNull(result.paymentAccountId)
    }

    @Test
    fun testMatchedWhenMultipleAccountsAndVpaIsExposed() {
        val account1 = PaymentAccountEntity(
            id = "pa_phonepe_1",
            organizationId = "org_1",
            label = "Counter 1",
            upiId = "store1@ybl",
            paymentAppId = "phonepe",
            paymentAppPackage = "com.phonepe.app",
            detectionEnabled = true
        )
        val account2 = PaymentAccountEntity(
            id = "pa_phonepe_2",
            organizationId = "org_1",
            label = "Counter 2",
            upiId = "store2@ybl",
            paymentAppId = "phonepe",
            paymentAppPackage = "com.phonepe.app",
            detectionEnabled = true
        )

        val result = resolver.resolve(
            sourcePackage = "com.phonepe.app",
            payerVpa = "store2@ybl",
            availableAccounts = listOf(account1, account2)
        )

        assertEquals(PaymentMatchStatus.MATCHED, result.status)
        assertEquals("pa_phonepe_2", result.paymentAccountId)
    }
}
