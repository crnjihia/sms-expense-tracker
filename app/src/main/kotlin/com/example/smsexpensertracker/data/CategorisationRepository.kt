// path: app/src/main/kotlin/com/example/smsexpensertracker/data/CategorisationRepository.kt
package com.example.smsexpensertracker.data

import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Intelligent categorization rule engine with strict rule precedence:
 *
 * 1. User & Learned Overrides ("always categorise X as Y" stored in [RuleDao]) - Highest precedence.
 *    Most specific (longest keyword) match wins if multiple user rules match.
 * 2. Paybill / Till specific business mappings (e.g. KPLC 888888, Nairobi Water 444400, Zuku 320320).
 * 3. Default Kenyan merchant keywords (e.g. Naivas, Quickmart, Uber, Java House).
 */
class CategorisationRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val ruleDao: RuleDao,
    private val categoryDao: CategoryDao? = null
) {

    /**
     * Applies categorisation rules to all transactions that currently have no category.
     * Evaluates user overrides first, then paybill/till mappings, then merchant keywords.
     *
     * @return Number of transactions that were updated with a category.
     */
    suspend fun applyRules(): Int {
        val uncategorised = transactionDao.getUncategorisedTransactions()
        if (uncategorised.isEmpty()) return 0

        val userRules = ruleDao.getAllRules().first()
        var updatedCount = 0

        for (txn in uncategorised) {
            val resolvedCategoryId = resolveCategoryForTransaction(txn, userRules)
            if (resolvedCategoryId != null) {
                transactionDao.update(txn.copy(categoryId = resolvedCategoryId))
                updatedCount++
            }
        }
        return updatedCount
    }

    /**
     * Re-categorises all transactions in the database using the latest rules.
     * User overrides are strictly respected over default mappings.
     *
     * @return Number of transactions whose category changed.
     */
    suspend fun recategoriseAll(): Int {
        val allTransactions = transactionDao.getAllTransactions().first()
        if (allTransactions.isEmpty()) return 0

        val userRules = ruleDao.getAllRules().first()
        var updatedCount = 0

        for (txn in allTransactions) {
            val resolvedCategoryId = resolveCategoryForTransaction(txn, userRules)
            if (resolvedCategoryId != null && resolvedCategoryId != txn.categoryId) {
                transactionDao.update(txn.copy(categoryId = resolvedCategoryId))
                updatedCount++
            }
        }
        return updatedCount
    }

    /**
     * Learned override ("Always categorise X as Y"):
     * 1. Inserts or updates a user rule in [RuleDao].
     * 2. Immediately updates all existing transactions (both categorized and uncategorized)
     *    matching the keyword.
     *
     * @param keyword The merchant name, paybill number, or search keyword (e.g. "NAIVAS", "KPLC").
     * @param categoryId Target category to assign.
     * @return Number of matching transactions that were updated.
     */
    suspend fun learnOverride(keyword: String, categoryId: Long): Int {
        val cleanKeyword = keyword.trim()
        if (cleanKeyword.isBlank()) return 0

        val allRules = ruleDao.getAllRules().first()
        val existingRule = allRules.find { it.keyword.equals(cleanKeyword, ignoreCase = true) }

        if (existingRule != null) {
            ruleDao.update(existingRule.copy(categoryId = categoryId))
        } else {
            ruleDao.insert(CategorisationRule(categoryId = categoryId, keyword = cleanKeyword))
        }

        // Apply this new override to all matching transactions in the database
        val allTransactions = transactionDao.getAllTransactions().first()
        var updatedCount = 0

        for (txn in allTransactions) {
            val matches = txn.counterparty?.contains(cleanKeyword, ignoreCase = true) == true ||
                          txn.description.contains(cleanKeyword, ignoreCase = true)

            if (matches && txn.categoryId != categoryId) {
                transactionDao.update(txn.copy(categoryId = categoryId))
                updatedCount++
            }
        }
        return updatedCount
    }

    /**
     * Resolves the appropriate category ID for a transaction following the precedence hierarchy.
     */
    suspend fun resolveCategoryForTransaction(
        txn: Transaction,
        userRules: List<CategorisationRule>? = null
    ): Long? {
        val rules = userRules ?: ruleDao.getAllRules().first()

        // 1. User overrides (Highest Precedence) - Longest keyword match wins
        val matchingUserRules = rules.filter { rule ->
            (txn.counterparty?.contains(rule.keyword, ignoreCase = true) == true) ||
            txn.description.contains(rule.keyword, ignoreCase = true)
        }
        if (matchingUserRules.isNotEmpty()) {
            return matchingUserRules.maxByOrNull { it.keyword.length }!!.categoryId
        }

        // 2. Paybill / Till Mappings
        val paybillCategory = resolvePaybillRule(txn)
        if (paybillCategory != null) {
            return paybillCategory
        }

        // 3. Default Kenyan Merchant Keywords
        val merchantCategory = resolveMerchantRule(txn)
        if (merchantCategory != null) {
            return merchantCategory
        }

        return null
    }

    private suspend fun resolvePaybillRule(txn: Transaction): Long? {
        if (categoryDao == null) return null
        for (rule in DEFAULT_PAYBILL_RULES) {
            val matches = txn.counterparty?.contains(rule.keyword, ignoreCase = true) == true ||
                          txn.description.contains(rule.keyword, ignoreCase = true)
            if (matches) {
                return categoryDao.findByName(rule.categoryName)?.id
            }
        }
        return null
    }

    private suspend fun resolveMerchantRule(txn: Transaction): Long? {
        if (categoryDao == null) return null
        for (rule in DEFAULT_MERCHANT_RULES) {
            val matches = txn.counterparty?.contains(rule.keyword, ignoreCase = true) == true ||
                          txn.description.contains(rule.keyword, ignoreCase = true)
            if (matches) {
                return categoryDao.findByName(rule.categoryName)?.id
            }
        }
        return null
    }

    companion object {
        data class PredefinedRule(val keyword: String, val categoryName: String)

        val DEFAULT_PAYBILL_RULES = listOf(
            PredefinedRule("888888", "Utilities"),
            PredefinedRule("888880", "Utilities"),
            PredefinedRule("KPLC", "Utilities"),
            PredefinedRule("444400", "Utilities"),
            PredefinedRule("NAIROBI WATER", "Utilities"),
            PredefinedRule("320320", "Utilities"),
            PredefinedRule("ZUKU", "Utilities"),
            PredefinedRule("222222", "Utilities")
        )

        val DEFAULT_MERCHANT_RULES = listOf(
            // Groceries
            PredefinedRule("NAIVAS", "Groceries"),
            PredefinedRule("QUICKMART", "Groceries"),
            PredefinedRule("CARREFOUR", "Groceries"),
            PredefinedRule("CHANDARANA", "Groceries"),
            PredefinedRule("CLEANSHELF", "Groceries"),
            PredefinedRule("FOODPLUS", "Groceries"),

            // Transport
            PredefinedRule("UBER", "Transport"),
            PredefinedRule("BOLT", "Transport"),
            PredefinedRule("LITTLE CAB", "Transport"),
            PredefinedRule("TOTAL", "Transport"),
            PredefinedRule("SHELL", "Transport"),
            PredefinedRule("RUBIS", "Transport"),
            PredefinedRule("OLA ENERGY", "Transport"),

            // Food & Dining
            PredefinedRule("JAVA HOUSE", "Food & Dining"),
            PredefinedRule("ARTCAFFE", "Food & Dining"),
            PredefinedRule("KFC", "Food & Dining"),
            PredefinedRule("DOMINOS", "Food & Dining"),
            PredefinedRule("PIZZA INN", "Food & Dining"),
            PredefinedRule("CHICKEN INN", "Food & Dining"),
            PredefinedRule("GLOVO", "Food & Dining"),

            // Airtime & Mobile
            PredefinedRule("AIRTIME", "Airtime & Data"),
            PredefinedRule("SAFARICOM AIRTIME", "Airtime & Data"),
            PredefinedRule("SAFARICOM DATA", "Airtime & Data"),

            // Healthcare
            PredefinedRule("PHARMACY", "Health"),
            PredefinedRule("HOSPITAL", "Health"),
            PredefinedRule("CHEMIST", "Health"),
            PredefinedRule("AGA KHAN", "Health"),
            PredefinedRule("NAIROBI HOSPITAL", "Health"),
            PredefinedRule("GERTRUDES", "Health")
        )
    }
}
