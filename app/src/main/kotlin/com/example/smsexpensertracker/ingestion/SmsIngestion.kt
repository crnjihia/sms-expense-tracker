// path: app/src/main/kotlin/com/example/smsexpensertracker/ingestion/SmsIngestion.kt
package com.example.smsexpensertracker.ingestion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.smsexpensertracker.data.SmsSourceState
import com.example.smsexpensertracker.data.SmsSourceStateDao
import com.example.smsexpensertracker.data.Transaction
import com.example.smsexpensertracker.data.TransactionDao
import com.example.smsexpensertracker.parser.ParsedSms
import com.example.smsexpensertracker.parser.SmsParseResult
import com.example.smsexpensertracker.parser.SmsParser
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * BroadcastReceiver for live SMS capture.
 * Protected by android.permission.BROADCAST_SMS to ensure only system telephony broadcasts are processed.
 * Concatenates multi-part messages and delegates parsing/persistence to WorkManager.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.displayMessageBody ?: it.messageBody ?: "" }
        if (body.isBlank()) return

        val workData = workDataOf(
            SmsIngestionWorker.KEY_SENDER to sender,
            SmsIngestionWorker.KEY_BODY to body
        )
        val request = OneTimeWorkRequestBuilder<SmsIngestionWorker>()
            .setInputData(workData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}

/**
 * Worker that parses a live or enqueued SMS message and persists a transaction
 * if parsing succeeds and no duplicate exists.
 */
@HiltWorker
class SmsIngestionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val parser: SmsParser,
    private val repository: SmsIngestionRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_SENDER = "sms_sender"
        const val KEY_BODY = "sms_body"
    }

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()

        return when (val result = parser.parse(sender, body)) {
            is SmsParseResult.Parsed -> {
                repository.saveIfNotDuplicate(result.parsed)
                Result.success()
            }
            SmsParseResult.Unparsed -> {
                // Non-financial or unparseable messages are safely ignored
                Result.success()
            }
        }
    }
}

/**
 * Repository encapsulating SMS inbox backfill, batch cursor paging,
 * sender high-water-mark tracking, and strict de-duplication by receipt code + timestamp.
 */
class SmsIngestionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val smsSourceStateDao: SmsSourceStateDao,
    private val parser: SmsParser
) {

    /**
     * Persists a parsed SMS transaction only if it does not already exist in Room.
     * Primary de-duplication: matching [ParsedSms.receiptCode].
     * Secondary de-duplication: matching [ParsedSms.amountKsh] + [ParsedSms.timestamp] + [ParsedSms.description].
     *
     * @return true if inserted, false if discarded as a duplicate.
     */
    suspend fun saveIfNotDuplicate(parsed: ParsedSms): Boolean {
        val duplicate = if (!parsed.receiptCode.isNullOrBlank()) {
            transactionDao.findByReceiptCode(parsed.receiptCode)
        } else {
            transactionDao.getDuplicate(
                amount = parsed.amountKsh,
                timestamp = parsed.timestamp,
                description = parsed.description
            )
        }

        if (duplicate != null) {
            return false
        }

        val transaction = Transaction(
            amount = parsed.amountKsh,
            timestamp = parsed.timestamp,
            description = parsed.description,
            categoryId = null,
            sourceStateId = null,
            receiptCode = parsed.receiptCode,
            counterparty = parsed.counterparty,
            transactionType = parsed.transactionType.name,
            balanceKsh = parsed.balanceKsh,
            feeKsh = parsed.feeKsh
        )
        transactionDao.insert(transaction)
        return true
    }

    /**
     * Backfills historical SMS messages from Telephony.Sms in configurable batches,
     * skipping messages already processed prior to the recorded high-water mark timestamp.
     *
     * @param context Application context for accessing contentResolver.
     * @param batchSize Number of SMS messages to process per batch chunk.
     * @return Total count of newly inserted transactions.
     */
    suspend fun backfillAll(context: Context, batchSize: Int = 50): Int {
        var newlyInsertedCount = 0
        val resolver = context.contentResolver
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        // Track max timestamps per sender encountered during this backfill run
        val senderMaxTimestamps = mutableMapOf<String, Long>()

        resolver.query(uri, projection, null, null, "${Telephony.Sms.DATE} ASC")?.use { cursor ->
            val idxAddress = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val idxBody = cursor.getColumnIndex(Telephony.Sms.BODY)
            val idxDate = cursor.getColumnIndex(Telephony.Sms.DATE)

            var currentBatchCount = 0

            while (cursor.moveToNext()) {
                val sender = cursor.getString(idxAddress) ?: continue
                val body = cursor.getString(idxBody) ?: continue
                val dateMs = cursor.getLong(idxDate)

                // High-water-mark check per sender
                val lastTimestamp = senderMaxTimestamps[sender]
                    ?: smsSourceStateDao.getBySource(sender)?.lastSeenTimestamp?.toInstant()?.toEpochMilli()
                    ?: 0L

                if (dateMs <= lastTimestamp) {
                    continue
                }

                val timestamp = OffsetDateTime.ofInstant(Instant.ofEpochMilli(dateMs), ZoneOffset.UTC)
                when (val result = parser.parse(sender, body)) {
                    is SmsParseResult.Parsed -> {
                        val inserted = saveIfNotDuplicate(result.parsed)
                        if (inserted) {
                            newlyInsertedCount++
                        }
                    }
                    SmsParseResult.Unparsed -> {
                        // Safely ignore non-matching SMS
                    }
                }

                // Update sender high water mark
                val previousMax = senderMaxTimestamps[sender] ?: lastTimestamp
                if (dateMs > previousMax) {
                    senderMaxTimestamps[sender] = dateMs
                }

                currentBatchCount++
                if (currentBatchCount >= batchSize) {
                    // Flush high-water marks to DB after each batch
                    flushSenderStates(senderMaxTimestamps, newlyInsertedCount)
                    currentBatchCount = 0
                }
            }
        }

        // Final flush of high-water marks
        flushSenderStates(senderMaxTimestamps, newlyInsertedCount)
        return newlyInsertedCount
    }

    private suspend fun flushSenderStates(senderMaxTimestamps: Map<String, Long>, processedTotal: Int) {
        for ((sender, maxTimestampMs) in senderMaxTimestamps) {
            val existing = smsSourceStateDao.getBySource(sender)
            val maxOffsetDateTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(maxTimestampMs), ZoneOffset.UTC)
            val state = existing?.copy(
                lastSeenTimestamp = maxOffsetDateTime,
                processedCount = existing.processedCount + processedTotal
            ) ?: SmsSourceState(
                sourceName = sender,
                lastSeenTimestamp = maxOffsetDateTime,
                processedCount = processedTotal
            )
            smsSourceStateDao.insert(state)
        }
    }
}
