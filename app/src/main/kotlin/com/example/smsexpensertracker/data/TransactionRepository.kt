// path: app/src/main/kotlin/com/example/smsexpensertracker/data/TransactionRepository.kt
package com.example.smsexpensertracker.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Filter options for transaction lists. */
enum class TransactionTypeFilter { ALL, EXPENSE, INCOME }
enum class DateRangeFilter { ALL, THIS_MONTH, LAST_7_DAYS }

/** UI-ready transaction item containing resolved category metadata. */
data class TransactionItem(
    val transaction: Transaction,
    val categoryName: String?,
    val categoryIcon: String?
) {
    val isExpense: Boolean get() = transaction.amount < 0
    val formattedAmount: String get() = String.format(Locale.US, "KES %,d", abs(transaction.amount))
    val displayTitle: String get() = transaction.counterparty?.ifBlank { null } ?: transaction.description
}

/** Top category spending summary item for home dashboard. */
data class TopCategorySpending(
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String?,
    val spentKsh: Long,
    val percentageOfTotal: Float
) {
    val formattedSpent: String get() = String.format(Locale.US, "KES %,d", spentKsh)
    val percentageInt: Int get() = (percentageOfTotal * 100f).toInt()
}

/** Dashboard summary for the current calendar month in Kenya. */
data class HomeMonthSummary(
    val month: String,
    val totalIncomeKsh: Long,
    val totalExpenseKsh: Long,
    val netSavingsKsh: Long,
    val topCategories: List<TopCategorySpending>,
    val recentTransactions: List<TransactionItem>
) {
    val formattedIncome: String get() = String.format(Locale.US, "KES %,d", totalIncomeKsh)
    val formattedExpense: String get() = String.format(Locale.US, "KES %,d", totalExpenseKsh)
    val formattedNet: String get() = String.format(Locale.US, "%sKES %,d", if (netSavingsKsh >= 0) "+" else "-", abs(netSavingsKsh))
}

/** Repository interface for transaction management, filtering, splitting, and summary aggregation. */
interface TransactionRepository {
    fun getTransactionsFlow(
        query: String = "",
        typeFilter: TransactionTypeFilter = TransactionTypeFilter.ALL,
        categoryId: Long? = null,
        dateFilter: DateRangeFilter = DateRangeFilter.ALL,
        zoneId: ZoneId = ZoneId.of("Africa/Nairobi")
    ): Flow<List<TransactionItem>>

    fun getHomeSummaryFlow(
        zoneId: ZoneId = ZoneId.of("Africa/Nairobi")
    ): Flow<HomeMonthSummary>

    suspend fun getTransactionById(id: Long): Transaction?
    suspend fun updateTransactionCategory(transactionId: Long, categoryId: Long?, learnKeyword: String? = null)
    suspend fun splitTransaction(
        transactionId: Long,
        firstAmountKsh: Long,
        firstCategoryId: Long?,
        secondAmountKsh: Long,
        secondCategoryId: Long?
    )
    suspend fun deleteTransaction(id: Long)
}

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val ruleDao: RuleDao
) : TransactionRepository {

    override fun getTransactionsFlow(
        query: String,
        typeFilter: TransactionTypeFilter,
        categoryId: Long?,
        dateFilter: DateRangeFilter,
        zoneId: ZoneId
    ): Flow<List<TransactionItem>> {
        val now = OffsetDateTime.now(zoneId)

        return combine(
            transactionDao.getAllTransactions(),
            categoryDao.getAllCategories()
        ) { transactions, categories ->
            val categoryMap = categories.associateBy { it.id }

            transactions
                .asSequence()
                .filter { tx ->
                    // Query filter (matches description, counterparty, or receiptCode)
                    if (query.isBlank()) true
                    else {
                        val q = query.trim().lowercase()
                        tx.description.lowercase().contains(q) ||
                                (tx.counterparty?.lowercase()?.contains(q) == true) ||
                                (tx.receiptCode?.lowercase()?.contains(q) == true)
                    }
                }
                .filter { tx ->
                    // Type filter
                    when (typeFilter) {
                        TransactionTypeFilter.ALL -> true
                        TransactionTypeFilter.EXPENSE -> tx.amount < 0
                        TransactionTypeFilter.INCOME -> tx.amount > 0
                    }
                }
                .filter { tx ->
                    // Category filter
                    if (categoryId == null) true
                    else tx.categoryId == categoryId
                }
                .filter { tx ->
                    // Date filter
                    when (dateFilter) {
                        DateRangeFilter.ALL -> true
                        DateRangeFilter.THIS_MONTH -> {
                            val ym = YearMonth.from(tx.timestamp.atZoneSameInstant(zoneId))
                            val currentYm = YearMonth.from(now.atZoneSameInstant(zoneId))
                            ym == currentYm
                        }
                        DateRangeFilter.LAST_7_DAYS -> {
                            val cutoff = now.minusDays(7)
                            !tx.timestamp.isBefore(cutoff)
                        }
                    }
                }
                .map { tx ->
                    val cat = tx.categoryId?.let { categoryMap[it] }
                    TransactionItem(
                        transaction = tx,
                        categoryName = cat?.name,
                        categoryIcon = cat?.icon
                    )
                }
                .toList()
        }
    }

    override fun getHomeSummaryFlow(zoneId: ZoneId): Flow<HomeMonthSummary> {
        return combine(
            transactionDao.getAllTransactions(),
            categoryDao.getAllCategories()
        ) { transactions, categories ->
            val now = OffsetDateTime.now(zoneId)
            val monthStr = "${now.year}-${"%02d".format(now.monthValue)}"
            val currentYm = YearMonth.of(now.year, now.monthValue)
            val categoryMap = categories.associateBy { it.id }

            // Current month transactions in Kenya time
            val monthTxs = transactions.filter { tx ->
                val ym = YearMonth.from(tx.timestamp.atZoneSameInstant(zoneId))
                ym == currentYm
            }

            var income = 0L
            var expense = 0L
            val categorySpendingMap = mutableMapOf<Long, Long>()

            for (tx in monthTxs) {
                if (tx.amount > 0) {
                    income += tx.amount
                } else if (tx.amount < 0) {
                    val absAmt = abs(tx.amount)
                    expense += absAmt
                    if (tx.categoryId != null) {
                        categorySpendingMap[tx.categoryId] =
                            (categorySpendingMap[tx.categoryId] ?: 0L) + absAmt
                    }
                }
            }

            // Top spending categories
            val topCategories = categorySpendingMap.entries
                .sortedByDescending { it.value }
                .take(4)
                .map { (catId, spent) ->
                    val cat = categoryMap[catId]
                    TopCategorySpending(
                        categoryId = catId,
                        categoryName = cat?.name ?: "Category #$catId",
                        categoryIcon = cat?.icon,
                        spentKsh = spent,
                        percentageOfTotal = if (expense > 0L) spent.toFloat() / expense.toFloat() else 0f
                    )
                }

            // Latest 5 transactions
            val recentTxs = transactions
                .take(5)
                .map { tx ->
                    val cat = tx.categoryId?.let { categoryMap[it] }
                    TransactionItem(
                        transaction = tx,
                        categoryName = cat?.name,
                        categoryIcon = cat?.icon
                    )
                }

            HomeMonthSummary(
                month = monthStr,
                totalIncomeKsh = income,
                totalExpenseKsh = expense,
                netSavingsKsh = income - expense,
                topCategories = topCategories,
                recentTransactions = recentTxs
            )
        }
    }

    override suspend fun getTransactionById(id: Long): Transaction? = transactionDao.getById(id)

    override suspend fun updateTransactionCategory(
        transactionId: Long,
        categoryId: Long?,
        learnKeyword: String?
    ) {
        val tx = transactionDao.getById(transactionId) ?: return
        transactionDao.update(tx.copy(categoryId = categoryId))

        // Save learning override rule if requested
        if (categoryId != null && !learnKeyword.isNullOrBlank()) {
            ruleDao.insert(
                CategorisationRule(
                    categoryId = categoryId,
                    keyword = learnKeyword.trim().lowercase()
                )
            )
        }
    }

    override suspend fun splitTransaction(
        transactionId: Long,
        firstAmountKsh: Long,
        firstCategoryId: Long?,
        secondAmountKsh: Long,
        secondCategoryId: Long?
    ) {
        require(firstAmountKsh > 0) { "First split amount must be positive" }
        require(secondAmountKsh > 0) { "Second split amount must be positive" }

        val original = transactionDao.getById(transactionId)
            ?: throw IllegalArgumentException("Transaction $transactionId not found")

        val totalSplit = firstAmountKsh + secondAmountKsh
        val originalAbs = abs(original.amount)
        require(totalSplit == originalAbs) {
            "Sum of split amounts ($totalSplit) must equal original amount ($originalAbs)"
        }

        // Maintain sign: negative for expenses, positive for income
        val sign = if (original.amount < 0) -1L else 1L

        // Update original transaction as the primary part
        val updatedOriginal = original.copy(
            amount = sign * firstAmountKsh,
            categoryId = firstCategoryId
        )
        transactionDao.update(updatedOriginal)

        // Insert new transaction as the secondary part
        val splitChild = original.copy(
            id = 0L,
            amount = sign * secondAmountKsh,
            categoryId = secondCategoryId,
            description = "${original.description} (Split)",
            receiptCode = original.receiptCode?.let { "$it-SPLIT" }
        )
        transactionDao.insert(splitChild)
    }

    override suspend fun deleteTransaction(id: Long) = transactionDao.deleteById(id)
}

