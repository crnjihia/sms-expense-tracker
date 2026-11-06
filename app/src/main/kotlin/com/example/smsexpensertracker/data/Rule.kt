package com.example.smsexpensertracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Simple categorisation rule – if a transaction description contains [keyword] (case‑insensitive),
 * the transaction will be assigned to the target [categoryId].
 */
@Entity(tableName = "categorisation_rules")
data class CategorisationRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val categoryId: Long,
    val keyword: String
)
