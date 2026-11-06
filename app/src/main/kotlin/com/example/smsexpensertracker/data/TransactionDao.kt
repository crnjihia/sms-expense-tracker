package com.example.smsexpensertracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.OffsetDateTime

/** DAO for transaction operations. */
@Dao
interface TransactionDao {
    // New query to sum spending per category for a given month (inclusive, expenses only)
    @Query("""
        SELECT categoryId AS categoryId, SUM(amount) AS total
        FROM transactions
        WHERE categoryId IS NOT NULL
          AND amount < 0
          AND timestamp BETWEEN :start AND :end
        GROUP BY categoryId
    """)
    suspend fun getSpendingByCategory(start: OffsetDateTime, end: OffsetDateTime): List<CategorySpending>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    @Query("""
        SELECT * FROM transactions
        WHERE amount = :amount
          AND timestamp = :timestamp
          AND description = :description
        LIMIT 1
    """)
    suspend fun getDuplicate(
        amount: Long,
        timestamp: OffsetDateTime,
        description: String
    ): Transaction?

    @Query("SELECT * FROM transactions WHERE categoryId IS NULL")
    suspend fun getUncategorisedTransactions(): List<Transaction>
}

/** DAO for category operations. */
@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category?
}

/** DAO for budget operations. */
@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget): Long

    @Update
    suspend fun update(budget: Budget)

    @Delete
    suspend fun delete(budget: Budget)

    @Query("SELECT * FROM budgets")
    suspend fun getAllBudgets(): List<Budget>

    @Query("""
        SELECT * FROM budgets WHERE month = :monthStr
    """)
    suspend fun getAllBudgetsForMonth(monthStr: String): List<Budget>
}

/** DAO for SMS source state tracking. */
@Dao
interface SmsSourceStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: SmsSourceState): Long

    @Update
    suspend fun update(state: SmsSourceState)

    @Query("SELECT * FROM sms_source_state WHERE sourceName = :sourceName")
    suspend fun getBySource(sourceName: String): SmsSourceState?
}
