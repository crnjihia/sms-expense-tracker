// path: app/src/main/kotlin/com/example/smsexpensertracker/ui/screens/TransactionsScreen.kt
package com.example.smsexpensertracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.smsexpensertracker.data.DateRangeFilter
import com.example.smsexpensertracker.data.TransactionItem
import com.example.smsexpensertracker.data.TransactionTypeFilter
import com.example.smsexpensertracker.ui.viewmodel.TransactionEffect
import com.example.smsexpensertracker.ui.viewmodel.TransactionIntent
import com.example.smsexpensertracker.ui.viewmodel.TransactionViewModel
import kotlinx.coroutines.flow.collectLatest
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Screen providing searchable, filterable transaction history with
 * category re-assignment, rule learning, and transaction split capabilities.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(navController: NavHostController) {
    val viewModel: TransactionViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTransactionForEdit by remember { mutableStateOf<TransactionItem?>(null) }
    var selectedTransactionForSplit by remember { mutableStateOf<TransactionItem?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is TransactionEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                TransactionEffect.CategoryUpdated -> selectedTransactionForEdit = null
                TransactionEffect.TransactionSplitSuccess -> selectedTransactionForSplit = null
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Transactions (${uiState.transactions.size})") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Input
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { viewModel.dispatch(TransactionIntent.UpdateSearchQuery(it)) },
                placeholder = { Text("Search by merchant, receipt, or text...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.dispatch(TransactionIntent.UpdateSearchQuery("")) }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Filter Chips Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Type filter chips
                item {
                    FilterChip(
                        selected = uiState.typeFilter == TransactionTypeFilter.ALL,
                        onClick = { viewModel.dispatch(TransactionIntent.SelectTypeFilter(TransactionTypeFilter.ALL)) },
                        label = { Text("All") },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.typeFilter == TransactionTypeFilter.EXPENSE,
                        onClick = { viewModel.dispatch(TransactionIntent.SelectTypeFilter(TransactionTypeFilter.EXPENSE)) },
                        label = { Text("Expenses") },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.typeFilter == TransactionTypeFilter.INCOME,
                        onClick = { viewModel.dispatch(TransactionIntent.SelectTypeFilter(TransactionTypeFilter.INCOME)) },
                        label = { Text("Income") },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    )
                }

                // Date filter chips
                item {
                    FilterChip(
                        selected = uiState.dateFilter == DateRangeFilter.THIS_MONTH,
                        onClick = {
                            val next = if (uiState.dateFilter == DateRangeFilter.THIS_MONTH) DateRangeFilter.ALL else DateRangeFilter.THIS_MONTH
                            viewModel.dispatch(TransactionIntent.SelectDateFilter(next))
                        },
                        label = { Text("This Month") },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.dateFilter == DateRangeFilter.LAST_7_DAYS,
                        onClick = {
                            val next = if (uiState.dateFilter == DateRangeFilter.LAST_7_DAYS) DateRangeFilter.ALL else DateRangeFilter.LAST_7_DAYS
                            viewModel.dispatch(TransactionIntent.SelectDateFilter(next))
                        },
                        label = { Text("Last 7 Days") },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    )
                }
            }

            // Transaction List
            if (uiState.transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.query.isNotBlank()) "No transactions found matching '${uiState.query}'"
                        else "No transactions found with selected filters.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.transactions, key = { it.transaction.id }) { item ->
                        TransactionRowItem(
                            item = item,
                            onEditCategory = { selectedTransactionForEdit = item },
                            onSplit = { selectedTransactionForSplit = item },
                            onDelete = { viewModel.dispatch(TransactionIntent.DeleteTransaction(item.transaction.id)) }
                        )
                    }
                }
            }
        }

        // Category Edit Dialog
        selectedTransactionForEdit?.let { item ->
            EditCategoryDialog(
                item = item,
                categories = uiState.availableCategories,
                onDismiss = { selectedTransactionForEdit = null },
                onSave = { categoryId, learnKeyword ->
                    viewModel.dispatch(
                        TransactionIntent.UpdateCategory(
                            transactionId = item.transaction.id,
                            categoryId = categoryId,
                            learnKeyword = learnKeyword
                        )
                    )
                }
            )
        }

        // Transaction Split Bottom Sheet
        selectedTransactionForSplit?.let { item ->
            SplitTransactionDialog(
                item = item,
                categories = uiState.availableCategories,
                onDismiss = { selectedTransactionForSplit = null },
                onSplit = { firstAmount, firstCat, secondAmount, secondCat ->
                    viewModel.dispatch(
                        TransactionIntent.SplitTransaction(
                            transactionId = item.transaction.id,
                            firstAmountKsh = firstAmount,
                            firstCategoryId = firstCat,
                            secondAmountKsh = secondAmount,
                            secondCategoryId = secondCat
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun TransactionRowItem(
    item: TransactionItem,
    onEditCategory: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit
) {
    val accessibilityDescription = "${item.displayTitle}, ${if (item.isExpense) "expense" else "income"} of ${item.formattedAmount}, receipt ${item.transaction.receiptCode ?: "none"}, category ${item.categoryName ?: "uncategorised"}"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDescription
            },
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (item.transaction.receiptCode != null) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = item.transaction.receiptCode,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "${if (item.isExpense) "-" else "+"}${item.formattedAmount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isExpense) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = onEditCategory,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        label = {
                            val iconPrefix = if (item.categoryIcon != null) "${item.categoryIcon} " else ""
                            Text(text = "$iconPrefix${item.categoryName ?: "Uncategorised"}")
                        },
                        trailingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.transaction.timestamp.format(DateTimeFormatter.ofPattern("dd MMM, hh:mm a")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    IconButton(
                        onClick = onSplit,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = "Split transaction ${item.displayTitle}")
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete transaction ${item.displayTitle}")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditCategoryDialog(
    item: TransactionItem,
    categories: List<com.example.smsexpensertracker.data.Category>,
    onDismiss: () -> Unit,
    onSave: (Long?, String?) -> Unit
) {
    var selectedCatId by remember { mutableStateOf(item.transaction.categoryId) }
    var shouldLearn by remember { mutableStateOf(false) }
    val defaultKeyword = item.transaction.counterparty?.ifBlank { null }
        ?: item.transaction.description.split(" ").firstOrNull { it.length > 3 } ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = item.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(categories) { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCatId = cat.id }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedCatId == cat.id, onClick = { selectedCatId = cat.id })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${cat.icon ?: ""} ${cat.name}")
                        }
                    }
                }

                if (selectedCatId != null && defaultKeyword.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { shouldLearn = !shouldLearn }
                    ) {
                        Checkbox(checked = shouldLearn, onCheckedChange = { shouldLearn = it })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Always categorise '$defaultKeyword' as this category",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedCatId, if (shouldLearn) defaultKeyword else null) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SplitTransactionDialog(
    item: TransactionItem,
    categories: List<com.example.smsexpensertracker.data.Category>,
    onDismiss: () -> Unit,
    onSplit: (Long, Long?, Long, Long?) -> Unit
) {
    val totalAbs = abs(item.transaction.amount)
    var firstAmountText by remember { mutableStateOf((totalAbs / 2).toString()) }
    var firstCategoryId by remember { mutableStateOf(item.transaction.categoryId) }
    var secondCategoryId by remember { mutableStateOf<Long?>(null) }

    val firstAmount = firstAmountText.toLongOrNull() ?: 0L
    val secondAmount = (totalAbs - firstAmount).coerceAtLeast(0L)
    val isValid = firstAmount > 0L && secondAmount > 0L && (firstAmount + secondAmount == totalAbs)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Total: ${item.formattedAmount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // First Split Part
                OutlinedTextField(
                    value = firstAmountText,
                    onValueChange = { input -> if (input.all { it.isDigit() }) firstAmountText = input },
                    label = { Text("Part 1 Amount (KES)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Part 2 Amount: ${String.format(Locale.US, "KES %,d", secondAmount)}", fontWeight = FontWeight.SemiBold)

                if (!isValid) {
                    Text("Split amounts must sum to ${item.formattedAmount}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSplit(firstAmount, firstCategoryId, secondAmount, secondCategoryId) },
                enabled = isValid
            ) {
                Text("Split")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
