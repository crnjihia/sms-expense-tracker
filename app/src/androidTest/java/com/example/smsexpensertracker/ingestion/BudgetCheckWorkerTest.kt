// path: app/src/androidTest/java/com/example/smsexpensertracker/ingestion/BudgetCheckWorkerTest.kt
package com.example.smsexpensertracker.ingestion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.smsexpensertracker.data.AppDatabase
import com.example.smsexpensertracker.data.Budget
import com.example.smsexpensertracker.data.BudgetDao
import com.example.smsexpensertracker.data.BudgetNotifier
import com.example.smsexpensertracker.data.BudgetRepositoryImpl
import com.example.smsexpensertracker.data.Category
import com.example.smsexpensertracker.data.CategoryDao
import com.example.smsexpensertracker.data.PreferencesBudgetAlertTracker
import com.example.smsexpensertracker.data.Transaction
import com.example.smsexpensertracker.data.TransactionDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.OffsetDateTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
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
    fun budget_worker_sends_alert_when_spending_exceeds_80_percent() = runBlocking {
        val catId = categoryDao.insert(Category(name = "Food"))
        val now = OffsetDateTime.now(ZoneId.of("Africa/Nairobi"))
        val monthStr = "${now.year}-${"%02d".format(now.monthValue)}"
        budgetDao.insert(Budget(categoryId = catId, month = monthStr, limitKsh = 1000))
        transactionDao.insert(
            Transaction(
                amount = -800,
                timestamp = now,
                description = "Coffee",
                categoryId = catId,
                sourceStateId = null
            )
        )
        val alertTracker = PreferencesBudgetAlertTracker(context)
        alertTracker.clearAll()
        val notifier = object : BudgetNotifier {
            override fun showBudgetAlert(
                budgetId: Long,
                categoryName: String,
                spentKsh: Long,
                limitKsh: Long,
                month: String,
                isExhausted: Boolean
            ): Boolean = true
        }
        val budgetRepository = BudgetRepositoryImpl(budgetDao, transactionDao, categoryDao, alertTracker, notifier)

        val worker = TestListenableWorkerBuilder<BudgetCheckWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return BudgetCheckWorker(appContext, workerParameters, budgetRepository)
                }
            })
            .build()
        val result = worker.doWork()
        val alerts = result.outputData.getInt(BudgetCheckWorker.KEY_ALERTS_SENT, -1)
        assertEquals(1, alerts)
    }
}
