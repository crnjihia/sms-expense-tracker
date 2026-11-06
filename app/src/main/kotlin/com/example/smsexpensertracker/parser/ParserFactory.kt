package com.example.smsexpensertracker.parser

/**
 * Factory that returns a ready‑to‑use parser instance.
 */
object ParserFactory {
    fun create(): SmsParser = TemplateSmsParser.createDefault()
}
