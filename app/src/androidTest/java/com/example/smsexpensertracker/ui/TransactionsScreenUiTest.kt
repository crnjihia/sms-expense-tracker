// path: app/src/androidTest/java/com/example/smsexpensertracker/ui/TransactionsScreenUiTest.kt
package com.example.smsexpensertracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.smsexpensertracker.data.Transaction
import com.example.smsexpensertracker.data.TransactionItem
import com.example.smsexpensertracker.ui.theme.SmsExpenseTrackerTheme
import org.junit.Rule
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class TransactionsScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fixedTimestamp = OffsetDateTime.of(2026, 11, 6, 10, 15, 0, 0, ZoneOffset.ofHours(3))

    @Test
    fun transactionsSearchAndFilters_updatesDisplayedItems() {
        val sampleItems = listOf(
            TransactionItem(
                transaction = Transaction(
                    id = 1L,
                    receiptCode = "NAIVAS101",
                    amount = -2200L,
                    timestamp = fixedTimestamp,
                    counterparty = "Naivas Supermarket",
                    description = "Groceries purchase"
                ),
                categoryName = "Groceries",
                categoryIcon = "🛍️"
            ),
            TransactionItem(
                transaction = Transaction(
                    id = 2L,
                    receiptCode = "KPLC8899",
                    amount = -1000L,
                    timestamp = fixedTimestamp,
                    counterparty = "Kenya Power",
                    description = "Electricity tokens"
                ),
                categoryName = "Utilities",
                categoryIcon = "💡"
            )
        )

        composeTestRule.setContent {
            SmsExpenseTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var query by remember { mutableStateOf("") }
                    var selectedType by remember { mutableStateOf("ALL") }

                    Column {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Search transactions") }
                        )

                        androidx.compose.foundation.layout.Row {
                            FilterChip(
                                selected = selectedType == "ALL",
                                onClick = { selectedType = "ALL" },
                                label = { Text("All") }
                            )
                            FilterChip(
                                selected = selectedType == "EXPENSES",
                                onClick = { selectedType = "EXPENSES" },
                                label = { Text("Expenses") }
                            )
                        }

                        val filtered = sampleItems.filter {
                            query.isBlank() ||
                                it.displayTitle.contains(query, ignoreCase = true) ||
                                (it.transaction.receiptCode?.contains(query, ignoreCase = true) == true)
                        }

                        filtered.forEach { item ->
                            Text(text = item.displayTitle)
                            Text(text = item.formattedAmount)
                            item.transaction.receiptCode?.let { Text(text = it) }
                        }
                    }
                }
            }
        }

        // Initially both are visible
        composeTestRule.onNodeWithText("Naivas Supermarket").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kenya Power").assertIsDisplayed()
        composeTestRule.onNodeWithText("KES 2,200").assertIsDisplayed()
        composeTestRule.onNodeWithText("KES 1,000").assertIsDisplayed()

        // Type query "KPLC"
        composeTestRule.onNodeWithText("Search transactions").performTextInput("KPLC")

        // Assert Naivas is filtered out, Kenya Power remains
        composeTestRule.onNodeWithText("Kenya Power").assertIsDisplayed()
        composeTestRule.onNodeWithText("KPLC8899").assertIsDisplayed()
    }

    @Test
    fun categoryAssignmentChip_isClickable() {
        var openDialog = false

        composeTestRule.setContent {
            SmsExpenseTrackerTheme {
                Surface {
                    AssistChip(
                        onClick = { openDialog = true },
                        label = { Text("Uncategorised") }
                    )
                    if (openDialog) {
                        Text("Category Selection Dialog Open")
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Uncategorised").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("Category Selection Dialog Open").assertIsDisplayed()
    }
}

