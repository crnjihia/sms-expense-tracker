// path: app/src/androidTest/java/com/example/smsexpensertracker/ingestion/CategorisationWorkerTest.kt
package com.example.smsexpensertracker.ingestion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.smsexpensertracker.data.AppDatabase
import com.example.smsexpensertracker.data.CategorisationRepository
import com.example.smsexpensertracker.data.CategorisationRule
import com.example.smsexpensertracker.data.Category
import com.example.smsexpensertracker.data.RuleDao
import com.example.smsexpensertracker.data.Transaction
import com.example.smsexpensertracker.data.TransactionDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class CategorisationWorkerTest {
    private lateinit var db: AppDatabase
    private lateinit var context: Context
    private lateinit var transactionDao: TransactionDao
    private lateinit var ruleDao: RuleDao
    private lateinit var repository: CategorisationRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionDao = db.transactionDao()
        ruleDao = db.ruleDao()
        repository = CategorisationRepository(transactionDao, ruleDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun categorisation_worker_updates_uncategorised_transactions() = runBlocking {
        val catId = db.categoryDao().insert(Category(name = "Food"))
        ruleDao.insert(CategorisationRule(categoryId = catId, keyword = "coffee"))
        val txn = Transaction(
            amount = -500,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            description = "Bought coffee at cafe"
        )
        transactionDao.insert(txn)
        val worker = TestListenableWorkerBuilder<CategorisationWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return CategorisationWorker(appContext, workerParameters, repository)
                }
            })
            .build()
        val result = worker.doWork()
        val updated = result.outputData.getInt(CategorisationWorker.KEY_UPDATED, -1)
        assertEquals(1, updated)
    }
}

