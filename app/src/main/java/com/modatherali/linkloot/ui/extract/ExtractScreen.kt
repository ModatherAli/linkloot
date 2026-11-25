package com.modatherali.linkloot.ui.extract

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.modatherali.linkloot.R
import com.modatherali.linkloot.domain.model.ExtractionStage
import com.modatherali.linkloot.domain.model.MediaFilterTab
import com.modatherali.linkloot.domain.model.MediaItem
import com.modatherali.linkloot.domain.model.MediaType
import com.modatherali.linkloot.domain.model.SortOption
import com.modatherali.linkloot.ui.common.Formatters
import com.modatherali.linkloot.ui.theme.TabularTextStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExtractScreen(
    uiState: ExtractUiState,
    onUrlChange: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
    onApplyClipboardChip: () -> Unit,
    onClearUrl: () -> Unit,
    onAnalyze: () -> Unit,
    onDeepWebViewScan: () -> Unit,
    onCancelAnalysis: () -> Unit,
    onOpenInteractiveBrowser: () -> Unit,
    onSelectTab: (MediaFilterTab) -> Unit,
    onToggleHideSmall: () -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onMediaTap: (MediaItem) -> Unit,
    onMediaLongPress: (MediaItem) -> Unit,
    onMediaDownloadClick: (MediaItem) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDownloadSelected: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var overflowExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + if (uiState.isSelectionMode) 88.dp else 16.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Hero URL Card
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.extract_hero_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = stringResource(R.string.extract_hero_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = uiState.urlInput,
                            onValueChange = onUrlChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.url_input_label)) },
                            placeholder = { Text(stringResource(R.string.url_input_placeholder)) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Language, contentDescription = null)
                            },
                            trailingIcon = {
                                Row {
                                    if (uiState.urlInput.isNotEmpty()) {
                                        IconButton(onClick = onClearUrl) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = stringResource(R.string.action_clear)
                                            )
                                        }
                                    } else {
                                        IconButton(onClick = onPasteFromClipboard) {
                                            Icon(
                                                imageVector = Icons.Default.ContentPaste,
                                                contentDescription = stringResource(R.string.action_paste)
                                            )
                                        }
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                    onAnalyze()
                                }
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )

                        AnimatedVisibility(visible = uiState.clipboardCandidate != null) {
                            val candidate = uiState.clipboardCandidate.orEmpty()
                            AssistChip(
                                onClick = onApplyClipboardChip,
                                label = {
                                    Text(
                                        text = stringResource(R.string.clipboard_chip_label, candidate),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    onAnalyze()
                                },
                                enabled = !uiState.isAnalyzing && uiState.urlInput.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.action_analyze),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = onOpenInteractiveBrowser,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Public, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_interactive_browser),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box {
                                IconButton(onClick = { overflowExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.action_more_options)
                                    )
                                }
                                DropdownMenu(
                                    expanded = overflowExpanded,
                                    onDismissRequest = { overflowExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_deep_scan)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.TravelExplore, contentDescription = null)
                                        },
                                        onClick = {
                                            overflowExpanded = false
                                            focusManager.clearFocus()
                                            onDeepWebViewScan()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_interactive_browser)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Public, contentDescription = null)
                                        },
                                        onClick = {
                                            overflowExpanded = false
                                            onOpenInteractiveBrowser()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Progress Card
            if (uiState.isAnalyzing) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ExtractionProgressCard(
                        stage = uiState.progress.stage,
                        imagesFound = uiState.progress.imagesFound,
                        videosFound = uiState.progress.videosFound,
                        isUsingWebView = uiState.progress.isUsingWebView,
                        onCancel = onCancelAnalysis
                    )
                }
            }

            // Error Card
            if (uiState.errorMessage != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.error_extraction_failed,
                                    uiState.errorMessage
                                ),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onDeepWebViewScan) {
                                    Text(stringResource(R.string.action_deep_scan))
                                }
                                OutlinedButton(onClick = onOpenInteractiveBrowser) {
                                    Text(stringResource(R.string.action_interactive_browser))
                                }
                            }
                        }
                    }
                }
            }

            // Discovered Media Controls
            if (uiState.allMedia.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (uiState.pageTitle.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = uiState.pageTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (uiState.usedWebView) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.using_webview_badge),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val tabs = listOf(
                                MediaFilterTab.ALL to stringResource(R.string.tab_all, uiState.totalVisibleCount),
                                MediaFilterTab.IMAGES to stringResource(R.string.tab_images, uiState.imageCount),
                                MediaFilterTab.VIDEOS to stringResource(R.string.tab_videos, uiState.videoCount)
                            )
                            tabs.forEachIndexed { index, (tab, label) ->
                                SegmentedButton(
                                    selected = uiState.activeTab == tab,
                                    onClick = { onSelectTab(tab) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = tabs.size
                                    )
                                ) {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val filterLabel = if (uiState.hideSmallMedia && uiState.hiddenSmallCount > 0) {
                                stringResource(R.string.filter_show_all, uiState.hiddenSmallCount)
                            } else {
                                stringResource(R.string.filter_hide_small)
                            }
                            FilterChip(
                                selected = uiState.hideSmallMedia,
                                onClick = onToggleHideSmall,
                                label = { Text(filterLabel) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )

                            Box {
                                val sortLabel = when (uiState.sortOption) {
                                    SortOption.DEFAULT -> stringResource(R.string.sort_default)
                                    SortOption.SIZE_DESC -> stringResource(R.string.sort_size)
                                    SortOption.TYPE -> stringResource(R.string.sort_type)
                                }
                                AssistChip(
                                    onClick = { sortMenuExpanded = true },
                                    label = { Text(sortLabel) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_default)) },
                                        onClick = {
                                            sortMenuExpanded = false
                                            onSelectSort(SortOption.DEFAULT)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_size)) },
                                        onClick = {
                                            sortMenuExpanded = false
                                            onSelectSort(SortOption.SIZE_DESC)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_type)) },
                                        onClick = {
                                            sortMenuExpanded = false
                                            onSelectSort(SortOption.TYPE)
                                        }
                                    )
                                }
                            }

                            if (!uiState.isSelectionMode && uiState.displayedMedia.isNotEmpty()) {
                                AssistChip(
                                    onClick = onSelectAll,
                                    label = { Text(stringResource(R.string.action_select_all)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.SelectAll,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Grid or Empty States
            if (!uiState.isAnalyzing && uiState.displayedMedia.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyExtractState(
                        hasAttemptedAnalysis = uiState.hasAttemptedAnalysis,
                        hasAnyMediaBeforeFilter = uiState.allMedia.isNotEmpty(),
                        onDeepScan = onDeepWebViewScan,
                        onOpenBrowser = onOpenInteractiveBrowser
                    )
                }
            } else {
                items(
                    items = uiState.displayedMedia,
                    key = { it.id }
                ) { item ->
                    MediaGridCard(
                        item = item,
                        isSelected = item.id in uiState.selectedIds,
                        isSelectionMode = uiState.isSelectionMode,
                        onTap = { onMediaTap(item) },
                        onLongPress = { onMediaLongPress(item) },
                        onDownloadClick = { onMediaDownloadClick(item) }
                    )
                }
            }
        }

        // Sticky Multi-Select Batch Bar
        AnimatedVisibility(
            visible = uiState.isSelectionMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 12.dp
                )
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClearSelection) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.action_deselect_all)
                            )
                        }
                        Text(
                            text = stringResource(R.string.selected_count, uiState.selectedIds.size),
                            style = MaterialTheme.typography.titleMedium.merge(TabularTextStyle)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onSelectAll) {
                            Text(stringResource(R.string.action_select_all))
                        }
                        Button(onClick = onDownloadSelected) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_download_selected, uiState.selectedIds.size))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtractionProgressCard(
    stage: ExtractionStage,
    imagesFound: Int,
    videosFound: Int,
    isUsingWebView: Boolean,
    onCancel: () -> Unit
) {
    val stageLabel = when (stage) {
        ExtractionStage.NORMALIZING -> stringResource(R.string.stage_normalizing)
        ExtractionStage.FAST_HTML -> stringResource(R.string.stage_fast_html)
        ExtractionStage.WEBVIEW_SNIFF -> stringResource(R.string.stage_webview)
        ExtractionStage.PROBING -> stringResource(R.string.stage_probing)
        ExtractionStage.SAVING -> stringResource(R.string.stage_saving)
        ExtractionStage.COMPLETED -> stringResource(R.string.stage_done)
        ExtractionStage.IDLE -> ""
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stageLabel,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (isUsingWebView) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.using_webview_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.extraction_counters, imagesFound, videosFound),
                    style = MaterialTheme.typography.labelMedium.merge(TabularTextStyle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun EmptyExtractState(
    hasAttemptedAnalysis: Boolean,
    hasAnyMediaBeforeFilter: Boolean,
    onDeepScan: () -> Unit,
    onOpenBrowser: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (hasAnyMediaBeforeFilter) Icons.Default.FilterList else Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = when {
                    hasAnyMediaBeforeFilter -> stringResource(R.string.empty_filtered_title)
                    else -> stringResource(R.string.empty_extract_title)
                },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = when {
                    hasAnyMediaBeforeFilter -> stringResource(R.string.empty_filtered_subtitle)
                    hasAttemptedAnalysis -> stringResource(R.string.try_deep_scan_hint)
                    else -> stringResource(R.string.empty_extract_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasAttemptedAnalysis && !hasAnyMediaBeforeFilter) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDeepScan) {
                        Text(stringResource(R.string.action_deep_scan))
                    }
                    Button(onClick = onOpenBrowser) {
                        Text(stringResource(R.string.action_interactive_browser))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGridCard(
    item: MediaItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val context = LocalContext.current
    val previewSource = item.thumbnailUrl ?: item.resolvedUrl
    val imageRequest = remember(previewSource, item.headers) {
        val headersBuilder = NetworkHeaders.Builder()
        item.headers.forEach { (k, v) -> headersBuilder.add(k, v) }
        ImageRequest.Builder(context)
            .data(previewSource)
            .httpHeaders(headersBuilder.build())
            .crossfade(true)
            .build()
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Subtle bottom gradient scrim for readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )

                // Top-start format badge
                val badgeText = when {
                    item.isEncrypted -> stringResource(R.string.badge_encrypted)
                    item.isHls -> item.qualityLabel ?: stringResource(R.string.badge_hls)
                    item.format.isNotBlank() -> item.format.uppercase()
                    item.type == MediaType.VIDEO -> "VIDEO"
                    else -> "IMG"
                }
                Surface(
                    color = if (item.isEncrypted) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        Color.Black.copy(alpha = 0.7f)
                    },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (item.isEncrypted) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(11.dp)
                            )
                        } else if (item.type == MediaType.VIDEO) {
                            Icon(
                                Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.isEncrypted) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                Color.White
                            }
                        )
                    }
                }

                // Selection checkmark
                if (isSelectionMode || isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    )
                }

                // Center Play badge for videos
                if (item.type == MediaType.VIDEO) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Duration pill bottom-end
                val duration = Formatters.formatDuration(item.durationMs)
                if (duration.isNotBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = duration,
                            style = MaterialTheme.typography.labelSmall.merge(TabularTextStyle),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Metadata Footer + Quick Download button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val dims = Formatters.formatDimensions(item.width, item.height)
                    val size = Formatters.formatBytes(item.fileSizeBytes)
                    Text(
                        text = dims.ifBlank { item.qualityLabel ?: item.format.uppercase() },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            .merge(TabularTextStyle),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = size.ifBlank { stringResource(R.string.unknown_size) },
                        style = MaterialTheme.typography.labelSmall.merge(TabularTextStyle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDownloadClick,
                    enabled = !item.isEncrypted,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = stringResource(R.string.action_download),
                        tint = if (item.isEncrypted) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
