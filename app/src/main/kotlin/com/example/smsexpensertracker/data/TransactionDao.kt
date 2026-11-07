// path: app/src/main/kotlin/com/example/smsexpensertracker/data/TransactionDao.kt
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
    @Query("""
        SELECT categoryId AS categoryId, SUM(amount) AS total
        FROM transactions
        WHERE categoryId IS NOT NULL
          AND amount < 0
          AND timestamp BETWEEN :start AND :end
        GROUP BY categoryId
    """)
    suspend fun getSpendingByCategory(start: OffsetDateTime, end: OffsetDateTime): List<CategorySpending>

    @Query("""
        SELECT categoryId AS categoryId, SUM(amount) AS total
        FROM transactions
        WHERE categoryId IS NOT NULL
          AND amount < 0
          AND timestamp BETWEEN :start AND :end
        GROUP BY categoryId
    """)
    fun getSpendingByCategoryFlow(start: OffsetDateTime, end: OffsetDateTime): Flow<List<CategorySpending>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<Transaction>): List<Long>

    @Update
    suspend fun update(transaction: Transaction)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE receiptCode = :receiptCode LIMIT 1")
    suspend fun findByReceiptCode(receiptCode: String): Transaction?

    @Query("""
        SELECT * FROM transactions
        WHERE description LIKE '%' || :query || '%'
           OR counterparty LIKE '%' || :query || '%'
           OR receiptCode LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun searchTransactions(query: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId ORDER BY timestamp DESC")
    fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>>

    @Query("""
        SELECT * FROM transactions
        WHERE timestamp BETWEEN :start AND :end
        ORDER BY timestamp DESC
    """)
    fun getTransactionsByDateRange(start: OffsetDateTime, end: OffsetDateTime): Flow<List<Transaction>>

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

    @Query("SELECT * FROM transactions WHERE categoryId IS NULL ORDER BY timestamp DESC")
    suspend fun getUncategorisedTransactions(): List<Transaction>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

/** DAO for category operations. */
@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<Category>): List<Long>

    @Update
    suspend fun update(category: Category)

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Category?

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)
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

    @Query("SELECT * FROM budgets ORDER BY month DESC")
    fun getAllBudgetsFlow(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets")
    suspend fun getAllBudgets(): List<Budget>

    @Query("SELECT * FROM budgets WHERE month = :monthStr")
    fun getBudgetsForMonthFlow(monthStr: String): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE month = :monthStr")
    suspend fun getAllBudgetsForMonth(monthStr: String): List<Budget>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND month = :monthStr LIMIT 1")
    suspend fun getBudgetByCategoryAndMonth(categoryId: Long, monthStr: String): Budget?

    @Query("SELECT * FROM budgets WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Budget?

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}

/** DAO for SMS source state tracking. */
@Dao
interface SmsSourceStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: SmsSourceState): Long

    @Update
    suspend fun update(state: SmsSourceState)

    @Query("SELECT * FROM sms_source_state ORDER BY sourceName ASC")
    fun getAllStatesFlow(): Flow<List<SmsSourceState>>

    @Query("SELECT * FROM sms_source_state WHERE sourceName = :sourceName LIMIT 1")
    suspend fun getBySource(sourceName: String): SmsSourceState?

    @Query("DELETE FROM sms_source_state")
    suspend fun deleteAll()
}
