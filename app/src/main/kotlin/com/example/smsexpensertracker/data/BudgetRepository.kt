// path: app/src/main/kotlin/com/example/smsexpensertracker/data/BudgetRepository.kt
package com.example.smsexpensertracker.data

import android.content.Context
import com.example.smsexpensertracker.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Visual and threshold state for a category budget. */
enum class BudgetStatus {
    NORMAL,
    WARNING,
    EXHAUSTED
}

/** Calculated progress of category spending against a monthly limit in KES. */
data class BudgetProgress(
    val budgetId: Long,
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String? = null,
    val month: String,
    val limitKsh: Long,
    val spentKsh: Long
) {
    val percentage: Float
        get() = if (limitKsh > 0L) (spentKsh.toFloat() / limitKsh.toFloat()) else 0f

    val percentageInt: Int
        get() = (percentage * 100f).toInt()

    val remainingKsh: Long
        get() = (limitKsh - spentKsh).coerceAtLeast(0L)

    val overspentKsh: Long
        get() = (spentKsh - limitKsh).coerceAtLeast(0L)

    val isWarning: Boolean
        get() = percentage >= 0.80f && percentage < 1.0f

    val isExhausted: Boolean
        get() = percentage >= 1.0f

    val status: BudgetStatus
        get() = when {
            isExhausted -> BudgetStatus.EXHAUSTED
            isWarning -> BudgetStatus.WARNING
            else -> BudgetStatus.NORMAL
        }

    val formattedSpent: String
        get() = String.format(Locale.US, "KES %,d", spentKsh)

    val formattedLimit: String
        get() = String.format(Locale.US, "KES %,d", limitKsh)

    val formattedRemaining: String
        get() = String.format(Locale.US, "KES %,d", remainingKsh)
}

/** Tracks alerted levels to throttle and prevent duplicate notifications within the same month. */
interface BudgetAlertTracker {
    fun getAlertStatus(budgetId: Long, month: String): BudgetStatus
    fun setAlertStatus(budgetId: Long, month: String, status: BudgetStatus)
    fun resetAlert(budgetId: Long, month: String)
    fun clearAll()
}

@Singleton
class PreferencesBudgetAlertTracker @Inject constructor(
    @ApplicationContext private val context: Context
) : BudgetAlertTracker {

    private val prefs = context.getSharedPreferences("budget_alert_tracker_prefs", Context.MODE_PRIVATE)

    override fun getAlertStatus(budgetId: Long, month: String): BudgetStatus {
        val raw = prefs.getString("alert_${budgetId}_${month}", null) ?: return BudgetStatus.NORMAL
        return try {
            BudgetStatus.valueOf(raw)
        } catch (_: Exception) {
            BudgetStatus.NORMAL
        }
    }

    override fun setAlertStatus(budgetId: Long, month: String, status: BudgetStatus) {
        prefs.edit().putString("alert_${budgetId}_${month}", status.name).apply()
    }

    override fun resetAlert(budgetId: Long, month: String) {
        prefs.edit().remove("alert_${budgetId}_${month}").apply()
    }

    override fun clearAll() {
        prefs.edit().clear().apply()
    }
}

/** Dispatches notifications to the user. Abstracts Android framework for pure JVM testing. */
interface BudgetNotifier {
    fun showBudgetAlert(
        budgetId: Long,
        categoryName: String,
        spentKsh: Long,
        limitKsh: Long,
        month: String,
        isExhausted: Boolean
    ): Boolean
}

@Singleton
class SystemBudgetNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) : BudgetNotifier {
    override fun showBudgetAlert(
        budgetId: Long,
        categoryName: String,
        spentKsh: Long,
        limitKsh: Long,
        month: String,
        isExhausted: Boolean
    ): Boolean {
        return NotificationHelper.showBudgetAlert(
            context = context,
            budgetId = budgetId,
            categoryName = categoryName,
            spentKsh = spentKsh,
            limitKsh = limitKsh,
            month = month,
            isExhausted = isExhausted
        )
    }
}

/** Result summary of periodic budget checks. */
data class BudgetCheckResult(
    val totalChecked: Int,
    val warningsSent: Int,
    val exhaustedSent: Int
)

/** Repository interface for Budget CRUD, monthly progress, and alert checks. */
interface BudgetRepository {
    fun getBudgetsForMonthFlow(
        month: String,
        zoneId: ZoneId = ZoneId.of("Africa/Nairobi")
    ): Flow<List<BudgetProgress>>

    suspend fun getBudgetsForMonth(
        month: String,
        zoneId: ZoneId = ZoneId.of("Africa/Nairobi")
    ): List<BudgetProgress>

    suspend fun getBudgetById(id: Long): Budget?
    suspend fun upsertBudget(categoryId: Long, month: String, limitKsh: Long): Long
    suspend fun updateBudget(budget: Budget)
    suspend fun deleteBudget(budget: Budget)
    suspend fun deleteBudgetById(id: Long)

    suspend fun checkBudgetsAndNotify(
        targetMonth: String? = null,
        zoneId: ZoneId = ZoneId.of("Africa/Nairobi")
    ): BudgetCheckResult
}

@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val alertTracker: BudgetAlertTracker,
    private val notifier: BudgetNotifier
) : BudgetRepository {

    override fun getBudgetsForMonthFlow(
        month: String,
        zoneId: ZoneId
    ): Flow<List<BudgetProgress>> {
        val (start, end) = computeMonthRange(month, zoneId)

        return combine(
            budgetDao.getBudgetsForMonthFlow(month),
            categoryDao.getAllCategories(),
            transactionDao.getSpendingByCategoryFlow(start, end)
        ) { budgets, categories, spendingList ->
            val categoryMap = categories.associateBy { it.id }
            val spendingMap = spendingList.associate { it.categoryId to abs(it.total) }

            budgets.map { budget ->
                val category = categoryMap[budget.categoryId]
                val categoryName = category?.name ?: "Category #${budget.categoryId}"
                val spent = spendingMap[budget.categoryId] ?: 0L

                BudgetProgress(
                    budgetId = budget.id,
                    categoryId = budget.categoryId,
                    categoryName = categoryName,
                    categoryIcon = category?.icon,
                    month = budget.month,
                    limitKsh = budget.limitKsh,
                    spentKsh = spent
                )
            }
        }
    }

    override suspend fun getBudgetsForMonth(
        month: String,
        zoneId: ZoneId
    ): List<BudgetProgress> {
        val (start, end) = computeMonthRange(month, zoneId)
        val budgets = budgetDao.getAllBudgetsForMonth(month)
        if (budgets.isEmpty()) return emptyList()

        val categories = categoryDao.getAllCategories()
        val categoryMap = mutableMapOf<Long, Category>()
        for (budget in budgets) {
            val cat = categoryDao.getById(budget.categoryId)
            if (cat != null) categoryMap[cat.id] = cat
        }

        val spendingList = transactionDao.getSpendingByCategory(start, end)
        val spendingMap = spendingList.associate { it.categoryId to abs(it.total) }

        return budgets.map { budget ->
            val category = categoryMap[budget.categoryId]
            val categoryName = category?.name ?: "Category #${budget.categoryId}"
            val spent = spendingMap[budget.categoryId] ?: 0L

            BudgetProgress(
                budgetId = budget.id,
                categoryId = budget.categoryId,
                categoryName = categoryName,
                categoryIcon = category?.icon,
                month = budget.month,
                limitKsh = budget.limitKsh,
                spentKsh = spent
            )
        }
    }

    override suspend fun getBudgetById(id: Long): Budget? = budgetDao.getById(id)

    override suspend fun upsertBudget(categoryId: Long, month: String, limitKsh: Long): Long {
        val existing = budgetDao.getBudgetByCategoryAndMonth(categoryId, month)
        return if (existing != null) {
            budgetDao.update(existing.copy(limitKsh = limitKsh))
            existing.id
        } else {
            budgetDao.insert(Budget(categoryId = categoryId, month = month, limitKsh = limitKsh))
        }
    }

    override suspend fun updateBudget(budget: Budget) = budgetDao.update(budget)

    override suspend fun deleteBudget(budget: Budget) = budgetDao.delete(budget)

    override suspend fun deleteBudgetById(id: Long) = budgetDao.deleteById(id)

    override suspend fun checkBudgetsAndNotify(
        targetMonth: String?,
        zoneId: ZoneId
    ): BudgetCheckResult {
        val now = OffsetDateTime.now(zoneId)
        val month = targetMonth ?: "${now.year}-${"%02d".format(now.monthValue)}"
        val (start, end) = computeMonthRange(month, zoneId)

        val budgets = budgetDao.getAllBudgetsForMonth(month)
        if (budgets.isEmpty()) {
            return BudgetCheckResult(totalChecked = 0, warningsSent = 0, exhaustedSent = 0)
        }

        val spendingList = transactionDao.getSpendingByCategory(start, end)
        val spendingMap = spendingList.associate { it.categoryId to abs(it.total) }

        var warningsSent = 0
        var exhaustedSent = 0

        for (budget in budgets) {
            if (budget.limitKsh <= 0L) continue

            val spent = spendingMap[budget.categoryId] ?: 0L
            val ratio = spent.toDouble() / budget.limitKsh.toDouble()
            val category = categoryDao.getById(budget.categoryId)
            val categoryName = category?.name ?: "Category #${budget.categoryId}"
            val currentAlert = alertTracker.getAlertStatus(budget.id, month)

            when {
                ratio >= 1.0 -> {
                    if (currentAlert != BudgetStatus.EXHAUSTED) {
                        val posted = notifier.showBudgetAlert(
                            budgetId = budget.id,
                            categoryName = categoryName,
                            spentKsh = spent,
                            limitKsh = budget.limitKsh,
                            month = month,
                            isExhausted = true
                        )
                        if (posted) {
                            alertTracker.setAlertStatus(budget.id, month, BudgetStatus.EXHAUSTED)
                            exhaustedSent++
                        }
                    }
                }
                ratio >= 0.8 -> {
                    if (currentAlert == BudgetStatus.NORMAL) {
                        val posted = notifier.showBudgetAlert(
                            budgetId = budget.id,
                            categoryName = categoryName,
                            spentKsh = spent,
                            limitKsh = budget.limitKsh,
                            month = month,
                            isExhausted = false
                        )
                        if (posted) {
                            alertTracker.setAlertStatus(budget.id, month, BudgetStatus.WARNING)
                            warningsSent++
                        }
                    }
                }
                else -> {
                    if (currentAlert != BudgetStatus.NORMAL) {
                        alertTracker.resetAlert(budget.id, month)
                    }
                }
            }
        }

        return BudgetCheckResult(
            totalChecked = budgets.size,
            warningsSent = warningsSent,
            exhaustedSent = exhaustedSent
        )
    }

    private fun computeMonthRange(
        month: String,
        zoneId: ZoneId
    ): Pair<OffsetDateTime, OffsetDateTime> {
        val yearMonth = try {
            YearMonth.parse(month)
        } catch (_: Exception) {
            YearMonth.now(zoneId)
        }
        val start = yearMonth.atDay(1).atStartOfDay(zoneId).toOffsetDateTime()
        val end = yearMonth.atEndOfMonth().atTime(23, 59, 59, 999_999_999).atZone(zoneId).toOffsetDateTime()
        return Pair(start, end)
    }
}

