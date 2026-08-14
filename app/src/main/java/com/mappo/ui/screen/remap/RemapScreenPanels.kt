package com.mappo.ui.screen.remap

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mappo.R
import com.mappo.data.model.Profile
import com.mappo.data.repository.InstalledAppsRepository.InstalledApp
import com.mappo.data.settings.TextSize
import com.mappo.ui.compact.scaledLayout
import com.mappo.ui.component.rememberAppIconPainter
import com.mappo.ui.minput.MinputIconButton
import com.mappo.ui.minput.MinputElevatedContainer
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputModal
import com.mappo.ui.minput.MinputPanelDividerContentGap
import com.mappo.ui.minput.MinputPanelDividerInset
import com.mappo.ui.minput.MinputPanelHeaderHeight
import com.mappo.ui.minput.MinputPanelTitleInset
import com.mappo.ui.minput.MinputPillButton
import com.mappo.ui.minput.MinputPillContentPadding
import com.mappo.ui.minput.MinputPillDropdown
import com.mappo.ui.minput.MinputPillIconSize
import com.mappo.ui.minput.MinputTextField
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle
import com.mappo.ui.screen.AppPickerSheet
import kotlinx.collections.immutable.ImmutableList

/**
 * The two full-screen views summoned from the top bar's corner pills (or their physical
 * buttons: Select → profile, Start → options).
 */
internal enum class RemapPanel { PROFILE, OPTIONS }

/**
 * The modals summoned from the layout panel: ADD is the new-layout form; OPTIONS holds
 * settings that apply across all layouts; APPLICATIONS is the application-filter picker
 * (summoned from the filter row's app button). All are [MinputModal] surfaces stacked
 * above the panel.
 */
internal enum class ProfilePanelModal { ADD, OPTIONS, APPLICATIONS }

/**
 * Sort options for the layout panel's list. RECENT approximates "recently used" with
 * creation recency (no last-used tracking yet); LIKES is a placeholder ordering until
 * community sharing brings real like counts. [naturalAscending] is the direction each sort
 * resets to when picked — the direction toggle flips from there.
 */
private enum class ProfileSort(val label: String, val naturalAscending: Boolean) {
    RECENT("Recent", naturalAscending = false),
    LIKES("Likes", naturalAscending = false),
    NAME("A to Z", naturalAscending = true),
}

/** Sort options for the Applications modal's list. RECENT approximates "most recently
 *  used" with the package's install/update recency ([InstalledApp.recencyKey]) until
 *  usage tracking or game-library scanning lands. */
private enum class AppSort(val label: String, val naturalAscending: Boolean) {
    RECENT("Recent", naturalAscending = false),
    NAME("A to Z", naturalAscending = true),
}

/**
 * The layout panel's application filter: [All] shows every layout, [Global] only layouts
 * with no app association, [App] only layouts bound (via auto-switch bindings) to one
 * package. Defaults to the detected foreground app each time the panel opens.
 */
internal sealed interface LayoutAppFilter {
    data object All : LayoutAppFilter
    data object Global : LayoutAppFilter
    data class App(val packageName: String, val label: String) : LayoutAppFilter
}

/**
 * One tappable row of the options panel — the destinations that used to live in the old home
 * screen's options fly-out. The caller owns navigation; the panel closes itself before firing.
 */
data class RemapOptionEntry(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * The profile / options panels: full-bleed [MinputModal]s over the whole screen (top bar
 * included) — separate raised interfaces above the controls view, inset by [EditorMargin]
 * so the screen edges peek through. Converted from the morph treatment 2026-08-08: the
 * pill-to-panel rect-lerp read awkwardly (tiny cornered summons becoming large centered
 * surfaces), so panels and their modals now share the modal's standard fade + settle. The
 * group editor keeps the morph — it genuinely lives inside the screen and collapses back
 * into its home box.
 *
 * The caller (RemapControlsScreen) hosts this as the last child of its root Box with
 * `Modifier.matchParentSize()` and owns [openPanel] (user intent, rememberSaveable there).
 */
@Composable
internal fun RemapPanelOverlay(
    openPanel: RemapPanel?,
    onClose: () -> Unit,
    profiles: ImmutableList<Profile>,
    activeProfileId: Long?,
    onSelectProfile: (Profile) -> Unit,
    powerOn: Boolean,
    onPowerChange: (Boolean) -> Unit,
    textSize: TextSize,
    onTextSizeChange: (TextSize) -> Unit,
    optionsEntries: List<RemapOptionEntry>,
    // ── Profile-panel modals (Add / all-profile options) ──────────────────────────
    openModal: ProfilePanelModal?,
    onOpenModal: (ProfilePanelModal) -> Unit,
    onCloseModal: () -> Unit,
    installedApps: List<InstalledApp>,
    appBindings: Map<String, Long>,
    onLoadInstalledApps: () -> Unit,
    onCreateProfile: (name: String, packages: Set<String>) -> Unit,
    currentApp: InstalledApp? = null,
    modifier: Modifier = Modifier,
) {
    // One focus seat per surface — each modal owns its own seat/trap/recovery through
    // MinputModal's focus contract; the requesters attach to each surface's Close button
    // via [PanelHeader]. Separate requesters per panel: during a Select↔Start switch both
    // contents briefly compose (one fading out), and a requester attached to two nodes
    // makes requestFocus throw.
    val profileCloseFocus = remember { FocusRequester() }
    val optionsCloseFocus = remember { FocusRequester() }
    val addModalCloseFocus = remember { FocusRequester() }
    val profileOptionsCloseFocus = remember { FocusRequester() }
    val applicationsCloseFocus = remember { FocusRequester() }
    val modalUp = openModal != null

    // The application filter, hoisted here so the Applications modal (stacked above the
    // panel) and the panel's filter row share it. Keyed on the panel being open: each
    // summon starts fresh, re-defaulting to the currently detected app.
    val profileOpen = openPanel == RemapPanel.PROFILE
    var appFilter by remember(profileOpen) {
        mutableStateOf<LayoutAppFilter>(
            currentApp?.let { LayoutAppFilter.App(it.packageName, it.label) } ?: LayoutAppFilter.All,
        )
    }

    Box(modifier) {
        // The two panels: full-bleed modals inset by EditorMargin (the group editor's
        // framing, so the remap surfaces still read as one family). [openPanel] keeps them
        // mutually exclusive — switching reads as a quick crossfade. While one of the
        // profile panel's own modals is up, the panel is obscured: it refuses focus entry
        // and the surface above owns the seat.
        MinputModal(
            open = openPanel == RemapPanel.PROFILE,
            onDismiss = onClose,
            margin = EditorMargin,
            focusSeat = profileCloseFocus,
            obscured = modalUp,
            testTag = "remap-panel:PROFILE",
            modifier = Modifier.matchParentSize(),
        ) {
            ProfilePanelContent(
                profiles = profiles,
                activeProfileId = activeProfileId,
                onSelectProfile = onSelectProfile,
                onClose = onClose,
                onOpenModal = onOpenModal,
                appBindings = appBindings,
                onLoadInstalledApps = onLoadInstalledApps,
                appFilter = appFilter,
                closeFocusRequester = profileCloseFocus,
            )
        }
        MinputModal(
            open = openPanel == RemapPanel.OPTIONS,
            onDismiss = onClose,
            margin = EditorMargin,
            focusSeat = optionsCloseFocus,
            obscured = modalUp,
            testTag = "remap-panel:OPTIONS",
            modifier = Modifier.matchParentSize(),
        ) {
            OptionsPanelContent(
                powerOn = powerOn,
                onPowerChange = onPowerChange,
                textSize = textSize,
                onTextSizeChange = onTextSizeChange,
                entries = optionsEntries,
                onClose = onClose,
                closeFocusRequester = optionsCloseFocus,
            )
        }

        // The profile panel's modals stack ABOVE the panels: composed after them, so their
        // BackHandlers win while open; [openModal] keeps them mutually exclusive.
        MinputModal(
            open = openModal == ProfilePanelModal.ADD,
            onDismiss = onCloseModal,
            height = AddProfileModalHeight,
            focusSeat = addModalCloseFocus,
            testTag = "profile-modal:ADD",
            modifier = Modifier.matchParentSize(),
        ) {
            AddProfileModalContent(
                profiles = profiles,
                installedApps = installedApps,
                appBindings = appBindings,
                onLoadInstalledApps = onLoadInstalledApps,
                onCreateProfile = onCreateProfile,
                onClose = onCloseModal,
                closeFocusRequester = addModalCloseFocus,
            )
        }
        MinputModal(
            open = openModal == ProfilePanelModal.OPTIONS,
            onDismiss = onCloseModal,
            height = ProfileOptionsModalHeight,
            focusSeat = profileOptionsCloseFocus,
            testTag = "profile-modal:OPTIONS",
            modifier = Modifier.matchParentSize(),
        ) {
            ProfileOptionsModalContent(
                onClose = onCloseModal,
                closeFocusRequester = profileOptionsCloseFocus,
            )
        }
        MinputModal(
            open = openModal == ProfilePanelModal.APPLICATIONS,
            onDismiss = onCloseModal,
            margin = ApplicationsModalMargin,
            focusSeat = applicationsCloseFocus,
            testTag = "profile-modal:APPLICATIONS",
            modifier = Modifier.matchParentSize(),
        ) {
            ApplicationsModalContent(
                installedApps = installedApps,
                currentFilter = appFilter,
                onPickFilter = { appFilter = it; onCloseModal() },
                onLoadInstalledApps = onLoadInstalledApps,
                onClose = onCloseModal,
                closeFocusRequester = applicationsCloseFocus,
            )
        }
    }
}

/**
 * Layout selection (UI label "Layouts"; the code keeps the Profile names): the filter row —
 * application filter (opens the Applications modal), inline search, dormant advanced-filters
 * button, sort + direction — over the layout list. Selecting a layout activates it and
 * closes the panel. (The former My profiles ↔ Community header tabs were removed 2026-08-13;
 * community browsing returns in a different form later.)
 */
@Composable
private fun ProfilePanelContent(
    profiles: ImmutableList<Profile>,
    activeProfileId: Long?,
    onSelectProfile: (Profile) -> Unit,
    onClose: () -> Unit,
    onOpenModal: (ProfilePanelModal) -> Unit,
    appBindings: Map<String, Long>,
    onLoadInstalledApps: () -> Unit,
    appFilter: LayoutAppFilter,
    closeFocusRequester: FocusRequester? = null,
) {
    // Live name filter + sort. Local so they reset whenever the panel closes (the content
    // leaves composition) — each summon starts fresh.
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ProfileSort.RECENT) }
    var ascending by remember { mutableStateOf(ProfileSort.RECENT.naturalAscending) }
    // The Applications modal needs the installed list the moment it's summoned — start the
    // (cached, one-shot) load as the panel opens so the modal never pops in empty.
    LaunchedEffect(Unit) { onLoadInstalledApps() }
    Column(Modifier.fillMaxSize()) {
        PanelHeader(
            glyphRes = R.drawable.xbox_button_view,
            title = "Layouts",
            onClose = onClose,
            closeFocusRequester = closeFocusRequester,
            actions = {
                // Add · Options sit adjacent to Close at one rhythm — the group editor's
                // header utility treatment (cog·kebab·close).
                MinputIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "New layout",
                    onClick = { onOpenModal(ProfilePanelModal.ADD) },
                )
                MinputIconButton(
                    icon = Icons.Filled.Tune,
                    contentDescription = "Layout options",
                    onClick = { onOpenModal(ProfilePanelModal.OPTIONS) },
                )
            },
        )
        // PanelDivider()
        // The filter row: application filter · search · (dormant) filters · sort + direction.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = PanelContentPadding,
                    end = PanelContentPadding,
                    top = MinputPanelDividerContentGap,
                    // bottom = MinputPanelDividerContentGap,
                ),
        ) {
            AppFilterButton(
                filter = appFilter,
                onClick = { onOpenModal(ProfilePanelModal.APPLICATIONS) },
            )
            MinputTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search layouts",
                leadingIcon = Icons.Filled.Search,
                clearable = true,
                // Top-of-panel field — the sanctioned modal-less variant; the IME overlays
                // the list below, never the field.
                inlineEdit = true,
                modifier = Modifier.weight(1f),
            )
            // Advanced filters — dormant until the filter menu is built.
            MinputIconButton(
                icon = Icons.Filled.FilterList,
                contentDescription = "Filters",
                onClick = {},
                enabled = false,
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
        // PanelDivider()
        val packagesByProfile = appBindings.entries.groupBy({ it.value }, { it.key })
        val appFiltered = when (appFilter) {
            LayoutAppFilter.All -> profiles
            LayoutAppFilter.Global -> profiles.filter { packagesByProfile[it.id].isNullOrEmpty() }
            is LayoutAppFilter.App -> profiles.filter {
                packagesByProfile[it.id]?.contains(appFilter.packageName) == true
            }
        }
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            appFiltered
        } else {
            appFiltered.filter { it.name.contains(trimmed, ignoreCase = true) }
        }
        val comparator = when (sort) {
            // No last-used tracking yet — "Recent" approximates with creation recency.
            ProfileSort.RECENT -> compareBy<Profile> { it.id }
            // Placeholder until community sharing brings real like counts — everything
            // ties at zero, so recency breaks the tie.
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
                            text = "No layouts match",
                            style = minputMiniTextStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(displayed, key = { it.id }) { profile ->
                PanelRow(
                    onClick = { onClose(); onSelectProfile(profile) },
                    active = profile.id == activeProfileId,
                ) {
                    // Leading slot keeps labels aligned whether or not the check shows.
                    Box(Modifier.size(PanelRowIconSize), contentAlignment = Alignment.Center) {
                        if (profile.id == activeProfileId) {
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
                    // Placeholder metadata until community sharing lands: every local
                    // layout is authored by the device owner with no like count.
                    TileMetaColumn(
                        label = "Author",
                        value = "You",
                        modifier = Modifier.widthIn(max = TileAuthorMaxWidth),
                    )
                    Spacer(Modifier.width(MinputPillContentPadding))
                    TileMetaColumn(label = "Likes", value = profileLikes(profile).toString())
                }
            }
        }
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

/**
 * The application-filter summon: current filter's icon + label on a pill, capped at
 * [AppFilterMaxWidth] so long application titles ellipsize instead of squeezing the row.
 */
@Composable
private fun AppFilterButton(filter: LayoutAppFilter, onClick: () -> Unit) {
    val appIcon = rememberAppIconPainter((filter as? LayoutAppFilter.App)?.packageName)
    val (label, fallbackIcon) = when (filter) {
        LayoutAppFilter.All -> "All applications" to Icons.Filled.Apps
        LayoutAppFilter.Global -> "Global" to Icons.Filled.Public
        is LayoutAppFilter.App -> filter.label to Icons.Filled.Apps
    }
    MinputPillButton(
        text = label,
        elevated = true,
        onClick = onClick,
        leadingIcon = appIcon ?: rememberVectorPainter(fallbackIcon),
        // App icons render untinted (full-color); the vector fallbacks take the standard
        // glyph tint.
        leadingIconTint = if (appIcon != null) Color.Unspecified
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = AppFilterMaxWidth),
    )
}

/** Sort-direction toggle: the icon shows the CURRENT direction; tapping flips it. */
@Composable
private fun SortDirectionButton(ascending: Boolean, onToggle: () -> Unit) {
    MinputIconButton(
        icon = if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
        contentDescription = if (ascending) "Sorted ascending" else "Sorted descending",
        onClick = onToggle,
    )
}

/**
 * The options panel: the master Power switch (rehomed from the old home screen) followed by
 * the destination rows that used to live in the home options fly-out.
 */
@Composable
private fun OptionsPanelContent(
    powerOn: Boolean,
    onPowerChange: (Boolean) -> Unit,
    textSize: TextSize,
    onTextSizeChange: (TextSize) -> Unit,
    entries: List<RemapOptionEntry>,
    onClose: () -> Unit,
    closeFocusRequester: FocusRequester? = null,
) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader(
            glyphRes = R.drawable.xbox_button_menu,
            title = "Options",
            onClose = onClose,
            closeFocusRequester = closeFocusRequester,
        )
        PanelDivider()
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = PanelContentPadding,
                end = PanelContentPadding,
                // First content sits the standard gap below the divider — matches the
                // profiles panel's search/sort row.
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
            items(entries, key = { it.id }) { entry ->
                PanelRow(onClick = { onClose(); entry.onClick() }) {
                    Icon(
                        entry.icon,
                        contentDescription = null,
                        modifier = Modifier.size(PanelRowIconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(MinputGlyphLabelGap))
                    Text(
                        text = entry.label,
                        style = minputMiniTextStyle(),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Sticky panel header, matching the group editor's header anatomy: leading identity
 * (a physical button's prompt glyph — rendered UNTINTED, fixed hardware colors — or a
 * tinted vector [icon] for surfaces without a summoning button), overline title, an
 * optional [center] control filling the flexible middle (it owns the weight), then
 * optional utility [actions] adjacent to Close at one rhythm.
 */
@Composable
private fun PanelHeader(
    title: String,
    onClose: () -> Unit,
    glyphRes: Int? = null,
    icon: ImageVector? = null,
    center: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    closeFocusRequester: FocusRequester? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(MinputPanelHeaderHeight)
            .padding(horizontal = PanelContentPadding),
    ) {
        // Non-interactive title block: nudged inward to optically match the trailing icon
        // buttons, whose glyphs sit inside an invisible circular tap target.
        Spacer(Modifier.width(MinputPanelTitleInset))
        if (glyphRes != null) {
            Icon(
                painterResource(glyphRes),
                contentDescription = null,
                modifier = Modifier.size(MinputPillIconSize),
                tint = Color.Unspecified,
            )
        } else if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(MinputPillIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(MinputGlyphLabelGap))
        Text(
            text = title.uppercase(),
            style = minputOverlineTextStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The flexible middle: a caller-supplied control (it owns the weight) or the
        // default gap pushing the utilities to the trailing edge.
        if (center != null) center() else Spacer(Modifier.weight(1f))
        actions()
        MinputIconButton(
            icon = Icons.Filled.Close,
            contentDescription = "Close",
            onClick = onClose,
            // Controller-focus seat for the surface (the group-editor pattern: focus lands
            // on a real button, never a container).
            modifier = if (closeFocusRequester != null) {
                Modifier.focusRequester(closeFocusRequester)
            } else Modifier,
        )
    }
}

/**
 * The header/content divider shared by every panel surface — the group editor's treatment,
 * at the family's standard inset.
 */
@Composable
private fun PanelDivider() {
    HorizontalDivider(Modifier.padding(horizontal = MinputPanelDividerInset))
}

/**
 * One compact panel row. Hand-rolled (not ListItem) to match the group editor's sub-Material
 * row dimensionality — a deliberate, annotated deviation shared by the whole remap chrome.
 * [active] raises the row to the topmost plane (the selection treatment on this surface).
 */
@Composable
private fun PanelRow(
    onClick: () -> Unit,
    active: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelRowHeight)
            .clip(shape)
            .then(if (active) Modifier.background(MinputElevatedContainer) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = MinputPillContentPadding),
    ) {
        content()
    }
}

/**
 * Master power row (the old home's PowerControl reshaped as a settings row): LED + label
 * left, the halo-stripped scaled switch right. Drives remap AND the button overlay in
 * lockstep via the caller.
 */
@Composable
private fun PowerRow(powerOn: Boolean, onPowerChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelPowerRowHeight)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onPowerChange(!powerOn) }
            .padding(horizontal = MinputPillContentPadding),
    ) {
        // Deliberate fixed LED green — a power LED is green regardless of theme.
        val led by animateColorAsState(
            targetValue = if (powerOn) Color(0xFF52E07C)
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            label = "powerLed",
        )
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(led),
        )
        Spacer(Modifier.width(MinputGlyphLabelGap))
        Text(
            text = "Power",
            style = minputMiniTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        // Strip the 48dp interactive halo + scale down so the switch fits the compact row.
        // The row owns the tap target; the switch itself stays interactive for thumb drags.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Switch(
                checked = powerOn,
                onCheckedChange = onPowerChange,
                modifier = Modifier.scaledLayout(0.8f),
                thumbContent = {
                    Icon(
                        imageVector = if (powerOn) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = if (powerOn) "Mappo features on" else "Mappo features off",
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        }
    }
}

/**
 * Text-size row: the app-level font scale, enforced independently of the OS setting (the
 * UI is tuned against the OS "Small" scale — see [TextSize]). Same anatomy as the settings
 * rows around it: glyph + label left, minput pill dropdown right.
 */
@Composable
private fun TextSizeRow(current: TextSize, onPick: (TextSize) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelPowerRowHeight)
            .padding(horizontal = MinputPillContentPadding),
    ) {
        Icon(
            Icons.Filled.FormatSize,
            contentDescription = null,
            modifier = Modifier.size(PanelRowIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(MinputGlyphLabelGap))
        Text(
            text = "Text size",
            style = minputMiniTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        MinputPillDropdown(
            current = current,
            options = TextSize.entries,
            optionLabel = { it.label },
            onPick = onPick,
            onClickLabel = "Change text size",
        )
    }
}

/**
 * The new-profile form: name plus (optionally) the apps auto-switch should open it for.
 * Form state is deliberately un-hoisted — the modal's content leaves composition on close,
 * so every summon starts a fresh form.
 */
@Composable
private fun AddProfileModalContent(
    profiles: ImmutableList<Profile>,
    installedApps: List<InstalledApp>,
    appBindings: Map<String, Long>,
    onLoadInstalledApps: () -> Unit,
    onCreateProfile: (name: String, packages: Set<String>) -> Unit,
    onClose: () -> Unit,
    // Attached to the Close button; the hosting MinputModal owns the seat/recovery.
    closeFocusRequester: FocusRequester? = null,
) {
    var name by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pickerOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        PanelHeader(
            title = "New profile",
            icon = Icons.Filled.Add,
            onClose = onClose,
            closeFocusRequester = closeFocusRequester,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = PanelContentPadding),
        ) {
            MinputTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Profile name",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Associated apps",
                        style = minputMiniTextStyle(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Auto switch opens this profile with these apps",
                        style = minputMiniTextStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(MinputGlyphLabelGap))
                MinputPillButton(
                    text = when (picked.size) {
                        0 -> "Pick apps"
                        1 -> "1 app"
                        else -> "${picked.size} apps"
                    },
                    onClick = { onLoadInstalledApps(); pickerOpen = true },
                    elevated = true,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(PanelContentPadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        ) {
            MinputPillButton(text = "Cancel", onClick = onClose)
            MinputPillButton(
                text = "Create",
                onClick = { onCreateProfile(name.trim(), picked); onClose() },
                enabled = name.isNotBlank(),
                filled = true,
                elevated = true,
            )
        }
    }

    if (pickerOpen) {
        // The auto-switch app picker, aimed at a profile that doesn't exist yet — the picked
        // set is held here and bound in one shot when Create fires.
        AppPickerSheet(
            visible = true,
            targetProfileName = name.trim().ifEmpty { "New profile" },
            targetProfileId = null,
            installedApps = installedApps,
            existingBindings = appBindings,
            profilesById = profiles.associateBy { it.id },
            onConfirm = { picked = it },
            onDismiss = { pickerOpen = false },
        )
    }
}

/**
 * The application-filter picker: every launchable app on the device (games join the list
 * when the planned library scanning lands — local-folder scraping plus installed-game lists
 * from frontends like GameNative / GameHub), behind its own search + sort row, with the two
 * meta filters pinned on top: "All applications" and "Global" (layouts with no app
 * association). Picking any row commits the filter and closes the modal.
 */
@Composable
private fun ApplicationsModalContent(
    installedApps: List<InstalledApp>,
    currentFilter: LayoutAppFilter,
    onPickFilter: (LayoutAppFilter) -> Unit,
    onLoadInstalledApps: () -> Unit,
    onClose: () -> Unit,
    // Attached to the Close button; the hosting MinputModal owns the seat/recovery.
    closeFocusRequester: FocusRequester? = null,
) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(AppSort.RECENT) }
    var ascending by remember { mutableStateOf(AppSort.RECENT.naturalAscending) }
    LaunchedEffect(Unit) { onLoadInstalledApps() }
    Column(Modifier.fillMaxSize()) {
        PanelHeader(
            title = "Applications",
            icon = Icons.Filled.Apps,
            onClose = onClose,
            closeFocusRequester = closeFocusRequester,
        )
        // PanelDivider()
        // The modal's own filter row: search + sort + direction (same anatomy as the
        // layout panel's row, minus the app filter it exists to set).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = PanelContentPadding,
                    end = PanelContentPadding,
                    // top = MinputPanelDividerContentGap,
                    bottom = MinputPanelDividerContentGap,
                ),
        ) {
            MinputTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search applications",
                leadingIcon = Icons.Filled.Search,
                clearable = true,
                // Top-of-modal field — the sanctioned modal-less variant.
                inlineEdit = true,
                modifier = Modifier.weight(1f),
            )
            MinputPillDropdown(
                current = sort,
                elevated = true,
                options = AppSort.entries,
                optionLabel = { it.label },
                onPick = { sort = it; ascending = it.naturalAscending },
                onClickLabel = "Sort applications",
            )
            SortDirectionButton(ascending = ascending, onToggle = { ascending = !ascending })
        }
        // PanelDivider()
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
            item(key = "all") {
                AppRow(
                    label = "All applications",
                    vectorIcon = Icons.Filled.Apps,
                    active = currentFilter == LayoutAppFilter.All,
                    onClick = { onPickFilter(LayoutAppFilter.All) },
                )
            }
            item(key = "global") {
                AppRow(
                    label = "Global",
                    vectorIcon = Icons.Filled.Public,
                    active = currentFilter == LayoutAppFilter.Global,
                    onClick = { onPickFilter(LayoutAppFilter.Global) },
                )
            }
            if (displayed.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(PanelRowHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (installedApps.isEmpty()) "Loading applications…" else "No applications match",
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
                    active = (currentFilter as? LayoutAppFilter.App)?.packageName == app.packageName,
                    onClick = { onPickFilter(LayoutAppFilter.App(app.packageName, app.label)) },
                )
            }
        }
    }
}

/**
 * One row of the Applications modal: launcher icon (or a tinted [vectorIcon] for the meta
 * filters) + label. [active] marks the currently applied filter.
 */
@Composable
private fun AppRow(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    packageName: String? = null,
    vectorIcon: ImageVector? = null,
) {
    PanelRow(onClick = onClick, active = active) {
        val appIcon = rememberAppIconPainter(packageName)
        when {
            appIcon != null -> Image(
                painter = appIcon,
                contentDescription = null,
                modifier = Modifier.size(PanelRowIconSize),
            )
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

/**
 * Options that apply across ALL profiles. Deliberately empty for now — the surface and its
 * summon exist so content can land here without another chrome pass.
 */
@Composable
private fun ProfileOptionsModalContent(
    onClose: () -> Unit,
    // Attached to the Close button; the hosting MinputModal owns the seat/recovery.
    closeFocusRequester: FocusRequester? = null,
) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader(
            title = "Profile options",
            icon = Icons.Filled.Tune,
            onClose = onClose,
            closeFocusRequester = closeFocusRequester,
        )
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(PanelContentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Options that apply to all profiles will live here",
                style = minputMiniTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Horizontal content inset of the panel's header + list (also the divider inset). */
private val PanelContentPadding = 8.dp

/** Width cap for the application-filter pill — application titles ellipsize past it. */
private val AppFilterMaxWidth = 132.dp

/** Width cap for a tile's Author value (user/community names are unbounded). */
private val TileAuthorMaxWidth = 88.dp

/** The Applications modal insets a step past the layout panel so it reads as stacked. */
private val ApplicationsModalMargin = EditorMargin + 10.dp

/** Compact list-row height (between the editor's 38dp rows and the 32dp tabs). */
private val PanelRowHeight = 32.dp

/** The power row runs slightly taller so the scaled switch keeps breathing room. */
private val PanelPowerRowHeight = 36.dp

/** Leading glyph edge inside panel rows. */
private val PanelRowIconSize = 16.dp

/** Target height of the new-profile modal: header + name field + apps row + footer. */
private val AddProfileModalHeight = 172.dp

/** Target height of the (for-now empty) all-profiles options modal. */
private val ProfileOptionsModalHeight = 120.dp
