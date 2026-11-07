// path: app/src/test/java/com/example/smsexpensertracker/data/CategorisationRepositoryTest.kt
package com.example.smsexpensertracker.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class CategorisationRepositoryTest {

    private lateinit var fakeTransactionDao: FakeTransactionDao
    private lateinit var fakeRuleDao: FakeRuleDao
    private lateinit var fakeCategoryDao: FakeCategoryDao
    private lateinit var repository: CategorisationRepository

    @BeforeEach
    fun setUp() {
        fakeTransactionDao = FakeTransactionDao()
        fakeRuleDao = FakeRuleDao()
        fakeCategoryDao = FakeCategoryDao()

        // Populate standard categories in fakeCategoryDao
        fakeCategoryDao.categories.addAll(
            listOf(
                Category(id = 1L, name = "Groceries"),
                Category(id = 2L, name = "Utilities"),
                Category(id = 3L, name = "Transport"),
                Category(id = 4L, name = "Food & Dining"),
                Category(id = 5L, name = "Airtime & Data")
            )
        )

        repository = CategorisationRepository(fakeTransactionDao, fakeRuleDao, fakeCategoryDao)
    }

    @Test
    fun `applyRules updates matching uncategorised transactions`() = runBlocking {
        fakeRuleDao.rules.add(CategorisationRule(id = 1L, categoryId = 10L, keyword = "coffee"))
        val txn = Transaction(
            id = 100L,
            amount = -500,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            description = "Bought coffee at cafe",
            categoryId = null
        )
        fakeTransactionDao.transactions.add(txn)

        val updatedCount = repository.applyRules()

        assertEquals(1, updatedCount)
        assertEquals(10L, fakeTransactionDao.transactions.first().categoryId)
    }

    @Test
    fun `applyRules ignores transactions that already have a category`() = runBlocking {
        fakeRuleDao.rules.add(CategorisationRule(id = 1L, categoryId = 10L, keyword = "coffee"))
        val txn = Transaction(
            id = 101L,
            amount = -500,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            description = "Bought coffee at cafe",
            categoryId = 99L
        )
        fakeTransactionDao.transactions.add(txn)

        val updatedCount = repository.applyRules()

        assertEquals(0, updatedCount)
        assertEquals(99L, fakeTransactionDao.transactions.first().categoryId)
    }

    @Test
    fun `applyRules is case insensitive`() = runBlocking {
        fakeRuleDao.rules.add(CategorisationRule(id = 1L, categoryId = 20L, keyword = "JAVA HOUSE"))
        val txn = Transaction(
            id = 102L,
            amount = -1200,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            description = "Payment to java house westlands",
            categoryId = null
        )
        fakeTransactionDao.transactions.add(txn)

        val updatedCount = repository.applyRules()

        assertEquals(1, updatedCount)
        assertEquals(20L, fakeTransactionDao.transactions.first().categoryId)
    }

    // ========================================================================
    // RULE PRECEDENCE TESTS
    // ========================================================================

    @Test
    fun `precedence - user override rule takes precedence over default merchant rule`() = runBlocking {
        // Default merchant rule would categorize "NAIVAS" as Groceries (id = 1L).
        // User explicitly defines a custom override for "NAIVAS" -> Category 99L ("Household Supplies").
        fakeRuleDao.rules.add(CategorisationRule(id = 1L, categoryId = 99L, keyword = "NAIVAS"))

        val txn = Transaction(
            id = 103L,
            amount = -4200L,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            description = "Buy Goods to NAIVAS SUPERMARKET",
            counterparty = "NAIVAS SUPERMARKET",
            categoryId = null
        )
        fakeTransactionDao.transactions.add(txn)

        val updatedCount = repository.applyRules()

        assertEquals(1, updatedCount)
        assertEquals(99L, fakeTransactionDao.transactions.first().categoryId)
    }

    @Test
    fun `precedence - longest user keyword match wins over shorter generic rule`() = runBlocking {
        // Shorter rule: "JAVA" -> Category 4L (Food & Dining)
        fakeRuleDao.rules.add(CategorisationRule(id = 1L, categoryId = 4L, keyword = "JAVA"))
        // Longer, more specific rule: "JAVA HOUSE MEETINGS" -> Category 50L (Business Meetings)
        fakeRuleDao.rules.add(CategorisationRule(id = 2L, categoryId = 50L, keyword = "JAVA HOUSE MEETINGS"))

        val txn = Transaction(
            id = 104L,
            amount = -2500L,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            description = "Expense at JAVA HOUSE MEETINGS with client",
            counterparty = "JAVA HOUSE MEETINGS",
            categoryId = null
        )
        fakeTransactionDao.transactions.add(txn)

        val updatedCount = repository.applyRules()

        assertEquals(1, updatedCount)
        assertEquals(50L, fakeTransactionDao.transactions.first().categoryId)
    }

    @Test
    fun `precedence - paybill rule categorizes utilities when no user override exists`() = runBlocking {
        // KPLC Paybill 888888 -> Utilities (id = 2L)
        val txn = Transaction(
            id = 105L,
            amount = -3500L,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            description = "Paybill to KPLC PREPAID (Acc: 14223456789)",
            counterparty = "KPLC PREPAID (Acc: 14223456789)",
            categoryId = null
        )
        fakeTransactionDao.transactions.add(txn)

        val updatedCount = repository.applyRules()

        assertEquals(1, updatedCount)
        assertEquals(2L, fakeTransactionDao.transactions.first().categoryId) // Utilities
    }

    @Test
    fun `precedence - default merchant rule categorizes transport when no user override exists`() = runBlocking {
        // Uber -> Transport (id = 3L)
        val txn = Transaction(
            id = 106L,
            amount = -850L,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            description = "Trip with UBER KENYA",
            counterparty = "UBER",
            categoryId = null
        )
        fakeTransactionDao.transactions.add(txn)

        val updatedCount = repository.applyRules()

        assertEquals(1, updatedCount)
        assertEquals(3L, fakeTransactionDao.transactions.first().categoryId) // Transport
    }

    @Test
    fun `unmatched transaction remains uncategorised`() = runBlocking {
        val txn = Transaction(
            id = 107L,
            amount = -100L,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            description = "Unknown transfer to John 0712345678",
            counterparty = "John",
            categoryId = null
        )
        fakeTransactionDao.transactions.add(txn)

        val updatedCount = repository.applyRules()

        assertEquals(0, updatedCount)
        assertNull(fakeTransactionDao.transactions.first().categoryId)
    }

    // ========================================================================
    // LEARNED OVERRIDE TESTS ("always categorise X as Y")
    // ========================================================================

    @Test
    fun `learnOverride inserts rule and re-categorises all matching transactions`() = runBlocking {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        // Two transactions with QUICKMART: one uncategorised, one with old category
        val txn1 = Transaction(id = 201L, amount = -1500L, timestamp = now, description = "QUICKMART KILIMANI", counterparty = "QUICKMART", categoryId = null)
        val txn2 = Transaction(id = 202L, amount = -2300L, timestamp = now, description = "QUICKMART CBD", counterparty = "QUICKMART", categoryId = 1L)
        val txn3 = Transaction(id = 203L, amount = -800L, timestamp = now, description = "NAIVAS", counterparty = "NAIVAS", categoryId = 1L)

        fakeTransactionDao.transactions.addAll(listOf(txn1, txn2, txn3))

        // User says: "Always categorise QUICKMART as Category 77L (Monthly Supplies)"
        val updatedCount = repository.learnOverride("QUICKMART", categoryId = 77L)

        assertEquals(2, updatedCount)
        // Assert rule was saved in RuleDao
        val savedRule = fakeRuleDao.rules.find { it.keyword == "QUICKMART" }
        assertEquals(77L, savedRule?.categoryId)

        // Assert both QUICKMART transactions were updated to 77L
        assertEquals(77L, fakeTransactionDao.transactions[0].categoryId)
        assertEquals(77L, fakeTransactionDao.transactions[1].categoryId)
        // Assert NAIVAS transaction was unchanged
        assertEquals(1L, fakeTransactionDao.transactions[2].categoryId)
    }

    @Test
    fun `recategoriseAll updates existing transactions when new user override rule is added`() = runBlocking {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val txn = Transaction(
            id = 301L,
            amount = -5000L,
            timestamp = now,
            description = "JAVA HOUSE",
            counterparty = "JAVA HOUSE",
            categoryId = 4L // Food & Dining
        )
        fakeTransactionDao.transactions.add(txn)

        // User adds custom override: JAVA HOUSE -> Category 88L (Client Entertainment)
        fakeRuleDao.rules.add(CategorisationRule(id = 5L, categoryId = 88L, keyword = "JAVA HOUSE"))

        val recategorisedCount = repository.recategoriseAll()

        assertEquals(1, recategorisedCount)
        assertEquals(88L, fakeTransactionDao.transactions.first().categoryId)
    }
}

// ----------------------------------------------------------------------------
// Test Fakes
// ----------------------------------------------------------------------------

private class FakeTransactionDao : TransactionDao {
    val transactions = mutableListOf<Transaction>()

    override suspend fun getSpendingByCategory(start: OffsetDateTime, end: OffsetDateTime): List<CategorySpending> = emptyList()

    override fun getSpendingByCategoryFlow(start: OffsetDateTime, end: OffsetDateTime): Flow<List<CategorySpending>> = flowOf(emptyList())

    override suspend fun insert(transaction: Transaction): Long {
        val id = if (transaction.id == 0L) (transactions.size + 1).toLong() else transaction.id
        transactions.add(transaction.copy(id = id))
        return id
    }

    override suspend fun insertAll(txns: List<Transaction>): List<Long> = txns.map { insert(it) }

    override suspend fun update(transaction: Transaction) {
        val index = transactions.indexOfFirst { it.id == transaction.id }
        if (index != -1) {
            transactions[index] = transaction
        }
    }

    override fun getAllTransactions(): Flow<List<Transaction>> = flowOf(transactions)

    override suspend fun getById(id: Long): Transaction? = transactions.find { it.id == id }

    override suspend fun findByReceiptCode(receiptCode: String): Transaction? =
        transactions.find { it.receiptCode == receiptCode }

    override fun searchTransactions(query: String): Flow<List<Transaction>> =
        flowOf(transactions.filter {
            it.description.contains(query, ignoreCase = true) ||
            it.counterparty?.contains(query, ignoreCase = true) == true ||
            it.receiptCode?.contains(query, ignoreCase = true) == true
        })

    override fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> =
        flowOf(transactions.filter { it.categoryId == categoryId })

    override fun getTransactionsByDateRange(start: OffsetDateTime, end: OffsetDateTime): Flow<List<Transaction>> =
        flowOf(transactions.filter { it.timestamp in start..end })

    override suspend fun getDuplicate(amount: Long, timestamp: OffsetDateTime, description: String): Transaction? =
        transactions.find { it.amount == amount && it.timestamp == timestamp && it.description == description }

    override suspend fun getUncategorisedTransactions(): List<Transaction> =
        transactions.filter { it.categoryId == null }

    override suspend fun deleteById(id: Long) {
        transactions.removeIf { it.id == id }
    }

    override suspend fun deleteAll() {
        transactions.clear()
    }
}

private class FakeRuleDao : RuleDao {
    val rules = mutableListOf<CategorisationRule>()

    override suspend fun insert(rule: CategorisationRule): Long {
        val id = if (rule.id == 0L) (rules.size + 1).toLong() else rule.id
        rules.add(rule.copy(id = id))
        return id
    }

    override suspend fun insertAll(newRules: List<CategorisationRule>): List<Long> =
        newRules.map { insert(it) }

    override suspend fun update(rule: CategorisationRule) {
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index != -1) {
            rules[index] = rule
        }
    }

    override fun getAllRules(): Flow<List<CategorisationRule>> = flowOf(rules)

    override suspend fun getById(id: Long): CategorisationRule? = rules.find { it.id == id }

    override suspend fun deleteById(id: Long) {
        rules.removeIf { it.id == id }
    }

    override suspend fun deleteAll() {
        rules.clear()
    }
}

private class FakeCategoryDao : CategoryDao {
    val categories = mutableListOf<Category>()

    override suspend fun insert(category: Category): Long {
        val id = if (category.id == 0L) (categories.size + 1).toLong() else category.id
        categories.add(category.copy(id = id))
        return id
    }

    override suspend fun insertAll(cats: List<Category>): List<Long> = cats.map { insert(it) }

    override suspend fun update(category: Category) {
        val idx = categories.indexOfFirst { it.id == category.id }
        if (idx != -1) categories[idx] = category
    }

    override fun getAllCategories(): Flow<List<Category>> = flowOf(categories)

    override suspend fun getById(id: Long): Category? = categories.find { it.id == id }

    override suspend fun findByName(name: String): Category? =
        categories.find { it.name.equals(name, ignoreCase = true) }

    override suspend fun deleteById(id: Long) {
        categories.removeIf { it.id == id }
    }
}
