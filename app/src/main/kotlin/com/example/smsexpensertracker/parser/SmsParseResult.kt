// path: app/src/main/kotlin/com/example/smsexpensertracker/parser/SmsParseResult.kt
package com.example.smsexpensertracker.parser

import java.time.OffsetDateTime

/**
 * Result of parsing a raw SMS message.
 */
sealed class SmsParseResult {
    /** Successful parse with structured transaction data. */
    data class Parsed(val parsed: ParsedSms) : SmsParseResult()

    /** No template matched; the message could not be parsed. */
    object Unparsed : SmsParseResult()
}

/**
 * Normalised representation of a parsed SMS transaction.
 *
 * @param amountKsh Amount in Kenyan Shillings (negative for debits/expenses, positive for credits/income).
 * @param timestamp Time of the transaction in Africa/Nairobi timezone converted to OffsetDateTime.
 * @param description Human-readable description (e.g. "sent to JANE DOE 0712345678").
 * @param receiptCode Unique transaction reference (e.g. M-Pesa receipt "QA12BC34DE", Equity "EQ883344").
 * @param transactionType Specific financial transaction category.
 * @param counterparty Party paid to or received from (e.g. merchant, paybill business, person name/number).
 * @param balanceKsh Account balance after transaction in Ksh, if provided in SMS.
 * @param feeKsh Transaction charge / fee in Ksh, if provided in SMS.
 */
data class ParsedSms(
    val amountKsh: Long,
    val timestamp: OffsetDateTime,
    val description: String,
    val receiptCode: String?,
    val transactionType: TransactionType,
    val counterparty: String? = null,
    val balanceKsh: Long? = null,
    val feeKsh: Long? = null
)

/**
 * Transaction types covering Kenyan mobile money and bank SMS notifications.
 */
enum class TransactionType {
    SENT,
    RECEIVED,
    PAYBILL,
    TILL,
    WITHDRAW,
    AIRTIME,
    REVERSAL,
    EXPENSE,
    INCOME,
    UNKNOWN
}
