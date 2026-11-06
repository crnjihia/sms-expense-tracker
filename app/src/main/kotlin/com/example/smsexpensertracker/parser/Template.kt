package com.example.smsexpensertracker.parser

/**
 * A template used to parse a specific sender's SMS messages.
 *
 * @param senderId Identifier (e.g., "MPESA", "EQUITY", "KCB").
 * @param pattern   Regex with named groups to extract fields.
 * @param mapper    Function that turns a [MatchResult] into a [ParsedSms].
 */
data class Template(
    val senderId: String,
    val pattern: Regex,
    val mapper: (MatchResult) -> ParsedSms
)
