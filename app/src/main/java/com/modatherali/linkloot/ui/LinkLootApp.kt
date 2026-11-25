package com.modatherali.linkloot.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.modatherali.linkloot.R
import com.modatherali.linkloot.di.AppContainer
import com.modatherali.linkloot.domain.model.DownloadStatus
import com.modatherali.linkloot.ui.browser.InteractiveBrowserScreen
import com.modatherali.linkloot.ui.downloads.DownloadsScreen
import com.modatherali.linkloot.ui.downloads.DownloadsViewModel
import com.modatherali.linkloot.ui.extract.ExtractEvent
import com.modatherali.linkloot.ui.extract.ExtractScreen
import com.modatherali.linkloot.ui.extract.ExtractViewModel
import com.modatherali.linkloot.ui.extract.HlsVariantSheet
import com.modatherali.linkloot.ui.history.HistoryScreen
import com.modatherali.linkloot.ui.history.HistoryViewModel
import com.modatherali.linkloot.ui.preview.MediaPreviewSheet
import com.modatherali.linkloot.ui.settings.SettingsEvent
import com.modatherali.linkloot.ui.settings.SettingsSheet
import com.modatherali.linkloot.ui.settings.SettingsViewModel
import com.modatherali.linkloot.ui.theme.LinkLootTheme
import kotlinx.coroutines.launch

enum class RootTab {
    EXTRACT,
    DOWNLOADS,
    HISTORY
}

data class ExternalLaunchRequest(
    val id: Long,
    val urlToAnalyze: String? = null,
    val openDownloads: Boolean = false
)

private sealed interface PendingDownloadAction {
    data class Single(val itemId: String) : PendingDownloadAction
    data class HlsVariantSelection(val itemId: String, val variantUrl: String) : PendingDownloadAction
    data object Selected : PendingDownloadAction
    data class ResumeTask(val taskId: String) : PendingDownloadAction
    data class RetryTask(val taskId: String) : PendingDownloadAction
    data object ResumeAll : PendingDownloadAction

    companion object {
        val StateSaver: Saver<PendingDownloadAction?, Any> = listSaver<PendingDownloadAction?, String>(
            save = { action ->
                when (action) {
                    null -> emptyList()
                    is Single -> listOf("single", action.itemId)
                    is HlsVariantSelection -> listOf("hls", action.itemId, action.variantUrl)
                    Selected -> listOf("selected")
                    is ResumeTask -> listOf("resume", action.taskId)
                    is RetryTask -> listOf("retry", action.taskId)
                    ResumeAll -> listOf("resumeAll")
                }
            },
            restore = { list ->
                when (list.firstOrNull()) {
                    "single" -> list.getOrNull(1)?.let(::Single)
                    "hls" -> {
                        val itemId = list.getOrNull(1)
                        val variantUrl = list.getOrNull(2)
                        if (itemId != null && variantUrl != null) {
                            HlsVariantSelection(itemId, variantUrl)
                        } else {
                            null
                        }
                    }
                    "selected" -> Selected
                    "resume" -> list.getOrNull(1)?.let(::ResumeTask)
                    "retry" -> list.getOrNull(1)?.let(::RetryTask)
                    "resumeAll" -> ResumeAll
                    else -> null
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkLootApp(
    appContainer: AppContainer,
    externalRequest: ExternalLaunchRequest? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(appContainer)
    )
    val extractViewModel: ExtractViewModel = viewModel(
        factory = ExtractViewModel.Factory(
            extractionRepository = appContainer.extractionRepository,
            historyRepository = appContainer.historyRepository,
            settingsRepository = appContainer.settingsRepository,
            downloadCoordinator = appContainer.downloadCoordinator
        )
    )
    val downloadsViewModel: DownloadsViewModel = viewModel(
        factory = DownloadsViewModel.Factory(
            downloadRepository = appContainer.downloadRepository,
            downloadCoordinator = appContainer.downloadCoordinator
        )
    )
    val historyViewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.Factory(appContainer.historyRepository)
    )

    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val extractState by extractViewModel.uiState.collectAsStateWithLifecycle()
    val downloadsState by downloadsViewModel.uiState.collectAsStateWithLifecycle()
    val historyState by historyViewModel.uiState.collectAsStateWithLifecycle()
    val activeDownloadCount by appContainer.downloadRepository
        .observeActiveCount()
        .collectAsStateWithLifecycle(initialValue = 0)

    var selectedTab by rememberSaveable { mutableStateOf(RootTab.EXTRACT) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showBrowser by rememberSaveable { mutableStateOf(false) }
    var notificationPermissionAttempted by rememberSaveable { mutableStateOf(false) }
    var pendingDownloadAction by rememberSaveable(stateSaver = PendingDownloadAction.StateSaver) {
        mutableStateOf(null)
    }

    fun executePendingAction(action: PendingDownloadAction) {
        when (action) {
            is PendingDownloadAction.Single -> {
                extractViewModel.uiState.value.allMedia
                    .firstOrNull { it.id == action.itemId }
                    ?.let { extractViewModel.requestDownloadSingle(it) }
            }
            is PendingDownloadAction.HlsVariantSelection -> {
                val state = extractViewModel.uiState.value
                val item = state.hlsPickerItem?.takeIf { it.id == action.itemId }
                    ?: state.allMedia.firstOrNull { it.id == action.itemId }
                val variant = item?.hlsVariants?.firstOrNull { it.url == action.variantUrl }
                if (item != null && variant != null) {
                    extractViewModel.downloadHlsVariant(item, variant)
                }
            }
            PendingDownloadAction.Selected -> {
                extractViewModel.downloadSelected()
            }
            is PendingDownloadAction.ResumeTask -> {
                downloadsViewModel.resumeTask(action.taskId)
            }
            is PendingDownloadAction.RetryTask -> {
                downloadsViewModel.retryTask(action.taskId)
            }
            PendingDownloadAction.ResumeAll -> {
                downloadsViewModel.resumeAll()
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val action = pendingDownloadAction
        pendingDownloadAction = null
        if (action != null) {
            executePendingAction(action)
        }
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingDownloadAction
        pendingDownloadAction = null
        if (granted) {
            if (action != null) {
                executePendingAction(action)
            }
        } else {
            extractViewModel.dismissHlsPicker()
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.storage_permission_required)
                )
            }
        }
    }

    fun runWithDownloadPermissions(action: PendingDownloadAction) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted && !notificationPermissionAttempted) {
                notificationPermissionAttempted = true
                pendingDownloadAction = action
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingDownloadAction = action
                legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        executePendingAction(action)
    }

    fun copyTextToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("LinkLoot URL", text))
        coroutineScope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.copied_to_clipboard))
        }
    }

    fun readClipboardText(): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    // Refresh clipboard URL chip on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                extractViewModel.updateClipboardCandidate(readClipboardText())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Handle Share Sheet / Deep Link / Notification clicks
    LaunchedEffect(externalRequest?.id) {
        val req = externalRequest ?: return@LaunchedEffect
        if (req.openDownloads) {
            selectedTab = RootTab.DOWNLOADS
        } else if (!req.urlToAnalyze.isNullOrBlank()) {
            selectedTab = RootTab.EXTRACT
            showBrowser = false
            extractViewModel.analyzeUrl(req.urlToAnalyze)
        }
    }

    // Collect ViewModel one-off events
    LaunchedEffect(Unit) {
        extractViewModel.events.collect { event ->
            when (event) {
                is ExtractEvent.DownloadsQueued -> {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.queued_downloads_snackbar, event.count)
                    )
                }
                ExtractEvent.EncryptedStreamWarning -> {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.encrypted_hls_warning)
                    )
                }
                ExtractEvent.InvalidUrlWarning -> {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.error_invalid_url)
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        settingsViewModel.events.collect { event ->
            when (event) {
                SettingsEvent.HistoryCleared -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.history_cleared_snackbar))
                }
                SettingsEvent.CookiesCleared -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.cookies_cleared_snackbar))
                }
            }
        }
    }

    LinkLootTheme(
        themeMode = settings.themeMode,
        dynamicColor = settings.useDynamicColor
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        actions = {
                            IconButton(onClick = { showSettings = true }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.cd_settings)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == RootTab.EXTRACT,
                                onClick = { selectedTab = RootTab.EXTRACT },
                                icon = {
                                    Icon(Icons.Default.TravelExplore, contentDescription = null)
                                },
                                label = { Text(stringResource(R.string.nav_extract)) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == RootTab.DOWNLOADS,
                                onClick = { selectedTab = RootTab.DOWNLOADS },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (activeDownloadCount > 0) {
                                                Badge { Text(activeDownloadCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                    }
                                },
                                label = { Text(stringResource(R.string.nav_downloads)) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == RootTab.HISTORY,
                                onClick = { selectedTab = RootTab.HISTORY },
                                icon = {
                                    Icon(Icons.Default.History, contentDescription = null)
                                },
                                label = { Text(stringResource(R.string.nav_history)) }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Row(modifier = Modifier.fillMaxSize()) {
                    if (useNavRail) {
                        NavigationRail(modifier = Modifier.fillMaxHeight()) {
                            NavigationRailItem(
                                selected = selectedTab == RootTab.EXTRACT,
                                onClick = { selectedTab = RootTab.EXTRACT },
                                icon = {
                                    Icon(Icons.Default.TravelExplore, contentDescription = null)
                                },
                                label = { Text(stringResource(R.string.nav_extract)) }
                            )
                            NavigationRailItem(
                                selected = selectedTab == RootTab.DOWNLOADS,
                                onClick = { selectedTab = RootTab.DOWNLOADS },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (activeDownloadCount > 0) {
                                                Badge { Text(activeDownloadCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                    }
                                },
                                label = { Text(stringResource(R.string.nav_downloads)) }
                            )
                            NavigationRailItem(
                                selected = selectedTab == RootTab.HISTORY,
                                onClick = { selectedTab = RootTab.HISTORY },
                                icon = {
                                    Icon(Icons.Default.History, contentDescription = null)
                                },
                                label = { Text(stringResource(R.string.nav_history)) }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .consumeWindowInsets(innerPadding)
                    ) {
                        val contentPadding = if (useNavRail) {
                            PaddingValues(
                                top = innerPadding.calculateTopPadding(),
                                bottom = innerPadding.calculateBottomPadding()
                            )
                        } else {
                            innerPadding
                        }

                        when (selectedTab) {
                            RootTab.EXTRACT -> {
                                ExtractScreen(
                                    uiState = extractState,
                                    onUrlChange = extractViewModel::onUrlInputChange,
                                    onPasteFromClipboard = {
                                        val text = readClipboardText()
                                        if (!text.isNullOrBlank()) {
                                            extractViewModel.onUrlInputChange(text)
                                        }
                                    },
                                    onApplyClipboardChip = extractViewModel::applyClipboardCandidate,
                                    onClearUrl = extractViewModel::clearUrlInput,
                                    onAnalyze = { extractViewModel.analyzeUrl(forceDeepWebView = false) },
                                    onDeepWebViewScan = { extractViewModel.analyzeUrl(forceDeepWebView = true) },
                                    onCancelAnalysis = extractViewModel::cancelAnalysis,
                                    onOpenInteractiveBrowser = { showBrowser = true },
                                    onSelectTab = extractViewModel::setTab,
                                    onToggleHideSmall = extractViewModel::toggleHideSmallMedia,
                                    onSelectSort = extractViewModel::setSortOption,
                                    onMediaTap = { item ->
                                        if (extractState.isSelectionMode) {
                                            extractViewModel.toggleSelection(item.id)
                                        } else {
                                            extractViewModel.openPreview(item)
                                        }
                                    },
                                    onMediaLongPress = { item ->
                                        extractViewModel.toggleSelection(item.id)
                                    },
                                    onMediaDownloadClick = { item ->
                                        if (item.isEncrypted || (item.isHls && item.hlsVariants.size > 1)) {
                                            extractViewModel.requestDownloadSingle(item)
                                        } else {
                                            runWithDownloadPermissions(PendingDownloadAction.Single(item.id))
                                        }
                                    },
                                    onSelectAll = extractViewModel::selectAllDisplayed,
                                    onClearSelection = extractViewModel::clearSelection,
                                    onDownloadSelected = {
                                        val hasDownloadable = extractState.allMedia.any {
                                            it.id in extractState.selectedIds && !it.isEncrypted
                                        }
                                        if (hasDownloadable) {
                                            runWithDownloadPermissions(PendingDownloadAction.Selected)
                                        } else {
                                            extractViewModel.downloadSelected()
                                        }
                                    },
                                    contentPadding = contentPadding
                                )
                            }

                            RootTab.DOWNLOADS -> {
                                DownloadsScreen(
                                    uiState = downloadsState,
                                    onSelectTab = downloadsViewModel::selectTab,
                                    onPauseTask = downloadsViewModel::pauseTask,
                                    onResumeTask = { taskId ->
                                        runWithDownloadPermissions(PendingDownloadAction.ResumeTask(taskId))
                                    },
                                    onRetryTask = { taskId ->
                                        runWithDownloadPermissions(PendingDownloadAction.RetryTask(taskId))
                                    },
                                    onCancelTask = downloadsViewModel::cancelTask,
                                    onPauseAll = downloadsViewModel::pauseAll,
                                    onResumeAll = {
                                        if (downloadsState.activeTasks.any { it.status == DownloadStatus.PAUSED }) {
                                            runWithDownloadPermissions(PendingDownloadAction.ResumeAll)
                                        }
                                    },
                                    onClearCompleted = downloadsViewModel::clearCompleted,
                                    onDeleteCompleted = { task ->
                                        downloadsViewModel.deleteTask(task, deleteFile = true)
                                    },
                                    contentPadding = contentPadding
                                )
                            }

                            RootTab.HISTORY -> {
                                HistoryScreen(
                                    uiState = historyState,
                                    onQueryChange = historyViewModel::onQueryChange,
                                    onOpenSession = { session ->
                                        extractViewModel.loadFromHistory(session.id)
                                        selectedTab = RootTab.EXTRACT
                                    },
                                    onRescanSession = { session ->
                                        selectedTab = RootTab.EXTRACT
                                        extractViewModel.analyzeUrl(session.pageUrl)
                                    },
                                    onCopyUrl = ::copyTextToClipboard,
                                    onDeleteSession = historyViewModel::deleteSession,
                                    contentPadding = contentPadding
                                )
                            }
                        }
                    }
                }
            }

            // Full-screen Media Preview Overlay
            val previewId = extractState.previewItemId
            if (previewId != null && extractState.displayedMedia.isNotEmpty()) {
                MediaPreviewSheet(
                    items = extractState.displayedMedia,
                    initialItemId = previewId,
                    okHttpClient = appContainer.okHttpClient,
                    onDismiss = extractViewModel::closePreview,
                    onCopyLink = ::copyTextToClipboard,
                    onDownloadItem = { item ->
                        if (item.isEncrypted || (item.isHls && item.hlsVariants.size > 1)) {
                            extractViewModel.requestDownloadSingle(item)
                        } else {
                            runWithDownloadPermissions(PendingDownloadAction.Single(item.id))
                        }
                    }
                )
            }

            // Full-screen Interactive Browser Overlay
            if (showBrowser) {
                InteractiveBrowserScreen(
                    initialUrl = extractState.urlInput.ifBlank { extractState.currentPageUrl },
                    onClose = { showBrowser = false },
                    onImportCapturedMedia = { pageUrl, pageTitle, items ->
                        showBrowser = false
                        selectedTab = RootTab.EXTRACT
                        extractViewModel.importCapturedMedia(pageUrl, pageTitle, items)
                    }
                )
            }

            // HLS Quality Variant Picker Sheet
            extractState.hlsPickerItem?.let { hlsItem ->
                HlsVariantSheet(
                    item = hlsItem,
                    onDismiss = extractViewModel::dismissHlsPicker,
                    onSelectVariant = { variant ->
                        runWithDownloadPermissions(
                            PendingDownloadAction.HlsVariantSelection(
                                itemId = hlsItem.id,
                                variantUrl = variant.url
                            )
                        )
                    }
                )
            }

            // Settings Modal Bottom Sheet
            if (showSettings) {
                SettingsSheet(
                    settings = settings,
                    onDismiss = { showSettings = false },
                    onThemeModeChange = settingsViewModel::setThemeMode,
                    onDynamicColorChange = settingsViewModel::setUseDynamicColor,
                    onLanguageChange = settingsViewModel::setAppLanguage,
                    onHideSmallMediaChange = settingsViewModel::setHideSmallMedia,
                    onAutoWebViewChange = settingsViewModel::setAutoFallbackToWebView,
                    onSubfolderStrategyChange = settingsViewModel::setSubfolderStrategy,
                    onConcurrentDownloadsChange = settingsViewModel::setMaxConcurrentDownloads,
                    onClearHistory = settingsViewModel::clearHistory,
                    onClearCookies = settingsViewModel::clearCookiesAndCache
                )
            }
        }
    }
}
