// path: app/src/main/kotlin/com/example/smsexpensertracker/ingestion/BackfillWorker.kt
package com.example.smsexpensertracker.ingestion

import android.content.Context
import android.provider.Telephony
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.Data
import dagger.assisted.AssistedInject
import dagger.assisted.Assisted
import com.example.smsexpensertracker.parser.SmsParser
import com.example.smsexpensertracker.data.SmsIngestionRepository
import kotlinx.coroutines.flow.first
import java.time.OffsetDateTime
import java.time.Instant
import java.time.ZoneOffset

/**
 * WorkManager worker that performs a one‑time backfill of the device's SMS inbox.
 * It reads all inbox messages, parses each with the injected [SmsParser], and stores
 * non‑duplicate transactions via [SmsIngestionRepository].
 */
@HiltWorker
class SmsBackfillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val parser: SmsParser,
    private val repository: SmsIngestionRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_INSERTED_COUNT = "inserted_count"
    }

    override suspend fun doWork(): Result {
        // The repository handles the heavy lifting – we just surface a count.
        val inserted = repository.backfillAll(appContext)
        val output = workDataOf(KEY_INSERTED_COUNT to inserted)
        return Result.success(output)
    }
}
