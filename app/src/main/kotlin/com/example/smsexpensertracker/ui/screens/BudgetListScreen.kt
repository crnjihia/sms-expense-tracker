package com.example.smsexpensertracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.smsexpensertracker.data.Budget
import com.example.smsexpensertracker.ui.viewmodel.BudgetViewModel
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Screen that lists all budget entries. Allows creating, editing and deleting.
 */
@Composable
fun BudgetListScreen(navController: NavHostController) {
    val viewModel: BudgetViewModel = hiltViewModel()
    val budgets = viewModel.budgets.collectAsState().value
    val spendingMap = viewModel.spendingMap.collectAsState().value

    Scaffold(
        topBar = { TopAppBar(title = { Text("Budgets") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("budget_edit") }) {
                Icon(Icons.Default.Add, contentDescription = "Add budget")
            }
        }
    ) { paddingValues ->
        if (budgets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No budgets defined yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(budgets) { budget ->
                    val spent = spendingMap[budget.categoryId] ?: 0L
                    BudgetItem(
                        budget = budget,
                        spent = spent,
                        onEdit = { navController.navigate("budget_edit/${budget.id}") },
                        onDelete = { viewModel.deleteBudget(budget) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetItem(
    budget: Budget,
    spent: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "Category ID: ${budget.categoryId}")
                Text(text = "Month: ${budget.month}")
                Text(text = "Limit: Ksh${budget.limitKsh}")
                Text(text = "Spent: Ksh${spent}")
            }
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
            }
        }
    }
}
