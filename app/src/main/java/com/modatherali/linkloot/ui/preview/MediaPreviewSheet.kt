package com.modatherali.linkloot.ui.preview

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.modatherali.linkloot.R
import com.modatherali.linkloot.data.download.CookieHeaderHelper
import com.modatherali.linkloot.domain.model.HlsVariant
import com.modatherali.linkloot.domain.model.MediaItem
import com.modatherali.linkloot.domain.model.MediaType
import com.modatherali.linkloot.ui.common.Formatters
import com.modatherali.linkloot.ui.theme.TabularTextStyle
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

@Composable
fun MediaPreviewSheet(
    items: List<MediaItem>,
    initialItemId: String,
    okHttpClient: OkHttpClient,
    onDismiss: () -> Unit,
    onCopyLink: (String) -> Unit,
    onDownloadItem: (MediaItem) -> Unit
) {
    BackHandler(onBack = onDismiss)

    val initialIndex = remember(items, initialItemId) {
        items.indexOfFirst { it.id == initialItemId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { items.size })
    var isCurrentPageZoomed by remember { mutableStateOf(false) }
    var metadataExpanded by remember { mutableStateOf(false) }

    val currentItem = items.getOrNull(pagerState.currentPage) ?: return

    LaunchedEffect(pagerState.currentPage, currentItem.type) {
        if (currentItem.type != MediaType.IMAGE) {
            isCurrentPageZoomed = false
        }
    }

    Surface(
        color = Color(0xFF070A12),
        contentColor = Color.White,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isCurrentPageZoomed,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val pageItem = items[pageIndex]
                val isActive = pagerState.currentPage == pageIndex
                if (pageItem.type == MediaType.IMAGE) {
                    ZoomableImagePage(
                        item = pageItem,
                        onZoomChanged = { zoomed ->
                            if (isActive) isCurrentPageZoomed = zoomed
                        }
                    )
                } else {
                    VideoPreviewPage(
                        item = pageItem,
                        isActivePage = isActive,
                        okHttpClient = okHttpClient
                    )
                }
            }

            // Top overlay bar
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                tint = Color.White
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.preview_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(
                                    R.string.preview_counter,
                                    pagerState.currentPage + 1,
                                    items.size
                                ),
                                style = MaterialTheme.typography.labelSmall.merge(TabularTextStyle),
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { metadataExpanded = !metadataExpanded }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.preview_metadata),
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = { onDownloadItem(currentItem) },
                            enabled = !currentItem.isEncrypted
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = stringResource(R.string.action_download),
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // Expandable metadata bottom sheet
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val summary = listOf(
                                currentItem.format.uppercase().ifBlank { currentItem.type.name },
                                Formatters.formatDimensions(currentItem.width, currentItem.height),
                                Formatters.formatBytes(currentItem.fileSizeBytes),
                                Formatters.formatDuration(currentItem.durationMs)
                            ).filter { it.isNotBlank() }.joinToString(" · ")

                            Text(
                                text = summary.ifBlank { stringResource(R.string.preview_metadata) },
                                style = MaterialTheme.typography.titleMedium.merge(TabularTextStyle),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { metadataExpanded = !metadataExpanded }) {
                            Icon(
                                imageVector = if (metadataExpanded) {
                                    Icons.Default.ExpandMore
                                } else {
                                    Icons.Default.ExpandLess
                                },
                                contentDescription = stringResource(R.string.preview_metadata)
                            )
                        }
                    }

                    AnimatedVisibility(visible = metadataExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(top = 6.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetadataRow(stringResource(R.string.meta_url), currentItem.resolvedUrl)
                            Formatters.formatDimensions(currentItem.width, currentItem.height)
                                .takeIf { it.isNotBlank() }
                                ?.let { MetadataRow(stringResource(R.string.meta_dimensions), it) }
                            if (currentItem.format.isNotBlank()) {
                                MetadataRow(stringResource(R.string.meta_format), currentItem.format.uppercase())
                            }
                            if (currentItem.mimeType.isNotBlank()) {
                                MetadataRow(stringResource(R.string.meta_mime), currentItem.mimeType)
                            }
                            Formatters.formatBytes(currentItem.fileSizeBytes)
                                .takeIf { it.isNotBlank() }
                                ?.let { MetadataRow(stringResource(R.string.meta_size), it) }
                            Formatters.formatDuration(currentItem.durationMs)
                                .takeIf { it.isNotBlank() }
                                ?.let { MetadataRow(stringResource(R.string.meta_duration), it) }
                            if (currentItem.refererUrl.isNotBlank()) {
                                MetadataRow(stringResource(R.string.meta_source_page), currentItem.refererUrl)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onCopyLink(currentItem.resolvedUrl) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_copy_link))
                                }
                                Button(
                                    onClick = { onDownloadItem(currentItem) },
                                    enabled = !currentItem.isEncrypted,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_download))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.merge(TabularTextStyle),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ZoomableImagePage(
    item: MediaItem,
    onZoomChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }

    val imageRequest = remember(item.resolvedUrl, item.headers) {
        val headersBuilder = NetworkHeaders.Builder()
        item.headers.forEach { (k, v) -> headersBuilder.add(k, v) }
        ImageRequest.Builder(context)
            .data(item.resolvedUrl)
            .httpHeaders(headersBuilder.build())
            .build()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.1f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomChanged(false)
                        } else {
                            scale = 2.4f
                            onZoomChanged(true)
                        }
                    }
                )
            }
            .pointerInput(item.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 4f)
                    scale = nextScale
                    offset = if (nextScale > 1.05f) {
                        val maxOffsetX = (size.width * (nextScale - 1f)) / 2f
                        val maxOffsetY = (size.height * (nextScale - 1f)) / 2f
                        Offset(
                            x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                            y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                        )
                    } else {
                        Offset.Zero
                    }
                    onZoomChanged(scale > 1.05f)
                }
            }
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPreviewPage(
    item: MediaItem,
    isActivePage: Boolean,
    okHttpClient: OkHttpClient
) {
    val context = LocalContext.current
    var selectedVariant by remember(item.id) {
        mutableStateOf<HlsVariant?>(item.hlsVariants.firstOrNull())
    }
    var variantMenuExpanded by remember { mutableStateOf(false) }
    val playbackUrl = selectedVariant?.url ?: item.resolvedUrl

    var isPlaying by remember(playbackUrl) { mutableStateOf(false) }
    var isMuted by remember(item.id) { mutableStateOf(false) }
    var positionMs by remember(playbackUrl) { mutableLongStateOf(0L) }
    var durationMs by remember(playbackUrl) { mutableLongStateOf(item.durationMs ?: 0L) }
    var playbackError by remember(playbackUrl) { mutableStateOf(false) }

    val exoPlayer = remember(playbackUrl) {
        val headers = CookieHeaderHelper.buildEffectiveHeaders(
            targetUrl = playbackUrl,
            refererUrl = item.refererUrl,
            baseHeaders = item.headers
        )
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(headers)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                volume = if (isMuted) 0f else 1f
                setMediaItem(Media3Item.fromUri(playbackUrl))
                prepare()
                playWhenReady = isActivePage
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isActivePage, exoPlayer) {
        if (isActivePage) {
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.pause()
        }
    }

    LaunchedEffect(exoPlayer, isActivePage) {
        while (isActivePage) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val d = exoPlayer.duration
            if (d > 0L) durationMs = d
            delay(250L)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = exoPlayer
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp)
        )

        if (playbackError) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.video_playback_error),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Video playback controls anchored above the bottom metadata bar
        Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 74.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (durationMs > 0L) {
                    Slider(
                        value = positionMs.coerceIn(0L, durationMs).toFloat(),
                        onValueChange = { exoPlayer.seekTo(it.toLong()) },
                        valueRange = 0f..durationMs.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.cd_pause else R.string.cd_play
                                ),
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else 1f
                            }
                        ) {
                            Icon(
                                imageVector = if (isMuted) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = stringResource(
                                    if (isMuted) R.string.cd_unmute else R.string.cd_mute
                                ),
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${Formatters.formatDuration(positionMs).ifBlank { "0:00" }} / ${Formatters.formatDuration(durationMs).ifBlank { "--:--" }}",
                            style = MaterialTheme.typography.labelMedium.merge(TabularTextStyle),
                            color = Color.White
                        )
                    }

                    if (item.hlsVariants.size > 1) {
                        Box {
                            AssistChip(
                                onClick = { variantMenuExpanded = true },
                                label = { Text(selectedVariant?.label ?: "Auto", color = Color.White) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.HighQuality,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            DropdownMenu(
                                expanded = variantMenuExpanded,
                                onDismissRequest = { variantMenuExpanded = false }
                            ) {
                                item.hlsVariants.forEach { variant ->
                                    DropdownMenuItem(
                                        text = { Text(variant.label) },
                                        onClick = {
                                            variantMenuExpanded = false
                                            selectedVariant = variant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
