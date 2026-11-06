package com.example.smsexpensertracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.OffsetDateTime

/**
 * Represents a financial transaction extracted from an SMS message.
 */
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val amount: Long,
    val timestamp: OffsetDateTime,
    val description: String,
    val categoryId: Long? = null,
    val sourceStateId: Long? = null
)
