// path: app/src/test/java/com/example/smsexpensertracker/data/BudgetRepositoryTest.kt
package com.example.smsexpensertracker.data

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

class BudgetRepositoryTest {

    private lateinit var fakeBudgetDao: BudgetTestFakeBudgetDao
    private lateinit var fakeTransactionDao: BudgetTestFakeTransactionDao
    private lateinit var fakeCategoryDao: BudgetTestFakeCategoryDao
    private lateinit var fakeAlertTracker: BudgetTestFakeAlertTracker
    private lateinit var fakeNotifier: BudgetTestFakeNotifier
    private lateinit var repository: BudgetRepositoryImpl

    private val kenyaZone = ZoneId.of("Africa/Nairobi")
    private val testMonth = "2026-11"

    @BeforeEach
    fun setup() {
        fakeBudgetDao = BudgetTestFakeBudgetDao()
        fakeTransactionDao = BudgetTestFakeTransactionDao()
        fakeCategoryDao = BudgetTestFakeCategoryDao()
        fakeAlertTracker = BudgetTestFakeAlertTracker()
        fakeNotifier = BudgetTestFakeNotifier()

        repository = BudgetRepositoryImpl(
            budgetDao = fakeBudgetDao,
            transactionDao = fakeTransactionDao,
            categoryDao = fakeCategoryDao,
            alertTracker = fakeAlertTracker,
            notifier = fakeNotifier
        )
    }

    @Test
    fun `progress status under 80 percent is NORMAL without warnings`() {
        val progress = BudgetProgress(
            budgetId = 1L,
            categoryId = 10L,
            categoryName = "Food & Dining",
            month = testMonth,
            limitKsh = 10_000L,
            spentKsh = 5_000L
        )

        assertEquals(0.5f, progress.percentage)
        assertEquals(50, progress.percentageInt)
        assertEquals(BudgetStatus.NORMAL, progress.status)
        assertFalse(progress.isWarning)
        assertFalse(progress.isExhausted)
        assertEquals(5_000L, progress.remainingKsh)
        assertEquals(0L, progress.overspentKsh)
        assertEquals("KES 5,000", progress.formattedSpent)
        assertEquals("KES 10,000", progress.formattedLimit)
    }

    @Test
    fun `progress status at 80 percent or higher is WARNING`() {
        val progress = BudgetProgress(
            budgetId = 2L,
            categoryId = 20L,
            categoryName = "Transport",
            month = testMonth,
            limitKsh = 10_000L,
            spentKsh = 8_200L
        )

        assertEquals(0.82f, progress.percentage, 0.001f)
        assertEquals(82, progress.percentageInt)
        assertEquals(BudgetStatus.WARNING, progress.status)
        assertTrue(progress.isWarning)
        assertFalse(progress.isExhausted)
        assertEquals(1_800L, progress.remainingKsh)
        assertEquals(0L, progress.overspentKsh)
    }

    @Test
    fun `progress status at or above 100 percent is EXHAUSTED`() {
        val progressExact = BudgetProgress(
            budgetId = 3L,
            categoryId = 30L,
            categoryName = "Utilities",
            month = testMonth,
            limitKsh = 5_000L,
            spentKsh = 5_000L
        )
        assertEquals(BudgetStatus.EXHAUSTED, progressExact.status)
        assertTrue(progressExact.isExhausted)
        assertFalse(progressExact.isWarning)
        assertEquals(0L, progressExact.remainingKsh)
        assertEquals(0L, progressExact.overspentKsh)

        val progressOver = progressExact.copy(spentKsh = 6_500L)
        assertEquals(BudgetStatus.EXHAUSTED, progressOver.status)
        assertTrue(progressOver.isExhausted)
        assertFalse(progressOver.isWarning)
        assertEquals(0L, progressOver.remainingKsh)
        assertEquals(1_500L, progressOver.overspentKsh)
    }

    @Test
    fun `checkBudgetsAndNotify triggers 80 percent warning alert once and deduplicates`() = runTest {
        // Given category and budget
        val catId = fakeCategoryDao.insert(Category(id = 1L, name = "Groceries", icon = "🛒"))
        fakeBudgetDao.insert(Budget(id = 101L, categoryId = catId, month = testMonth, limitKsh = 10_000L))

        // Spending at 85% (negative amounts for expenses)
        val timestamp = OffsetDateTime.of(2026, 11, 10, 14, 30, 0, 0, ZoneOffset.ofHours(3))
        fakeTransactionDao.insert(
            Transaction(
                id = 1L,
                amount = -8_500L,
                timestamp = timestamp,
                description = "Naivas Supermarket",
                categoryId = catId
            )
        )

        // First check: should trigger warning
        val result1 = repository.checkBudgetsAndNotify(targetMonth = testMonth, zoneId = kenyaZone)
        assertEquals(1, result1.totalChecked)
        assertEquals(1, result1.warningsSent)
        assertEquals(0, result1.exhaustedSent)
        assertEquals(1, fakeNotifier.sentAlerts.size)

        val alert1 = fakeNotifier.sentAlerts[0]
        assertEquals(101L, alert1.budgetId)
        assertEquals("Groceries", alert1.categoryName)
        assertEquals(8_500L, alert1.spentKsh)
        assertEquals(10_000L, alert1.limitKsh)
        assertFalse(alert1.isExhausted)

        // Second check with unchanged spending: must NOT re-send duplicate alert
        val result2 = repository.checkBudgetsAndNotify(targetMonth = testMonth, zoneId = kenyaZone)
        assertEquals(1, result2.totalChecked)
        assertEquals(0, result2.warningsSent)
        assertEquals(0, result2.exhaustedSent)
        assertEquals(1, fakeNotifier.sentAlerts.size) // Unchanged
    }

    @Test
    fun `checkBudgetsAndNotify triggers 100 percent exhaustion alert after warning`() = runTest {
        val catId = fakeCategoryDao.insert(Category(id = 2L, name = "Fuel", icon = "⛽"))
        fakeBudgetDao.insert(Budget(id = 202L, categoryId = catId, month = testMonth, limitKsh = 5_000L))

        // First spending at 80% (4,000 KES)
        val timestamp = OffsetDateTime.of(2026, 11, 5, 10, 0, 0, 0, ZoneOffset.ofHours(3))
        fakeTransactionDao.insert(
            Transaction(id = 10L, amount = -4_000L, timestamp = timestamp, description = "Total Energies", categoryId = catId)
        )

        val result1 = repository.checkBudgetsAndNotify(targetMonth = testMonth, zoneId = kenyaZone)
        assertEquals(1, result1.warningsSent)
        assertEquals(0, result1.exhaustedSent)

        // Additional spending pushing total to 105% (5,250 KES)
        fakeTransactionDao.insert(
            Transaction(id = 11L, amount = -1_250L, timestamp = timestamp.plusDays(2), description = "Shell", categoryId = catId)
        )

        val result2 = repository.checkBudgetsAndNotify(targetMonth = testMonth, zoneId = kenyaZone)
        assertEquals(0, result2.warningsSent)
        assertEquals(1, result2.exhaustedSent)
        assertEquals(2, fakeNotifier.sentAlerts.size)

        val alert2 = fakeNotifier.sentAlerts[1]
        assertTrue(alert2.isExhausted)
        assertEquals(5_250L, alert2.spentKsh)

        // Third check with unchanged spending: must NOT re-send duplicate exhausted alert
        val result3 = repository.checkBudgetsAndNotify(targetMonth = testMonth, zoneId = kenyaZone)
        assertEquals(0, result3.warningsSent)
        assertEquals(0, result3.exhaustedSent)
    }

    @Test
    fun `checkBudgetsAndNotify resets alert level when budget limit is raised`() = runTest {
        val catId = fakeCategoryDao.insert(Category(id = 3L, name = "Dining", icon = "🍽️"))
        fakeBudgetDao.insert(Budget(id = 303L, categoryId = catId, month = testMonth, limitKsh = 5_000L))

        val timestamp = OffsetDateTime.of(2026, 11, 8, 12, 0, 0, 0, ZoneOffset.ofHours(3))
        fakeTransactionDao.insert(
            Transaction(id = 20L, amount = -4_500L, timestamp = timestamp, description = "Java House", categoryId = catId)
        )

        repository.checkBudgetsAndNotify(targetMonth = testMonth, zoneId = kenyaZone)
        assertEquals(BudgetStatus.WARNING, fakeAlertTracker.getAlertStatus(303L, testMonth))

        // Increase budget limit to 10,000 KES (spent 4,500 is now 45% < 80%)
        repository.upsertBudget(categoryId = catId, month = testMonth, limitKsh = 10_000L)

        repository.checkBudgetsAndNotify(targetMonth = testMonth, zoneId = kenyaZone)
        assertEquals(BudgetStatus.NORMAL, fakeAlertTracker.getAlertStatus(303L, testMonth))
    }

    @Test
    fun `Africa Nairobi timezone correctly bounds transactions by month`() = runTest {
        val catId = fakeCategoryDao.insert(Category(id = 4L, name = "Entertainment"))
        fakeBudgetDao.insert(Budget(id = 404L, categoryId = catId, month = testMonth, limitKsh = 10_000L))

        // Transaction on 2026-10-31 23:59:59 EAT (Previous month in Nairobi)
        val oct31Eat = OffsetDateTime.of(2026, 10, 31, 23, 59, 59, 0, ZoneOffset.ofHours(3))
        fakeTransactionDao.insert(Transaction(id = 30L, amount = -3_000L, timestamp = oct31Eat, description = "Oct Expense", categoryId = catId))

        // Transaction on 2026-11-01 00:01:00 EAT (Current month in Nairobi)
        val nov1Eat = OffsetDateTime.of(2026, 11, 1, 0, 1, 0, 0, ZoneOffset.ofHours(3))
        fakeTransactionDao.insert(Transaction(id = 31L, amount = -2_000L, timestamp = nov1Eat, description = "Nov Expense", categoryId = catId))

        val budgets = repository.getBudgetsForMonth(month = testMonth, zoneId = kenyaZone)
        assertEquals(1, budgets.size)
        // Only the Nov 1 expense (2,000 KES) must be included, Oct 31 expense must be excluded
        assertEquals(2_000L, budgets[0].spentKsh)
    }

    @Test
    fun `budget CRUD operations work as expected`() = runTest {
        val catId = fakeCategoryDao.insert(Category(id = 5L, name = "Rent"))

        // Upsert creates budget
        val budgetId = repository.upsertBudget(categoryId = catId, month = "2026-12", limitKsh = 35_000L)
        val retrieved = repository.getBudgetById(budgetId)
        assertNotNull(retrieved)
        assertEquals(35_000L, retrieved?.limitKsh)

        // Upsert updates existing budget
        repository.upsertBudget(categoryId = catId, month = "2026-12", limitKsh = 40_000L)
        val updated = repository.getBudgetById(budgetId)
        assertEquals(40_000L, updated?.limitKsh)

        // Delete budget
        repository.deleteBudgetById(budgetId)
        assertNull(repository.getBudgetById(budgetId))
    }

    @Test
    fun `reactive flow emits updated progress when transactions change`() = runTest {
        val catId = fakeCategoryDao.insert(Category(id = 6L, name = "Shopping"))
        fakeBudgetDao.insert(Budget(id = 606L, categoryId = catId, month = testMonth, limitKsh = 10_000L))

        repository.getBudgetsForMonthFlow(month = testMonth, zoneId = kenyaZone).test {
            // Initial item with 0 spending
            val initial = awaitItem()
            assertEquals(1, initial.size)
            assertEquals(0L, initial[0].spentKsh)

            // Add transaction
            val ts = OffsetDateTime.of(2026, 11, 15, 16, 0, 0, 0, ZoneOffset.ofHours(3))
            fakeTransactionDao.insert(Transaction(id = 40L, amount = -6_000L, timestamp = ts, description = "Shoes", categoryId = catId))

            // Updated item with 6,000 spending
            val updated = awaitItem()
            assertEquals(6_000L, updated[0].spentKsh)
            assertEquals(60, updated[0].percentageInt)

            cancelAndIgnoreRemainingEvents()
        }
    }
}

// ============================================================================
// Test Fakes with unique names for BudgetRepositoryTest
// ============================================================================

class BudgetTestFakeAlertTracker : BudgetAlertTracker {
    private val map = ConcurrentHashMap<String, BudgetStatus>()

    override fun getAlertStatus(budgetId: Long, month: String): BudgetStatus =
        map["${budgetId}_${month}"] ?: BudgetStatus.NORMAL

    override fun setAlertStatus(budgetId: Long, month: String, status: BudgetStatus) {
        map["${budgetId}_${month}"] = status
    }

    override fun resetAlert(budgetId: Long, month: String) {
        map.remove("${budgetId}_${month}")
    }

    override fun clearAll() {
        map.clear()
    }
}

data class BudgetTestSentAlert(
    val budgetId: Long,
    val categoryName: String,
    val spentKsh: Long,
    val limitKsh: Long,
    val month: String,
    val isExhausted: Boolean
)

class BudgetTestFakeNotifier : BudgetNotifier {
    val sentAlerts = mutableListOf<BudgetTestSentAlert>()

    override fun showBudgetAlert(
        budgetId: Long,
        categoryName: String,
        spentKsh: Long,
        limitKsh: Long,
        month: String,
        isExhausted: Boolean
    ): Boolean {
        sentAlerts.add(
            BudgetTestSentAlert(
                budgetId = budgetId,
                categoryName = categoryName,
                spentKsh = spentKsh,
                limitKsh = limitKsh,
                month = month,
                isExhausted = isExhausted
            )
        )
        return true
    }
}

class BudgetTestFakeBudgetDao : BudgetDao {
    private val budgets = mutableListOf<Budget>()
    private val flow = MutableStateFlow<List<Budget>>(emptyList())
    private var nextId = 1L

    private fun emit() {
        flow.value = budgets.toList()
    }

    override suspend fun insert(budget: Budget): Long {
        val id = if (budget.id == 0L) nextId++ else budget.id
        val copy = budget.copy(id = id)
        budgets.removeAll { it.id == id }
        budgets.add(copy)
        emit()
        return id
    }

    override suspend fun update(budget: Budget) {
        val index = budgets.indexOfFirst { it.id == budget.id }
        if (index != -1) {
            budgets[index] = budget
            emit()
        }
    }

    override suspend fun delete(budget: Budget) {
        budgets.removeAll { it.id == budget.id }
        emit()
    }

    override fun getAllBudgetsFlow(): Flow<List<Budget>> = flow

    override suspend fun getAllBudgets(): List<Budget> = budgets.toList()

    override fun getBudgetsForMonthFlow(monthStr: String): Flow<List<Budget>> =
        flow.map { list -> list.filter { it.month == monthStr } }

    override suspend fun getAllBudgetsForMonth(monthStr: String): List<Budget> =
        budgets.filter { it.month == monthStr }

    override suspend fun getBudgetByCategoryAndMonth(categoryId: Long, monthStr: String): Budget? =
        budgets.find { it.categoryId == categoryId && it.month == monthStr }

    override suspend fun getById(id: Long): Budget? =
        budgets.find { it.id == id }

    override suspend fun deleteById(id: Long) {
        budgets.removeAll { it.id == id }
        emit()
    }

    override suspend fun deleteAll() {
        budgets.clear()
        emit()
    }
}

class BudgetTestFakeCategoryDao : CategoryDao {
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

    override suspend fun insertAll(categoriesList: List<Category>): List<Long> {
        return categoriesList.map { insert(it) }
    }

    override suspend fun update(category: Category) {
        val index = categories.indexOfFirst { it.id == category.id }
        if (index != -1) {
            categories[index] = category
            emit()
        }
    }

    override fun getAllCategories(): Flow<List<Category>> = flow

    override suspend fun getById(id: Long): Category? =
        categories.find { it.id == id }

    override suspend fun findByName(name: String): Category? =
        categories.find { it.name.equals(name, ignoreCase = true) }

    override suspend fun deleteById(id: Long) {
        categories.removeAll { it.id == id }
        emit()
    }
}

class BudgetTestFakeTransactionDao : TransactionDao {
    private val transactions = mutableListOf<Transaction>()
    private val changeNotifier = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    private fun notifyChanged() {
        changeNotifier.tryEmit(Unit)
    }

    override suspend fun getSpendingByCategory(
        start: OffsetDateTime,
        end: OffsetDateTime
    ): List<CategorySpending> {
        return transactions
            .filter { it.categoryId != null && it.amount < 0 && !it.timestamp.isBefore(start) && !it.timestamp.isAfter(end) }
            .groupBy { it.categoryId!! }
            .map { (catId, txs) -> CategorySpending(catId, txs.sumOf { it.amount }) }
    }

    override fun getSpendingByCategoryFlow(
        start: OffsetDateTime,
        end: OffsetDateTime
    ): Flow<List<CategorySpending>> {
        return changeNotifier.map {
            getSpendingByCategory(start, end)
        }
    }

    override suspend fun insert(transaction: Transaction): Long {
        val id = if (transaction.id == 0L) (transactions.size + 1).toLong() else transaction.id
        transactions.add(transaction.copy(id = id))
        notifyChanged()
        return id
    }

    override suspend fun insertAll(transactionsList: List<Transaction>): List<Long> {
        return transactionsList.map { insert(it) }
    }

    override suspend fun update(transaction: Transaction) {
        val index = transactions.indexOfFirst { it.id == transaction.id }
        if (index != -1) {
            transactions[index] = transaction
            notifyChanged()
        }
    }

    override fun getAllTransactions(): Flow<List<Transaction>> = flowOf(transactions)
    override suspend fun getById(id: Long): Transaction? = transactions.find { it.id == id }
    override suspend fun findByReceiptCode(receiptCode: String): Transaction? = transactions.find { it.receiptCode == receiptCode }
    override fun searchTransactions(query: String): Flow<List<Transaction>> = flowOf(emptyList())
    override fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> = flowOf(emptyList())
    override fun getTransactionsByDateRange(start: OffsetDateTime, end: OffsetDateTime): Flow<List<Transaction>> = flowOf(emptyList())
    override suspend fun getDuplicate(amount: Long, timestamp: OffsetDateTime, description: String): Transaction? = null
    override suspend fun getUncategorisedTransactions(): List<Transaction> = emptyList()
    override suspend fun deleteById(id: Long) { transactions.removeAll { it.id == id }; notifyChanged() }
    override suspend fun deleteAll() { transactions.clear(); notifyChanged() }
}
