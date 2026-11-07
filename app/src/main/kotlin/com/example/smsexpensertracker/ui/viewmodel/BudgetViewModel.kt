// path: app/src/main/kotlin/com/example/smsexpensertracker/ui/viewmodel/BudgetViewModel.kt
package com.example.smsexpensertracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsexpensertracker.data.Budget
import com.example.smsexpensertracker.data.BudgetProgress
import com.example.smsexpensertracker.data.BudgetRepository
import com.example.smsexpensertracker.data.Category
import com.example.smsexpensertracker.data.CategoryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

/** Sealed intents representing user actions in the Budget feature. */
sealed interface BudgetIntent {
    data class SelectMonth(val month: String) : BudgetIntent
    data class SaveBudget(val categoryId: Long, val month: String, val limitKsh: Long) : BudgetIntent
    data class DeleteBudget(val budgetId: Long) : BudgetIntent
    data object CheckBudgets : BudgetIntent
}

/** Immutable state representing the UI view of monthly budgets and spending. */
data class BudgetUiState(
    val selectedMonth: String = currentKenyaMonth(),
    val budgetItems: List<BudgetProgress> = emptyList(),
    val availableCategories: List<Category> = emptyList(),
    val totalLimitKsh: Long = 0L,
    val totalSpentKsh: Long = 0L,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val totalRemainingKsh: Long
        get() = (totalLimitKsh - totalSpentKsh).coerceAtLeast(0L)

    val overallPercentage: Float
        get() = if (totalLimitKsh > 0L) (totalSpentKsh.toFloat() / totalLimitKsh.toFloat()) else 0f
}

/** One-off side effects emitted to the UI (e.g. snackbars, navigation events). */
sealed interface BudgetEffect {
    data class ShowSnackbar(val message: String) : BudgetEffect
    data object BudgetSaved : BudgetEffect
    data object BudgetDeleted : BudgetEffect
}

private fun currentKenyaMonth(): String {
    val now = OffsetDateTime.now(ZoneId.of("Africa/Nairobi"))
    return "${now.year}-${"%02d".format(now.monthValue)}"
}

/**
 * ViewModel orchestrating Budget MVI: Intent -> Reducer -> State with one-off Effects.
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    private val _effectChannel = Channel<BudgetEffect>(Channel.BUFFERED)
    val effect: Flow<BudgetEffect> = _effectChannel.receiveAsFlow()

    private var observeJob: Job? = null

    // Compatibility flows for existing screens
    val budgets: StateFlow<List<BudgetProgress>> = _uiState
        .map { it.budgetItems }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val spendingMap: StateFlow<Map<Long, Long>> = _uiState
        .map { state -> state.budgetItems.associate { it.categoryId to it.spentKsh } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        loadCategories()
        observeBudgetsForMonth(_uiState.value.selectedMonth)
    }

    fun dispatch(intent: BudgetIntent) {
        when (intent) {
            is BudgetIntent.SelectMonth -> {
                _uiState.update { it.copy(selectedMonth = intent.month) }
                observeBudgetsForMonth(intent.month)
            }
            is BudgetIntent.SaveBudget -> {
                saveBudget(intent.categoryId, intent.month, intent.limitKsh)
            }
            is BudgetIntent.DeleteBudget -> {
                deleteBudget(intent.budgetId)
            }
            is BudgetIntent.CheckBudgets -> {
                checkBudgets()
            }
        }
    }

    fun addBudget(categoryId: Long, month: String, limitKsh: Long) =
        dispatch(BudgetIntent.SaveBudget(categoryId, month, limitKsh))

    fun updateBudget(budget: Budget) =
        dispatch(BudgetIntent.SaveBudget(budget.categoryId, budget.month, budget.limitKsh))

    fun deleteBudget(budget: Budget) =
        dispatch(BudgetIntent.DeleteBudget(budget.id))

    suspend fun getBudgetById(id: Long): Budget? = budgetRepository.getBudgetById(id)

    fun getBudgetItemById(id: Long): BudgetProgress? =
        _uiState.value.budgetItems.find { it.budgetId == id }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryDao.getAllCategories().collectLatest { categories ->
                _uiState.update { it.copy(availableCategories = categories) }
            }
        }
    }

    private fun observeBudgetsForMonth(month: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            budgetRepository.getBudgetsForMonthFlow(month).collectLatest { items ->
                val totalLimit = items.sumOf { it.limitKsh }
                val totalSpent = items.sumOf { it.spentKsh }
                _uiState.update { current ->
                    current.copy(
                        budgetItems = items,
                        totalLimitKsh = totalLimit,
                        totalSpentKsh = totalSpent,
                        isLoading = false,
                        error = null
                    )
                }
            }
        }
    }

    private fun saveBudget(categoryId: Long, month: String, limitKsh: Long) {
        viewModelScope.launch {
            try {
                budgetRepository.upsertBudget(categoryId, month, limitKsh)
                _effectChannel.send(BudgetEffect.BudgetSaved)
                _effectChannel.send(BudgetEffect.ShowSnackbar("Budget saved successfully"))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage ?: "Failed to save budget") }
                _effectChannel.send(BudgetEffect.ShowSnackbar("Failed to save budget"))
            }
        }
    }

    private fun deleteBudget(budgetId: Long) {
        viewModelScope.launch {
            try {
                budgetRepository.deleteBudgetById(budgetId)
                _effectChannel.send(BudgetEffect.BudgetDeleted)
                _effectChannel.send(BudgetEffect.ShowSnackbar("Budget deleted"))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage ?: "Failed to delete budget") }
            }
        }
    }

    private fun checkBudgets() {
        viewModelScope.launch {
            val result = budgetRepository.checkBudgetsAndNotify(targetMonth = _uiState.value.selectedMonth)
            val total = result.warningsSent + result.exhaustedSent
            val message = if (total > 0) {
                "Sent $total budget alert(s) (${result.warningsSent} warnings, ${result.exhaustedSent} exhausted)"
            } else {
                "All budgets evaluated. No new alerts to send."
            }
            _effectChannel.send(BudgetEffect.ShowSnackbar(message))
        }
    }
}
