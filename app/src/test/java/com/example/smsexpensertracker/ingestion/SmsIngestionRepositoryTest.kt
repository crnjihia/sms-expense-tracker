// path: app/src/test/java/com/example/smsexpensertracker/ingestion/SmsIngestionRepositoryTest.kt
package com.example.smsexpensertracker.ingestion

import com.example.smsexpensertracker.data.CategorySpending
import com.example.smsexpensertracker.data.SmsSourceState
import com.example.smsexpensertracker.data.SmsSourceStateDao
import com.example.smsexpensertracker.data.Transaction
import com.example.smsexpensertracker.data.TransactionDao
import com.example.smsexpensertracker.parser.ParsedSms
import com.example.smsexpensertracker.parser.TemplateSmsParser
import com.example.smsexpensertracker.parser.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SmsIngestionRepositoryTest {

    private lateinit var fakeTransactionDao: FakeTestTransactionDao
    private lateinit var fakeSmsSourceStateDao: FakeTestSmsSourceStateDao
    private lateinit var repository: SmsIngestionRepository
    private val parser = TemplateSmsParser.createDefault()

    @BeforeEach
    fun setUp() {
        fakeTransactionDao = FakeTestTransactionDao()
        fakeSmsSourceStateDao = FakeTestSmsSourceStateDao()
        repository = SmsIngestionRepository(
            transactionDao = fakeTransactionDao,
            smsSourceStateDao = fakeSmsSourceStateDao,
            parser = parser
        )
    }

    @Test
    fun `saveIfNotDuplicate with matching receipt code rejects duplicate`() = runTest {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val parsed1 = ParsedSms(
            amountKsh = -1500L,
            timestamp = now,
            description = "sent to JANE DOE 0712345678",
            receiptCode = "QA12BC34DE",
            transactionType = TransactionType.SENT,
            counterparty = "JANE DOE 0712345678",
            balanceKsh = 5230L,
            feeKsh = 15L
        )
        val parsed2 = parsed1.copy(description = "duplicate message with same receipt")

        val insertedFirst = repository.saveIfNotDuplicate(parsed1)
        val insertedSecond = repository.saveIfNotDuplicate(parsed2)

        assertTrue(insertedFirst, "First transaction should be inserted")
        assertFalse(insertedSecond, "Duplicate receiptCode must be rejected")
        assertEquals(1, fakeTransactionDao.transactions.size)
        assertEquals("QA12BC34DE", fakeTransactionDao.transactions.first().receiptCode)
    }

    @Test
    fun `saveIfNotDuplicate with distinct receipt codes inserts both`() = runTest {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val txn1 = ParsedSms(
            amountKsh = -1500L,
            timestamp = now,
            description = "sent to JANE",
            receiptCode = "QA12BC34DE",
            transactionType = TransactionType.SENT
        )
        val txn2 = ParsedSms(
            amountKsh = 2000L,
            timestamp = now.plusMinutes(10),
            description = "received from JOHN",
            receiptCode = "QA99ZZ88YY",
            transactionType = TransactionType.RECEIVED
        )

        assertTrue(repository.saveIfNotDuplicate(txn1))
        assertTrue(repository.saveIfNotDuplicate(txn2))
        assertEquals(2, fakeTransactionDao.transactions.size)
    }

    @Test
    fun `saveIfNotDuplicate without receipt code deduplicates by amount, timestamp and description`() = runTest {
        val now = OffsetDateTime.of(2024, 8, 15, 14, 30, 0, 0, ZoneOffset.UTC)
        val txnNoReceipt1 = ParsedSms(
            amountKsh = -500L,
            timestamp = now,
            description = "Manual / legacy transaction",
            receiptCode = null,
            transactionType = TransactionType.EXPENSE
        )
        val txnNoReceipt2 = txnNoReceipt1.copy()

        val first = repository.saveIfNotDuplicate(txnNoReceipt1)
        val duplicate = repository.saveIfNotDuplicate(txnNoReceipt2)

        assertTrue(first)
        assertFalse(duplicate, "Duplicate by amount, timestamp, description should be rejected")
        assertEquals(1, fakeTransactionDao.transactions.size)
    }

    @Test
    fun `saveIfNotDuplicate correctly maps all attributes into Room Transaction`() = runTest {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val parsed = ParsedSms(
            amountKsh = -3500L,
            timestamp = now,
            description = "Paybill to KPLC PREPAID (Acc: 14223456789)",
            receiptCode = "QA33CD44EF",
            transactionType = TransactionType.PAYBILL,
            counterparty = "KPLC PREPAID (Acc: 14223456789)",
            balanceKsh = 8500L,
            feeKsh = 23L
        )

        val inserted = repository.saveIfNotDuplicate(parsed)
        assertTrue(inserted)

        val saved = fakeTransactionDao.transactions.first()
        assertEquals(-3500L, saved.amount)
        assertEquals(now, saved.timestamp)
        assertEquals("Paybill to KPLC PREPAID (Acc: 14223456789)", saved.description)
        assertEquals("QA33CD44EF", saved.receiptCode)
        assertEquals("KPLC PREPAID (Acc: 14223456789)", saved.counterparty)
        assertEquals("PAYBILL", saved.transactionType)
        assertEquals(8500L, saved.balanceKsh)
        assertEquals(23L, saved.feeKsh)
    }

    @Test
    fun `sms source state records high-water mark timestamp`() = runTest {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        fakeSmsSourceStateDao.insert(
            SmsSourceState(sourceName = "MPESA", lastSeenTimestamp = now, processedCount = 5)
        )

        val state = fakeSmsSourceStateDao.getBySource("MPESA")
        assertNotNull(state)
        assertEquals(now, state?.lastSeenTimestamp)
        assertEquals(5, state?.processedCount)

        // Update high-water mark to a later date
        val later = now.plusDays(1)
        fakeSmsSourceStateDao.insert(
            state!!.copy(lastSeenTimestamp = later, processedCount = 10)
        )
        val updated = fakeSmsSourceStateDao.getBySource("MPESA")
        assertEquals(later, updated?.lastSeenTimestamp)
        assertEquals(10, updated?.processedCount)
    }
}

private class FakeTestTransactionDao : TransactionDao {
    val transactions = mutableListOf<Transaction>()

    override suspend fun getSpendingByCategory(start: OffsetDateTime, end: OffsetDateTime): List<CategorySpending> = emptyList()

    override fun getSpendingByCategoryFlow(start: OffsetDateTime, end: OffsetDateTime): Flow<List<CategorySpending>> = flowOf(emptyList())

    override suspend fun insert(transaction: Transaction): Long {
        val id = if (transaction.id == 0L) (transactions.size + 1).toLong() else transaction.id
        transactions.add(transaction.copy(id = id))
        return id
    }

    override suspend fun insertAll(txns: List<Transaction>): List<Long> = txns.map { insert(it) }

    override suspend fun update(transaction: Transaction) {
        val idx = transactions.indexOfFirst { it.id == transaction.id }
        if (idx != -1) transactions[idx] = transaction
    }

    override fun getAllTransactions(): Flow<List<Transaction>> = flowOf(transactions)

    override suspend fun getById(id: Long): Transaction? = transactions.find { it.id == id }

    override suspend fun findByReceiptCode(receiptCode: String): Transaction? =
        transactions.find { it.receiptCode == receiptCode }

    override fun searchTransactions(query: String): Flow<List<Transaction>> =
        flowOf(transactions.filter { it.description.contains(query, ignoreCase = true) })

    override fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> =
        flowOf(transactions.filter { it.categoryId == categoryId })

    override fun getTransactionsByDateRange(start: OffsetDateTime, end: OffsetDateTime): Flow<List<Transaction>> =
        flowOf(transactions.filter { it.timestamp in start..end })

    override suspend fun getDuplicate(amount: Long, timestamp: OffsetDateTime, description: String): Transaction? =
        transactions.find { it.amount == amount && it.timestamp == timestamp && it.description == description }

    override suspend fun getUncategorisedTransactions(): List<Transaction> =
        transactions.filter { it.categoryId == null }

    override suspend fun deleteById(id: Long) {
        transactions.removeIf { it.id == id }
    }

    override suspend fun deleteAll() {
        transactions.clear()
    }
}

private class FakeTestSmsSourceStateDao : SmsSourceStateDao {
    private val states = mutableMapOf<String, SmsSourceState>()

    override suspend fun insert(state: SmsSourceState): Long {
        states[state.sourceName] = state
        return state.id
    }

    override suspend fun update(state: SmsSourceState) {
        states[state.sourceName] = state
    }

    override suspend fun getBySource(sourceName: String): SmsSourceState? = states[sourceName]

    override fun getAllStatesFlow(): Flow<List<SmsSourceState>> = flowOf(states.values.toList())

    override suspend fun deleteAll() {
        states.clear()
    }
}
