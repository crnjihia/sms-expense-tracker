// path: app/src/main/kotlin/com/example/smsexpensertracker/ui/screens/BudgetListScreen.kt
package com.example.smsexpensertracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.smsexpensertracker.data.BudgetProgress
import com.example.smsexpensertracker.data.BudgetStatus
import com.example.smsexpensertracker.ui.viewmodel.BudgetEffect
import com.example.smsexpensertracker.ui.viewmodel.BudgetIntent
import com.example.smsexpensertracker.ui.viewmodel.BudgetViewModel
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

/**
 * Screen displaying monthly category budgets, progress indicators (80% warning / 100% exhausted),
 * and budget CRUD actions.
 * Enhanced with TalkBack accessibility semantics, ProgressBarRangeInfo, and 48dp touch targets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetListScreen(navController: NavHostController) {
    val viewModel: BudgetViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is BudgetEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Monthly Budgets") },
                actions = {
                    IconButton(
                        onClick = { viewModel.dispatch(BudgetIntent.CheckBudgets) },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Check Budget Alerts"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("budget_edit") },
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add new budget")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Month Summary Card
            BudgetSummaryCard(
                month = uiState.selectedMonth,
                totalSpent = uiState.totalSpentKsh,
                totalLimit = uiState.totalLimitKsh,
                percentage = uiState.overallPercentage
            )

            if (uiState.budgetItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No budgets defined for ${uiState.selectedMonth}.\nTap '+' to create one.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.budgetItems, key = { it.budgetId }) { item ->
                        BudgetItem(
                            item = item,
                            onEdit = { navController.navigate("budget_edit/${item.budgetId}") },
                            onDelete = { viewModel.dispatch(BudgetIntent.DeleteBudget(item.budgetId)) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetSummaryCard(
    month: String,
    totalSpent: Long,
    totalLimit: Long,
    percentage: Float
) {
    val percentInt = (percentage * 100).toInt()
    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Budget summary for $month: Spent KES $totalSpent of KES $totalLimit limit, $percentInt percent used"
            }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Overall Usage ($month)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "$percentInt%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        percentage >= 1.0f -> MaterialTheme.colorScheme.error
                        percentage >= 0.8f -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }

            LinearProgressIndicator(
                progress = { percentage.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(percentage.coerceIn(0f, 1f), 0f..1f)
                    },
                color = when {
                    percentage >= 1.0f -> MaterialTheme.colorScheme.error
                    percentage >= 0.8f -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Spent: ${String.format(Locale.US, "KES %,d", totalSpent)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Limit: ${String.format(Locale.US, "KES %,d", totalLimit)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun BudgetItem(
    item: BudgetProgress,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val progressColor = when (item.status) {
        BudgetStatus.EXHAUSTED -> MaterialTheme.colorScheme.error
        BudgetStatus.WARNING -> Color(0xFFE65100) // Deep orange warning
        BudgetStatus.NORMAL -> MaterialTheme.colorScheme.primary
    }

    val itemAccessibilityDescription = "${item.categoryName} budget: ${item.percentageInt} percent used, ${item.formattedSpent} of ${item.formattedLimit}. ${if (item.isExhausted) "Exhausted" else "Remaining ${item.formattedRemaining}"}"

    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = itemAccessibilityDescription
            }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.categoryIcon != null) {
                        Text(text = item.categoryIcon, modifier = Modifier.padding(end = 8.dp))
                    }
                    Text(
                        text = item.categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Status Badge
                when (item.status) {
                    BudgetStatus.EXHAUSTED -> {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "Exhausted (100%+)",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    BudgetStatus.WARNING -> {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = Color(0xFFFFE0B2)
                        ) {
                            Text(
                                text = "Warning (80%+)",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                    BudgetStatus.NORMAL -> {
                        Text(
                            text = "${item.percentageInt}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { item.percentage.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(item.percentage.coerceIn(0f, 1f), 0f..1f)
                    },
                color = progressColor
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${item.formattedSpent} of ${item.formattedLimit}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (item.isExhausted) {
                            "Overspent by ${String.format(Locale.US, "KES %,d", item.overspentKsh)}"
                        } else {
                            "Remaining: ${item.formattedRemaining}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isExhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                }

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit budget for ${item.categoryName}")
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete budget for ${item.categoryName}")
                    }
                }
            }
        }
    }
}
