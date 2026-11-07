// path: app/src/test/java/com/example/smsexpensertracker/data/TransactionRepositoryTest.kt
package com.example.smsexpensertracker.data

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class TransactionRepositoryTest {

    private lateinit var transactionDao: TxRepoFakeTransactionDao
    private lateinit var categoryDao: TxRepoFakeCategoryDao
    private lateinit var ruleDao: TxRepoFakeRuleDao
    private lateinit var repository: TransactionRepositoryImpl

    private val kenyaZone = ZoneId.of("Africa/Nairobi")

    @BeforeEach
    fun setup() {
        transactionDao = TxRepoFakeTransactionDao()
        categoryDao = TxRepoFakeCategoryDao()
        ruleDao = TxRepoFakeRuleDao()
        repository = TransactionRepositoryImpl(transactionDao, categoryDao, ruleDao)
    }

    @Test
    fun `search filter matches by description, counterparty, and receipt code`() = runTest {
        val now = OffsetDateTime.of(2026, 11, 10, 10, 0, 0, 0, ZoneOffset.ofHours(3))
        transactionDao.insert(Transaction(id = 1L, amount = -1000L, timestamp = now, description = "Paid to KPLC", counterparty = "KPLC Prepaid", receiptCode = "QA12BC34DE"))
        transactionDao.insert(Transaction(id = 2L, amount = -2500L, timestamp = now, description = "Naivas Supermarket", counterparty = "Naivas", receiptCode = "QB56CD78EF"))
        transactionDao.insert(Transaction(id = 3L, amount = 5000L, timestamp = now, description = "Received from John", counterparty = "John Kamau", receiptCode = "QC90EF12GH"))

        // Search by receipt code
        repository.getTransactionsFlow(query = "QA12BC34DE").test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("KPLC Prepaid", items[0].transaction.counterparty)
            cancelAndIgnoreRemainingEvents()
        }

        // Search by merchant counterparty
        repository.getTransactionsFlow(query = "naivas").test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals(-2500L, items[0].transaction.amount)
            cancelAndIgnoreRemainingEvents()
        }

        // Search by generic keyword
        repository.getTransactionsFlow(query = "john").test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals(5000L, items[0].transaction.amount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `type filter correctly isolates expenses and income`() = runTest {
        val now = OffsetDateTime.of(2026, 11, 10, 10, 0, 0, 0, ZoneOffset.ofHours(3))
        transactionDao.insert(Transaction(id = 1L, amount = -1500L, timestamp = now, description = "Fuel", counterparty = "Shell"))
        transactionDao.insert(Transaction(id = 2L, amount = 10000L, timestamp = now, description = "Salary", counterparty = "Acme Corp"))

        repository.getTransactionsFlow(typeFilter = TransactionTypeFilter.EXPENSE).test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertTrue(items[0].isExpense)
            assertEquals(1500L, items[0].transaction.amount * -1)
            cancelAndIgnoreRemainingEvents()
        }

        repository.getTransactionsFlow(typeFilter = TransactionTypeFilter.INCOME).test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals(10000L, items[0].transaction.amount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateTransactionCategory saves category and creates learning rule if keyword provided`() = runTest {
        val catId = categoryDao.insert(Category(id = 1L, name = "Dining", icon = "🍽️"))
        val now = OffsetDateTime.of(2026, 11, 10, 12, 0, 0, 0, ZoneOffset.ofHours(3))
        val txId = transactionDao.insert(Transaction(id = 1L, amount = -850L, timestamp = now, description = "Artcaffe Grand Cafe", counterparty = "Artcaffe"))

        repository.updateTransactionCategory(
            transactionId = txId,
            categoryId = catId,
            learnKeyword = "Artcaffe"
        )

        val updated = transactionDao.getById(txId)
        assertEquals(catId, updated?.categoryId)

        val rule = ruleDao.findMatchingRule("artcaffe")
        assertNotNull(rule)
        assertEquals(catId, rule?.categoryId)
    }

    @Test
    fun `splitTransaction successfully splits transaction into two parts with matching sums`() = runTest {
        val catGroceries = categoryDao.insert(Category(id = 1L, name = "Groceries", icon = "🛒"))
        val catHousehold = categoryDao.insert(Category(id = 2L, name = "Household", icon = "🏠"))

        val now = OffsetDateTime.of(2026, 11, 10, 15, 0, 0, 0, ZoneOffset.ofHours(3))
        val originalId = transactionDao.insert(
            Transaction(
                id = 1L,
                amount = -3500L, // Total KES 3,500 expense
                timestamp = now,
                description = "Carrefour Hub Karen",
                counterparty = "Carrefour",
                receiptCode = "QA11BB22CC"
            )
        )

        // Split KES 3,500 into KES 2,000 (Groceries) and KES 1,500 (Household)
        repository.splitTransaction(
            transactionId = originalId,
            firstAmountKsh = 2000L,
            firstCategoryId = catGroceries,
            secondAmountKsh = 1500L,
            secondCategoryId = catHousehold
        )

        // Verify primary transaction was updated
        val primary = transactionDao.getById(originalId)
        assertNotNull(primary)
        assertEquals(-2000L, primary?.amount)
        assertEquals(catGroceries, primary?.categoryId)

        // Verify secondary transaction was created
        val all = transactionDao.getAll()
        assertEquals(2, all.size)
        val secondary = all.find { it.id != originalId }
        assertNotNull(secondary)
        assertEquals(-1500L, secondary?.amount)
        assertEquals(catHousehold, secondary?.categoryId)
        assertEquals("Carrefour Hub Karen (Split)", secondary?.description)
        assertEquals("QA11BB22CC-SPLIT", secondary?.receiptCode)
    }

    @Test
    fun `splitTransaction throws exception when split amounts do not equal total`() {
        val now = OffsetDateTime.of(2026, 11, 10, 15, 0, 0, 0, ZoneOffset.ofHours(3))
        val txId = runBlocking {
            transactionDao.insert(
                Transaction(id = 1L, amount = -5000L, timestamp = now, description = "Supermarket")
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.splitTransaction(
                    transactionId = txId,
                    firstAmountKsh = 2000L,
                    firstCategoryId = null,
                    secondAmountKsh = 2000L, // 2000 + 2000 = 4000 != 5000
                    secondCategoryId = null
                )
            }
        }
    }
}

// ============================================================================
// Test Fakes
// ============================================================================

class TxRepoFakeTransactionDao : TransactionDao {
    private val transactions = mutableListOf<Transaction>()
    private val flow = MutableStateFlow<List<Transaction>>(emptyList())
    private var nextId = 1L

    private fun emit() {
        flow.value = transactions.toList()
    }

    fun getAll(): List<Transaction> = transactions.toList()

    override suspend fun insert(transaction: Transaction): Long {
        val id = if (transaction.id == 0L) {
            nextId++
        } else {
            if (transaction.id >= nextId) nextId = transaction.id + 1
            transaction.id
        }
        val copy = transaction.copy(id = id)
        transactions.removeAll { it.id == id }
        transactions.add(copy)
        emit()
        return id
    }

    override suspend fun insertAll(transactionsList: List<Transaction>): List<Long> {
        return transactionsList.map { insert(it) }
    }

    override suspend fun update(transaction: Transaction) {
        val idx = transactions.indexOfFirst { it.id == transaction.id }
        if (idx != -1) {
            transactions[idx] = transaction
            emit()
        }
    }

    override fun getAllTransactions(): Flow<List<Transaction>> = flow

    override suspend fun getById(id: Long): Transaction? =
        transactions.find { it.id == id }

    override suspend fun findByReceiptCode(receiptCode: String): Transaction? =
        transactions.find { it.receiptCode == receiptCode }

    override fun searchTransactions(query: String): Flow<List<Transaction>> =
        flow.map { list -> list.filter { it.description.contains(query, ignoreCase = true) } }

    override fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> =
        flow.map { list -> list.filter { it.categoryId == categoryId } }

    override fun getTransactionsByDateRange(start: OffsetDateTime, end: OffsetDateTime): Flow<List<Transaction>> =
        flow.map { list -> list.filter { !it.timestamp.isBefore(start) && !it.timestamp.isAfter(end) } }

    override suspend fun getDuplicate(amount: Long, timestamp: OffsetDateTime, description: String): Transaction? = null
    override suspend fun getUncategorisedTransactions(): List<Transaction> = transactions.filter { it.categoryId == null }
    override suspend fun deleteById(id: Long) {
        transactions.removeAll { it.id == id }
        emit()
    }
    override suspend fun deleteAll() {
        transactions.clear()
        emit()
    }
    override suspend fun getSpendingByCategory(start: OffsetDateTime, end: OffsetDateTime): List<CategorySpending> = emptyList()
    override fun getSpendingByCategoryFlow(start: OffsetDateTime, end: OffsetDateTime): Flow<List<CategorySpending>> = flowOf(emptyList())
}

class TxRepoFakeCategoryDao : CategoryDao {
    private val categories = mutableListOf<Category>()
    private val flow = MutableStateFlow<List<Category>>(emptyList())
    private var nextId = 1L

    private fun emit() {
        flow.value = categories.toList()
    }

    override suspend fun insert(category: Category): Long {
        val id = if (category.id == 0L) nextId++ else category.id
        val copy = category.copy(id = id)
        categories.removeAll { it.id == id }
        categories.add(copy)
        emit()
        return id
    }

    override suspend fun insertAll(categoriesList: List<Category>): List<Long> = categoriesList.map { insert(it) }

    override suspend fun update(category: Category) {
        val idx = categories.indexOfFirst { it.id == category.id }
        if (idx != -1) {
            categories[idx] = category
            emit()
        }
    }

    override fun getAllCategories(): Flow<List<Category>> = flow
    override suspend fun getById(id: Long): Category? = categories.find { it.id == id }
    override suspend fun findByName(name: String): Category? = categories.find { it.name.equals(name, ignoreCase = true) }
    override suspend fun deleteById(id: Long) { categories.removeAll { it.id == id }; emit() }
}

class TxRepoFakeRuleDao : RuleDao {
    private val rules = mutableListOf<CategorisationRule>()
    private val flow = MutableStateFlow<List<CategorisationRule>>(emptyList())
    private var nextId = 1L

    private fun emit() { flow.value = rules.toList() }

    override suspend fun insert(rule: CategorisationRule): Long {
        val id = if (rule.id == 0L) nextId++ else rule.id
        val copy = rule.copy(id = id)
        rules.removeAll { it.id == id }
        rules.add(copy)
        emit()
        return id
    }

    override suspend fun insertAll(rulesList: List<CategorisationRule>): List<Long> =
        rulesList.map { insert(it) }

    override suspend fun update(rule: CategorisationRule) {
        val idx = rules.indexOfFirst { it.id == rule.id }
        if (idx != -1) { rules[idx] = rule; emit() }
    }

    override fun getAllRules(): Flow<List<CategorisationRule>> = flow
    override suspend fun getById(id: Long): CategorisationRule? = rules.find { it.id == id }
    override suspend fun deleteById(id: Long) { rules.removeAll { it.id == id }; emit() }
    override suspend fun deleteAll() { rules.clear(); emit() }

    fun findMatchingRule(text: String): CategorisationRule? =
        rules.filter { text.contains(it.keyword, ignoreCase = true) }.maxByOrNull { it.keyword.length }
}
