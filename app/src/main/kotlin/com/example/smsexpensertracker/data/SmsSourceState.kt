// path: app/src/main/kotlin/com/example/smsexpensertracker/data/SmsSourceState.kt
package com.example.smsexpensertracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.OffsetDateTime

/**
 * Tracks the ingestion state for a particular SMS source (e.g. MPESA, EQUITY, KCB).
 */
@Entity(
    tableName = "sms_source_state",
    indices = [Index(value = ["sourceName"], unique = true)]
)
data class SmsSourceState(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceName: String,
    val lastSeenTimestamp: OffsetDateTime,
    val processedCount: Int
)
