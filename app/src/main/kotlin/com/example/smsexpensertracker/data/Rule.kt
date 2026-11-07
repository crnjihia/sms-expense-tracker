// path: app/src/main/kotlin/com/example/smsexpensertracker/data/Rule.kt
package com.example.smsexpensertracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Categorisation rule: assigns [categoryId] when transaction description matches [keyword].
 */
@Entity(
    tableName = "categorisation_rules",
    indices = [Index(value = ["keyword"])]
)
data class CategorisationRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val categoryId: Long,
    val keyword: String
)
