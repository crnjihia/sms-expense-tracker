// path: app/src/main/kotlin/com/example/smsexpensertracker/data/AppDatabase.kt
package com.example.smsexpensertracker.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Root Room database with schema export and explicit migrations.
 */
@Database(
    entities = [
        Transaction::class,
        Category::class,
        Budget::class,
        SmsSourceState::class,
        CategorisationRule::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun smsSourceStateDao(): SmsSourceStateDao
    abstract fun ruleDao(): RuleDao

    companion object {
        const val DATABASE_NAME = "sms_expense_tracker.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN receiptCode TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN counterparty TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN transactionType TEXT NOT NULL DEFAULT 'SENT'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN balanceKsh INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN feeKsh INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_receiptCode ON transactions (receiptCode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_timestamp ON transactions (timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_categoryId ON transactions (categoryId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name ON categories (name)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_budgets_categoryId_month ON budgets (categoryId, month)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sms_source_state_sourceName ON sms_source_state (sourceName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categorisation_rules_keyword ON categorisation_rules (keyword)")
            }
        }
    }
}
