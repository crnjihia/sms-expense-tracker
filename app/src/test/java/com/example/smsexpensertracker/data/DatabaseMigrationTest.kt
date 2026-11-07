// path: app/src/test/java/com/example/smsexpensertracker/data/DatabaseMigrationTest.kt
package com.example.smsexpensertracker.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class DatabaseMigrationTest {

    @Test
    fun `migration 1 to 2 executes all required column and index DDL statements`() {
        val executedSqlStatements = mutableListOf<String>()
        val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSqlStatements.add(args[0] as String)
            }
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                else -> null
            }
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_1_2.migrate(fakeDb)

        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)

        // Assert all 5 added columns
        assertTrue(executedSqlStatements.any { it.contains("ALTER TABLE transactions ADD COLUMN receiptCode TEXT") })
        assertTrue(executedSqlStatements.any { it.contains("ALTER TABLE transactions ADD COLUMN counterparty TEXT") })
        assertTrue(executedSqlStatements.any { it.contains("ALTER TABLE transactions ADD COLUMN transactionType TEXT") })
        assertTrue(executedSqlStatements.any { it.contains("ALTER TABLE transactions ADD COLUMN balanceKsh INTEGER") })
        assertTrue(executedSqlStatements.any { it.contains("ALTER TABLE transactions ADD COLUMN feeKsh INTEGER") })

        // Assert indices
        assertTrue(executedSqlStatements.any { it.contains("index_transactions_receiptCode") })
        assertTrue(executedSqlStatements.any { it.contains("index_transactions_timestamp") })
        assertTrue(executedSqlStatements.any { it.contains("index_transactions_categoryId") })
        assertTrue(executedSqlStatements.any { it.contains("index_categories_name") })
        assertTrue(executedSqlStatements.any { it.contains("index_budgets_categoryId_month") })
        assertTrue(executedSqlStatements.any { it.contains("index_sms_source_state_sourceName") })
        assertTrue(executedSqlStatements.any { it.contains("index_categorisation_rules_keyword") })
    }
}
