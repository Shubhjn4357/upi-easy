package com.aerospace.upieasy

import com.aerospace.upieasy.core.util.UpiPaymentDetails
import com.aerospace.upieasy.core.util.UpiUriHelper
import org.junit.Assert.*
import org.junit.Test

class UpiUriHelperTest {

    @Test
    fun testBuildUri_matchesStandardNpciFormat() {
        val details = UpiPaymentDetails(
            payeeVpa = "merchant@icici",
            payeeName = "Merchant Store",
            amount = 2500.50,
            currency = "INR",
            transactionNote = "Order #1234",
            referenceId = "RRN99998888"
        )

        val uri = UpiUriHelper.buildUri(details)

        assertTrue(uri.startsWith("upi://pay?"))
        assertTrue(uri.contains("pa=merchant%40icici"))
        assertTrue(uri.contains("pn=Merchant+Store"))
        assertTrue(uri.contains("am=2500.50"))
        assertTrue(uri.contains("cu=INR"))
        assertTrue(uri.contains("tn=Order+%231234"))
        assertTrue(uri.contains("tr=RRN99998888"))
    }

    @Test
    fun testBuildUri_withoutOptionalAmount() {
        val details = UpiPaymentDetails(
            payeeVpa = "shop@hdfcbank",
            payeeName = "Shop Name"
        )

        val uri = UpiUriHelper.buildUri(details)

        assertTrue(uri.startsWith("upi://pay?"))
        assertTrue(uri.contains("pa=shop%40hdfcbank"))
        assertTrue(uri.contains("pn=Shop+Name"))
        assertFalse(uri.contains("am="))
    }

    @Test
    fun testParseUri_validUri() {
        val rawUri = "upi://pay?pa=merchant@axisbank&pn=Test+Merchant&am=100.00&cu=INR&tn=Coffee"
        val parsed = UpiUriHelper.parseUri(rawUri)

        assertNotNull(parsed)
        assertEquals("merchant@axisbank", parsed?.payeeVpa)
        assertEquals("Test Merchant", parsed?.payeeName)
        assertEquals(100.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals("INR", parsed?.currency)
        assertEquals("Coffee", parsed?.transactionNote)
    }

    @Test
    fun testParseUri_invalidScheme_returnsNull() {
        val rawUri = "https://example.com/pay?pa=test@upi"
        val parsed = UpiUriHelper.parseUri(rawUri)

        assertNull(parsed)
    }
}
