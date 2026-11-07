// path: app/src/main/kotlin/com/example/smsexpensertracker/ui/screens/BudgetEditScreen.kt
package com.example.smsexpensertracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.smsexpensertracker.data.Budget
import com.example.smsexpensertracker.ui.viewmodel.BudgetEffect
import com.example.smsexpensertracker.ui.viewmodel.BudgetIntent
import com.example.smsexpensertracker.ui.viewmodel.BudgetViewModel
import kotlinx.coroutines.flow.collectLatest
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Screen for creating a new category budget or editing an existing one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditScreen(navController: NavHostController, budgetId: Long?) {
    val viewModel: BudgetViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    var existingBudget by remember { mutableStateOf<Budget?>(null) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var monthText by remember { mutableStateOf(currentKenyaMonth()) }
    var limitText by remember { mutableStateOf("") }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(budgetId) {
        if (budgetId != null) {
            val budget = viewModel.getBudgetById(budgetId)
            if (budget != null) {
                existingBudget = budget
                selectedCategoryId = budget.categoryId
                monthText = budget.month
                limitText = budget.limitKsh.toString()
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effect.collectLatest { effect ->
            if (effect is BudgetEffect.BudgetSaved) {
                navController.popBackStack()
            }
        }
    }

    val selectedCategoryName = uiState.availableCategories
        .find { it.id == selectedCategoryId }?.name
        ?: if (selectedCategoryId != null) "Category #${selectedCategoryId}" else "Select Category"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingBudget == null) "Add Budget" else "Edit Budget") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category Selector
            if (uiState.availableCategories.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedCategoryName,
                        onValueChange = {},
                        label = { Text("Category") },
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select category")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { categoryDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false }
                    ) {
                        uiState.availableCategories.forEach { category ->
                            DropdownMenuItem(
                                text = {
                                    val iconPrefix = if (category.icon != null) "${category.icon} " else ""
                                    Text("$iconPrefix${category.name}")
                                },
                                onClick = {
                                    selectedCategoryId = category.id
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = selectedCategoryId?.toString() ?: "",
                    onValueChange = { selectedCategoryId = it.toLongOrNull() },
                    label = { Text("Category ID") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Month Field
            OutlinedTextField(
                value = monthText,
                onValueChange = { monthText = it },
                label = { Text("Month (YYYY-MM)") },
                placeholder = { Text("e.g. 2026-11") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Monthly Limit Field (KES)
            OutlinedTextField(
                value = limitText,
                onValueChange = { input ->
                    if (input.all { it.isDigit() }) {
                        limitText = input
                    }
                },
                label = { Text("Monthly Limit (KES)") },
                placeholder = { Text("e.g. 15000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = limitText.isNotBlank() && (limitText.toLongOrNull() == null || limitText.toLong() <= 0)
            )

            Spacer(modifier = Modifier.height(16.dp))

            val isFormValid = selectedCategoryId != null &&
                    monthText.matches(Regex("""\d{4}-\d{2}""")) &&
                    (limitText.toLongOrNull() ?: 0L) > 0L

            Button(
                onClick = {
                    val catId = selectedCategoryId ?: return@Button
                    val limit = limitText.toLongOrNull() ?: return@Button
                    viewModel.dispatch(
                        BudgetIntent.SaveBudget(
                            categoryId = catId,
                            month = monthText.trim(),
                            limitKsh = limit
                        )
                    )
                },
                enabled = isFormValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Budget")
            }
        }
    }
}

private fun currentKenyaMonth(): String {
    val now = OffsetDateTime.now(ZoneId.of("Africa/Nairobi"))
    return "${now.year}-${"%02d".format(now.monthValue)}"
}
