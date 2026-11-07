package com.example.smsexpensertracker.ui.viewmodel

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsexpensertracker.data.BudgetDao
import com.example.smsexpensertracker.data.CategoryDao
import com.example.smsexpensertracker.data.RuleDao
import com.example.smsexpensertracker.data.SmsSourceStateDao
import com.example.smsexpensertracker.data.TransactionDao
import com.example.smsexpensertracker.data.export.CsvExportManager
import com.example.smsexpensertracker.security.BiometricHelper
import com.example.smsexpensertracker.security.BiometricStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class SettingsUiState(
    val isReadSmsGranted: Boolean = false,
    val isReceiveSmsGranted: Boolean = false,
    val isNotificationsGranted: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val biometricStatus: BiometricStatus = BiometricStatus.UNAVAILABLE,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val currency: String = "KES",
    val isAutoCategoriseEnabled: Boolean = true,
    val budgetThresholdPercent: Int = 80,
    val isWipingData: Boolean = false,
    val isExportingCsv: Boolean = false,
    val userMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val biometricHelper: BiometricHelper,
    private val csvExportManager: CsvExportManager,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val ruleDao: RuleDao,
    private val budgetDao: BudgetDao,
    private val smsSourceStateDao: SmsSourceStateDao
) : ViewModel() {

    private val prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshState()
    }

    fun refreshState() {
        val hasReadSms = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val hasReceiveSms = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotifications = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val bioStatus = biometricHelper.checkBiometricStatus()
        val bioEnabled = biometricHelper.isBiometricEnabled()

        val savedTheme = prefs.getString("theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        val themeMode = try { AppThemeMode.valueOf(savedTheme) } catch (_: Exception) { AppThemeMode.SYSTEM }
        val currency = prefs.getString("currency", "KES") ?: "KES"
        val autoCat = prefs.getBoolean("auto_categorise", true)
        val threshold = prefs.getInt("budget_threshold", 80)

        _uiState.update {
            it.copy(
                isReadSmsGranted = hasReadSms,
                isReceiveSmsGranted = hasReceiveSms,
                isNotificationsGranted = hasNotifications,
                isBiometricEnabled = bioEnabled,
                biometricStatus = bioStatus,
                themeMode = themeMode,
                currency = currency,
                isAutoCategoriseEnabled = autoCat,
                budgetThresholdPercent = threshold
            )
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setCurrency(currency: String) {
        prefs.edit().putString("currency", currency).apply()
        _uiState.update { it.copy(currency = currency) }
    }

    fun setAutoCategoriseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_categorise", enabled).apply()
        _uiState.update { it.copy(isAutoCategoriseEnabled = enabled) }
    }

    fun setBudgetThresholdPercent(threshold: Int) {
        prefs.edit().putInt("budget_threshold", threshold).apply()
        _uiState.update { it.copy(budgetThresholdPercent = threshold) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        biometricHelper.setBiometricEnabled(enabled)
        _uiState.update { it.copy(isBiometricEnabled = enabled) }
    }

    fun exportTransactionsCsv(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExportingCsv = true) }
            try {
                val outputStream = contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    val count = csvExportManager.exportToStream(outputStream)
                    _uiState.update {
                        it.copy(
                            isExportingCsv = false,
                            userMessage = "Exported $count transactions to CSV successfully."
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isExportingCsv = false,
                            userMessage = "Could not open target location for CSV export."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExportingCsv = false,
                        userMessage = "Export failed: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun wipeAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWipingData = true) }
            try {
                transactionDao.deleteAll()
                ruleDao.deleteAll()
                budgetDao.deleteAll()
                smsSourceStateDao.deleteAll()
                _uiState.update {
                    it.copy(
                        isWipingData = false,
                        userMessage = "All transaction and budget data has been permanently wiped."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isWipingData = false,
                        userMessage = "Data wipe failed: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
