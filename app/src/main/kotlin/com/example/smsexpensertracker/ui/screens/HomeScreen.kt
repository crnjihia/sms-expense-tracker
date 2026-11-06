package com.example.smsexpensertracker.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.smsexpensertracker.R
import com.example.smsexpensertracker.ingestion.SmsBackfillWorker
import com.example.smsexpensertracker.ingestion.BudgetCheckWorker

/**
 * Home screen – greeting and actions (permission request, backfill, manual import).
 */
@Composable
fun HomeScreen(navController: NavHostController) {
    val context = LocalContext.current
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(R.string.welcome_message))

        Spacer(modifier = Modifier.height(24.dp))

        if (!hasPermission) {
            Button(onClick = { navController.navigate("permission") }) {
                Text(text = stringResource(R.string.request_permission))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { navController.navigate("manual_import") }) {
                Text(text = stringResource(R.string.manual_import_button))
            }
        } else {
            Button(onClick = {
                // Enqueue backfill work
                val request = OneTimeWorkRequestBuilder<SmsBackfillWorker>()
                    .build()
                WorkManager.getInstance(context).enqueue(request)
            }) {
                Text(text = stringResource(R.string.backfill_sms))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { navController.navigate("manual_import") }) {
                Text(text = stringResource(R.string.manual_import_button))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { navController.navigate("rule_list") }) {
                Text(text = stringResource(R.string.manage_rules))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { navController.navigate("budget_list") }) {
                Text(text = stringResource(R.string.manage_budgets))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = {
                // Enqueue budget check work
                val request = OneTimeWorkRequestBuilder<BudgetCheckWorker>()
                    .build()
                WorkManager.getInstance(context).enqueue(request)
            }) {
                Text(text = stringResource(R.string.check_budgets))
            }
        }
    }
}

/**
 * Permission rationale screen – explains why the app needs SMS permission.
 */
@Composable
fun PermissionScreen(navController: NavHostController) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            navController.popBackStack()
        } else {
            // Stay on this screen; could show a toast.
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(R.string.permission_rationale))
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { launcher.launch(Manifest.permission.READ_SMS) }) {
            Text(text = stringResource(R.string.grant_permission))
        }
    }
}
