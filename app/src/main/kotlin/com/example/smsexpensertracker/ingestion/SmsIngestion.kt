package com.example.smsexpensertracker.ingestion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.AssistedInject
import dagger.assisted.Assisted
import com.example.smsexpensertracker.parser.SmsParser
import com.example.smsexpensertracker.parser.SmsParseResult
import com.example.smsexpensertracker.parser.ParsedSms
import com.example.smsexpensertracker.data.SmsIngestionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import java.time.OffsetDateTime
import java.time.Instant
import java.time.ZoneOffset

/**
 * BroadcastReceiver that listens for incoming SMS messages and forwards them to a
 * WorkManager [SmsIngestionWorker] for background processing.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val bundle = intent.extras ?: return
        val pdus = bundle["pdus"] as? Array<*>
        if (pdus.isNullOrEmpty()) return

        val messages = pdus.mapNotNull { pdu ->
            Telephony.Sms.Intents.getMessagesFromIntent(intent)?.firstOrNull()
        }

        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.messageBody }

        val workData = workDataOf(
            SmsIngestionWorker.KEY_SENDER to sender,
            SmsIngestionWorker.KEY_BODY to body
        )
        val request = OneTimeWorkRequestBuilder<SmsIngestionWorker>()
            .setInputData(workData)
            .build()
        WorkManager.getInstance(context!!).enqueue(request)
    }
}

/**
 * Worker that parses a single SMS message and persists a transaction if the
 * parsing succeeds and the transaction is not already stored.
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
            SmsParseResult.Unparsed -> Result.success()
        }
    }
}

/**
 * Repository that encapsulates the de‑duplication logic and persists parsed
 * transactions into Room.
 */
class SmsIngestionRepository @Inject constructor(
    private val transactionDao: com.example.smsexpensertracker.data.TransactionDao,
    private val parser: SmsParser
) {

    /** Saves a parsed transaction only if an identical one does not already exist. */
    suspend fun saveIfNotDuplicate(parsed: ParsedSms) {
        val duplicate = transactionDao.getDuplicate(
            amount = parsed.amountKsh,
            timestamp = parsed.timestamp,
            description = parsed.description
        )
        if (duplicate != null) return
        transactionDao.insert(
            com.example.smsexpensertracker.data.Transaction(
                amount = parsed.amountKsh,
                timestamp = parsed.timestamp,
                description = parsed.description,
                categoryId = null,
                sourceStateId = null
            )
        )
    }

    /**
     * Backfills the entire SMS inbox. Returns the number of newly inserted
     * transactions.
     */
    suspend fun backfillAll(context: Context): Int {
        var inserted = 0
        val resolver = context.contentResolver
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        resolver.query(uri, projection, null, null, Telephony.Sms.DATE + " DESC")?.use { cursor ->
            val idxAddress = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val idxBody = cursor.getColumnIndex(Telephony.Sms.BODY)
            val idxDate = cursor.getColumnIndex(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                val sender = cursor.getString(idxAddress) ?: continue
                val body = cursor.getString(idxBody) ?: continue
                val dateMs = cursor.getLong(idxDate)
                val timestamp = OffsetDateTime.ofInstant(Instant.ofEpochMilli(dateMs), ZoneOffset.UTC)
                when (val result = parser.parse(sender, body)) {
                    is SmsParseResult.Parsed -> {
                        val dup = transactionDao.getDuplicate(
                            amount = result.parsed.amountKsh,
                            timestamp = result.parsed.timestamp,
                            description = result.parsed.description
                        )
                        if (dup == null) {
                            transactionDao.insert(
                                com.example.smsexpensertracker.data.Transaction(
                                    amount = result.parsed.amountKsh,
                                    timestamp = result.parsed.timestamp,
                                    description = result.parsed.description,
                                    categoryId = null,
                                    sourceStateId = null
                                )
                            )
                            inserted++
                        }
                    }
                    SmsParseResult.Unparsed -> {
                        // ignore unparseable messages
                    }
                }
            }
        }
        return inserted
    }
}
