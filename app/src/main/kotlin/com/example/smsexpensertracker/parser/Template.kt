// path: app/src/main/kotlin/com/example/smsexpensertracker/parser/Template.kt
package com.example.smsexpensertracker.parser

/**
 * A template used to parse a specific sender's SMS messages.
 *
 * @param templateId Identifier for the template rule (e.g. "MPESA_SENT", "EQUITY_DEBIT").
 * @param senderMatcher Predicate that checks whether a given sender matches this template.
 * @param pattern Regex with named or numbered groups to extract fields.
 * @param mapper Function that turns a [MatchResult] into a [ParsedSms].
 */
data class Template(
    val templateId: String,
    val senderMatcher: (String) -> Boolean,
    val pattern: Regex,
    val mapper: (MatchResult) -> ParsedSms
) {
    /** Convenience constructor for a single fixed sender ID (case-insensitive). */
    constructor(
        senderId: String,
        pattern: Regex,
        mapper: (MatchResult) -> ParsedSms
    ) : this(
        templateId = senderId,
        senderMatcher = { candidate -> candidate.equals(senderId, ignoreCase = true) },
        pattern = pattern,
        mapper = mapper
    )
}
