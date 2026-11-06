package com.example.smsexpensertracker.ingestion

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.smsexpensertracker.data.*
import com.example.smsexpensertracker.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * WorkManager worker that checks each budget for the current month and sends alerts
 * when spending exceeds 80 % (warning) or 100 % (danger) of the limit.
 */
@HiltWorker
class BudgetCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val categoryDao: CategoryDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_ALERTS_SENT = "alerts_sent"
    }

    override suspend fun doWork(): Result {
        // Determine current month string, e.g., "2024-08"
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val monthStr = "${now.year}-${"%02d".format(now.monthValue)}"

        // Define month start/end for transaction filtering
        val monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
        val monthEnd = monthStart.plusMonths(1).minusNanos(1)

        // Get aggregated spending per category for this month (only negative amounts = expense)
        val spendingList = transactionDao.getSpendingByCategory(monthStart, monthEnd)
        // Map categoryId -> total expense (negative numbers). We'll use absolute value for comparison.
        val spendingMap = spendingList.associate { it.categoryId to -it.total }

        var alertsSent = 0
        // Iterate over all budgets for the current month
        val budgets = budgetDao.getAllBudgetsForMonth(monthStr)
        for (budget in budgets) {
            val spent = spendingMap[budget.categoryId] ?: 0L
            val limit = budget.limitKsh
            if (limit <= 0L) continue // ignore zero/negative budgets
            val percentage = spent.toDouble() / limit
            when {
                percentage >= 1.0 -> {
                    // 100 % or over – critical alert
                    val categoryName = categoryDao.getById(budget.categoryId)?.name ?: "Category ${budget.categoryId}"
                    NotificationHelper.notify(
                        applicationContext,
                        "Budget Exhausted",
                        "You have spent Ksh${spent} of Ksh${limit} for $categoryName.",
                        budget.id.toInt()
                    )
                    alertsSent++
                }
                percentage >= 0.8 -> {
                    // 80 % warning
                    val categoryName = categoryDao.getById(budget.categoryId)?.name ?: "Category ${budget.categoryId}"
                    NotificationHelper.notify(
                        applicationContext,
                        "Budget Approaching Limit",
                        "You have used ${"%.0f".format(percentage * 100)}% of your Ksh${limit} budget for $categoryName.",
                        budget.id.toInt()
                    )
                    alertsSent++
                }
            }
        }
        val output = workDataOf(KEY_ALERTS_SENT to alertsSent)
        return Result.success(output)
    }
}
