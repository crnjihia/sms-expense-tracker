package com.example.smsexpensertracker

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.smsexpensertracker.ui.navigation.AppNavGraph
import com.example.smsexpensertracker.ui.theme.SmsExpenseTrackerTheme
import com.example.smsexpensertracker.ui.viewmodel.AppThemeMode
import dagger.hilt.android.AndroidEntryPoint

/**
 * Entry point Activity – extends AppCompatActivity for BiometricPrompt compatibility
 * and hosts the Compose UI navigation tree.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmsExpenseTrackerApp()
        }
    }
}

@Composable
fun SmsExpenseTrackerApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE) }
    var themeModeName by remember {
        mutableStateOf(prefs.getString("theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name)
    }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme_mode") {
                themeModeName = prefs.getString("theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val useDarkTheme = when (themeModeName) {
        AppThemeMode.LIGHT.name -> false
        AppThemeMode.DARK.name -> true
        else -> isSystemInDarkTheme()
    }

    SmsExpenseTrackerTheme(useDarkTheme = useDarkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppNavGraph()
        }
    }
}
