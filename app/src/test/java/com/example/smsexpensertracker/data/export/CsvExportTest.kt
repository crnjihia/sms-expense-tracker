// path: app/src/test/java/com/example/smsexpensertracker/data/export/CsvExportTest.kt
package com.example.smsexpensertracker.data.export

import com.example.smsexpensertracker.data.Category
import com.example.smsexpensertracker.data.CategoryDao
import com.example.smsexpensertracker.data.CategorySpending
import com.example.smsexpensertracker.data.Transaction
import com.example.smsexpensertracker.data.TransactionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime
import java.time.ZoneOffset

class CsvExportTest {

    private val fixedTimestamp = OffsetDateTime.of(2026, 11, 6, 15, 30, 0, 0, ZoneOffset.ofHours(3))

    @Test
    fun `escapeCsvField properly escapes special characters per RFC 4180`() {
        val exporter = CsvExportManager(ExportFakeTransactionDao(), ExportFakeCategoryDao())

        assertEquals("Normal Text", exporter.escapeCsvField("Normal Text"))
        assertEquals("", exporter.escapeCsvField(null))
        assertEquals("\"Naivas, Westlands\"", exporter.escapeCsvField("Naivas, Westlands"))
        assertEquals("\"Buy \"\"Tokens\"\" Now\"", exporter.escapeCsvField("Buy \"Tokens\" Now"))
        assertEquals("\"Line 1\nLine 2\"", exporter.escapeCsvField("Line 1\nLine 2"))
    }

    @Test
    fun `formatTransactionRow formats transaction attributes correctly`() {
        val exporter = CsvExportManager(ExportFakeTransactionDao(), ExportFakeCategoryDao())
        val tx = Transaction(
            id = 1L,
            receiptCode = "SH12345678",
            timestamp = fixedTimestamp,
            description = "Payment to Java House, Nairobi",
            counterparty = "Java House",
            transactionType = "PAYBILL",
            amount = -1500L,
            balanceKsh = 12450L,
            feeKsh = 23L,
            categoryId = 10L
        )

        val row = exporter.formatTransactionRow(tx, "Dining")
        val expected = "SH12345678,2026-11-06 15:30:00,Java House,\"Payment to Java House, Nairobi\",PAYBILL,-1500,12450,23,Dining"
        assertEquals(expected, row)
    }

    @Test
    fun `generateCsvContent includes header and maps categories`() {
        val exporter = CsvExportManager(ExportFakeTransactionDao(), ExportFakeCategoryDao())
        val txList = listOf(
            Transaction(
                id = 1L,
                receiptCode = "TX1",
                timestamp = fixedTimestamp,
                description = "Uber Nairobi",
                counterparty = "Uber",
                transactionType = "TILL",
                amount = -850L,
                balanceKsh = 5000L,
                feeKsh = 0L,
                categoryId = 1L
            ),
            Transaction(
                id = 2L,
                receiptCode = "TX2",
                timestamp = fixedTimestamp,
                description = "Unknown deposit",
                counterparty = "John",
                transactionType = "RECEIVED",
                amount = 3000L,
                balanceKsh = 8000L,
                feeKsh = 0L,
                categoryId = null
            )
        )

        val catMap = mapOf(1L to "Transport")
        val csv = exporter.generateCsvContent(txList, catMap)

        val lines = csv.split("\r\n").filter { it.isNotBlank() }
        assertEquals(3, lines.size)
        assertEquals("Receipt Code,Date (EAT),Counterparty,Description,Transaction Type,Amount (KES),Balance (KES),Fee (KES),Category", lines[0])
        assertTrue(lines[1].contains("Transport"))
        assertTrue(lines[1].contains("-850"))
        assertTrue(lines[2].contains("Uncategorised"))
        assertTrue(lines[2].contains("3000"))
    }

    @Test
    fun `exportToStream writes all Room transactions into target stream`() = runBlocking {
        val txDao = ExportFakeTransactionDao()
        val catDao = ExportFakeCategoryDao()

        catDao.insert(Category(id = 5L, name = "Groceries"))
        txDao.insert(
            Transaction(
                id = 1L,
                receiptCode = "QK998877",
                timestamp = fixedTimestamp,
                description = "Carrefour Sarit",
                counterparty = "Carrefour",
                transactionType = "TILL",
                amount = -4500L,
                balanceKsh = 20000L,
                feeKsh = 0L,
                categoryId = 5L
            )
        )

        val exporter = CsvExportManager(txDao, catDao)
        val stream = ByteArrayOutputStream()
        val count = exporter.exportToStream(stream)

        assertEquals(1, count)
        val written = stream.toString(Charsets.UTF_8.name())
        assertTrue(written.startsWith("Receipt Code,"))
        assertTrue(written.contains("Carrefour"))
        assertTrue(written.contains("Groceries"))
    }
}

private class ExportFakeTransactionDao : TransactionDao {
    private val transactions = mutableListOf<Transaction>()

    override suspend fun insert(transaction: Transaction): Long {
        transactions.add(transaction)
        return transaction.id
    }

    override suspend fun insertAll(transactions: List<Transaction>): List<Long> {
        this.transactions.addAll(transactions)
        return transactions.map { it.id }
    }

    override suspend fun update(transaction: Transaction) {}
    override fun getAllTransactions(): Flow<List<Transaction>> = flowOf(transactions)
    override suspend fun getById(id: Long): Transaction? = transactions.find { it.id == id }
    override suspend fun findByReceiptCode(receiptCode: String): Transaction? = transactions.find { it.receiptCode == receiptCode }
    override fun searchTransactions(query: String): Flow<List<Transaction>> = flowOf(transactions)
    override fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> = flowOf(transactions)
    override fun getTransactionsByDateRange(start: OffsetDateTime, end: OffsetDateTime): Flow<List<Transaction>> = flowOf(transactions)
    override suspend fun getDuplicate(amount: Long, timestamp: OffsetDateTime, description: String): Transaction? = null
    override suspend fun getUncategorisedTransactions(): List<Transaction> = emptyList()
    override suspend fun deleteById(id: Long) {}
    override suspend fun deleteAll() { transactions.clear() }
    override suspend fun getSpendingByCategory(start: OffsetDateTime, end: OffsetDateTime): List<CategorySpending> = emptyList()
    override fun getSpendingByCategoryFlow(start: OffsetDateTime, end: OffsetDateTime): Flow<List<CategorySpending>> = flowOf(emptyList())
}

private class ExportFakeCategoryDao : CategoryDao {
    private val categories = mutableListOf<Category>()

    override suspend fun insert(category: Category): Long {
        categories.add(category)
        return category.id
    }

    override suspend fun insertAll(categories: List<Category>): List<Long> {
        this.categories.addAll(categories)
        return categories.map { it.id }
    }

    override suspend fun update(category: Category) {}
    override fun getAllCategories(): Flow<List<Category>> = flowOf(categories)
    override suspend fun getById(id: Long): Category? = categories.find { it.id == id }
    override suspend fun findByName(name: String): Category? = categories.find { it.name == name }
    override suspend fun deleteById(id: Long) {}
}

