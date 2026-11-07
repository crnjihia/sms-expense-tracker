package com.example.smsexpensertracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.smsexpensertracker.R
import com.example.smsexpensertracker.ui.viewmodel.ManualImportViewModel

/**
 * Screen that allows the user to paste raw SMS messages (sender|body) and import them
 * into the app's database. Results (inserted / unparsed) are shown after the import.
 */
@Composable
fun ManualImportScreen(navController: NavHostController) {
    val viewModel: ManualImportViewModel = hiltViewModel()
    var inputText by remember { mutableStateOf("") }
    val importResult by viewModel.importResult.collectAsState(initial = null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(R.string.manual_import_title))
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text(stringResource(R.string.paste_messages_label)) },
            placeholder = { Text(stringResource(R.string.paste_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            maxLines = 10
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { viewModel.importMessages(inputText) },
            enabled = inputText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.import_button))
        }
        Spacer(modifier = Modifier.height(12.dp))
        importResult?.let { result ->
            Text(
                text = stringResource(
                    R.string.import_summary,
                    result.inserted,
                    result.unparsed
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { navController.popBackStack() }) {
                Text(text = stringResource(R.string.done_button))
            }
        }
    }
}
