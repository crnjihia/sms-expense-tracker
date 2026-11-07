// path: app/src/main/kotlin/com/example/smsexpensertracker/data/Category.kt
package com.example.smsexpensertracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Category for classifying a transaction.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val icon: String? = null
)
