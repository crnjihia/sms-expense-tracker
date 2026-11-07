// path: app/src/main/kotlin/com/example/smsexpensertracker/data/Budget.kt
package com.example.smsexpensertracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Budget definition per category for a specific month (e.g. "2024-08").
 */
@Entity(
    tableName = "budgets",
    indices = [Index(value = ["categoryId", "month"], unique = true)]
)
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val categoryId: Long,
    val month: String, // ISO-8601 month format: YYYY-MM
    val limitKsh: Long
)
