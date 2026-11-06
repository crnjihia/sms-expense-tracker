package com.example.smsexpensertracker.ingestion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.TestWorkerBuilder
import com.example.smsexpensertracker.AppDatabase
import com.example.smsexpensertracker.data.Budget
import com.example.smsexpensertracker.data.Category
import com.example.smsexpensertracker.data.CategorisationRule
import com.example.smsexpensertracker.data.Transaction
import com.example.smsexpensertracker.data.TransactionDao
import com.example.smsexpensertracker.data.RuleDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

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
    fun `categorisation worker updates uncategorised transactions`() = runBlocking {
        // Insert a category
        val catId = db.categoryDao().insert(Category(name = "Food"))
        // Insert a rule that matches keyword "coffee"
        ruleDao.insert(CategorisationRule(categoryId = catId, keyword = "coffee"))
        // Insert an uncategorised transaction with description containing the keyword
        val txn = Transaction(
            amount = -500,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            description = "Bought coffee at cafe"
        )
        transactionDao.insert(txn)
        // Build worker
        val worker = TestWorkerBuilder<CategorisationWorker>(context)
            .setWorkerFactory { _, workerParams, _ ->
                CategorisationWorker(context, workerParams, repository)
            }
            .build()
        val result = worker.doWork()
        val updated = result.outputData.getInt(CategorisationWorker.KEY_UPDATED, -1)
        assertEquals(1, updated)
    }
}
