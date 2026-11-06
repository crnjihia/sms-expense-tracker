package com.example.smsexpensertracker.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Root Room database.
 */
@Database(
    entities = [
        Transaction::class,
        Category::class,
        Budget::class,
        SmsSourceState::class,
        CategorisationRule::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun smsSourceStateDao(): SmsSourceStateDao
    abstract fun ruleDao(): RuleDao
}
