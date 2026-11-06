package com.example.smsexpensertracker.ingestion

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.smsexpensertracker.data.CategorisationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** WorkManager worker that runs the categorisation rule engine. */
@HiltWorker
class CategorisationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: CategorisationRepository
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        const val KEY_UPDATED = "updated_count"
    }

    override suspend fun doWork(): Result {
        val updated = repository.applyRules()
        val output = workDataOf(KEY_UPDATED to updated)
        return Result.success(output)
    }
}
