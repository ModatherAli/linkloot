package com.modatherali.linkloot.ui.extract

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.modatherali.linkloot.R
import com.modatherali.linkloot.domain.model.HlsVariant
import com.modatherali.linkloot.domain.model.MediaItem
import com.modatherali.linkloot.ui.common.Formatters
import com.modatherali.linkloot.ui.theme.TabularTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HlsVariantSheet(
    item: MediaItem,
    onDismiss: () -> Unit,
    onSelectVariant: (HlsVariant) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.hls_select_quality_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.hls_select_quality_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            val best = item.hlsVariants.firstOrNull()
            if (best != null) {
                Button(
                    onClick = { onSelectVariant(best) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("${stringResource(R.string.hls_download_best)} (${best.label})")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            item.hlsVariants.forEachIndexed { index, variant ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val dims = Formatters.formatDimensions(variant.width, variant.height)
                val kbps = variant.bandwidthBps?.let {
                    stringResource(R.string.hls_variant_bitrate, (it / 1000L).toString())
                }.orEmpty()
                val supporting = listOf(dims, kbps, variant.codecs.orEmpty())
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")

                ListItem(
                    headlineContent = {
                        Text(
                            text = variant.label,
                            style = MaterialTheme.typography.titleMedium.merge(TabularTextStyle)
                        )
                    },
                    supportingContent = if (supporting.isNotBlank()) {
                        {
                            Text(
                                text = supporting,
                                style = MaterialTheme.typography.bodyMedium.merge(TabularTextStyle)
                            )
                        }
                    } else null,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.HighQuality,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = stringResource(R.string.action_download)
                            )
                        }
                    },
                    modifier = Modifier.clickable { onSelectVariant(variant) }
                )
            }
        }
    }
}
