// path: app/src/androidTest/java/com/example/smsexpensertracker/ui/BudgetScreenUiTest.kt
package com.example.smsexpensertracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.smsexpensertracker.data.BudgetProgress
import com.example.smsexpensertracker.data.BudgetStatus
import com.example.smsexpensertracker.ui.theme.SmsExpenseTrackerTheme
import org.junit.Rule
import org.junit.Test

class BudgetScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun budgetProgressList_displaysStatusBadgesAndLimits() {
        val budgets = listOf(
            BudgetProgress(
                budgetId = 1L,
                categoryId = 10L,
                categoryName = "Food & Groceries",
                categoryIcon = "🛒",
                month = "2026-11",
                limitKsh = 10000L,
                spentKsh = 4500L // 45% -> Normal
            ),
            BudgetProgress(
                budgetId = 2L,
                categoryId = 20L,
                categoryName = "Transport",
                categoryIcon = "🚗",
                month = "2026-11",
                limitKsh = 5000L,
                spentKsh = 4200L // 84% -> Warning
            ),
            BudgetProgress(
                budgetId = 3L,
                categoryId = 30L,
                categoryName = "Entertainment",
                categoryIcon = "🎬",
                month = "2026-11",
                limitKsh = 3000L,
                spentKsh = 3500L // 116% -> Exhausted
            )
        )

        composeTestRule.setContent {
            SmsExpenseTrackerTheme {
                Surface {
                    Column {
                        budgets.forEach { item ->
                            Text(text = item.categoryName)
                            Text(text = "${item.formattedSpent} of ${item.formattedLimit}")

                            when (item.status) {
                                BudgetStatus.EXHAUSTED -> Text(text = "Exhausted (100%+)")
                                BudgetStatus.WARNING -> Text(text = "Warning (80%+)")
                                BudgetStatus.NORMAL -> Text(text = "${item.percentageInt}%")
                            }
                        }
                    }
                }
            }
        }

        // Verify categories and status badges
        composeTestRule.onNodeWithText("Food & Groceries").assertIsDisplayed()
        composeTestRule.onNodeWithText("45%").assertIsDisplayed()

        composeTestRule.onNodeWithText("Transport").assertIsDisplayed()
        composeTestRule.onNodeWithText("Warning (80%+)").assertIsDisplayed()

        composeTestRule.onNodeWithText("Entertainment").assertIsDisplayed()
        composeTestRule.onNodeWithText("Exhausted (100%+)").assertIsDisplayed()
    }
}

