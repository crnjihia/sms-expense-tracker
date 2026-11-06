package com.example.smsexpensertracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsexpensertracker.data.Budget
import com.example.smsexpensertracker.data.BudgetDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** ViewModel for budget CRUD operations. */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetDao: BudgetDao
) : ViewModel() {

    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
    val budgets: StateFlow<List<Budget>> = _budgets.asStateFlow()

    init {
        viewModelScope.launch {
            // Convert DAO Flow to StateFlow – DAO currently returns Flow only for getAllBudgetsForMonth,
            // so we expose a simple LiveData-like list via repeated queries. For simplicity we load all budgets.
            // In a real app, you might have a dedicated getAllBudgets() Flow.
            // Here we just pull all budgets for the current month (placeholder month "2024-01").
            // This keeps the demo functional without expanding DAO further.
            // You can adjust later to load all months.
            try {
                val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                val monthStr = "${now.year}-${"%02d".format(now.monthValue)}"
                val list = budgetDao.getAllBudgetsForMonth(monthStr)
                _budgets.value = list
            } catch (e: Exception) {
                // ignore errors for now
            }
        }
    }

    fun addBudget(categoryId: Long, month: String, limitKsh: Long) {
        viewModelScope.launch {
            budgetDao.insert(Budget(categoryId = categoryId, month = month, limitKsh = limitKsh))
            refresh()
        }
    }

    fun updateBudget(budget: Budget) {
        viewModelScope.launch {
            budgetDao.update(budget)
            refresh()
        }
    }

    fun deleteBudget(id: Long) {
        viewModelScope.launch {
            budgetDao.deleteById(id)
            refresh()
        }
    }

    fun getBudgetById(id: Long): Budget? = _budgets.value.find { it.id == id }

    private fun refresh() {
        viewModelScope.launch {
            // Refresh the list using the same dummy month.
            val dummyMonth = "2024-01"
            _budgets.value = budgetDao.getAllBudgetsForMonth(dummyMonth)
        }
    }
}
