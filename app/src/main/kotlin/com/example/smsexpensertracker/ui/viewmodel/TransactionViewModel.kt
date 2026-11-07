// path: app/src/main/kotlin/com/example/smsexpensertracker/ui/viewmodel/TransactionViewModel.kt
package com.example.smsexpensertracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsexpensertracker.data.Category
import com.example.smsexpensertracker.data.CategoryDao
import com.example.smsexpensertracker.data.DateRangeFilter
import com.example.smsexpensertracker.data.TransactionItem
import com.example.smsexpensertracker.data.TransactionRepository
import com.example.smsexpensertracker.data.TransactionTypeFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TransactionIntent {
    data class UpdateSearchQuery(val query: String) : TransactionIntent
    data class SelectTypeFilter(val type: TransactionTypeFilter) : TransactionIntent
    data class SelectCategoryFilter(val categoryId: Long?) : TransactionIntent
    data class SelectDateFilter(val dateRange: DateRangeFilter) : TransactionIntent
    data class UpdateCategory(
        val transactionId: Long,
        val categoryId: Long?,
        val learnKeyword: String? = null
    ) : TransactionIntent
    data class SplitTransaction(
        val transactionId: Long,
        val firstAmountKsh: Long,
        val firstCategoryId: Long?,
        val secondAmountKsh: Long,
        val secondCategoryId: Long?
    ) : TransactionIntent
    data class DeleteTransaction(val transactionId: Long) : TransactionIntent
}

data class TransactionUiState(
    val query: String = "",
    val typeFilter: TransactionTypeFilter = TransactionTypeFilter.ALL,
    val selectedCategoryId: Long? = null,
    val dateFilter: DateRangeFilter = DateRangeFilter.ALL,
    val transactions: List<TransactionItem> = emptyList(),
    val availableCategories: List<Category> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface TransactionEffect {
    data class ShowSnackbar(val message: String) : TransactionEffect
    data object CategoryUpdated : TransactionEffect
    data object TransactionSplitSuccess : TransactionEffect
}

private data class FilterParams(
    val query: String,
    val type: TransactionTypeFilter,
    val categoryId: Long?,
    val dateFilter: DateRangeFilter
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionUiState())
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    private val _filterParams = MutableStateFlow(
        FilterParams(
            query = "",
            type = TransactionTypeFilter.ALL,
            categoryId = null,
            dateFilter = DateRangeFilter.ALL
        )
    )

    private val _effectChannel = Channel<TransactionEffect>(Channel.BUFFERED)
    val effect: Flow<TransactionEffect> = _effectChannel.receiveAsFlow()

    init {
        loadCategories()
        observeTransactions()
    }

    fun dispatch(intent: TransactionIntent) {
        when (intent) {
            is TransactionIntent.UpdateSearchQuery -> {
                _uiState.update { it.copy(query = intent.query) }
                _filterParams.update { it.copy(query = intent.query) }
            }
            is TransactionIntent.SelectTypeFilter -> {
                _uiState.update { it.copy(typeFilter = intent.type) }
                _filterParams.update { it.copy(type = intent.type) }
            }
            is TransactionIntent.SelectCategoryFilter -> {
                _uiState.update { it.copy(selectedCategoryId = intent.categoryId) }
                _filterParams.update { it.copy(categoryId = intent.categoryId) }
            }
            is TransactionIntent.SelectDateFilter -> {
                _uiState.update { it.copy(dateFilter = intent.dateRange) }
                _filterParams.update { it.copy(dateFilter = intent.dateRange) }
            }
            is TransactionIntent.UpdateCategory -> {
                updateCategory(intent.transactionId, intent.categoryId, intent.learnKeyword)
            }
            is TransactionIntent.SplitTransaction -> {
                splitTransaction(
                    intent.transactionId,
                    intent.firstAmountKsh,
                    intent.firstCategoryId,
                    intent.secondAmountKsh,
                    intent.secondCategoryId
                )
            }
            is TransactionIntent.DeleteTransaction -> {
                deleteTransaction(intent.transactionId)
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryDao.getAllCategories().collectLatest { categories ->
                _uiState.update { it.copy(availableCategories = categories) }
            }
        }
    }

    private fun observeTransactions() {
        viewModelScope.launch {
            _filterParams.flatMapLatest { params ->
                _uiState.update { it.copy(isLoading = true) }
                transactionRepository.getTransactionsFlow(
                    query = params.query,
                    typeFilter = params.type,
                    categoryId = params.categoryId,
                    dateFilter = params.dateFilter
                )
            }.collectLatest { items ->
                _uiState.update {
                    it.copy(
                        transactions = items,
                        isLoading = false,
                        error = null
                    )
                }
            }
        }
    }

    private fun updateCategory(transactionId: Long, categoryId: Long?, learnKeyword: String?) {
        viewModelScope.launch {
            try {
                transactionRepository.updateTransactionCategory(transactionId, categoryId, learnKeyword)
                _effectChannel.send(TransactionEffect.CategoryUpdated)
                _effectChannel.send(TransactionEffect.ShowSnackbar("Category updated"))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage) }
            }
        }
    }

    private fun splitTransaction(
        transactionId: Long,
        firstAmount: Long,
        firstCategoryId: Long?,
        secondAmount: Long,
        secondCategoryId: Long?
    ) {
        viewModelScope.launch {
            try {
                transactionRepository.splitTransaction(
                    transactionId,
                    firstAmount,
                    firstCategoryId,
                    secondAmount,
                    secondCategoryId
                )
                _effectChannel.send(TransactionEffect.TransactionSplitSuccess)
                _effectChannel.send(TransactionEffect.ShowSnackbar("Transaction split successfully"))
            } catch (e: Exception) {
                _effectChannel.send(TransactionEffect.ShowSnackbar(e.localizedMessage ?: "Failed to split transaction"))
            }
        }
    }

    private fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(id)
            _effectChannel.send(TransactionEffect.ShowSnackbar("Transaction deleted"))
        }
    }
}

