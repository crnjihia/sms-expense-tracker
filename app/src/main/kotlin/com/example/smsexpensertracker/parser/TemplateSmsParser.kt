package com.example.smsexpensertracker.parser

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Default implementation supplying M‑Pesa, Equity, and KCB templates.
 */
class TemplateSmsParser(private val templates: List<Template>) : SmsParser {
    override fun parse(sender: String, body: String): SmsParseResult {
        val template = templates.find { it.senderId.equals(sender, ignoreCase = true) } ?: return SmsParseResult.Unparsed
        val match = template.pattern.find(body) ?: return SmsParseResult.Unparsed
        return SmsParseResult.Parsed(template.mapper(match))
    }

    companion object {
        fun createDefault(): TemplateSmsParser = TemplateSmsParser(
            listOf(mpesaTemplate(), equityTemplate(), kcbTemplate())
        )

        // --------------------------------------------------------------------
        // M‑Pesa template (example pattern)
        // --------------------------------------------------------------------
        private fun mpesaTemplate(): Template {
            val regex = Regex(
                "(?i)Ksh(?<amount>[\\d,]+\\.\\d{2})\\s+(?<direction>sent|received)\\s+to\\s+(?<counterparty>[^.]+?)\\s+on\\s+(?<date>\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}\\s+[AP]M)\\..*?Ref:\\s+(?<receipt>\\w+)",
                RegexOption.DOT_MATCHES_ALL
            )
            val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm a").withZone(ZoneOffset.UTC)
            fun mapper(m: MatchResult): ParsedSms {
                val amount = parseAmount(m.groups["amount"]!!.value)
                val dir = m.groups["direction"]!!.value.lowercase()
                val counter = m.groups["counterparty"]!!.value.trim()
                val description = "${dir} to $counter"
                val ts = OffsetDateTime.parse(m.groups["date"]!!.value, fmt)
                val receipt = m.groups["receipt"]!!.value
                val type = if (dir == "sent") TransactionType.SENT else if (dir == "received") TransactionType.RECEIVED else TransactionType.UNKNOWN
                return ParsedSms(
                    amountKsh = if (type == TransactionType.SENT) -amount else amount,
                    timestamp = ts,
                    description = description,
                    receiptCode = receipt,
                    transactionType = type
                )
            }
            return Template("MPESA", regex, ::mapper)
        }

        // --------------------------------------------------------------------
        // Equity template (synthetic)
        // --------------------------------------------------------------------
        private fun equityTemplate(): Template {
            val regex = Regex(
                "(?i)spent\\s+KES(?<amount>[\\d,]+\\.\\d{2})\\s+at\\s+(?<merchant>[^.]+)\\s+on\\s+(?<date>\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}\\s+[AP]M)\\.\\s+Transaction\\s+Ref:\\s+(?<receipt>\\w+)",
                RegexOption.DOT_MATCHES_ALL
            )
            val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm a").withZone(ZoneOffset.UTC)
            fun mapper(m: MatchResult): ParsedSms {
                val amount = parseAmount(m.groups["amount"]!!.value)
                val merchant = m.groups["merchant"]!!.value.trim()
                val description = "spent at $merchant"
                val ts = OffsetDateTime.parse(m.groups["date"]!!.value, fmt)
                val receipt = m.groups["receipt"]!!.value
                return ParsedSms(
                    amountKsh = -amount,
                    timestamp = ts,
                    description = description,
                    receiptCode = receipt,
                    transactionType = TransactionType.SENT
                )
            }
            return Template("EQUITY", regex, ::mapper)
        }

        // --------------------------------------------------------------------
        // KCB template (synthetic)
        // --------------------------------------------------------------------
        private fun kcbTemplate(): Template {
            val regex = Regex(
                "(?i)KCB:\\s+Debit\\s+of\\s+KES(?<amount>[\\d,]+\\.\\d{2})\\s+on\\s+(?<date>\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}\\s+[AP]M)\\.\\s+Ref\\s+No:\\s+(?<receipt>\\w+)",
                RegexOption.DOT_MATCHES_ALL
            )
            val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm a").withZone(ZoneOffset.UTC)
            fun mapper(m: MatchResult): ParsedSms {
                val amount = parseAmount(m.groups["amount"]!!.value)
                val ts = OffsetDateTime.parse(m.groups["date"]!!.value, fmt)
                val receipt = m.groups["receipt"]!!.value
                return ParsedSms(
                    amountKsh = -amount,
                    timestamp = ts,
                    description = "debit via KCB",
                    receiptCode = receipt,
                    transactionType = TransactionType.SENT
                )
            }
            return Template("KCB", regex, ::mapper)
        }

        private fun parseAmount(raw: String): Long {
            val whole = raw.replace(",", "").substringBefore('.')
            return whole.toLongOrNull() ?: 0L
        }
    }
}
