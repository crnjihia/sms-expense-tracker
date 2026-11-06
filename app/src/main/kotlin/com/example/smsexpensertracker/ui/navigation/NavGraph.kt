package com.example.smsexpensertracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smsexpensertracker.ui.screens.HomeScreen
import com.example.smsexpensertracker.ui.screens.PermissionScreen
import com.example.smsexpensertracker.ui.screens.RuleListScreen
import com.example.smsexpensertracker.ui.screens.RuleEditScreen
import com.example.smsexpensertracker.ui.screens.BudgetListScreen
import com.example.smsexpensertracker.ui.screens.ManualImportScreen



/**
 * Navigation graph for the app.
 * Routes:
 * - "home" – main screen
 * - "permission" – permission rationale screen
 */
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("permission") { PermissionScreen(navController) }
        composable("manual_import") { ManualImportScreen(navController) }
        // Rule management
        composable("rule_list") { RuleListScreen(navController) }
        composable("rule_edit") { RuleEditScreen(navController, null) }
        composable("rule_edit/{ruleId}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("ruleId")?.toLongOrNull()
            RuleEditScreen(navController, id)
        }
        // Budget management
        composable("budget_list") { BudgetListScreen(navController) }
        composable("budget_edit") { BudgetEditScreen(navController, null) }
        composable("budget_edit/{budgetId}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("budgetId")?.toLongOrNull()
            BudgetEditScreen(navController, id)
        }
    }

}
