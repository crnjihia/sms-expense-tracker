// path: app/src/main/kotlin/com/example/smsexpensertracker/ui/navigation/Screen.kt
package com.example.smsexpensertracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Destinations in the application. Top-level destinations populate the bottom navigation bar.
 */
sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null
) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Transactions : Screen("transactions", "Transactions", Icons.AutoMirrored.Filled.ReceiptLong)
    data object Budgets : Screen("budget_list", "Budgets", Icons.Default.AccountBalanceWallet)
    data object Reports : Screen("reports", "Reports", Icons.Default.BarChart)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    // Sub-screens
    data object Permission : Screen("permission", "Permissions")
    data object ManualImport : Screen("manual_import", "Manual Import")
    data object RuleList : Screen("rule_list", "Rules")
    data object RuleEdit : Screen("rule_edit", "Edit Rule")
    data object BudgetEdit : Screen("budget_edit", "Edit Budget")

    companion object {
        val bottomNavItems = listOf(Home, Transactions, Budgets, Reports, Settings)
    }
}
