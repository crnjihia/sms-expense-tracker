// path: app/src/test/java/com/example/smsexpensertracker/ui/viewmodel/ReportsViewModelTest.kt
package com.example.smsexpensertracker.ui.viewmodel

import app.cash.turbine.test
import com.example.smsexpensertracker.data.Category
import com.example.smsexpensertracker.data.CategoryDao
import com.example.smsexpensertracker.data.CategorySpending
import com.example.smsexpensertracker.data.Transaction
import com.example.smsexpensertracker.data.TransactionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val zoneId = ZoneId.of("Africa/Nairobi")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState aggregates monthly expenses and category breakdown`() = runTest {
        val txDao = ReportsFakeTransactionDao()
        val catDao = ReportsFakeCategoryDao()

        catDao.insert(Category(id = 1L, name = "Food & Groceries"))
        catDao.insert(Category(id = 2L, name = "Transport"))

        val now = OffsetDateTime.now(zoneId)

        // Insert expenses in current month
        txDao.insert(
            Transaction(
                id = 1L,
                amount = -3000L,
                timestamp = now,
                description = "Carrefour",
                categoryId = 1L
            )
        )
        txDao.insert(
            Transaction(
                id = 2L,
                amount = -1000L,
                timestamp = now,
                description = "Uber",
                categoryId = 2L
            )
        )

        val viewModel = ReportsViewModel(txDao, catDao)

        viewModel.uiState.test {
            // Wait for non-loading state
            val state = awaitItem()
            testDispatcher.scheduler.advanceUntilIdle()

            val loadedState = if (state.isLoading) awaitItem() else state

            assertFalse(loadedState.isLoading)
            assertEquals(4000L, loadedState.totalPeriodExpenseKsh)
            assertEquals("Food & Groceries", loadedState.topExpenseCategoryName)
            assertEquals(2, loadedState.categorySlices.size)

            val foodSlice = loadedState.categorySlices.first { it.categoryName == "Food & Groceries" }
            assertEquals(3000L, foodSlice.totalExpenseKsh)
            assertEquals(0.75f, foodSlice.percentage)

            val transportSlice = loadedState.categorySlices.first { it.categoryName == "Transport" }
            assertEquals(1000L, transportSlice.totalExpenseKsh)
            assertEquals(0.25f, transportSlice.percentage)
        }
    }

    @Test
    fun `onPeriodSelected changes selected period in uiState`() = runTest {
        val txDao = ReportsFakeTransactionDao()
        val catDao = ReportsFakeCategoryDao()
        val viewModel = ReportsViewModel(txDao, catDao)

        viewModel.uiState.test {
            awaitItem() // Initial
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onPeriodSelected(ReportPeriod.LAST_3_MONTHS)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(ReportPeriod.LAST_3_MONTHS, state.selectedPeriod)
            assertEquals(3, state.monthlyTrends.size)
        }
    }
}

private class ReportsFakeTransactionDao : TransactionDao {
    private val transactions = mutableListOf<Transaction>()
    private val flow = MutableStateFlow<List<Transaction>>(emptyList())

    override suspend fun insert(transaction: Transaction): Long {
        transactions.add(transaction)
        flow.value = transactions.toList()
        return transaction.id
    }

    override suspend fun insertAll(transactions: List<Transaction>): List<Long> {
        this.transactions.addAll(transactions)
        flow.value = this.transactions.toList()
        return transactions.map { it.id }
    }

    override suspend fun update(transaction: Transaction) {}
    override fun getAllTransactions(): Flow<List<Transaction>> = flow
    override suspend fun getById(id: Long): Transaction? = transactions.find { it.id == id }
    override suspend fun findByReceiptCode(receiptCode: String): Transaction? = transactions.find { it.receiptCode == receiptCode }
    override fun searchTransactions(query: String): Flow<List<Transaction>> = flow
    override fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> = flow
    override fun getTransactionsByDateRange(start: OffsetDateTime, end: OffsetDateTime): Flow<List<Transaction>> = flow
    override suspend fun getDuplicate(amount: Long, timestamp: OffsetDateTime, description: String): Transaction? = null
    override suspend fun getUncategorisedTransactions(): List<Transaction> = emptyList()
    override suspend fun deleteById(id: Long) {}
    override suspend fun deleteAll() { transactions.clear(); flow.value = emptyList() }
    override suspend fun getSpendingByCategory(start: OffsetDateTime, end: OffsetDateTime): List<CategorySpending> = emptyList()
    override fun getSpendingByCategoryFlow(start: OffsetDateTime, end: OffsetDateTime): Flow<List<CategorySpending>> = flowOfEmpty()

    private fun flowOfEmpty(): Flow<List<CategorySpending>> = MutableStateFlow(emptyList())
}

private class ReportsFakeCategoryDao : CategoryDao {
    private val categories = mutableListOf<Category>()
    private val flow = MutableStateFlow<List<Category>>(emptyList())

    override suspend fun insert(category: Category): Long {
        categories.add(category)
        flow.value = categories.toList()
        return category.id
    }

    override suspend fun insertAll(categories: List<Category>): List<Long> {
        this.categories.addAll(categories)
        flow.value = this.categories.toList()
        return categories.map { it.id }
    }

    override suspend fun update(category: Category) {}
    override fun getAllCategories(): Flow<List<Category>> = flow
    override suspend fun getById(id: Long): Category? = categories.find { it.id == id }
    override suspend fun findByName(name: String): Category? = categories.find { it.name == name }
    override suspend fun deleteById(id: Long) {}
}

