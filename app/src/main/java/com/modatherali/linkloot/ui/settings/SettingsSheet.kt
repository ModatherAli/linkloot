package com.modatherali.linkloot.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.modatherali.linkloot.R
import com.modatherali.linkloot.domain.model.AppLanguage
import com.modatherali.linkloot.domain.model.AppSettings
import com.modatherali.linkloot.domain.model.SubfolderStrategy
import com.modatherali.linkloot.domain.model.ThemeMode
import com.modatherali.linkloot.ui.theme.TabularTextStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onHideSmallMediaChange: (Boolean) -> Unit,
    onAutoWebViewChange: (Boolean) -> Unit,
    onSubfolderStrategyChange: (SubfolderStrategy) -> Unit,
    onConcurrentDownloadsChange: (Int) -> Unit,
    onClearHistory: () -> Unit,
    onClearCookies: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium
            )

            // Appearance
            SectionHeader(stringResource(R.string.settings_appearance))
            Text(
                text = stringResource(R.string.settings_theme_mode),
                style = MaterialTheme.typography.titleMedium
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                    ThemeMode.LIGHT to stringResource(R.string.theme_light),
                    ThemeMode.DARK to stringResource(R.string.theme_dark)
                )
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_desc),
                    checked = settings.useDynamicColor,
                    onCheckedChange = onDynamicColorChange
                )
            }

            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val languages = listOf(
                    AppLanguage.SYSTEM to stringResource(R.string.lang_system),
                    AppLanguage.EN to stringResource(R.string.lang_english),
                    AppLanguage.AR to stringResource(R.string.lang_arabic)
                )
                languages.forEachIndexed { index, (lang, label) ->
                    SegmentedButton(
                        selected = settings.appLanguage == lang,
                        onClick = { onLanguageChange(lang) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = languages.size)
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Extraction & Filtering
            SectionHeader(stringResource(R.string.settings_extraction))
            SettingsSwitchRow(
                title = stringResource(R.string.settings_hide_small_media),
                subtitle = stringResource(R.string.settings_hide_small_media_desc),
                checked = settings.hideSmallMedia,
                onCheckedChange = onHideSmallMediaChange
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_auto_webview),
                subtitle = stringResource(R.string.settings_auto_webview_desc),
                checked = settings.autoFallbackToWebView,
                onCheckedChange = onAutoWebViewChange
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Downloads & Storage
            SectionHeader(stringResource(R.string.settings_downloads))
            Text(
                text = stringResource(R.string.settings_subfolder_org),
                style = MaterialTheme.typography.titleMedium
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val strategies = listOf(
                    SubfolderStrategy.BY_MEDIA_TYPE to stringResource(R.string.subfolder_by_type),
                    SubfolderStrategy.BY_DOMAIN_AND_TYPE to stringResource(R.string.subfolder_by_domain)
                )
                strategies.forEachIndexed { index, (strategy, label) ->
                    SegmentedButton(
                        selected = settings.subfolderStrategy == strategy,
                        onClick = { onSubfolderStrategyChange(strategy) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = strategies.size)
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Column {
                Text(
                    text = stringResource(
                        R.string.settings_concurrent_downloads,
                        settings.maxConcurrentDownloads
                    ),
                    style = MaterialTheme.typography.titleMedium.merge(TabularTextStyle)
                )
                Slider(
                    value = settings.maxConcurrentDownloads.toFloat(),
                    onValueChange = { onConcurrentDownloadsChange(it.roundToInt()) },
                    valueRange = 1f..5f,
                    steps = 3
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Privacy & Data
            SectionHeader(stringResource(R.string.settings_privacy))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onClearHistory,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 3.dp))
                    Text(
                        text = stringResource(R.string.settings_clear_history),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onClearCookies,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Cookie, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 3.dp))
                    Text(
                        text = stringResource(R.string.settings_clear_cookies),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
