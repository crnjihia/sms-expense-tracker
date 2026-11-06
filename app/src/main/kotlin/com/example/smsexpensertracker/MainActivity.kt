package com.example.smsexpensertracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.Surface
import com.example.smsexpensertracker.ui.theme.SmsExpenseTrackerTheme
import com.example.smsexpensertracker.ui.navigation.AppNavGraph
import dagger.hilt.android.AndroidEntryPoint

/**
 * Entry point Activity – hosts the Compose UI.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmsExpenseTrackerApp() }
    }
}

@Composable
fun SmsExpenseTrackerApp() {
    SmsExpenseTrackerTheme {
        Surface(modifier = Modifier) {
            AppNavGraph()
        }
    }
}
