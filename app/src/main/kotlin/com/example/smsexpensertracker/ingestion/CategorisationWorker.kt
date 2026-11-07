// path: app/src/main/kotlin/com/example/smsexpensertracker/ingestion/CategorisationWorker.kt
package com.example.smsexpensertracker.ingestion

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.smsexpensertracker.data.CategorisationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager CoroutineWorker that runs the categorisation rule engine in the background.
 * Supports incremental rule application and full database re-categorisation.
 */
@HiltWorker
class CategorisationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: CategorisationRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_UPDATED = "updated_count"
        const val KEY_RECATEGORISE_ALL = "recategorise_all"
    }

    override suspend fun doWork(): Result {
        return try {
            val recategoriseAll = inputData.getBoolean(KEY_RECATEGORISE_ALL, false)
            val updated = if (recategoriseAll) {
                repository.recategoriseAll()
            } else {
                repository.applyRules()
            }
            val output = workDataOf(KEY_UPDATED to updated)
            Result.success(output)
        } catch (_: Exception) {
            Result.failure()
        }
    }
}
