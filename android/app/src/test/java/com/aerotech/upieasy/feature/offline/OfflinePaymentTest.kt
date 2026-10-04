package com.aerotech.upieasy.feature.offline

import com.aerotech.upieasy.feature.offline.core.*
import org.junit.Assert.*
import org.junit.Test

class OfflinePaymentTest {

    @Test
    fun testUpi123CallStringBuilder_Valid() {
        val result = Upi123CallStringBuilder.build("08045163666", "9876543210", "500")
        assertTrue(result is Upi123CallStringBuilder.Result.Valid)
        val valid = result as Upi123CallStringBuilder.Result.Valid
        assertEquals("tel:08045163666,,1,9876543210,,500,,1", valid.callString)
    }

    @Test
    fun testUpi123CallStringBuilder_CapEnforced() {
        val result = Upi123CallStringBuilder.build("08045163666", "9876543210", "5000")
        assertTrue(result is Upi123CallStringBuilder.Result.Invalid)
        val invalid = result as Upi123CallStringBuilder.Result.Invalid
        assertEquals(Upi123CallStringBuilder.Reason.AMOUNT_ABOVE_CAP, invalid.reason)
    }

    @Test
    fun testUpi123CallStringBuilder_WholeRupeesOnly() {
        val result = Upi123CallStringBuilder.build("08045163666", "9876543210", "150.50")
        assertTrue(result is Upi123CallStringBuilder.Result.Invalid)
        val invalid = result as Upi123CallStringBuilder.Result.Invalid
        assertEquals(Upi123CallStringBuilder.Reason.AMOUNT_NOT_WHOLE_RUPEES, invalid.reason)
    }

    @Test
    fun testQRCodeParser_StandardUpiUri() {
        val uri = "upi://pay?pa=merchant@okhdfcbank&pn=Merchant%20Store&am=250.00&cu=INR"
        val parsed = QRCodeParser.parse(uri)
        assertTrue(parsed is QRCodeParser.ParseResult.Valid)
        val data = (parsed as QRCodeParser.ParseResult.Valid).data
        assertEquals("merchant@okhdfcbank", data.payeeAddress)
        assertEquals("Merchant Store", data.payeeName)
        assertEquals(250.00, data.amount ?: 0.0, 0.001)
    }

    @Test
    fun testQRCodeParser_BareVpa() {
        val vpa = "shubh@upi"
        val parsed = QRCodeParser.parse(vpa)
        assertTrue(parsed is QRCodeParser.ParseResult.Valid)
        val data = (parsed as QRCodeParser.ParseResult.Valid).data
        assertEquals("shubh@upi", data.payeeAddress)
    }

    @Test
    fun testPhoneNumberUtils_Normalize() {
        assertEquals("9876543210", PhoneNumberUtils.normalize("+91 98765 43210"))
        assertEquals("9876543210", PhoneNumberUtils.normalize("09876543210"))
        assertEquals("9876543210", PhoneNumberUtils.normalize("98765-43210"))
    }

    @Test
    fun testPhoneNumberUtils_FormatDisplay() {
        val formatted = PhoneNumberUtils.formatPhoneForDisplay("9876543210")
        assertEquals("••••••3210", formatted)
    }

    @Test
    fun testCurrencyFormat_Inr() {
        assertEquals("1,00,000.00", CurrencyFormat.inr(100000.0))
        assertEquals("4,999.00", CurrencyFormat.inr(4999.0))
        assertEquals("500.00", CurrencyFormat.inr(500.0))
    }

    @Test
    fun testSmsTransactionParser_HdfcDebit() {
        val body = "Rs.500.00 debited from HDFC Bank A/C **1234 on 04-OCT-26 to VPA merchant@upi UPI:627819283748"
        val parsed = SmsTransactionParser.parse("HDFCBK", body)
        assertNotNull(parsed)
        assertEquals("500.00", parsed?.amount)
        assertEquals("HDFC Bank", parsed?.bankName)
        assertTrue(parsed?.transactionId?.startsWith("627819283748") == true)
        assertEquals("DEBIT", parsed?.transactionType)
    }

    @Test
    fun testSmsTransactionParser_SbiDebit() {
        val body = "Dear SBI User, your A/C 9876 has been debited by Rs 1,250 on 04Oct26 by transfer to UPI/638192019283/Ramesh"
        val parsed = SmsTransactionParser.parse("SBIINB", body)
        assertNotNull(parsed)
        assertEquals("1250", parsed?.amount)
        assertEquals("State Bank of India", parsed?.bankName)
        assertTrue(parsed?.transactionId?.startsWith("638192019283") == true)
    }
}
