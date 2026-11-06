package com.example.smsexpensertracker.data

import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Repository that applies categorisation rules to uncategorised transactions. */
class CategorisationRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val ruleDao: RuleDao
) {
    /**
     * Applies all categorisation rules to transactions that currently have no category.
     * Returns the number of transactions that were updated.
     */
    suspend fun applyRules(): Int {
        // Load uncategorised transactions.
        val uncategorised = transactionDao.getUncategorisedTransactions()
        if (uncategorised.isEmpty()) return 0
        // Load all rules (use first emission of the Flow).
        val rules = ruleDao.getAllRules().first()
        var updated = 0
        for (txn in uncategorised) {
            val matchingRule = rules.firstOrNull { rule ->
                txn.description.contains(rule.keyword, ignoreCase = true)
            }
            if (matchingRule != null) {
                val updatedTxn = txn.copy(categoryId = matchingRule.categoryId)
                transactionDao.update(updatedTxn)
                updated++
            }
        }
        return updated
    }
}
