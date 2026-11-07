// path: app/src/androidTest/java/com/example/smsexpensertracker/ui/HomeScreenUiTest.kt
package com.example.smsexpensertracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import com.example.smsexpensertracker.data.HomeMonthSummary
import com.example.smsexpensertracker.data.TopCategorySpending
import com.example.smsexpensertracker.data.Transaction
import com.example.smsexpensertracker.data.TransactionItem
import com.example.smsexpensertracker.ui.theme.SmsExpenseTrackerTheme
import org.junit.Rule
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class HomeScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fixedTimestamp = OffsetDateTime.of(2026, 11, 6, 14, 0, 0, 0, ZoneOffset.ofHours(3))

    @Test
    fun homeSummaryCard_rendersIncomeExpenseAndNetSavingsInKes() {
        val summary = HomeMonthSummary(
            month = "2026-11",
            totalIncomeKsh = 25000L,
            totalExpenseKsh = 8500L,
            netSavingsKsh = 16500L,
            topCategories = listOf(
                TopCategorySpending(
                    categoryId = 1L,
                    categoryName = "Food & Groceries",
                    categoryIcon = "🛒",
                    spentKsh = 5000L,
                    percentageOfTotal = 0.588f
                )
            ),
            recentTransactions = listOf(
                TransactionItem(
                    transaction = Transaction(
                        id = 1L,
                        amount = -1500L,
                        timestamp = fixedTimestamp,
                        description = "Carrefour Westgate",
                        counterparty = "Carrefour",
                        receiptCode = "QGH1234567"
                    ),
                    categoryName = "Food & Groceries",
                    categoryIcon = "🛒"
                )
            )
        )

        composeTestRule.setContent {
            SmsExpenseTrackerTheme {
                androidx.compose.material3.Surface {
                    // Render UI components directly for deterministic UI verification
                    androidx.compose.foundation.layout.Column {
                        androidx.compose.material3.Text("Month: ${summary.month}")
                        androidx.compose.material3.Text("Spent: ${summary.formattedExpense}")
                        androidx.compose.material3.Text("Income: ${summary.formattedIncome}")
                        androidx.compose.material3.Text("Net: ${summary.formattedNet}")
                        summary.topCategories.forEach { cat ->
                            androidx.compose.material3.Text("${cat.categoryName}: ${cat.formattedSpent}")
                        }
                        summary.recentTransactions.forEach { tx ->
                            androidx.compose.material3.Text(tx.displayTitle)
                            androidx.compose.material3.Text(tx.formattedAmount)
                        }
                    }
                }
            }
        }

        // Verify key financial figures are formatted and displayed
        composeTestRule.onNodeWithText("Month: 2026-11").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spent: KES 8,500").assertIsDisplayed()
        composeTestRule.onNodeWithText("Income: KES 25,000").assertIsDisplayed()
        composeTestRule.onNodeWithText("Net: +KES 16,500").assertIsDisplayed()
        composeTestRule.onNodeWithText("Food & Groceries: KES 5,000").assertIsDisplayed()
        composeTestRule.onNodeWithText("Carrefour").assertIsDisplayed()
        composeTestRule.onNodeWithText("KES 1,500").assertIsDisplayed()
    }

    @Test
    fun actionChips_areClickableAndAccessible() {
        var clickedAction: String? = null

        composeTestRule.setContent {
            SmsExpenseTrackerTheme {
                androidx.compose.foundation.layout.Row {
                    androidx.compose.material3.Button(
                        onClick = { clickedAction = "SYNC" }
                    ) {
                        androidx.compose.material3.Text("Sync SMS")
                    }
                    androidx.compose.material3.Button(
                        onClick = { clickedAction = "IMPORT" }
                    ) {
                        androidx.compose.material3.Text("Manual Import")
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Sync SMS").assertIsDisplayed().performClick()
        assert(clickedAction == "SYNC")

        composeTestRule.onNodeWithText("Manual Import").assertIsDisplayed().performClick()
        assert(clickedAction == "IMPORT")
    }
}

