package com.modatherali.linkloot.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.modatherali.linkloot.domain.model.MediaFilterTab
import com.modatherali.linkloot.domain.model.MediaItem
import com.modatherali.linkloot.domain.model.MediaType
import com.modatherali.linkloot.ui.extract.ExtractScreen
import com.modatherali.linkloot.ui.extract.ExtractUiState
import com.modatherali.linkloot.ui.theme.LinkLootTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkLootUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleItems = listOf(
        MediaItem(
            id = "img1",
            mediaUrl = "https://example.com/photo.jpg",
            type = MediaType.IMAGE,
            format = "jpg",
            width = 1200,
            height = 800,
            fileSizeBytes = 120_000L
        ),
        MediaItem(
            id = "vid1",
            mediaUrl = "https://example.com/clip.mp4",
            type = MediaType.VIDEO,
            format = "mp4",
            width = 1920,
            height = 1080,
            fileSizeBytes = 2_400_000L
        )
    )

    @Test
    fun extractScreenSeparatesImagesAndVideosAndShowsBatchBar() {
        var state by mutableStateOf(
            ExtractUiState(
                urlInput = "https://example.com",
                allMedia = sampleItems,
                activeTab = MediaFilterTab.ALL
            )
        )

        composeRule.setContent {
            LinkLootTheme {
                ExtractScreen(
                    uiState = state,
                    onUrlChange = {},
                    onPasteFromClipboard = {},
                    onApplyClipboardChip = {},
                    onClearUrl = {},
                    onAnalyze = {},
                    onDeepWebViewScan = {},
                    onCancelAnalysis = {},
                    onOpenInteractiveBrowser = {},
                    onSelectTab = { tab -> state = state.copy(activeTab = tab) },
                    onToggleHideSmall = {},
                    onSelectSort = {},
                    onMediaTap = {},
                    onMediaLongPress = { item ->
                        state = state.copy(selectedIds = state.selectedIds + item.id)
                    },
                    onMediaDownloadClick = {},
                    onSelectAll = {
                        state = state.copy(selectedIds = state.displayedMedia.map { it.id }.toSet())
                    },
                    onClearSelection = { state = state.copy(selectedIds = emptySet()) },
                    onDownloadSelected = {},
                    contentPadding = PaddingValues(0.dp)
                )
            }
        }

        // Both image and video metadata visible in ALL tab
        composeRule.onNodeWithText("1200×800").assertIsDisplayed()
        composeRule.onNodeWithText("1920×1080").assertIsDisplayed()

        // Switch to Videos tab
        composeRule.onNodeWithText("Videos (1)").performClick()
        composeRule.onNodeWithText("1920×1080").assertIsDisplayed()

        // Trigger Select All -> sticky batch download bar appears
        composeRule.onNodeWithText("Select all").performClick()
        composeRule.onNodeWithText("Download (1)").assertIsDisplayed()
    }

    @Test
    fun extractScreenRendersCleanlyInRtl() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                LinkLootTheme {
                    ExtractScreen(
                        uiState = ExtractUiState(allMedia = sampleItems),
                        onUrlChange = {},
                        onPasteFromClipboard = {},
                        onApplyClipboardChip = {},
                        onClearUrl = {},
                        onAnalyze = {},
                        onDeepWebViewScan = {},
                        onCancelAnalysis = {},
                        onOpenInteractiveBrowser = {},
                        onSelectTab = {},
                        onToggleHideSmall = {},
                        onSelectSort = {},
                        onMediaTap = {},
                        onMediaLongPress = {},
                        onMediaDownloadClick = {},
                        onSelectAll = {},
                        onClearSelection = {},
                        onDownloadSelected = {},
                        contentPadding = PaddingValues(0.dp)
                    )
                }
            }
        }

        composeRule.onNodeWithText("All (2)").assertIsDisplayed()
        composeRule.onNodeWithText("1200×800").assertIsDisplayed()
    }
}
