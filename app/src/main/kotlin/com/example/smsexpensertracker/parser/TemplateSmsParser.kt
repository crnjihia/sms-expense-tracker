// path: app/src/main/kotlin/com/example/smsexpensertracker/parser/TemplateSmsParser.kt
package com.example.smsexpensertracker.parser

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parsing engine that applies sender-specific regex templates with named/positional capture groups
 * to turn SMS text into structured [ParsedSms] transactions.
 */
class TemplateSmsParser(
    private val templates: List<Template> = defaultTemplates()
) : SmsParser {

    override fun parse(sender: String, body: String): SmsParseResult {
        if (sender.isBlank() || body.isBlank()) return SmsParseResult.Unparsed

        // Normalize whitespace (including multi-line linebreaks and tabs) to simplify regex matching
        val normalizedBody = body.replace(Regex("\\s+"), " ").trim()

        for (template in templates) {
            if (template.senderMatcher(sender)) {
                val match = template.pattern.find(normalizedBody)
                if (match != null) {
                    try {
                        val parsed = template.mapper(match)
                        return SmsParseResult.Parsed(parsed)
                    } catch (_: Exception) {
                        // Any extraction/mapping glitch safely falls back to unparsed
                        return SmsParseResult.Unparsed
                    }
                }
            }
        }
        return SmsParseResult.Unparsed
    }

    companion object {
        val NAIROBI_ZONE: ZoneId = ZoneId.of("Africa/Nairobi")

        fun createDefault(): TemplateSmsParser = TemplateSmsParser(defaultTemplates())

        fun defaultTemplates(): List<Template> = listOf(
            // Specific M-Pesa templates (must precede general P2P sent)
            mpesaPaybillTemplate(),
            mpesaBuyGoodsTemplate(),
            mpesaWithdrawTemplate(),
            mpesaAirtimeTemplate(),
            mpesaReversalTemplate(),
            mpesaSentStandardTemplate(),
            mpesaSentLegacyTemplate(),
            mpesaReceivedStandardTemplate(),
            mpesaReceivedLegacyTemplate(),

            // Equity Bank templates
            equitySpentMerchantTemplate(),
            equityAccountDebitTemplate(),
            equityAccountCreditTemplate(),

            // KCB Bank templates
            kcbDebitBriefTemplate(),
            kcbAccountDebitTemplate(),
            kcbAccountCreditTemplate()
        )

        // --------------------------------------------------------------------
        // Helpers
        // --------------------------------------------------------------------
        private fun isMpesaSender(sender: String): Boolean =
            sender.equals("MPESA", ignoreCase = true) ||
            sender.equals("M-PESA", ignoreCase = true)

        private fun isEquitySender(sender: String): Boolean =
            sender.equals("EQUITY", ignoreCase = true) ||
            sender.equals("EQUITYBANK", ignoreCase = true) ||
            sender.equals("EQUITYBC", ignoreCase = true) ||
            sender.equals("EquityBank", ignoreCase = true)

        private fun isKcbSender(sender: String): Boolean =
            sender.equals("KCB", ignoreCase = true) ||
            sender.equals("KCBBANK", ignoreCase = true) ||
            sender.equals("KCBGroup", ignoreCase = true)

        fun parseAmount(raw: String?): Long {
            if (raw.isNullOrBlank()) return 0L
            val cleaned = raw.replace(Regex("[^0-9.]"), "")
            val whole = cleaned.substringBefore('.')
            return whole.toLongOrNull() ?: 0L
        }

        fun parseOptionalAmount(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            val cleaned = raw.replace(Regex("[^0-9.]"), "")
            if (cleaned.isEmpty()) return null
            val whole = cleaned.substringBefore('.')
            return whole.toLongOrNull()
        }

        fun parseNairobiDateTime(raw: String): OffsetDateTime {
            val cleaned = raw.replace(Regex("\\s+"), " ").trim()
            val patterns = listOf(
                "dd/MM/yyyy 'at' h:mm a",
                "dd/MM/yyyy 'at' hh:mm a",
                "dd/MM/yyyy h:mm a",
                "dd/MM/yyyy hh:mm a",
                "dd/MM/yyyy 'at' HH:mm:ss",
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy 'at' HH:mm",
                "dd/MM/yyyy HH:mm",
                "yyyy-MM-dd HH:mm:ss",
                "dd/MM/yyyy"
            )
            for (pattern in patterns) {
                try {
                    val formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
                    if (pattern.contains("HH:mm") || pattern.contains("h:mm")) {
                        val local = LocalDateTime.parse(cleaned, formatter)
                        return local.atZone(NAIROBI_ZONE).toOffsetDateTime()
                    } else {
                        val localDate = LocalDate.parse(cleaned, formatter)
                        return localDate.atStartOfDay(NAIROBI_ZONE).toOffsetDateTime()
                    }
                } catch (_: Exception) {
                    // Try next pattern
                }
            }
            return OffsetDateTime.now(NAIROBI_ZONE)
        }

        // --------------------------------------------------------------------
        // M-PESA Templates
        // --------------------------------------------------------------------

        /** Standard Safaricom P2P sent message. */
        private fun mpesaSentStandardTemplate(): Template {
            val regex = Regex(
                "(?i)^(?<receipt>[A-Z0-9]+)\\s+Confirmed\\.\\s*(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+sent\\s+to\\s+(?<counterparty>.+?)\\s+on\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{4}(?:\\s+at|\\s+)\\s+\\d{1,2}:\\d{2}\\s+[AP]M)\\.\\s*New\\s+M-PESA\\s+balance\\s+is\\s+(?:Ksh|KES)\\s*(?<balance>[\\d,]+(?:\\.\\d{2})?)\\.(?:\\s*Transaction\\s+cost,?\\s*(?:Ksh|KES)\\s*(?<fee>[\\d,]+(?:\\.\\d{2})?)\\.)?"
            )
            return Template(
                templateId = "MPESA_SENT_STANDARD",
                senderMatcher = ::isMpesaSender,
                pattern = regex,
                mapper = { m ->
                    val receipt = m.groups["receipt"]!!.value
                    val amount = parseAmount(m.groups["amount"]!!.value)
                    val counterparty = m.groups["counterparty"]!!.value.trim()
                    val timestamp = parseNairobiDateTime(m.groups["date"]!!.value)
                    val balance = parseOptionalAmount(m.groups["balance"]?.value)
                    val fee = parseOptionalAmount(m.groups["fee"]?.value)
                    ParsedSms(
                        amountKsh = -amount,
                        timestamp = timestamp,
                        description = "sent to $counterparty",
                        receiptCode = receipt,
                        transactionType = TransactionType.SENT,
                        counterparty = counterparty,
                        balanceKsh = balance,
                        feeKsh = fee
                    )
                }
            )
        }

        /** Backwards-compatible legacy M-Pesa sent message. */
        private fun mpesaSentLegacyTemplate(): Template {
            val regex = Regex(
                "(?i)(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+sent\\s+to\\s+(?<counterparty>[^.]+?)\\s+on\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\d{1,2}:\\d{2}\\s+[AP]M)\\..*?Ref:\\s*(?<receipt>[A-Z0-9]+)"
            )
            return Template(
                templateId = "MPESA_SENT_LEGACY",
                senderMatcher = ::isMpesaSender,
                pattern = regex,
                mapper = { m ->
                    val amount = parseAmount(m.groups["amount"]!!.value)
                    val counterparty = m.groups["counterparty"]!!.value.trim()
                    val timestamp = parseNairobiDateTime(m.groups["date"]!!.value)
                    val receipt = m.groups["receipt"]!!.value
                    ParsedSms(
                        amountKsh = -amount,
                        timestamp = timestamp,
                        description = "sent to $counterparty",
                        receiptCode = receipt,
                        transactionType = TransactionType.SENT,
                        counterparty = counterparty
                    )
                }
            )
        }

        /** Standard Safaricom P2P received message. */
        private fun mpesaReceivedStandardTemplate(): Template {
            val regex = Regex(
                "(?i)^(?<receipt>[A-Z0-9]+)\\s+Confirmed\\.\\s*You\\s+have\\s+received\\s+(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+from\\s+(?<counterparty>.+?)\\s+on\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{4}(?:\\s+at|\\s+)\\s+\\d{1,2}:\\d{2}\\s+[AP]M)\\.\\s*New\\s+M-PESA\\s+balance\\s+is\\s+(?:Ksh|KES)\\s*(?<balance>[\\d,]+(?:\\.\\d{2})?)\\."
            )
            return Template(
                templateId = "MPESA_RECEIVED_STANDARD",
                senderMatcher = ::isMpesaSender,
                pattern = regex,
                mapper = { m ->
                    val receipt = m.groups["receipt"]!!.value
                    val amount = parseAmount(m.groups["amount"]!!.value)
                    val counterparty = m.groups["counterparty"]!!.value.trim()
                    val timestamp = parseNairobiDateTime(m.groups["date"]!!.value)
                    val balance = parseOptionalAmount(m.groups["balance"]?.value)
                    ParsedSms(
                        amountKsh = amount,
                        timestamp = timestamp,
                        description = "received from $counterparty",
                        receiptCode = receipt,
                        transactionType = TransactionType.RECEIVED,
                        counterparty = counterparty,
                        balanceKsh = balance,
                        feeKsh = 0L
                    )
                }
            )
        }

        /** Backwards-compatible legacy M-Pesa received message. */
        private fun mpesaReceivedLegacyTemplate(): Template {
            val regex = Regex(
                "(?i)(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+received\\s+(?:to|from)\\s+(?<counterparty>[^.]+?)\\s+on\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\d{1,2}:\\d{2}\\s+[AP]M)\\..*?Ref:\\s*(?<receipt>[A-Z0-9]+)"
            )
            return Template(
                templateId = "MPESA_RECEIVED_LEGACY",
                senderMatcher = ::isMpesaSender,
                pattern = regex,
                mapper = { m ->
                    val amount = parseAmount(m.groups["amount"]!!.value)
                    val counterparty = m.groups["counterparty"]!!.value.trim()
                    val timestamp = parseNairobiDateTime(m.groups["date"]!!.value)
                    val receipt = m.groups["receipt"]!!.value
                    ParsedSms(
                        amountKsh = amount,
                        timestamp = timestamp,
                        description = "received to $counterparty",
                        receiptCode = receipt,
                        transactionType = TransactionType.RECEIVED,
                        counterparty = counterparty
                    )
                }
            )
        }

        /** Safaricom Pay Bill payment. */
        private fun mpesaPaybillTemplate(): Template {
            val regex = Regex(
                "(?i)^(?<receipt>[A-Z0-9]+)\\s+Confirmed\\.\\s*(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+sent\\s+to\\s+(?<business>.+?)\\s+for\\s+account\\s+(?<account>.+?)\\s+on\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{4}(?:\\s+at|\\s+)\\s+\\d{1,2}:\\d{2}\\s+[AP]M)\\.\\s*New\\s+M-PESA\\s+balance\\s+is\\s+(?:Ksh|KES)\\s*(?<balance>[\\d,]+(?:\\.\\d{2})?)\\.(?:\\s*Transaction\\s+cost,?\\s*(?:Ksh|KES)\\s*(?<fee>[\\d,]+(?:\\.\\d{2})?)\\.)?"
            )
            return Template(
                templateId = "MPESA_PAYBILL",
                senderMatcher = ::isMpesaSender,
                pattern = regex,
                mapper = { m ->
                    val receipt = m.groups["receipt"]!!.value
                    val amount = parseAmount(m.groups["amount"]!!.value)
                    val business = m.groups["business"]!!.value.trim()
                    val account = m.groups["account"]!!.value.trim()
                    val counterparty = "$business (Acc: $account)"
                    val timestamp = parseNairobiDateTime(m.groups["date"]!!.value)
                    val balance = parseOptionalAmount(m.groups["balance"]?.value)
                    val fee = parseOptionalAmount(m.groups["fee"]?.value)
                    ParsedSms(
                        amountKsh = -amount,
                        timestamp = timestamp,
                        description = "Paybill to $counterparty",
                        receiptCode = receipt,
                        transactionType = TransactionType.PAYBILL,
                        counterparty = counterparty,
                        balanceKsh = balance,
                        feeKsh = fee
                    )
                }
            )
        }

        /** Safaricom Buy Goods and Services (Till). */
        private fun mpesaBuyGoodsTemplate(): Template {
            val regex = Regex(
                "(?i)^(?<receipt>[A-Z0-9]+)\\s+Confirmed\\.\\s*(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+paid\\s+to\\s+(?<merchant>.+?)\\s+on\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{4}(?:\\s+at|\\s+)\\s+\\d{1,2}:\\d{2}\\s+[AP]M)\\.\\s*New\\s+M-PESA\\s+balance\\s+is\\s+(?:Ksh|KES)\\s*(?<balance>[\\d,]+(?:\\.\\d{2})?)\\.(?:\\s*Transaction\\s+cost,?\\s*(?:Ksh|KES)\\s*(?<fee>[\\d,]+(?:\\.\\d{2})?)\\.)?"
            )
            return Template(
                templateId = "MPESA_BUY_GOODS",
                senderMatcher = ::isMpesaSender,
                pattern = regex,
                mapper = { m ->
                    val receipt = m.groups["receipt"]!!.value
                    val amount = parseAmount(m.groups["amount"]!!.value)
                    val merchant = m.groups["merchant"]!!.value.trim()
                    val timestamp = parseNairobiDateTime(m.groups["date"]!!.value)
                    val balance = parseOptionalAmount(m.groups["balance"]?.value)
                    val fee = parseOptionalAmount(m.groups["fee"]?.value) ?: 0L
                    ParsedSms(
                        amountKsh = -amount,
                        timestamp = timestamp,
                        description = "Buy Goods to $merchant",
                        receiptCode = receipt,
                        transactionType = TransactionType.TILL,
                        counterparty = merchant,
                        balanceKsh = balance,
                        feeKsh = fee
                    )
                }
            )
        }

        /** Safaricom Cash Withdrawal (Agent or ATM). */
        private fun mpesaWithdrawTemplate(): Template {
            val regex = Regex(
                "(?i)^(?<receipt>[A-Z0-9]+)\\s+Confirmed\\.\\s*On\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{4}(?:\\s+at|\\s+)\\s+\\d{1,2}:\\d{2}\\s+[AP]M)\\s*(?:Withdraw|Withdrawal\\s+of)\\s+(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+from\\s+(?<agent>.+?)\\.\\s*New\\s+M-PESA\\s+balance\\s+is\\s+(?:Ksh|KES)\\s*(?<balance>[\\d,]+(?:\\.\\d{2})?)\\.(?:\\s*Transaction\\s+cost,?\\s*(?:Ksh|KES)\\s*(?<fee>[\\d,]+(?:\\.\\d{2})?)\\.)?"
            )
            return Template(
                templateId = "MPESA_WITHDRAW",
                senderMatcher = ::isMpesaSender,
                pattern = regex,
                mapper = { m ->
                    val receipt = m.groups["receipt"]!!.value
                    val amount = parseAmount(m.groups["amount"]!!.value)
                    val agent = m.groups["agent"]!!.value.trim()
                    val timestamp = parseNairobiDateTime(m.groups["date"]!!.value)
                    val balance = parseOptionalAmount(m.groups["balance"]?.value)
                    val fee = parseOptionalAmount(m.groups["fee"]?.value)
                    ParsedSms(
                        amountKsh = -amount,
                        timestamp = timestamp,
                        description = "Withdraw from $agent",
                        receiptCode = receipt,
                        transactionType = TransactionType.WITHDRAW,
                        counterparty = agent,
                        balanceKsh = balance,
                        feeKsh = fee
                    )
                }
            )
        }

        /** Safaricom Airtime purchase. */
        private fun mpesaAirtimeTemplate(): Template {
            val regex = Regex(
                "(?i)^(?<receipt>[A-Z0-9]+)\\s+Confirmed\\.\\s*You\\s+bought\\s+(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+of\\s+airtime(?:\\s+for\\s+(?<recipient>\\S+))?\\s+on\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{4}(?:\\s+at|\\s+)\\s+\\d{1,2}:\\d{2}\\s+[AP]M)\\.\\s*New\\s+M-PESA\\s+balance\\s+is\\s+(?:Ksh|KES)\\s*(?<balance>[\\d,]+(?:\\.\\d{2})?)\\."
            )
            return Template(
                templateId = "MPESA_AIRTIME",
                senderMatcher = ::isMpesaSender,
                pattern = regex,
                mapper = { m ->
                    val receipt = m.groups["receipt"]!!.value
                    val amount = parseAmount(m.groups["amount"]!!.value)
                    val recipient = m.groups["recipient"]?.value?.trim() ?: "My Phone"
                    val timestamp = parseNairobiDateTime(m.groups["date"]!!.value)
                    val balance = parseOptionalAmount(m.groups["balance"]?.value)
                    ParsedSms(
                        amountKsh = -amount,
                        timestamp = timestamp,
                        description = "Airtime for $recipient",
                        receiptCode = receipt,
                        transactionType = TransactionType.AIRTIME,
                        counterparty = "Safaricom Airtime ($recipient)",
                        balanceKsh = balance,
                        feeKsh = 0L
                    )
                }
            )
        }

        /** Safaricom Transaction Reversal. */
        private fun mpesaReversalTemplate(): Template {
            val regex = Regex(
                "(?i)^(?<receipt>[A-Z0-9]+)\\s+Confirmed\\.\\s*Reversal\\s+of\\s+transaction\\s+(?<origReceipt>[A-Z0-9]+)\\s+has\\s+been\\s+completed\\s+on\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{4}(?:\\s+at|\\s+)\\s+\\d{1,2}:\\d{2}\\s+[AP]M)\\s+and\\s+(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+has\\s+been\\s+credited\\s+to\\s+your\\s+M-PESA\\s+account\\.\\s*New\\s+M-PESA\\s+balance\\s+is\\s+(?:Ksh|KES)\\s*(?<balance>[\\d,]+(?:\\.\\d{2})?)\\."
            )
            return Template(
                templateId = "MPESA_REVERSAL",
                senderMatcher = ::isMpesaSender,
                pattern = regex,
                mapper = { m ->
                    val receipt = m.groups["receipt"]!!.value
                    val origReceipt = m.groups["origReceipt"]!!.value
                    val amount = parseAmount(m.groups["amount"]!!.value)
                    val timestamp = parseNairobiDateTime(m.groups["date"]!!.value)
                    val balance = parseOptionalAmount(m.groups["balance"]?.value)
                    ParsedSms(
                        amountKsh = amount, // Credit reversal is positive
                        timestamp = timestamp,
                        description = "Reversal of $origReceipt",
                        receiptCode = receipt,
                        transactionType = TransactionType.REVERSAL,
                        counterparty = "Reversal ($origReceipt)",
                        balanceKsh = balance,
                        feeKsh = 0L
                    )
                }
            )
        }

        // --------------------------------------------------------------------
        // EQUITY BANK Templates
        // --------------------------------------------------------------------

        /** Equity Bank POS/Merchant Spend (legacy and modern). */
        private fun equitySpentMerchantTemplate(): Template {
            val regex = Regex(
                "(?i)spent\\s+(?:KES|Ksh)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+at\\s+(?<merchant>[^.]+?)\\s+on\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?\\s*[AP]?M?)\\.\\s*Transaction\\s+Ref:\\s*(?<receipt>[A-Z0-9]+)"
            )
            return Template(
                templateId = "EQUITY_SPENT_MERCHANT",
                senderMatcher = ::isEquitySender,
                pattern = regex,
                mapper = { m ->
                    val amount = parseAmount(m.groups["amount"]!!.value)
                    val merchant = m.groups["merchant"]!!.value.trim()
                    val timestamp = parseNairobiDateTime(m.groups["date"]!!.value)
                    val receipt = m.groups["receipt"]!!.value
                    ParsedSms(
                        amountKsh = -amount,
                        timestamp = timestamp,
                        description = "spent at $merchant",
                        receiptCode = receipt,
                        transactionType = TransactionType.SENT,
                        counterparty = merchant
                    )
                }
            )
        }

        /** Equity Bank Account Debit. */
        private fun equityAccountDebitTemplate(): Template {
            val regex = Regex(
                "(?i)(?:Your\\s+A/C\\s+(?<account>\\S+)\\s+has\\s+been\\s+debited|Debit\\s+of)\\s+(?:KES|Ksh)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+on\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?\\s*[AP]?M?)(?:\\s+at\\s+(?<merchant>[^.]+?))?\\.\\s*(?:Available\\s+balance\\s+is\\s+(?:KES|Ksh)\\s*(?<balance>[\\d,]+(?:\\.\\d{2})?)\\.)?.*?Ref:\\s*(?<receipt>[A-Z0-9]+)"
            )
            return Template(
                templateId = "EQUITY_DEBIT",
                senderMatcher = ::isEquitySender,
                pattern = regex,
                mapper = { m ->
                    val amount = parseAmount(m.groups["amount"]!!.value)
                    val merchant = m.groups["merchant"]?.value?.trim() ?: "Equity Debit"
                    val timestamp = parseNairobiDateTime(m.groups["date"]!!.value)
                    val receipt = m.groups["receipt"]!!.value
                    val balance = parseOptionalAmount(m.groups["balance"]?.value)
                    ParsedSms(
                        amountKsh = -amount,
                        timestamp = timestamp,
                        description = "Equity Debit at $merchant",
                        receiptCode = receipt,
                        transactionType = TransactionType.EXPENSE,
                        counterparty = merchant,
                        balanceKsh = balance
                    )
                }
            )
        }

        /** Equity Bank Account Credit (e.g. Salary / Incoming funds). */
        private fun equityAccountCreditTemplate(): Template {
            val regex = Regex(
                "(?i)(?:Your\\s+A/C\\s+(?<account>\\S+)\\s+has\\s+been\\s+credited|Credit\\s+of)\\s+(?:KES|Ksh)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+on\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?\\s*[AP]?M?)(?:\\s+by\\s+(?<sender>[^.]+?))?\\.\\s*(?:Available\\s+balance\\s+is\\s+(?:KES|Ksh)\\s*(?<balance>[\\d,]+(?:\\.\\d{2})?)\\.)?.*?Ref:\\s*(?<receipt>[A-Z0-9]+)"
            )
            return Template(
                templateId = "EQUITY_CREDIT",
                senderMatcher = ::isEquitySender,
                pattern = regex,
                mapper = { m ->
                    val amount = parseAmount(m.groups["amount"]!!.value)
                    val sender = m.groups["sender"]?.value?.trim() ?: "Equity Credit"
                    val timestamp = parseNairobiDateTime(m.groups["date"]!!.value)
                    val receipt = m.groups["receipt"]!!.value
                    val balance = parseOptionalAmount(m.groups["balance"]?.value)
                    ParsedSms(
                        amountKsh = amount,
                        timestamp = timestamp,
                        description = "Equity Credit by $sender",
                        receiptCode = receipt,
                        transactionType = TransactionType.INCOME,
                        counterparty = sender,
                        balanceKsh = balance
                    )
                }
            )
        }

        // --------------------------------------------------------------------
        // KCB BANK Templates
        // --------------------------------------------------------------------

        /** KCB Brief Debit (legacy). */
        private fun kcbDebitBriefTemplate(): Template {
            val regex = Regex(
                "(?i)KCB:\\s+Debit\\s+of\\s+(?:KES|Ksh)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+on\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?\\s*[AP]?M?)\\.\\s*Ref\\s*No:\\s*(?<receipt>[A-Z0-9]+)"
            )
            return Template(
                templateId = "KCB_DEBIT_BRIEF",
                senderMatcher = ::isKcbSender,
                pattern = regex,
                mapper = { m ->
                    val amount = parseAmount(m.groups["amount"]!!.value)
                    val timestamp = parseNairobiDateTime(m.groups["date"]!!.value)
                    val receipt = m.groups["receipt"]!!.value
                    ParsedSms(
                        amountKsh = -amount,
                        timestamp = timestamp,
                        description = "debit via KCB",
                        receiptCode = receipt,
                        transactionType = TransactionType.SENT,
                        counterparty = "KCB"
                    )
                }
            )
        }

        /** KCB Detailed Account Debit. */
        private fun kcbAccountDebitTemplate(): Template {
            val regex = Regex(
                "(?i)(?:Dear\\s+Customer,\\s*)?(?:KES|Ksh)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+has\\s+been\\s+debited\\s+from\\s+your\\s+A/C\\s+(?<account>\\S+)\\s+on\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{4}(?:\\s+at|\\s+)\\s+\\d{1,2}:\\d{2}(?::\\d{2})?\\s*[AP]?M?)(?:\\s+at\\s+(?<merchant>[^.]+?))?\\.\\s*(?:Bal\\s+(?:KES|Ksh)\\s*(?<balance>[\\d,]+(?:\\.\\d{2})?)\\.)?.*?Ref:\\s*(?<receipt>[A-Z0-9]+)"
            )
            return Template(
                templateId = "KCB_DEBIT",
                senderMatcher = ::isKcbSender,
                pattern = regex,
                mapper = { m ->
                    val amount = parseAmount(m.groups["amount"]!!.value)
                    val merchant = m.groups["merchant"]?.value?.trim() ?: "KCB Debit"
                    val timestamp = parseNairobiDateTime(m.groups["date"]!!.value)
                    val receipt = m.groups["receipt"]!!.value
                    val balance = parseOptionalAmount(m.groups["balance"]?.value)
                    ParsedSms(
                        amountKsh = -amount,
                        timestamp = timestamp,
                        description = "KCB Debit at $merchant",
                        receiptCode = receipt,
                        transactionType = TransactionType.EXPENSE,
                        counterparty = merchant,
                        balanceKsh = balance
                    )
                }
            )
        }

        /** KCB Detailed Account Credit. */
        private fun kcbAccountCreditTemplate(): Template {
            val regex = Regex(
                "(?i)(?:Dear\\s+Customer,\\s*)?(?:KES|Ksh)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+has\\s+been\\s+credited\\s+to\\s+your\\s+A/C\\s+(?<account>\\S+)\\s+on\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{4}(?:\\s+at|\\s+)\\s+\\d{1,2}:\\d{2}(?::\\d{2})?\\s*[AP]?M?)(?:\\s+by\\s+(?<sender>[^.]+?))?\\.\\s*(?:Bal\\s+(?:KES|Ksh)\\s*(?<balance>[\\d,]+(?:\\.\\d{2})?)\\.)?.*?Ref:\\s*(?<receipt>[A-Z0-9]+)"
            )
            return Template(
                templateId = "KCB_CREDIT",
                senderMatcher = ::isKcbSender,
                pattern = regex,
                mapper = { m ->
                    val amount = parseAmount(m.groups["amount"]!!.value)
                    val sender = m.groups["sender"]?.value?.trim() ?: "KCB Credit"
                    val timestamp = parseNairobiDateTime(m.groups["date"]!!.value)
                    val receipt = m.groups["receipt"]!!.value
                    val balance = parseOptionalAmount(m.groups["balance"]?.value)
                    ParsedSms(
                        amountKsh = amount,
                        timestamp = timestamp,
                        description = "KCB Credit by $sender",
                        receiptCode = receipt,
                        transactionType = TransactionType.INCOME,
                        counterparty = sender,
                        balanceKsh = balance
                    )
                }
            )
        }
    }
}
