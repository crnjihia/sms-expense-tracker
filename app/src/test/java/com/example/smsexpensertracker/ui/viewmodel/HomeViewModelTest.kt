// path: app/src/test/java/com/example/smsexpensertracker/ui/viewmodel/HomeViewModelTest.kt
package com.example.smsexpensertracker.ui.viewmodel

import com.example.smsexpensertracker.data.Budget
import com.example.smsexpensertracker.data.BudgetCheckResult
import com.example.smsexpensertracker.data.BudgetProgress
import com.example.smsexpensertracker.data.BudgetRepository
import com.example.smsexpensertracker.data.DateRangeFilter
import com.example.smsexpensertracker.data.HomeMonthSummary
import com.example.smsexpensertracker.data.TopCategorySpending
import com.example.smsexpensertracker.data.Transaction
import com.example.smsexpensertracker.data.TransactionItem
import com.example.smsexpensertracker.data.TransactionRepository
import com.example.smsexpensertracker.data.TransactionTypeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeTxRepo: HomeFakeTransactionRepository
    private lateinit var fakeBudgetRepo: HomeFakeBudgetRepository
    private lateinit var viewModel: HomeViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeTxRepo = HomeFakeTransactionRepository()
        fakeBudgetRepo = HomeFakeBudgetRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `viewModel loads and aggregates monthly income, expenses, and budget usage`() = runTest {
        val summary = HomeMonthSummary(
            month = "2026-11",
            totalIncomeKsh = 50_000L,
            totalExpenseKsh = 20_000L,
            netSavingsKsh = 30_000L,
            topCategories = listOf(
                TopCategorySpending(
                    categoryId = 1L,
                    categoryName = "Food & Dining",
                    categoryIcon = "🍔",
                    spentKsh = 12_000L,
                    percentageOfTotal = 0.60f
                ),
                TopCategorySpending(
                    categoryId = 2L,
                    categoryName = "Transport",
                    categoryIcon = "🚗",
                    spentKsh = 8_000L,
                    percentageOfTotal = 0.40f
                )
            ),
            recentTransactions = emptyList()
        )
        fakeTxRepo.summaryFlow.value = summary

        val budgetProgress = listOf(
            BudgetProgress(
                budgetId = 1L,
                categoryId = 1L,
                categoryName = "Food & Dining",
                month = "2026-11",
                limitKsh = 25_000L,
                spentKsh = 12_000L
            )
        )
        fakeBudgetRepo.budgetFlow.value = budgetProgress

        viewModel = HomeViewModel(fakeTxRepo, fakeBudgetRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(50_000L, state.summary.totalIncomeKsh)
        assertEquals(20_000L, state.summary.totalExpenseKsh)
        assertEquals(30_000L, state.summary.netSavingsKsh)
        assertEquals(2, state.summary.topCategories.size)
        assertEquals("Food & Dining", state.summary.topCategories[0].categoryName)
        assertEquals(25_000L, state.totalBudgetLimitKsh)
        assertEquals(12_000f / 25_000f, state.budgetUsageRatio)
    }
}

// ============================================================================
// Test Fakes
// ============================================================================

class HomeFakeTransactionRepository : TransactionRepository {
    val summaryFlow = MutableStateFlow(
        HomeMonthSummary(
            month = "2026-11",
            totalIncomeKsh = 0L,
            totalExpenseKsh = 0L,
            netSavingsKsh = 0L,
            topCategories = emptyList(),
            recentTransactions = emptyList()
        )
    )

    override fun getTransactionsFlow(
        query: String,
        typeFilter: TransactionTypeFilter,
        categoryId: Long?,
        dateFilter: DateRangeFilter,
        zoneId: ZoneId
    ): Flow<List<TransactionItem>> = flowOf(emptyList())

    override fun getHomeSummaryFlow(zoneId: ZoneId): Flow<HomeMonthSummary> = summaryFlow

    override suspend fun getTransactionById(id: Long): Transaction? = null
    override suspend fun updateTransactionCategory(transactionId: Long, categoryId: Long?, learnKeyword: String?) {}
    override suspend fun splitTransaction(
        transactionId: Long,
        firstAmountKsh: Long,
        firstCategoryId: Long?,
        secondAmountKsh: Long,
        secondCategoryId: Long?
    ) {}
    override suspend fun deleteTransaction(id: Long) {}
}

class HomeFakeBudgetRepository : BudgetRepository {
    val budgetFlow = MutableStateFlow<List<BudgetProgress>>(emptyList())

    override fun getBudgetsForMonthFlow(month: String, zoneId: ZoneId): Flow<List<BudgetProgress>> = budgetFlow
    override suspend fun getBudgetsForMonth(month: String, zoneId: ZoneId): List<BudgetProgress> = budgetFlow.value
    override suspend fun getBudgetById(id: Long): Budget? = null
    override suspend fun upsertBudget(categoryId: Long, month: String, limitKsh: Long): Long = 1L
    override suspend fun updateBudget(budget: Budget) {}
    override suspend fun deleteBudget(budget: Budget) {}
    override suspend fun deleteBudgetById(id: Long) {}
    override suspend fun checkBudgetsAndNotify(targetMonth: String?, zoneId: ZoneId): BudgetCheckResult =
        BudgetCheckResult(0, 0, 0)
}

