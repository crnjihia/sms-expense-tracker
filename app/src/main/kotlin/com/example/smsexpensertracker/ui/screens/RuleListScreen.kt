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
import com.example.smsexpensertracker.data.CategorisationRule
import com.example.smsexpensertracker.ui.viewmodel.RuleViewModel
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Screen that lists all categorisation rules.
 */
@Composable
fun RuleListScreen(navController: NavHostController) {
    val viewModel: RuleViewModel = hiltViewModel()
    val rules = viewModel.rules.collectAsState().value

    Scaffold(
        topBar = { TopAppBar(title = { Text("Categorisation Rules") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("rule_edit") }) {
                Icon(Icons.Default.Add, contentDescription = "Add rule")
            }
        }
    ) { paddingValues ->
        if (rules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No rules defined yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(rules) { rule ->
                    RuleItem(
                        rule = rule,
                        onEdit = { navController.navigate("rule_edit/${rule.id}") },
                        onDelete = { viewModel.deleteRule(rule.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleItem(
    rule: CategorisationRule,
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
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "Category ID: ${rule.categoryId}")
                Text(text = "Keyword: \"${rule.keyword}\"")
            }
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
            }
        }
    }
}
