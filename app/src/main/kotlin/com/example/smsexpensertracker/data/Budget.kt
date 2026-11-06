package com.example.smsexpensertracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Budget definition per category for a specific month.
 */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val categoryId: Long,
    val month: String, // ISO‑8601 month, e.g. "2024-08"
    val limitKsh: Long
)
