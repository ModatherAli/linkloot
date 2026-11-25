package com.modatherali.linkloot.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.modatherali.linkloot.data.download.DownloadCoordinator
import com.modatherali.linkloot.data.download.DownloadRepository
import com.modatherali.linkloot.domain.model.DownloadStatus
import com.modatherali.linkloot.domain.model.DownloadTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class DownloadsTab {
    ACTIVE,
    COMPLETED
}

data class DownloadsUiState(
    val selectedTab: DownloadsTab = DownloadsTab.ACTIVE,
    val activeTasks: List<DownloadTask> = emptyList(),
    val completedTasks: List<DownloadTask> = emptyList()
)

class DownloadsViewModel(
    downloadRepository: DownloadRepository,
    private val downloadCoordinator: DownloadCoordinator
) : ViewModel() {

    private val selectedTab = MutableStateFlow(DownloadsTab.ACTIVE)

    val uiState: StateFlow<DownloadsUiState> = combine(
        selectedTab,
        downloadRepository.observeAllTasks()
    ) { tab, allTasks ->
        val active = allTasks.filter {
            it.status == DownloadStatus.QUEUED ||
                it.status == DownloadStatus.DOWNLOADING ||
                it.status == DownloadStatus.MUXING ||
                it.status == DownloadStatus.PAUSED ||
                it.status == DownloadStatus.FAILED
        }
        val completed = allTasks.filter { it.status == DownloadStatus.COMPLETED }
        DownloadsUiState(
            selectedTab = tab,
            activeTasks = active,
            completedTasks = completed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadsUiState()
    )

    fun selectTab(tab: DownloadsTab) {
        selectedTab.value = tab
    }

    fun pauseTask(taskId: String) = downloadCoordinator.pauseTask(taskId)
    fun resumeTask(taskId: String) = downloadCoordinator.resumeTask(taskId)
    fun retryTask(taskId: String) = downloadCoordinator.retryTask(taskId)
    fun cancelTask(taskId: String) = downloadCoordinator.cancelTask(taskId)
    fun pauseAll() = downloadCoordinator.pauseAll()
    fun resumeAll() = downloadCoordinator.resumeAll()
    fun clearCompleted() = downloadCoordinator.clearCompleted()
    fun deleteTask(task: DownloadTask, deleteFile: Boolean) =
        downloadCoordinator.deleteTask(task, deleteFile)

    class Factory(
        private val downloadRepository: DownloadRepository,
        private val downloadCoordinator: DownloadCoordinator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DownloadsViewModel(downloadRepository, downloadCoordinator) as T
        }
    }
}
