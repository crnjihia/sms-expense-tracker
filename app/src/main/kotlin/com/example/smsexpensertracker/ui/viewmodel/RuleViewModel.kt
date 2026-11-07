package com.example.smsexpensertracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsexpensertracker.data.CategorisationRule
import com.example.smsexpensertracker.data.RuleDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/** ViewModel for rule CRUD operations. */
@HiltViewModel
class RuleViewModel @Inject constructor(
    private val ruleDao: RuleDao
) : ViewModel() {

    private val _rules = MutableStateFlow<List<CategorisationRule>>(emptyList())
    val rules: StateFlow<List<CategorisationRule>> = _rules.asStateFlow()

    init {
        viewModelScope.launch {
            // Collect the Flow from RuleDao and update the StateFlow.
            ruleDao.getAllRules().collect { list ->
                _rules.value = list
            }
        }
    }

    fun addRule(categoryId: Long, keyword: String) {
        viewModelScope.launch {
            ruleDao.insert(CategorisationRule(categoryId = categoryId, keyword = keyword))
            // Insertion triggers the Flow, but we can also refresh just in case.
        }
    }

    fun updateRule(rule: CategorisationRule) {
        viewModelScope.launch {
            ruleDao.update(rule)
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            ruleDao.deleteById(id)
        }
    }

    fun getRuleById(id: Long): CategorisationRule? = _rules.value.find { it.id == id }
}
