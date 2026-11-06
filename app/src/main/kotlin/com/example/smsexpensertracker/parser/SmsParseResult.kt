package com.example.smsexpensertracker.parser

import java.time.OffsetDateTime

/**
 * Result of parsing an SMS.
 */
sealed class SmsParseResult {
    /** Successful parse with structured data. */
    data class Parsed(val parsed: ParsedSms) : SmsParseResult()

    /** No template matched; the message could not be parsed. */
    object Unparsed : SmsParseResult()
}

/** Normalised representation of a parsed SMS transaction. */
data class ParsedSms(
    val amountKsh: Long,
    val timestamp: OffsetDateTime,
    val description: String,
    val receiptCode: String?,
    val transactionType: TransactionType
)

enum class TransactionType { SENT, RECEIVED, UNKNOWN }
