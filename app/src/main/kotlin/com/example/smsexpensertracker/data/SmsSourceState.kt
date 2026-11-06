package com.example.smsexpensertracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.OffsetDateTime

/**
 * Tracks the ingestion state for a particular SMS source (e.g., M‑Pesa).
 */
@Entity(tableName = "sms_source_state")
data class SmsSourceState(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceName: String,
    val lastSeenTimestamp: OffsetDateTime,
    val processedCount: Int
)
