package com.example.smsexpensertracker.parser

/**
 * Simple interface for parsing raw SMS messages.
 */
interface SmsParser {
    fun parse(sender: String, body: String): SmsParseResult
}
