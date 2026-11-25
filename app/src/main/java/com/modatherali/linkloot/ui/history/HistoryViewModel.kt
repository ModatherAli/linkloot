package com.modatherali.linkloot.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.modatherali.linkloot.data.local.HistoryRepository
import com.modatherali.linkloot.domain.model.HistorySession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val query: String = "",
    val sessions: List<HistorySession> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    val uiState: StateFlow<HistoryUiState> = combine(
        queryFlow,
        queryFlow.flatMapLatest { q -> historyRepository.observeHistory(q) }
    ) { query, sessions ->
        HistoryUiState(query = query, sessions = sessions)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
    )

    fun onQueryChange(newQuery: String) {
        queryFlow.value = newQuery
    }

    fun deleteSession(historyId: Long) {
        viewModelScope.launch {
            historyRepository.deleteHistory(historyId)
        }
    }

    class Factory(
        private val historyRepository: HistoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(historyRepository) as T
        }
    }
}
