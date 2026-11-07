// path: app/src/main/kotlin/com/example/smsexpensertracker/ui/viewmodel/HomeViewModel.kt
package com.example.smsexpensertracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsexpensertracker.data.BudgetRepository
import com.example.smsexpensertracker.data.HomeMonthSummary
import com.example.smsexpensertracker.data.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

/** Intents representing user interactions on the Home Dashboard. */
sealed interface HomeIntent {
    data object Refresh : HomeIntent
    data object CheckBudgetAlerts : HomeIntent
}

/** Dashboard UI State containing financial summaries and top spending categories. */
data class HomeUiState(
    val isLoading: Boolean = true,
    val summary: HomeMonthSummary = HomeMonthSummary(
        month = currentKenyaMonth(),
        totalIncomeKsh = 0L,
        totalExpenseKsh = 0L,
        netSavingsKsh = 0L,
        topCategories = emptyList(),
        recentTransactions = emptyList()
    ),
    val totalBudgetLimitKsh: Long = 0L,
    val budgetUsageRatio: Float = 0f
)

sealed interface HomeEffect {
    data class ShowSnackbar(val message: String) : HomeEffect
}

private fun currentKenyaMonth(): String {
    val now = OffsetDateTime.now(ZoneId.of("Africa/Nairobi"))
    return "${now.year}-${"%02d".format(now.monthValue)}"
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _effectChannel = Channel<HomeEffect>(Channel.BUFFERED)
    val effect: Flow<HomeEffect> = _effectChannel.receiveAsFlow()

    init {
        observeHomeData()
    }

    fun dispatch(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.Refresh -> observeHomeData()
            is HomeIntent.CheckBudgetAlerts -> checkBudgetAlerts()
        }
    }

    private fun observeHomeData() {
        val monthStr = currentKenyaMonth()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            transactionRepository.getHomeSummaryFlow().collectLatest { summary ->
                _uiState.update { it.copy(summary = summary, isLoading = false) }
            }
        }

        viewModelScope.launch {
            budgetRepository.getBudgetsForMonthFlow(monthStr).collectLatest { budgets ->
                val totalLimit = budgets.sumOf { it.limitKsh }
                val totalSpent = budgets.sumOf { it.spentKsh }
                val ratio = if (totalLimit > 0L) totalSpent.toFloat() / totalLimit.toFloat() else 0f
                _uiState.update {
                    it.copy(
                        totalBudgetLimitKsh = totalLimit,
                        budgetUsageRatio = ratio
                    )
                }
            }
        }
    }

    private fun checkBudgetAlerts() {
        viewModelScope.launch {
            val result = budgetRepository.checkBudgetsAndNotify()
            val total = result.warningsSent + result.exhaustedSent
            val msg = if (total > 0) {
                "Dispatched $total alert(s) (${result.warningsSent} warnings, ${result.exhaustedSent} exhausted)"
            } else {
                "All budgets evaluated. Spending within bounds."
            }
            _effectChannel.send(HomeEffect.ShowSnackbar(msg))
        }
    }
}

