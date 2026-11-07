// path: app/src/main/kotlin/com/example/smsexpensertracker/ingestion/BudgetCheckWorker.kt
package com.example.smsexpensertracker.ingestion

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.smsexpensertracker.data.BudgetRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.ZoneId

/**
 * WorkManager worker that checks each budget for the current month and sends alerts
 * when spending exceeds 80% (warning) or 100% (danger) of the limit.
 * Uses Kenya timezone (Africa/Nairobi) for month boundaries.
 */
@HiltWorker
class BudgetCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val budgetRepository: BudgetRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_BUDGETS_CHECKED = "budgets_checked"
        const val KEY_WARNINGS_SENT = "warnings_sent"
        const val KEY_EXHAUSTED_SENT = "exhausted_sent"
        const val KEY_ALERTS_SENT = "alerts_sent"
    }

    override suspend fun doWork(): Result {
        val result = budgetRepository.checkBudgetsAndNotify(zoneId = ZoneId.of("Africa/Nairobi"))
        val totalAlerts = result.warningsSent + result.exhaustedSent

        val output = workDataOf(
            KEY_BUDGETS_CHECKED to result.totalChecked,
            KEY_WARNINGS_SENT to result.warningsSent,
            KEY_EXHAUSTED_SENT to result.exhaustedSent,
            KEY_ALERTS_SENT to totalAlerts
        )
        return Result.success(output)
    }
}
