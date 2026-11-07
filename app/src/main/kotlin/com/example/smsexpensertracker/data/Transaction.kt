// path: app/src/main/kotlin/com/example/smsexpensertracker/data/Transaction.kt
package com.example.smsexpensertracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.OffsetDateTime

/**
 * Represents a financial transaction extracted from an SMS message.
 * Amounts are in KES: negative for expenses (debits), positive for income (credits).
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["receiptCode"]),
        Index(value = ["timestamp"]),
        Index(value = ["categoryId"])
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val amount: Long,
    val timestamp: OffsetDateTime,
    val description: String,
    val categoryId: Long? = null,
    val sourceStateId: Long? = null,
    val receiptCode: String? = null,
    val counterparty: String? = null,
    val transactionType: String = "SENT",
    val balanceKsh: Long? = null,
    val feeKsh: Long? = null
)
