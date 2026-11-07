// path: app/src/androidTest/java/com/example/smsexpensertracker/ui/ManualImportUiTest.kt
package com.example.smsexpensertracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.smsexpensertracker.parser.TemplateSmsParser
import com.example.smsexpensertracker.ui.theme.SmsExpenseTrackerTheme
import org.junit.Rule
import org.junit.Test

class ManualImportUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun manualImport_pastesSyntheticMpesaSmsAndDisplaysSummary() {
        val parser = TemplateSmsParser()

        composeTestRule.setContent {
            SmsExpenseTrackerTheme {
                Surface {
                    var textInput by remember { mutableStateOf("") }
                    var summaryText by remember { mutableStateOf<String?>(null) }

                    Column {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Paste SMS Messages") }
                        )

                        Button(
                            onClick = {
                                val lines = textInput.lines().filter { it.isNotBlank() }
                                var parsedCount = 0
                                var unparsedCount = 0

                                lines.forEach { line ->
                                    val parts = line.split("|", limit = 2)
                                    val sender = if (parts.size == 2) parts[0].trim() else "MPESA"
                                    val body = if (parts.size == 2) parts[1].trim() else parts[0].trim()

                                    val result = parser.parse(sender, body)
                                    if (result is com.example.smsexpensertracker.parser.SmsParseResult.Parsed) {
                                        parsedCount++
                                    } else {
                                        unparsedCount++
                                    }
                                }
                                summaryText = "$parsedCount messages imported, $unparsedCount unparsed"
                            }
                        ) {
                            Text("Import")
                        }

                        if (summaryText != null) {
                            Text(text = summaryText!!)
                        }
                    }
                }
            }
        }

        // Paste synthetic M-Pesa transaction
        val syntheticSms = "MPESA|QGH1234567 Confirmed. Ksh1,500.00 paid to KPLC PREPAID on 06/11/2026 at 10:30 AM. New M-PESA balance is Ksh4,500.00. Transaction cost, Ksh23.00."

        composeTestRule.onNodeWithText("Paste SMS Messages").performTextInput(syntheticSms)
        composeTestRule.onNodeWithText("Import").performClick()

        // Verify parsing summary is displayed
        composeTestRule.onNodeWithText("1 messages imported, 0 unparsed").assertIsDisplayed()
    }
}

