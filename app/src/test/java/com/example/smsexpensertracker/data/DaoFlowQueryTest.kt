// path: app/src/test/java/com/example/smsexpensertracker/data/DaoFlowQueryTest.kt
package com.example.smsexpensertracker.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DaoFlowQueryTest {

    private lateinit var transactionDao: InMemoryTransactionDao
    private lateinit var categoryDao: InMemoryCategoryDao
    private lateinit var budgetDao: InMemoryBudgetDao

    @BeforeEach
    fun setUp() {
        transactionDao = InMemoryTransactionDao()
        categoryDao = InMemoryCategoryDao()
        budgetDao = InMemoryBudgetDao()
    }

    @Test
    fun `searchTransactions returns matching items by description, counterparty or receipt`() = runBlocking {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        transactionDao.insert(
            Transaction(
                id = 1L,
                amount = -1500,
                timestamp = now,
                description = "Java House Coffee",
                counterparty = "Java House",
                receiptCode = "QA12BC34DE"
            )
        )
        transactionDao.insert(
            Transaction(
                id = 2L,
                amount = -5000,
                timestamp = now,
                description = "Carrefour Supermarket",
                counterparty = "Carrefour",
                receiptCode = "CR88990011"
            )
        )

        // Search by counterparty
        val coffeeResults = transactionDao.searchTransactions("Java").first()
        assertEquals(1, coffeeResults.size)
        assertEquals("QA12BC34DE", coffeeResults.first().receiptCode)

        // Search by receipt code
        val receiptResults = transactionDao.searchTransactions("CR8899").first()
        assertEquals(1, receiptResults.size)
        assertEquals(-5000L, receiptResults.first().amount)

        // Search with non-matching term
        val noMatch = transactionDao.searchTransactions("NonExistent").first()
        assertTrue(noMatch.isEmpty())
    }

    @Test
    fun `getTransactionsByCategory filters correctly via Flow`() = runBlocking {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        transactionDao.insert(Transaction(id = 1L, amount = -500, timestamp = now, description = "A", categoryId = 10L))
        transactionDao.insert(Transaction(id = 2L, amount = -600, timestamp = now, description = "B", categoryId = 20L))
        transactionDao.insert(Transaction(id = 3L, amount = -700, timestamp = now, description = "C", categoryId = 10L))

        val cat10List = transactionDao.getTransactionsByCategory(10L).first()
        assertEquals(2, cat10List.size)
        assertTrue(cat10List.all { it.categoryId == 10L })
    }

    @Test
    fun `findByReceiptCode returns exact transaction or null`() = runBlocking {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        transactionDao.insert(
            Transaction(id = 1L, amount = -850, timestamp = now, description = "Fuel", receiptCode = "SH9900AA11")
        )

        val found = transactionDao.findByReceiptCode("SH9900AA11")
        assertNotNull(found)
        assertEquals(-850L, found?.amount)

        val notFound = transactionDao.findByReceiptCode("UNKNOWN_RECEIPT")
        assertNull(notFound)
    }

    @Test
    fun `budgetDao getBudgetsForMonthFlow returns entries for specific month`() = runBlocking {
        budgetDao.insert(Budget(id = 1L, categoryId = 1L, month = "2024-08", limitKsh = 10000))
        budgetDao.insert(Budget(id = 2L, categoryId = 2L, month = "2024-08", limitKsh = 5000))
        budgetDao.insert(Budget(id = 3L, categoryId = 1L, month = "2024-09", limitKsh = 12000))

        val augBudgets = budgetDao.getBudgetsForMonthFlow("2024-08").first()
        assertEquals(2, augBudgets.size)
        assertTrue(augBudgets.all { it.month == "2024-08" })
    }

    @Test
    fun `categoryDao getAllCategories emits categories alphabetically`() = runBlocking {
        categoryDao.insert(Category(id = 1L, name = "Shopping"))
        categoryDao.insert(Category(id = 2L, name = "Bills"))
        categoryDao.insert(Category(id = 3L, name = "Food"))

        val categories = categoryDao.getAllCategories().first()
        assertEquals(3, categories.size)
        assertEquals(listOf("Bills", "Food", "Shopping"), categories.map { it.name })
    }
}

private class InMemoryTransactionDao : TransactionDao {
    private val store = mutableListOf<Transaction>()

    override suspend fun getSpendingByCategory(start: OffsetDateTime, end: OffsetDateTime): List<CategorySpending> {
        return store
            .filter { it.categoryId != null && it.amount < 0 && it.timestamp in start..end }
            .groupBy { it.categoryId!! }
            .map { (catId, txns) -> CategorySpending(catId, txns.sumOf { it.amount }) }
    }

    override fun getSpendingByCategoryFlow(start: OffsetDateTime, end: OffsetDateTime): Flow<List<CategorySpending>> {
        return flowOf(runBlocking { getSpendingByCategory(start, end) })
    }

    override suspend fun insert(transaction: Transaction): Long {
        store.removeIf { it.id == transaction.id && transaction.id != 0L }
        val id = if (transaction.id == 0L) (store.size + 1).toLong() else transaction.id
        store.add(transaction.copy(id = id))
        return id
    }

    override suspend fun insertAll(transactions: List<Transaction>): List<Long> {
        return transactions.map { insert(it) }
    }

    override suspend fun update(transaction: Transaction) {
        val idx = store.indexOfFirst { it.id == transaction.id }
        if (idx != -1) store[idx] = transaction
    }

    override fun getAllTransactions(): Flow<List<Transaction>> =
        flowOf(store.sortedByDescending { it.timestamp })

    override suspend fun getById(id: Long): Transaction? = store.find { it.id == id }

    override suspend fun findByReceiptCode(receiptCode: String): Transaction? =
        store.find { it.receiptCode == receiptCode }

    override fun searchTransactions(query: String): Flow<List<Transaction>> {
        val filtered = store.filter {
            it.description.contains(query, ignoreCase = true) ||
            (it.counterparty?.contains(query, ignoreCase = true) == true) ||
            (it.receiptCode?.contains(query, ignoreCase = true) == true)
        }.sortedByDescending { it.timestamp }
        return flowOf(filtered)
    }

    override fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> {
        return flowOf(store.filter { it.categoryId == categoryId }.sortedByDescending { it.timestamp })
    }

    override fun getTransactionsByDateRange(start: OffsetDateTime, end: OffsetDateTime): Flow<List<Transaction>> {
        return flowOf(store.filter { it.timestamp in start..end }.sortedByDescending { it.timestamp })
    }

    override suspend fun getDuplicate(amount: Long, timestamp: OffsetDateTime, description: String): Transaction? {
        return store.find { it.amount == amount && it.timestamp == timestamp && it.description == description }
    }

    override suspend fun getUncategorisedTransactions(): List<Transaction> {
        return store.filter { it.categoryId == null }
    }

    override suspend fun deleteById(id: Long) {
        store.removeIf { it.id == id }
    }

    override suspend fun deleteAll() {
        store.clear()
    }
}

private class InMemoryCategoryDao : CategoryDao {
    private val store = mutableListOf<Category>()

    override suspend fun insert(category: Category): Long {
        val id = if (category.id == 0L) (store.size + 1).toLong() else category.id
        store.add(category.copy(id = id))
        return id
    }

    override suspend fun insertAll(categories: List<Category>): List<Long> {
        return categories.map { insert(it) }
    }

    override suspend fun update(category: Category) {
        val idx = store.indexOfFirst { it.id == category.id }
        if (idx != -1) store[idx] = category
    }

    override fun getAllCategories(): Flow<List<Category>> =
        flowOf(store.sortedBy { it.name })

    override suspend fun getById(id: Long): Category? = store.find { it.id == id }

    override suspend fun findByName(name: String): Category? = store.find { it.name == name }

    override suspend fun deleteById(id: Long) {
        store.removeIf { it.id == id }
    }
}

private class InMemoryBudgetDao : BudgetDao {
    private val store = mutableListOf<Budget>()

    override suspend fun insert(budget: Budget): Long {
        val id = if (budget.id == 0L) (store.size + 1).toLong() else budget.id
        store.add(budget.copy(id = id))
        return id
    }

    override suspend fun update(budget: Budget) {
        val idx = store.indexOfFirst { it.id == budget.id }
        if (idx != -1) store[idx] = budget
    }

    override suspend fun delete(budget: Budget) {
        store.removeIf { it.id == budget.id }
    }

    override fun getAllBudgetsFlow(): Flow<List<Budget>> = flowOf(store)

    override suspend fun getAllBudgets(): List<Budget> = store.toList()

    override fun getBudgetsForMonthFlow(monthStr: String): Flow<List<Budget>> =
        flowOf(store.filter { it.month == monthStr })

    override suspend fun getAllBudgetsForMonth(monthStr: String): List<Budget> =
        store.filter { it.month == monthStr }

    override suspend fun getBudgetByCategoryAndMonth(categoryId: Long, monthStr: String): Budget? =
        store.find { it.categoryId == categoryId && it.month == monthStr }

    override suspend fun getById(id: Long): Budget? =
        store.find { it.id == id }

    override suspend fun deleteById(id: Long) {
        store.removeIf { it.id == id }
    }

    override suspend fun deleteAll() {
        store.clear()
    }
}

