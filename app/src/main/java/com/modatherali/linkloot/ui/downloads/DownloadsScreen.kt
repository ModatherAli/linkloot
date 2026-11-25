package com.modatherali.linkloot.ui.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.modatherali.linkloot.R
import com.modatherali.linkloot.domain.model.DownloadStatus
import com.modatherali.linkloot.domain.model.DownloadTask
import com.modatherali.linkloot.domain.model.MediaType
import com.modatherali.linkloot.ui.common.Formatters
import com.modatherali.linkloot.ui.theme.TabularTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    uiState: DownloadsUiState,
    onSelectTab: (DownloadsTab) -> Unit,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onRetryTask: (String) -> Unit,
    onCancelTask: (String) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onDeleteCompleted: (DownloadTask) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val tabs = listOf(
                    DownloadsTab.ACTIVE to stringResource(
                        R.string.downloads_active_tab,
                        uiState.activeTasks.size
                    ),
                    DownloadsTab.COMPLETED to stringResource(
                        R.string.downloads_completed_tab,
                        uiState.completedTasks.size
                    )
                )
                tabs.forEachIndexed { index, (tab, label) ->
                    SegmentedButton(
                        selected = uiState.selectedTab == tab,
                        onClick = { onSelectTab(tab) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size)
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        if (uiState.selectedTab == DownloadsTab.ACTIVE) {
            if (uiState.activeTasks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPauseAll,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_pause_all))
                        }
                        OutlinedButton(
                            onClick = onResumeAll,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_resume_all))
                        }
                    }
                }

                items(items = uiState.activeTasks, key = { it.id }) { task ->
                    ActiveDownloadCard(
                        task = task,
                        onPause = { onPauseTask(task.id) },
                        onResume = { onResumeTask(task.id) },
                        onRetry = { onRetryTask(task.id) },
                        onCancel = { onCancelTask(task.id) }
                    )
                }
            } else {
                item {
                    EmptyDownloadsCard(
                        title = stringResource(R.string.empty_active_downloads_title),
                        subtitle = stringResource(R.string.empty_active_downloads_subtitle)
                    )
                }
            }
        } else {
            if (uiState.completedTasks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onClearCompleted) {
                            Text(stringResource(R.string.action_clear_completed))
                        }
                    }
                }
                items(items = uiState.completedTasks, key = { it.id }) { task ->
                    CompletedDownloadCard(
                        task = task,
                        onOpen = { openSavedMedia(context, task) },
                        onShare = { shareSavedMedia(context, task) },
                        onDelete = { onDeleteCompleted(task) }
                    )
                }
            } else {
                item {
                    EmptyDownloadsCard(
                        title = stringResource(R.string.empty_completed_downloads_title),
                        subtitle = stringResource(R.string.empty_completed_downloads_subtitle)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveDownloadCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val statusText = when (task.status) {
        DownloadStatus.QUEUED -> stringResource(R.string.status_queued)
        DownloadStatus.DOWNLOADING -> stringResource(R.string.status_downloading)
        DownloadStatus.MUXING -> stringResource(R.string.status_muxing)
        DownloadStatus.PAUSED -> stringResource(R.string.status_paused)
        DownloadStatus.FAILED -> stringResource(R.string.status_failed)
        DownloadStatus.COMPLETED -> stringResource(R.string.status_completed)
        DownloadStatus.CANCELLED -> stringResource(R.string.status_cancelled)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (task.mediaType == MediaType.VIDEO) Icons.Default.Videocam else Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${task.domain} · ${task.subfolder}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = if (task.status == DownloadStatus.FAILED) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (task.status == DownloadStatus.FAILED) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            val fraction = task.progressFraction
            if (task.status == DownloadStatus.MUXING || (task.status == DownloadStatus.DOWNLOADING && fraction == null)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val progressDetails = buildString {
                    if (task.isHls && task.segmentsTotal > 0) {
                        append(stringResource(R.string.hls_segments_progress, task.segmentsCompleted, task.segmentsTotal))
                        if (task.bytesDownloaded > 0) {
                            append(" · ")
                            append(Formatters.formatBytes(task.bytesDownloaded))
                        }
                    } else if (task.totalBytes > 0) {
                        append("${Formatters.formatBytes(task.bytesDownloaded)} / ${Formatters.formatBytes(task.totalBytes)}")
                    } else if (task.bytesDownloaded > 0) {
                        append(Formatters.formatBytes(task.bytesDownloaded))
                    }
                }
                Text(
                    text = task.errorMessage?.takeIf { task.status == DownloadStatus.FAILED } ?: progressDetails,
                    style = MaterialTheme.typography.labelMedium.merge(TabularTextStyle),
                    color = if (task.status == DownloadStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row {
                    when (task.status) {
                        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.cd_pause))
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            IconButton(onClick = onResume) {
                                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.action_resume))
                            }
                        }
                        DownloadStatus.FAILED -> {
                            IconButton(onClick = onRetry) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_retry))
                            }
                        }
                        else -> Unit
                    }
                    if (task.status != DownloadStatus.MUXING) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Cancel, contentDescription = stringResource(R.string.action_cancel))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedDownloadCard(
    task: DownloadTask,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val metaLine = listOf(
                        Formatters.formatBytes(task.totalBytes.takeIf { it > 0 }),
                        task.savedDisplayPath ?: task.subfolder
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = metaLine,
                            style = MaterialTheme.typography.labelSmall.merge(TabularTextStyle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onOpen) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_open))
                }
                TextButton(onClick = onShare) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_share))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDownloadsCard(title: String, subtitle: String) {
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun openSavedMedia(context: Context, task: DownloadTask) {
    val uriStr = task.savedUri ?: return
    val uri = Uri.parse(uriStr)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, task.mimeType.ifBlank { "*/*" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, task.fileName))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.no_viewer_app_error), Toast.LENGTH_SHORT).show()
    }
}

private fun shareSavedMedia(context: Context, task: DownloadTask) {
    val uriStr = task.savedUri ?: return
    val uri = Uri.parse(uriStr)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = task.mimeType.ifBlank { "*/*" }
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, task.fileName))
    }
}
