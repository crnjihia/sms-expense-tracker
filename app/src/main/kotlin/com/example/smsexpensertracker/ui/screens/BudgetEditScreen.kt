package com.example.smsexpensertracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.smsexpensertracker.ui.viewmodel.BudgetViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smsexpensertracker.data.Budget

/**
 * Screen for creating a new budget or editing an existing one.
 */
@Composable
fun BudgetEditScreen(navController: NavHostController, budgetId: Long?) {
    val viewModel: BudgetViewModel = hiltViewModel()
    val existing = budgetId?.let { viewModel.getBudgetById(it) }

    var categoryIdText by remember { mutableStateOf(existing?.categoryId?.toString() ?: "") }
    var monthText by remember { mutableStateOf(existing?.month ?: "") }
    var limitText by remember { mutableStateOf(existing?.limitKsh?.toString() ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (existing == null) "Add Budget" else "Edit Budget") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = categoryIdText,
                onValueChange = { categoryIdText = it },
                label = { Text("Category ID") },
                isError = categoryIdText.isNotBlank() && categoryIdText.toLongOrNull() == null,
                singleLine = true
            )
            OutlinedTextField(
                value = monthText,
                onValueChange = { monthText = it },
                label = { Text("Month (yyyy-MM)") },
                singleLine = true
            )
            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it },
                label = { Text("Limit (Ksh)") },
                isError = limitText.isNotBlank() && limitText.toLongOrNull() == null,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val catId = categoryIdText.toLongOrNull()
                    val limit = limitText.toLongOrNull()
                    if (catId != null && monthText.isNotBlank() && limit != null) {
                        if (existing == null) {
                            viewModel.addBudget(catId, monthText.trim(), limit)
                        } else {
                            val updated = existing.copy(categoryId = catId, month = monthText.trim(), limitKsh = limit)
                            viewModel.updateBudget(updated)
                        }
                        navController.popBackStack()
                    }
                },
                enabled = categoryIdText.toLongOrNull() != null && limitText.toLongOrNull() != null && monthText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}
