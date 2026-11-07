// path: app/src/test/java/com/example/smsexpensertracker/parser/TemplateSmsParserTest.kt
package com.example.smsexpensertracker.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Month

class TemplateSmsParserTest {

    private lateinit var parser: SmsParser

    @BeforeEach
    fun setUp() {
        parser = ParserFactory.create()
    }

    // ========================================================================
    // M-PESA SENDER TESTS
    // ========================================================================

    @Test
    fun `table-driven test for mpesa p2p sent messages`() {
        data class TestCase(
            val message: String,
            val expectedAmount: Long,
            val expectedReceipt: String,
            val expectedCounterparty: String,
            val expectedBalance: Long?,
            val expectedFee: Long?
        )

        val testCases = listOf(
            TestCase(
                message = "QA12BC34DE Confirmed. Ksh1,500.00 sent to JANE DOE 0712345678 on 15/08/2024 at 2:30 PM. New M-PESA balance is Ksh5,230.00. Transaction cost, Ksh15.00.",
                expectedAmount = -1500L,
                expectedReceipt = "QA12BC34DE",
                expectedCounterparty = "JANE DOE 0712345678",
                expectedBalance = 5230L,
                expectedFee = 15L
            ),
            TestCase(
                message = "QA99XX88WW Confirmed. KES 25,000.00 sent to PETER KAMAU +254722112233 on 01/09/2024 at 09:15 AM. New M-PESA balance is Ksh14,500.00. Transaction cost, Ksh105.00.",
                expectedAmount = -25000L,
                expectedReceipt = "QA99XX88WW",
                expectedCounterparty = "PETER KAMAU +254722112233",
                expectedBalance = 14500L,
                expectedFee = 105L
            ),
            // Legacy backwards-compatible format
            TestCase(
                message = "Ksh1,500.00 sent to JANE DOE 0712345678 on 15/08/2024 02:30 PM. New M-PESA balance is Ksh5,230.00. Ref: QA12BC34DE",
                expectedAmount = -1500L,
                expectedReceipt = "QA12BC34DE",
                expectedCounterparty = "JANE DOE 0712345678",
                expectedBalance = null,
                expectedFee = null
            )
        )

        for (tc in testCases) {
            val result = parser.parse("MPESA", tc.message)
            assertTrue(result is SmsParseResult.Parsed, "Failed for message: ${tc.message}")
            val parsed = (result as SmsParseResult.Parsed).parsed

            assertEquals(tc.expectedAmount, parsed.amountKsh)
            assertEquals(tc.expectedReceipt, parsed.receiptCode)
            assertEquals(tc.expectedCounterparty, parsed.counterparty)
            assertEquals(tc.expectedBalance, parsed.balanceKsh)
            assertEquals(tc.expectedFee, parsed.feeKsh)
            assertEquals(TransactionType.SENT, parsed.transactionType)
        }
    }

    @Test
    fun `table-driven test for mpesa p2p received messages`() {
        data class TestCase(
            val message: String,
            val expectedAmount: Long,
            val expectedReceipt: String,
            val expectedCounterparty: String,
            val expectedBalance: Long?
        )

        val testCases = listOf(
            TestCase(
                message = "QA99ZZ88YY Confirmed. You have received Ksh2,000.00 from JOHN DOE 0722000000 on 10/08/2024 at 10:15 AM. New M-PESA balance is Ksh12,000.00.",
                expectedAmount = 2000L,
                expectedReceipt = "QA99ZZ88YY",
                expectedCounterparty = "JOHN DOE 0722000000",
                expectedBalance = 12000L
            ),
            TestCase(
                message = "RC98ZY76XW Confirmed. You have received KES 50,000.00 from MARY WANJIKU on 12/09/2024 at 4:50 PM. New M-PESA balance is Ksh65,200.00.",
                expectedAmount = 50000L,
                expectedReceipt = "RC98ZY76XW",
                expectedCounterparty = "MARY WANJIKU",
                expectedBalance = 65200L
            ),
            // Legacy received format
            TestCase(
                message = "Ksh2,000.00 received to JOHN DOE on 10/08/2024 10:15 AM. Balance Ksh12,000.00. Ref: RC98ZY76XW",
                expectedAmount = 2000L,
                expectedReceipt = "RC98ZY76XW",
                expectedCounterparty = "JOHN DOE",
                expectedBalance = null
            )
        )

        for (tc in testCases) {
            val result = parser.parse("M-PESA", tc.message)
            assertTrue(result is SmsParseResult.Parsed, "Failed for message: ${tc.message}")
            val parsed = (result as SmsParseResult.Parsed).parsed

            assertEquals(tc.expectedAmount, parsed.amountKsh)
            assertEquals(tc.expectedReceipt, parsed.receiptCode)
            assertEquals(tc.expectedCounterparty, parsed.counterparty)
            assertEquals(tc.expectedBalance, parsed.balanceKsh)
            assertEquals(TransactionType.RECEIVED, parsed.transactionType)
        }
    }

    @Test
    fun `parse mpesa paybill message extracts business and account`() {
        val message = "QA33CD44EF Confirmed. Ksh3,500.00 sent to KPLC PREPAID for account 14223456789 on 18/08/2024 at 8:15 PM. New M-PESA balance is Ksh8,500.00. Transaction cost, Ksh23.00."

        val result = parser.parse("MPESA", message)
        assertTrue(result is SmsParseResult.Parsed)
        val parsed = (result as SmsParseResult.Parsed).parsed

        assertEquals(-3500L, parsed.amountKsh)
        assertEquals("QA33CD44EF", parsed.receiptCode)
        assertEquals("KPLC PREPAID (Acc: 14223456789)", parsed.counterparty)
        assertEquals(TransactionType.PAYBILL, parsed.transactionType)
        assertEquals(8500L, parsed.balanceKsh)
        assertEquals(23L, parsed.feeKsh)
    }

    @Test
    fun `parse mpesa buy goods till message extracts merchant and zero fee`() {
        val message = "QA55EF66GH Confirmed. Ksh4,200.00 paid to NAIVAS SUPERMARKET on 20/08/2024 at 6:45 PM. New M-PESA balance is Ksh4,300.00. Transaction cost, Ksh0.00."

        val result = parser.parse("MPESA", message)
        assertTrue(result is SmsParseResult.Parsed)
        val parsed = (result as SmsParseResult.Parsed).parsed

        assertEquals(-4200L, parsed.amountKsh)
        assertEquals("QA55EF66GH", parsed.receiptCode)
        assertEquals("NAIVAS SUPERMARKET", parsed.counterparty)
        assertEquals(TransactionType.TILL, parsed.transactionType)
        assertEquals(4300L, parsed.balanceKsh)
        assertEquals(0L, parsed.feeKsh)
    }

    @Test
    fun `parse mpesa cash withdrawal extracts agent and fee`() {
        val message = "QA77GH88IJ Confirmed. On 22/08/2024 at 11:00 AM Withdraw Ksh1,000.00 from 123456 - AGENT KIMATHI ST. New M-PESA balance is Ksh3,300.00. Transaction cost, Ksh28.00."

        val result = parser.parse("MPESA", message)
        assertTrue(result is SmsParseResult.Parsed)
        val parsed = (result as SmsParseResult.Parsed).parsed

        assertEquals(-1000L, parsed.amountKsh)
        assertEquals("QA77GH88IJ", parsed.receiptCode)
        assertEquals("123456 - AGENT KIMATHI ST", parsed.counterparty)
        assertEquals(TransactionType.WITHDRAW, parsed.transactionType)
        assertEquals(3300L, parsed.balanceKsh)
        assertEquals(28L, parsed.feeKsh)
    }

    @Test
    fun `parse mpesa airtime purchase extracts recipient and balance`() {
        val message = "QA88IJ99KL Confirmed. You bought Ksh100.00 of airtime for 0712345678 on 23/08/2024 at 1:10 PM. New M-PESA balance is Ksh3,200.00."

        val result = parser.parse("MPESA", message)
        assertTrue(result is SmsParseResult.Parsed)
        val parsed = (result as SmsParseResult.Parsed).parsed

        assertEquals(-100L, parsed.amountKsh)
        assertEquals("QA88IJ99KL", parsed.receiptCode)
        assertEquals("Safaricom Airtime (0712345678)", parsed.counterparty)
        assertEquals(TransactionType.AIRTIME, parsed.transactionType)
        assertEquals(3200L, parsed.balanceKsh)
        assertEquals(0L, parsed.feeKsh)
    }

    @Test
    fun `parse mpesa transaction reversal extracts credited amount and original receipt`() {
        val message = "QA00KL11MN Confirmed. Reversal of transaction QA12BC34DE has been completed on 24/08/2024 at 3:00 PM and Ksh1,500.00 has been credited to your M-PESA account. New M-PESA balance is Ksh4,700.00."

        val result = parser.parse("MPESA", message)
        assertTrue(result is SmsParseResult.Parsed)
        val parsed = (result as SmsParseResult.Parsed).parsed

        assertEquals(1500L, parsed.amountKsh) // Positive credit
        assertEquals("QA00KL11MN", parsed.receiptCode)
        assertEquals("Reversal (QA12BC34DE)", parsed.counterparty)
        assertEquals(TransactionType.REVERSAL, parsed.transactionType)
        assertEquals(4700L, parsed.balanceKsh)
    }

    // ========================================================================
    // EQUITY BANK TESTS
    // ========================================================================

    @Test
    fun `table-driven test for equity bank messages`() {
        data class TestCase(
            val sender: String,
            val message: String,
            val expectedAmount: Long,
            val expectedReceipt: String,
            val expectedCounterparty: String,
            val expectedType: TransactionType
        )

        val testCases = listOf(
            TestCase(
                sender = "EQUITY",
                message = "spent KES3,450.50 at NAIROBI SUPERMARKET on 12/08/2024 07:45 PM. Transaction Ref: EQ883344",
                expectedAmount = -3450L,
                expectedReceipt = "EQ883344",
                expectedCounterparty = "NAIROBI SUPERMARKET",
                expectedType = TransactionType.SENT
            ),
            TestCase(
                sender = "EquityBank",
                message = "Your A/C 1234567890123 has been debited KES 4,500.00 on 14/08/2024 14:10:05 at QUICKMART KILIMANI. Available balance is KES 45,200.00. Ref: EQ554433",
                expectedAmount = -4500L,
                expectedReceipt = "EQ554433",
                expectedCounterparty = "QUICKMART KILIMANI",
                expectedType = TransactionType.EXPENSE
            ),
            TestCase(
                sender = "EQUITYBC",
                message = "Your A/C 1234567890123 has been credited KES 85,000.00 on 25/08/2024 09:30:00 by TECH KENYA LTD. Available balance is KES 130,200.00. Ref: EQ998877",
                expectedAmount = 85000L,
                expectedReceipt = "EQ998877",
                expectedCounterparty = "TECH KENYA LTD",
                expectedType = TransactionType.INCOME
            )
        )

        for (tc in testCases) {
            val result = parser.parse(tc.sender, tc.message)
            assertTrue(result is SmsParseResult.Parsed, "Failed for Equity message: ${tc.message}")
            val parsed = (result as SmsParseResult.Parsed).parsed

            assertEquals(tc.expectedAmount, parsed.amountKsh)
            assertEquals(tc.expectedReceipt, parsed.receiptCode)
            assertEquals(tc.expectedCounterparty, parsed.counterparty)
            assertEquals(tc.expectedType, parsed.transactionType)
        }
    }

    // ========================================================================
    // KCB BANK TESTS
    // ========================================================================

    @Test
    fun `table-driven test for kcb bank messages`() {
        data class TestCase(
            val sender: String,
            val message: String,
            val expectedAmount: Long,
            val expectedReceipt: String,
            val expectedCounterparty: String,
            val expectedType: TransactionType
        )

        val testCases = listOf(
            TestCase(
                sender = "KCB",
                message = "KCB: Debit of KES750.00 on 14/08/2024 09:20 AM. Ref No: KCB992211",
                expectedAmount = -750L,
                expectedReceipt = "KCB992211",
                expectedCounterparty = "KCB",
                expectedType = TransactionType.SENT
            ),
            TestCase(
                sender = "KCBBANK",
                message = "Dear Customer, KES 2,800.00 has been debited from your A/C 1122334455 on 16/08/2024 at 16:45:00 at JAVA HOUSE. Bal KES 34,100.00. Ref: KCB334455.",
                expectedAmount = -2800L,
                expectedReceipt = "KCB334455",
                expectedCounterparty = "JAVA HOUSE",
                expectedType = TransactionType.EXPENSE
            ),
            TestCase(
                sender = "KCBGroup",
                message = "Dear Customer, KES 15,000.00 has been credited to your A/C 1122334455 on 17/08/2024 at 11:20:00 by MOBILE MONEY TRANSFER. Bal KES 49,100.00. Ref: KCB667788.",
                expectedAmount = 15000L,
                expectedReceipt = "KCB667788",
                expectedCounterparty = "MOBILE MONEY TRANSFER",
                expectedType = TransactionType.INCOME
            )
        )

        for (tc in testCases) {
            val result = parser.parse(tc.sender, tc.message)
            assertTrue(result is SmsParseResult.Parsed, "Failed for KCB message: ${tc.message}")
            val parsed = (result as SmsParseResult.Parsed).parsed

            assertEquals(tc.expectedAmount, parsed.amountKsh)
            assertEquals(tc.expectedReceipt, parsed.receiptCode)
            assertEquals(tc.expectedCounterparty, parsed.counterparty)
            assertEquals(tc.expectedType, parsed.transactionType)
        }
    }

    // ========================================================================
    // EDGE CASES & FUZZ-ISH TESTS
    // ========================================================================

    @Test
    fun `edge case - multi-line messages with linebreaks are parsed correctly`() {
        val multiLineMpesa = """
            QA12BC34DE Confirmed.
            Ksh1,500.00 sent to JANE DOE 0712345678
            on 15/08/2024 at 2:30 PM.
            New M-PESA balance is Ksh5,230.00.
            Transaction cost, Ksh15.00.
        """.trimIndent()

        val result = parser.parse("MPESA", multiLineMpesa)
        assertTrue(result is SmsParseResult.Parsed)
        val parsed = (result as SmsParseResult.Parsed).parsed
        assertEquals(-1500L, parsed.amountKsh)
        assertEquals("QA12BC34DE", parsed.receiptCode)
        assertEquals("JANE DOE 0712345678", parsed.counterparty)
    }

    @Test
    fun `edge case - amounts with commas and KES prefix are parsed correctly`() {
        val message = "QA77KK88LL Confirmed. KES 120,500.00 sent to SUPERMARKET SUPPLIERS for account INV-990 on 20/08/2024 at 10:00 AM. New M-PESA balance is Ksh34,000.00. Transaction cost, Ksh105.00."
        val result = parser.parse("MPESA", message)
        assertTrue(result is SmsParseResult.Parsed)
        val parsed = (result as SmsParseResult.Parsed).parsed
        assertEquals(-120500L, parsed.amountKsh)
        assertEquals(34000L, parsed.balanceKsh)
        assertEquals(105L, parsed.feeKsh)
    }

    @Test
    fun `edge case - timestamp in Africa Nairobi timezone is correctly assigned`() {
        val message = "QA12BC34DE Confirmed. Ksh1,500.00 sent to JANE DOE 0712345678 on 15/08/2024 at 2:30 PM. New M-PESA balance is Ksh5,230.00. Transaction cost, Ksh15.00."
        val result = parser.parse("MPESA", message)
        assertTrue(result is SmsParseResult.Parsed)
        val parsed = (result as SmsParseResult.Parsed).parsed

        assertEquals(2024, parsed.timestamp.year)
        assertEquals(Month.AUGUST, parsed.timestamp.month)
        assertEquals(15, parsed.timestamp.dayOfMonth)
        assertEquals(14, parsed.timestamp.hour) // 2:30 PM is 14:30
        assertEquals(30, parsed.timestamp.minute)
    }

    @Test
    fun `unknown sender returns unparsed`() {
        val result = parser.parse("SAFARICOM_PROMO", "Get 500MB for 20 bob today only!")
        assertEquals(SmsParseResult.Unparsed, result)
    }

    @Test
    fun `fuzz test - corrupt, truncated, noisy, or empty input never crashes and returns unparsed`() {
        val noisyInputs = listOf(
            "",
            "   ",
            "\n\r\n\t",
            "Random non-financial marketing text",
            "Ksh NaN sent to unknown on invalid date Ref:",
            "Confirmed. sent to on at . New M-PESA balance is .",
            "QA12BC34DE Confirmed. Ksh NOT_A_NUMBER sent to ...",
            "QA12BC34DE Confirmed. Ksh1,500.00 sent to JANE on INVALID_DATE. New M-PESA balance is Ksh5,230.00.",
            "!!!@@@###$$$%%%^^^&&&***()_+=-[]{}|;:',.<>?/",
            "Dear Customer, KES has been debited from your A/C on at . Ref: .",
            "KCB: Debit of KES on . Ref No:"
        )

        val senders = listOf("MPESA", "M-PESA", "EQUITY", "EquityBank", "KCB", "KCBBANK", "UNKNOWN_SENDER")

        for (sender in senders) {
            for (input in noisyInputs) {
                val result = parser.parse(sender, input)
                assertNotNull(result)
                // If it fails to match a valid transaction, it must return Unparsed safely without throwing
                if (sender == "UNKNOWN_SENDER") {
                    assertEquals(SmsParseResult.Unparsed, result)
                }
            }
        }
    }
}
