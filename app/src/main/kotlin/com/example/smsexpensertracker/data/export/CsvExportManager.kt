// path: app/src/main/kotlin/com/example/smsexpensertracker/data/export/CsvExportManager.kt
package com.example.smsexpensertracker.data.export

import com.example.smsexpensertracker.data.CategoryDao
import com.example.smsexpensertracker.data.Transaction
import com.example.smsexpensertracker.data.TransactionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles exporting on-device transactions into standard RFC-4180 CSV files.
 * Designed for Android Storage Access Framework (SAF) integration and auditing.
 */
@Singleton
class CsvExportManager @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) {
    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val CSV_HEADERS = listOf(
            "Receipt Code",
            "Date (EAT)",
            "Counterparty",
            "Description",
            "Transaction Type",
            "Amount (KES)",
            "Balance (KES)",
            "Fee (KES)",
            "Category"
        )
    }

    /**
     * Formats a single CSV field following RFC-4180 escaping rules.
     */
    fun escapeCsvField(value: String?): String {
        if (value == null) return ""
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuotes) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /**
     * Converts a transaction and category name into a CSV row string.
     */
    fun formatTransactionRow(tx: Transaction, categoryName: String?): String {
        val dateStr = tx.timestamp.format(DATE_FORMATTER)
        val amountStr = tx.amount.toString()
        val balanceStr = tx.balanceKsh?.toString() ?: ""
        val feeStr = tx.feeKsh?.toString() ?: ""
        val counterpartyStr = tx.counterparty ?: ""
        val receiptCodeStr = tx.receiptCode ?: ""
        val categoryStr = categoryName ?: "Uncategorised"

        val fields = listOf(
            escapeCsvField(receiptCodeStr),
            escapeCsvField(dateStr),
            escapeCsvField(counterpartyStr),
            escapeCsvField(tx.description),
            escapeCsvField(tx.transactionType),
            escapeCsvField(amountStr),
            escapeCsvField(balanceStr),
            escapeCsvField(feeStr),
            escapeCsvField(categoryStr)
        )

        return fields.joinToString(",")
    }

    /**
     * Exports transactions with resolved categories to a CSV string.
     */
    fun generateCsvContent(transactions: List<Transaction>, categoryMap: Map<Long, String>): String {
        val builder = StringBuilder()
        builder.append(CSV_HEADERS.joinToString(",")).append("\r\n")

        transactions.forEach { tx ->
            val catName = tx.categoryId?.let { categoryMap[it] }
            builder.append(formatTransactionRow(tx, catName)).append("\r\n")
        }

        return builder.toString()
    }

    /**
     * Reads all transactions and categories from Room and writes the complete CSV to the target stream.
     */
    suspend fun exportToStream(outputStream: OutputStream): Int = withContext(Dispatchers.IO) {
        val transactions = transactionDao.getAllTransactions().first()
        val categories = categoryDao.getAllCategories().first()
        val categoryMap = categories.associate { it.id to it.name }

        val csvText = generateCsvContent(transactions, categoryMap)
        OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(csvText)
            writer.flush()
        }

        transactions.size
    }
}

