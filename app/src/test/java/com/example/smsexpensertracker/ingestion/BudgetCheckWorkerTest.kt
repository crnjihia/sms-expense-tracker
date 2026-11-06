package com.example.smsexpensertracker.ingestion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestWorkerBuilder
import com.example.smsexpensertracker.AppDatabase
import com.example.smsexpensertracker.data.Budget
import com.example.smsexpensertracker.data.BudgetDao
import com.example.smsexpensertracker.data.Category
import com.example.smsexpensertracker.data.CategoryDao
import com.example.smsexpensertracker.data.CategorySpending
import com.example.smsexpensertracker.data.Transaction
import com.example.smsexpensertracker.data.TransactionDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class BudgetCheckWorkerTest {
    private lateinit var db: AppDatabase
    private lateinit var context: Context
    private lateinit var transactionDao: TransactionDao
    private lateinit var budgetDao: BudgetDao
    private lateinit var categoryDao: CategoryDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionDao = db.transactionDao()
        budgetDao = db.budgetDao()
        categoryDao = db.categoryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `budget worker sends alert when spending exceeds 80 percent`() = runBlocking {
        // Create a category
        val catId = categoryDao.insert(Category(name = "Food"))
        // Create a budget with limit 1000 Ksh for current month
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val monthStr = "${now.year}-${"%02d".format(now.monthValue)}"
        budgetDao.insert(Budget(categoryId = catId, month = monthStr, limitKsh = 1000))
        // Insert a transaction of -800 (80% of limit)
        transactionDao.insert(
            Transaction(
                amount = -800,
                timestamp = now,
                description = "Coffee",
                categoryId = catId,
                sourceStateId = null
            )
        )
        // Build worker
        val worker = TestWorkerBuilder<BudgetCheckWorker>(context)
            .setWorkerFactory { _, _, _ -> BudgetCheckWorker(context, it, transactionDao, budgetDao, categoryDao) }
            .build()
        val result = worker.doWork()
        // Verify that alerts_sent is 1 (warning)
        val alerts = result.outputData.getInt(BudgetCheckWorker.KEY_ALERTS_SENT, -1)
        assertEquals(1, alerts)
    }
}
