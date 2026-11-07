// path: app/src/main/kotlin/com/example/smsexpensertracker/data/CategorySpending.kt
package com.example.smsexpensertracker.data

/**
 * Simple DTO for aggregation of spending per category within a given period.
 */
data class CategorySpending(
    val categoryId: Long,
    val total: Long
)
