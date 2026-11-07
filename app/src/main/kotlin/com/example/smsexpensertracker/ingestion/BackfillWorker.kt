// path: app/src/main/kotlin/com/example/smsexpensertracker/ingestion/BackfillWorker.kt
package com.example.smsexpensertracker.ingestion

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that performs an inbox backfill in batches.
 * Emits the total number of newly inserted transactions in its output Data.
 */
@HiltWorker
class SmsBackfillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: SmsIngestionRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_INSERTED_COUNT = "inserted_count"
    }

    override suspend fun doWork(): Result {
        return try {
            val inserted = repository.backfillAll(applicationContext)
            val output = workDataOf(KEY_INSERTED_COUNT to inserted)
            Result.success(output)
        } catch (_: Exception) {
            Result.failure()
        }
    }
}
