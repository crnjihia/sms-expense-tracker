// path: app/src/main/kotlin/com/example/smsexpensertracker/ui/navigation/NavGraph.kt
package com.example.smsexpensertracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smsexpensertracker.ui.screens.BudgetEditScreen
import com.example.smsexpensertracker.ui.screens.BudgetListScreen
import com.example.smsexpensertracker.ui.screens.HomeScreen
import com.example.smsexpensertracker.ui.screens.ManualImportScreen
import com.example.smsexpensertracker.ui.screens.PermissionScreen
import com.example.smsexpensertracker.ui.screens.ReportsScreen
import com.example.smsexpensertracker.ui.screens.RuleEditScreen
import com.example.smsexpensertracker.ui.screens.RuleListScreen
import com.example.smsexpensertracker.ui.screens.SettingsScreen
import com.example.smsexpensertracker.ui.screens.TransactionsScreen

/**
 * Top-level application navigation graph with Material 3 bottom navigation bar.
 */
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in Screen.bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Screen.bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { screen.icon?.let { Icon(it, contentDescription = screen.title) } },
                            label = {
                                Text(
                                    text = screen.title,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    fontSize = 10.5.sp
                                )
                            },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            // Top-Level Screens
            composable(Screen.Home.route) { HomeScreen(navController) }
            composable(Screen.Transactions.route) { TransactionsScreen(navController) }
            composable(Screen.Budgets.route) { BudgetListScreen(navController) }
            composable(Screen.Reports.route) { ReportsScreen(navController) }
            composable(Screen.Settings.route) { SettingsScreen(navController) }

            // Sub-Flows
            composable(Screen.Permission.route) { PermissionScreen(navController) }
            composable(Screen.ManualImport.route) { ManualImportScreen(navController) }
            composable(Screen.RuleList.route) { RuleListScreen(navController) }
            composable(Screen.RuleEdit.route) { RuleEditScreen(navController, null) }
            composable("rule_edit/{ruleId}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("ruleId")?.toLongOrNull()
                RuleEditScreen(navController, id)
            }
            composable(Screen.BudgetEdit.route) { BudgetEditScreen(navController, null) }
            composable("budget_edit/{budgetId}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("budgetId")?.toLongOrNull()
                BudgetEditScreen(navController, id)
            }
        }
    }
}
