package com.example.smsexpensertracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.smsexpensertracker.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import com.example.smsexpensertracker.data.Category
import com.example.smsexpensertracker.data.CategorisationRule
import com.example.smsexpensertracker.data.Transaction

class CategorisationRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var ruleDao: RuleDao
    private lateinit var repository: CategorisationRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        transactionDao = db.transactionDao()
        ruleDao = db.ruleDao()
        repository = CategorisationRepository(transactionDao, ruleDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `applyRules updates matching uncategorised transactions`() = runBlocking {
        // Insert a category
        val categoryId = db.categoryDao().insert(Category(name = "Food"))
        // Insert a rule that matches keyword "coffee"
        ruleDao.insert(CategorisationRule(categoryId = categoryId, keyword = "coffee"))
        // Insert an uncategorised transaction with description containing the keyword
        val txn = Transaction(
            amount = -500,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            description = "Bought coffee at cafe"
        )
        val txnId = transactionDao.insert(txn)
        // Apply rules
        val updated = repository.applyRules()
        assertEquals(1, updated)
        // Verify the transaction now has the category set
        val updatedTxn = transactionDao.getById(txnId)
        assertEquals(categoryId, updatedTxn?.categoryId)
    }
}
