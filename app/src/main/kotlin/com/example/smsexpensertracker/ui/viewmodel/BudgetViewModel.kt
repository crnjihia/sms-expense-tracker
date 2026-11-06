package com.example.smsexpensertracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsexpensertracker.data.Budget
import com.example.smsexpensertracker.data.BudgetDao
import com.example.smsexpensertracker.data.TransactionDao
import com.example.smsexpensertracker.data.CategorySpending
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** ViewModel for budget CRUD operations and spending overview. */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
    val budgets: StateFlow<List<Budget>> = _budgets.asStateFlow()

    private val _spendingMap = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val spendingMap: StateFlow<Map<Long, Long>> = _spendingMap.asStateFlow()

    init {
        viewModelScope.launch {
            loadBudgetsAndSpending()
        }
    }

    private suspend fun loadBudgetsAndSpending() {
        // Load all budgets (no month filter)
        _budgets.value = budgetDao.getAllBudgets()
        // Load spending for the current month (expenses only)
        val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
        val monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
        val monthEnd = monthStart.plusMonths(1).minusNanos(1)
        val spendingList = transactionDao.getSpendingByCategory(monthStart, monthEnd)
        val map = spendingList.associate { it.categoryId to -it.total }
        _spendingMap.value = map
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

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            budgetDao.delete(budget)
            refresh()
        }
    }

    fun getBudgetById(id: Long): Budget? = _budgets.value.find { it.id == id }

    private fun refresh() {
        viewModelScope.launch {
            loadBudgetsAndSpending()
        }
    }
}
