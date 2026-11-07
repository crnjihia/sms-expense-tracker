// path: app/src/main/kotlin/com/example/smsexpensertracker/ui/viewmodel/ReportsViewModel.kt
package com.example.smsexpensertracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsexpensertracker.data.CategoryDao
import com.example.smsexpensertracker.data.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

enum class ReportPeriod(val label: String, val monthsCount: Int) {
    LAST_3_MONTHS("3 Months", 3),
    LAST_6_MONTHS("6 Months", 6),
    THIS_YEAR("This Year", 12)
}

data class MonthlyTrendPoint(
    val yearMonth: YearMonth,
    val label: String,
    val expenseKsh: Long,
    val incomeKsh: Long
) {
    val formattedExpense: String get() = String.format(Locale.US, "KES %,d", expenseKsh)
}

data class CategoryReportSlice(
    val categoryId: Long?,
    val categoryName: String,
    val totalExpenseKsh: Long,
    val percentage: Float
) {
    val formattedExpense: String get() = String.format(Locale.US, "KES %,d", totalExpenseKsh)
    val percentageInt: Int get() = (percentage * 100f).toInt()
}

data class ReportsUiState(
    val selectedPeriod: ReportPeriod = ReportPeriod.LAST_6_MONTHS,
    val monthlyTrends: List<MonthlyTrendPoint> = emptyList(),
    val categorySlices: List<CategoryReportSlice> = emptyList(),
    val totalPeriodExpenseKsh: Long = 0L,
    val averageMonthlyExpenseKsh: Long = 0L,
    val topExpenseCategoryName: String? = null,
    val isLoading: Boolean = false
) {
    val formattedTotalExpense: String get() = String.format(Locale.US, "KES %,d", totalPeriodExpenseKsh)
    val formattedAverageExpense: String get() = String.format(Locale.US, "KES %,d", averageMonthlyExpenseKsh)
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    transactionDao: TransactionDao,
    categoryDao: CategoryDao
) : ViewModel() {

    private val selectedPeriodFlow = MutableStateFlow(ReportPeriod.LAST_6_MONTHS)
    private val zoneId = ZoneId.of("Africa/Nairobi")
    private val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.US)

    val uiState: StateFlow<ReportsUiState> = combine(
        transactionDao.getAllTransactions(),
        categoryDao.getAllCategories(),
        selectedPeriodFlow
    ) { transactions, categories, period ->
        val now = OffsetDateTime.now(zoneId)
        val currentYearMonth = YearMonth.from(now)
        val categoryMap = categories.associate { it.id to it.name }

        // Determine list of YearMonths in the selected period (chronological)
        val periodMonths = (0 until period.monthsCount).map { offset ->
            currentYearMonth.minusMonths((period.monthsCount - 1 - offset).toLong())
        }
        val startOfPeriod = periodMonths.first().atDay(1).atStartOfDay(zoneId).toOffsetDateTime()

        val periodTransactions = transactions.filter {
            !it.timestamp.isBefore(startOfPeriod)
        }

        // 1. Monthly Trends
        val trends = periodMonths.map { ym ->
            val monthTxs = periodTransactions.filter { YearMonth.from(it.timestamp) == ym }
            val expense = monthTxs.filter { it.amount < 0 }.sumOf { abs(it.amount) }
            val income = monthTxs.filter { it.amount > 0 }.sumOf { it.amount }
            MonthlyTrendPoint(
                yearMonth = ym,
                label = ym.format(monthFormatter),
                expenseKsh = expense,
                incomeKsh = income
            )
        }

        // 2. Category Breakdown
        val expenseTxs = periodTransactions.filter { it.amount < 0 }
        val totalPeriodExpense = expenseTxs.sumOf { abs(it.amount) }

        val categoryGroups = expenseTxs.groupBy { it.categoryId }
        val slices = categoryGroups.map { (catId, txs) ->
            val spent = txs.sumOf { abs(it.amount) }
            val catName = if (catId != null) categoryMap[catId] ?: "Other" else "Uncategorised"
            val pct = if (totalPeriodExpense > 0L) spent.toFloat() / totalPeriodExpense.toFloat() else 0f
            CategoryReportSlice(
                categoryId = catId,
                categoryName = catName,
                totalExpenseKsh = spent,
                percentage = pct
            )
        }.sortedByDescending { it.totalExpenseKsh }

        val topCategory = slices.firstOrNull()?.categoryName
        val avgExpense = if (period.monthsCount > 0) totalPeriodExpense / period.monthsCount else 0L

        ReportsUiState(
            selectedPeriod = period,
            monthlyTrends = trends,
            categorySlices = slices,
            totalPeriodExpenseKsh = totalPeriodExpense,
            averageMonthlyExpenseKsh = avgExpense,
            topExpenseCategoryName = topCategory,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReportsUiState(isLoading = true)
    )

    fun onPeriodSelected(period: ReportPeriod) {
        selectedPeriodFlow.value = period
    }
}

