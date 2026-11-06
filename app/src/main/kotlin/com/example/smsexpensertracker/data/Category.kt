package com.example.smsexpensertracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Category for classifying a transaction.
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val icon: String? = null
)
