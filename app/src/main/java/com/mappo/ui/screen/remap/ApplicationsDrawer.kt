package com.mappo.ui.screen.remap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.mappo.data.repository.InstalledAppsRepository.InstalledApp
import com.mappo.ui.component.AppIconImage
import com.mappo.ui.component.rememberAppIconPainter
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputMorphCorner
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputBoxContainer
import com.mappo.ui.minput.minputHighlightContainer
import com.mappo.ui.minput.minputIndication
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMicroTextStyle
import com.mappo.ui.minput.minputMiniTextStyle

/**
 * The applications list (2026-08-27) — the layouts drawer's APPLICATIONS MODE content.
 * (The standalone right-side applications drawer this file used to host is retired: the
 * drawer's full-width Applications button now radiates this list into the same pane —
 * see [LayoutsDrawerPane].)
 *
 * Focusing (d-pad) or tapping an application card previews that application's ACTIVE
 * layout in the controls view (via [onPreviewApplication] — the caller resolves the
 * app's binding to a layout), the way the layouts list previews layouts. **Installed** =
 * applications detected on this device — Android packages for now; local-directory game
 * scanning and verified-source metadata (IGDB/ScreenScraper-class) come later.
 * **Community** stays empty until community sharing lands. The application whose layout
 * is currently ACTIVE wears the highlight plane.
 *
 * Tapping a card SELECTS the application as the viewing context — a preview-equivalent
 * move (no activation, no auto-detection change); the drawer transitions back to layouts
 * mode scoped to it.
 */
@Composable
internal fun ApplicationsList(
    apps: List<InstalledApp>,
    activeAppPackage: String?,
    query: String,
    sort: ApplicationSort,
    onPreviewApplication: (String) -> Unit,
    onSelectApplication: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trimmed = query.trim()
    val filtered = if (trimmed.isEmpty()) apps
    else apps.filter { it.label.contains(trimmed, ignoreCase = true) }
    val installed = filtered.sortedWith(sort.comparator)
    val community = emptyList<InstalledApp>()

    // Preview triggers are DELIBERATE only (2026-08-26): card focus or tap —
    // scroll-position-driven preview retired (see the layouts drawer's note).
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DrawerControlGap),
        // The pod supplies the surrounding inset now (2026-08-30) — only the gap up to
        // the Applications button above is this list's to give.
        contentPadding = PaddingValues(top = DrawerControlGap),
    ) {
        // Headerless (2026-08-30, matching the layouts list): the INSTALLED/COMMUNITY
        // overlines retired — the download marker at a card's end says "installed on
        // this device", and its absence says "community".
        if (installed.isEmpty() && community.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                Text(
                    text = if (query.isBlank()) "No applications detected" else "No matches",
                    style = minputMicroTextStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = MinputGlyphLabelGap),
                )
            }
        }
        items(installed, key = { AppCardKeyPrefix + it.packageName }, contentType = { "card" }) { app ->
            ApplicationCard(
                app = app,
                active = app.packageName == activeAppPackage,
                installed = true,
                onPreview = { onPreviewApplication(app.packageName) },
                onSelect = { onSelectApplication(app) },
            )
        }
        items(community, key = { AppCardKeyPrefix + it.packageName }, contentType = { "card" }) { app ->
            ApplicationCard(
                app = app,
                active = false,
                installed = false,
                onPreview = { onPreviewApplication(app.packageName) },
                onSelect = { onSelectApplication(app) },
            )
        }
    }
}

/**
 * One application card: launcher icon beside the application name (2026-08-26: subtitle
 * retired — name only), closed by the [installed] marker. Cards wear the layout cards'
 * chrome; the application whose layout is ACTIVE wears the highlight plane.
 *
 * [installed] draws the download glyph at the row's END (2026-08-30): it replaced the
 * INSTALLED/COMMUNITY section headers, so the marker on the card is what separates an
 * application detected on this device from a community entry.
 */
@Composable
private fun ApplicationCard(
    app: InstalledApp,
    active: Boolean,
    installed: Boolean,
    onPreview: () -> Unit,
    onSelect: () -> Unit,
) {
    val container = if (active) minputHighlightContainer() else minputBoxContainer()
    val primaryContent = if (active) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val secondaryContent = if (active) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = SecondaryOnHighlightAlpha)
    } else MaterialTheme.colorScheme.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(MinputMorphCorner),
        color = container,
        border = minputBevelBorder(container, MinputMorphCorner),
        modifier = Modifier
            .fillMaxWidth()
            .minputInteractiveMotion(interaction)
            // Controller browsing previews as focus lands, mirroring touch scroll.
            .onFocusChanged { if (it.isFocused) onPreview() }
            .clip(RoundedCornerShape(MinputMorphCorner))
            .clickable(
                interactionSource = interaction,
                indication = minputIndication(),
                onClickLabel = "Select ${app.label}",
                onClick = onSelect,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(CardPadding),
        ) {
            val icon = rememberAppIconPainter(app.packageName)
            if (icon != null) {
                AppIconImage(icon, size = AppCardIconSize)
            } else {
                Icon(
                    Lucide.LayoutGrid,
                    contentDescription = null,
                    modifier = Modifier.size(AppCardIconSize),
                    tint = secondaryContent,
                )
            }
            Spacer(Modifier.width(AppCardIconTextGap))
            Text(
                text = app.label,
                style = minputMiniTextStyle(),
                color = primaryContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (installed) {
                Spacer(Modifier.width(MinputGlyphLabelGap))
                Icon(
                    Icons.Filled.Download,
                    contentDescription = "Installed",
                    modifier = Modifier.size(InstalledIconSize),
                    tint = secondaryContent,
                )
            }
        }
    }
}

/**
 * Sort orders for the applications drawer — same option set as the layouts drawer's
 * [LayoutSort]. RECENT rides [InstalledApp.recencyKey] (package lastUpdateTime — a
 * most-recently-used proxy until usage-access/game-library scanning lands); LIKES ties
 * at zero until community sharing brings real counts, so recency breaks the tie.
 */
internal enum class ApplicationSort(val label: String, val comparator: Comparator<InstalledApp>) {
    RECENT("Recent", compareByDescending<InstalledApp> { it.recencyKey }),
    LIKES("Likes", compareByDescending<InstalledApp> { 0 }.thenByDescending { it.recencyKey }),
    A_TO_Z("A to Z", compareBy<InstalledApp> { it.label.lowercase() }),
    Z_TO_A("Z to A", compareByDescending<InstalledApp> { it.label.lowercase() }),
}

/** LazyColumn key prefix distinguishing application cards from header/hint rows for the
 *  scroll-preview scan (the suffix is the package name). */
private const val AppCardKeyPrefix = "application:"

/** Launcher-icon edge on an application card — sized to the single name line
 *  (2026-08-26: the active-layout subtitle retired; name-only cards). */
private val AppCardIconSize = 16.dp

/** Gap between the launcher icon and the card's text stack. */
private val AppCardIconTextGap = 6.dp

/** The installed (download) marker closing an application card — the layout cards' size. */
private val InstalledIconSize = 12.dp
