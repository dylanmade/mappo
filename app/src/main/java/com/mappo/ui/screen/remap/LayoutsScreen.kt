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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mappo.data.model.Profile
import com.mappo.data.repository.InstalledAppsRepository.InstalledApp
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputIconButton
import com.mappo.ui.minput.MinputModal
import com.mappo.ui.minput.MinputPanelDividerContentGap
import com.mappo.ui.minput.MinputPillContentPadding
import com.mappo.ui.minput.MinputPillDropdown
import com.mappo.ui.minput.MinputTextField
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle
import kotlinx.collections.immutable.ImmutableList

/**
 * Sort options for the layouts list. RECENT approximates "recently used" with creation
 * recency (no last-used tracking yet); LIKES is a placeholder ordering until community
 * sharing brings real like counts. [naturalAscending] is the direction each sort resets
 * to when picked — the direction toggle flips from there.
 */
internal enum class ProfileSort(val label: String, val naturalAscending: Boolean) {
    RECENT("Recent", naturalAscending = false),
    LIKES("Likes", naturalAscending = false),
    NAME("A to Z", naturalAscending = true),
}

/** The modals stacked above the layouts view: ADD is the new-layout form; OPTIONS holds
 *  settings that apply across all layouts. */
private enum class LayoutsModal { ADD, OPTIONS }

/**
 * The layouts view for ONE application (UI label "layouts"; the code keeps the Profile
 * names) — the middle of the applications → layouts → controls browse chain (2026-08-14).
 * Rebuilt on the Profiles view's anatomy: the shared browse-chain top bar (back arrow · the
 * profile's application icon · "<profile> - Layouts" · Add/Tune utilities), search + sort +
 * direction row, then the layout tiles. Only child layouts of [appPackage] (associated via auto-switch bindings)
 * are listed; selecting one opens its controls view for VIEWING (2026-08-20 — activation
 * moved to the controls bar's "Activate layout").
 */
@Composable
fun LayoutsScreen(
    appPackage: String,
    appLabel: String,
    profiles: ImmutableList<Profile>,
    activeProfileId: Long?,
    appBindings: Map<String, Long>,
    onSelectLayout: (Profile) -> Unit,
    onBack: () -> Unit,
    installedApps: List<InstalledApp>,
    onLoadInstalledApps: () -> Unit,
    onCreateProfile: (name: String, packages: Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var openModal by remember { mutableStateOf<LayoutsModal?>(null) }
    // Back closes the topmost surface first: an open modal, then the view itself.
    BackHandler { if (openModal != null) openModal = null else onBack() }
    // Live name filter + sort — local so a fresh visit starts clean.
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ProfileSort.RECENT) }
    var ascending by remember { mutableStateOf(ProfileSort.RECENT.naturalAscending) }
    // The Add form's app picker needs the installed list — start the (cached, one-shot)
    // load with the view so the modal never pops in empty.
    LaunchedEffect(Unit) { onLoadInstalledApps() }

    // Focus seats for the stacked modals (MinputModal owns seat/trap/recovery per surface).
    val addModalCloseFocus = remember { FocusRequester() }
    val optionsModalCloseFocus = remember { FocusRequester() }

    Box(modifier.fillMaxSize()) {
        // surface — the screen's content plane.
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                // The shared browse-chain bar: back to the profiles home, the profile's
                // application icon, then its utilities.
                RemapTopBar(
                    overline = "$appLabel Layouts",
                    appPackage = appPackage,
                    onBack = onBack,
                    actions = {
                        // Add · Options adjacent at one rhythm — the group editor's header
                        // utility treatment.
                        MinputIconButton(
                            icon = Icons.Filled.Add,
                            contentDescription = "New layout",
                            onClick = { openModal = LayoutsModal.ADD },
                        )
                        MinputIconButton(
                            icon = Icons.Filled.Tune,
                            contentDescription = "Layout options",
                            onClick = { openModal = LayoutsModal.OPTIONS },
                        )
                    },
                )
                // The filter row: search + sort + direction (the Applications anatomy; the
                // former application-filter pill died with the per-app route).
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
                        placeholder = "Search layouts",
                        leadingIcon = Icons.Filled.Search,
                        clearable = true,
                        // Top-of-screen field — the sanctioned modal-less variant.
                        inlineEdit = true,
                        modifier = Modifier.weight(1f),
                    )
                    MinputPillDropdown(
                        current = sort,
                        elevated = true,
                        options = ProfileSort.entries,
                        optionLabel = { it.label },
                        onPick = { sort = it; ascending = it.naturalAscending },
                        onClickLabel = "Sort layouts",
                    )
                    SortDirectionButton(ascending = ascending, onToggle = { ascending = !ascending })
                }
                // Child layouts only: profiles bound (via auto-switch bindings) to this app.
                val packagesByProfile = appBindings.entries.groupBy({ it.value }, { it.key })
                val children = profiles.filter {
                    packagesByProfile[it.id]?.contains(appPackage) == true
                }
                val trimmed = query.trim()
                val filtered = if (trimmed.isEmpty()) {
                    children
                } else {
                    children.filter { it.name.contains(trimmed, ignoreCase = true) }
                }
                val comparator = when (sort) {
                    // No last-used tracking yet — "Recent" approximates with creation recency.
                    ProfileSort.RECENT -> compareBy<Profile> { it.id }
                    // Placeholder until community sharing brings real like counts —
                    // everything ties at zero, so recency breaks the tie.
                    ProfileSort.LIKES -> compareBy<Profile> { profileLikes(it) }.thenBy { it.id }
                    ProfileSort.NAME -> compareBy { it.name.lowercase() }
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
                                    text = if (children.isEmpty()) "No layouts for this profile yet"
                                    else "No layouts match",
                                    style = minputMiniTextStyle(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(displayed, key = { it.id }) { profile ->
                        LayoutTileRow(
                            profile = profile,
                            active = profile.id == activeProfileId,
                            onClick = { onSelectLayout(profile) },
                        )
                    }
                }
            }
        }

        // The view's modals, stacked above the content (composed after it, so their
        // BackHandlers win while open); [openModal] keeps them mutually exclusive.
        MinputModal(
            open = openModal == LayoutsModal.ADD,
            onDismiss = { openModal = null },
            height = AddProfileModalHeight,
            focusSeat = addModalCloseFocus,
            testTag = "layouts-modal:ADD",
            modifier = Modifier.matchParentSize(),
        ) {
            AddProfileModalContent(
                profiles = profiles,
                installedApps = installedApps,
                appBindings = appBindings,
                onLoadInstalledApps = onLoadInstalledApps,
                onCreateProfile = onCreateProfile,
                onClose = { openModal = null },
                // New layouts default to children of this view's application.
                initialPackages = setOf(appPackage),
                closeFocusRequester = addModalCloseFocus,
            )
        }
        MinputModal(
            open = openModal == LayoutsModal.OPTIONS,
            onDismiss = { openModal = null },
            height = ProfileOptionsModalHeight,
            focusSeat = optionsModalCloseFocus,
            testTag = "layouts-modal:OPTIONS",
            modifier = Modifier.matchParentSize(),
        ) {
            ProfileOptionsModalContent(
                onClose = { openModal = null },
                closeFocusRequester = optionsModalCloseFocus,
            )
        }
    }
}

/**
 * One layout tile: active check slot, name, then the overline-labeled metadata attributes.
 * Author/Likes are placeholders until community sharing lands: every local layout is
 * authored by the device owner with no like count.
 */
@Composable
private fun LayoutTileRow(
    profile: Profile,
    active: Boolean,
    onClick: () -> Unit,
) {
    PanelRow(onClick = onClick, active = active) {
        // Leading slot keeps labels aligned whether or not the check shows.
        Box(Modifier.size(PanelRowIconSize), contentAlignment = Alignment.Center) {
            if (active) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Active layout",
                    modifier = Modifier.size(PanelRowIconSize),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(MinputGlyphLabelGap))
        Text(
            text = profile.name,
            style = minputMiniTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(MinputGlyphLabelGap))
        TileMetaColumn(
            label = "Author",
            value = "You",
            modifier = Modifier.widthIn(max = TileAuthorMaxWidth),
        )
        Spacer(Modifier.width(MinputPillContentPadding))
        TileMetaColumn(label = "Likes", value = profileLikes(profile).toString())
    }
}

/** Like count for a layout — a constant until community sharing brings real counts. */
private fun profileLikes(@Suppress("UNUSED_PARAMETER") profile: Profile): Int = 0

/**
 * One overline-labeled metadata attribute on a layout tile (Author, Likes) — label stacked
 * over value, both single-line.
 */
@Composable
private fun TileMetaColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = label.uppercase(),
            style = minputOverlineTextStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = value,
            style = minputMiniTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Width cap for a tile's Author value (user/community names are unbounded). */
private val TileAuthorMaxWidth = 88.dp
