package com.mappo.ui.screen.remap

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mappo.data.repository.InstalledAppsRepository.InstalledApp
import com.mappo.ui.component.AppIconImage
import com.mappo.ui.component.rememberAppIconPainter
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputPanelDividerContentGap
import com.mappo.ui.minput.MinputPillDropdown
import com.mappo.ui.minput.MinputTextField
import com.mappo.ui.minput.minputMiniTextStyle

/** Sort options for the applications list. RECENT approximates "most recently used" with
 *  the package's install/update recency ([InstalledApp.recencyKey]) until usage tracking
 *  or game-library scanning lands. */
internal enum class AppSort(val label: String, val naturalAscending: Boolean) {
    RECENT("Recent", naturalAscending = false),
    NAME("A to Z", naturalAscending = true),
}

/**
 * The Profiles view (retitled "Profiles" 2026-08-15 — each row is an application's
 * profile; the code keeps the Applications names): every launchable app on the device
 * (games join the list when the planned library scanning lands — local-folder scraping
 * plus installed-game lists from frontends like GameNative / GameHub), behind its own
 * search + sort row. Picking a profile opens its [LayoutsScreen]. No longer the home
 * (2026-08-20 — the controls view of the active layout is): reached as the "View
 * layouts" fallback when the viewed layout has no bound application.
 *
 * Upgraded from the layout panel's Applications modal; the "All applications" / "Global"
 * meta-filter rows died with the filter they existed to set.
 */
@Composable
fun ApplicationsScreen(
    installedApps: List<InstalledApp>,
    onLoadInstalledApps: () -> Unit,
    onPickApp: (InstalledApp) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }
    // Live name filter + sort — local so a fresh visit starts clean.
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(AppSort.RECENT) }
    var ascending by remember { mutableStateOf(AppSort.RECENT.naturalAscending) }
    LaunchedEffect(Unit) { onLoadInstalledApps() }

    // surface — the screen's content plane.
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            // The shared browse-chain bar.
            RemapTopBar(overline = "Profiles", onBack = onBack)
            // The filter row: search + sort + direction.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PanelContentPadding,
                        end = PanelContentPadding,
                        top = MinputPanelDividerContentGap,
                        bottom = MinputPanelDividerContentGap,
                    ),
            ) {
                MinputTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search profiles",
                    leadingIcon = Icons.Filled.Search,
                    clearable = true,
                    // Top-of-screen field — the sanctioned modal-less variant.
                    inlineEdit = true,
                    modifier = Modifier.weight(1f),
                )
                MinputPillDropdown(
                    current = sort,
                    elevated = true,
                    options = AppSort.entries,
                    optionLabel = { it.label },
                    onPick = { sort = it; ascending = it.naturalAscending },
                    onClickLabel = "Sort profiles",
                )
                SortDirectionButton(ascending = ascending, onToggle = { ascending = !ascending })
            }
            val trimmed = query.trim()
            val filtered = if (trimmed.isEmpty()) {
                installedApps
            } else {
                installedApps.filter { it.label.contains(trimmed, ignoreCase = true) }
            }
            val comparator = when (sort) {
                AppSort.RECENT -> compareBy<InstalledApp> { it.recencyKey }
                AppSort.NAME -> compareBy { it.label.lowercase() }
            }
            val displayed = filtered.sortedWith(if (ascending) comparator else comparator.reversed())
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    start = PanelContentPadding,
                    end = PanelContentPadding,
                    top = MinputPanelDividerContentGap,
                    bottom = 2.dp,
                ),
            ) {
                if (displayed.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(PanelRowHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (installedApps.isEmpty()) "Loading profiles…" else "No profiles match",
                                style = minputMiniTextStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(displayed, key = { it.packageName }) { app ->
                    AppRow(
                        label = app.label,
                        packageName = app.packageName,
                        onClick = { onPickApp(app) },
                    )
                }
            }
        }
    }
}

/**
 * One row of the applications list: launcher icon (via [AppIconImage], optically matched to
 * vector-glyph sizing; a tinted [vectorIcon] fallback while the icon loads) + label.
 */
@Composable
internal fun AppRow(
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    packageName: String? = null,
    vectorIcon: ImageVector? = null,
) {
    PanelRow(onClick = onClick, active = active) {
        val appIcon = rememberAppIconPainter(packageName)
        when {
            appIcon != null -> AppIconImage(appIcon, size = PanelRowIconSize)
            vectorIcon != null -> Icon(
                vectorIcon,
                contentDescription = null,
                modifier = Modifier.size(PanelRowIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Box(Modifier.size(PanelRowIconSize))
        }
        Spacer(Modifier.width(MinputGlyphLabelGap))
        Text(
            text = label,
            style = minputMiniTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
