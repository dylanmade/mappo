package com.mappo.ui.screen.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mappo.data.settings.TextSize
import com.mappo.ui.minput.MinputPanelDividerContentGap
import com.mappo.ui.minput.MinputPanelHeaderHeight
import com.mappo.ui.minput.MinputPanelTitleInset
import com.mappo.ui.screen.remap.FontDebugRow
import com.mappo.ui.screen.remap.OptionEntryRow
import com.mappo.ui.screen.remap.PanelContentPadding
import com.mappo.ui.screen.remap.PanelDivider
import com.mappo.ui.screen.remap.PowerRow
import com.mappo.ui.screen.remap.RemapOptionEntry
import com.mappo.ui.screen.remap.TextSizeRow

/**
 * The wordmark drawer (2026-08-20 flow re-imagining): the traditional left navigation
 * drawer opened from the bottom bar's Mappo wordmark, holding the GLOBAL app options —
 * master power, text size, and the destination rows that used to live in the controls
 * screen's options panel (that panel is now the layout-scoped "Layout settings").
 * Content rows reuse the panel row family so the two surfaces read as siblings.
 */
@Composable
fun MappoDrawerContent(
    powerOn: Boolean,
    onPowerChange: (Boolean) -> Unit,
    textSize: TextSize,
    onTextSizeChange: (TextSize) -> Unit,
    // Dev tooling: the floating Theme Studio font picker in the window corner.
    fontDebugEnabled: Boolean,
    onFontDebugChange: (Boolean) -> Unit,
    entries: List<RemapOptionEntry>,
    // Close the drawer; destination rows close before navigating.
    onClose: () -> Unit,
) {
    // surfaceContainerHigh — modal drawer plane (the M3 drawer role).
    ModalDrawerSheet(
        modifier = Modifier.width(DrawerWidth),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxSize()) {
            // Header: the wordmark the drawer was summoned from, on the panel-header
            // anatomy.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MinputPanelHeaderHeight)
                    .padding(horizontal = PanelContentPadding),
            ) {
                Spacer(Modifier.width(MinputPanelTitleInset))
                Text(
                    text = "Mappo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PanelDivider()
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    start = PanelContentPadding,
                    end = PanelContentPadding,
                    top = MinputPanelDividerContentGap,
                    bottom = 2.dp,
                ),
            ) {
                item(key = "power") {
                    PowerRow(powerOn = powerOn, onPowerChange = onPowerChange)
                }
                item(key = "text_size") {
                    TextSizeRow(current = textSize, onPick = onTextSizeChange)
                }
                item(key = "font_debug") {
                    FontDebugRow(enabled = fontDebugEnabled, onEnabledChange = onFontDebugChange)
                }
                items(entries, key = { it.id }) { entry ->
                    OptionEntryRow(entry = entry, onClose = onClose)
                }
            }
        }
    }
}

/** Drawer sheet width — narrower than M3's 360dp default so the compact 1:1 canvas keeps
 *  meaningful content peeking behind the scrim. */
private val DrawerWidth = 280.dp
