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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
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
import com.mappo.ui.minput.MinputIconButton
import com.mappo.ui.minput.MinputElevatedContainer
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputGroupButton
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
 * The two modals summoned from the profile panel's header utility buttons: ADD is the
 * new-profile form; OPTIONS holds settings that apply across all profiles. Both are
 * centered [MinputModal] cards stacked above the profile panel.
 */
internal enum class ProfilePanelModal { ADD, OPTIONS }

/**
 * Sort tabs for the profiles panel list. RECENT approximates "recently used" with creation
 * recency (no last-used tracking yet); GAME_APP orders by the profile's associated app
 * (auto-switch bindings), app-less profiles last.
 */
private enum class ProfileSort(val label: String) {
    RECENT("Recent"),
    NAME("Name"),
    GAME_APP("Game / app"),
}

/**
 * The profile panel's top-level views: the user's own profiles vs. browsing/importing
 * community-uploaded ones (servers / Steam). COMMUNITY is a placeholder surface for now —
 * the UX is being pinned down before any browsing/importing lands.
 */
private enum class ProfileTab(val label: String) {
    MINE("My profiles"),
    COMMUNITY("Community"),
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
    val modalUp = openModal != null

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
                installedApps = installedApps,
                appBindings = appBindings,
                onLoadInstalledApps = onLoadInstalledApps,
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
    }
}

/**
 * Profile selection, v1: a basic list of the current profiles in the group-editor's
 * dimensionality. Selecting a profile activates it and closes the panel. The header's
 * center tabs split the panel into "My profiles" (the list) and "Community" (a placeholder
 * for browsing/importing shared profiles). (Duplicate / delete and richer layout return in
 * a later pass — this view is deliberately minimal.)
 */
@Composable
private fun ProfilePanelContent(
    profiles: ImmutableList<Profile>,
    activeProfileId: Long?,
    onSelectProfile: (Profile) -> Unit,
    onClose: () -> Unit,
    onOpenModal: (ProfilePanelModal) -> Unit,
    installedApps: List<InstalledApp>,
    appBindings: Map<String, Long>,
    onLoadInstalledApps: () -> Unit,
    closeFocusRequester: FocusRequester? = null,
) {
    // View tab + live name filter + sort tab. All live here so they reset whenever the
    // panel closes (the panel content leaves composition) — each summon starts fresh.
    var tab by remember { mutableStateOf(ProfileTab.MINE) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ProfileSort.RECENT) }
    // Game/app sort labels profiles by their bound apps' display names — make sure the
    // installed-app labels are loaded once that sort is picked.
    LaunchedEffect(sort) {
        if (sort == ProfileSort.GAME_APP) onLoadInstalledApps()
    }
    Column(Modifier.fillMaxSize()) {
        PanelHeader(
            glyphRes = R.drawable.xbox_button_view,
            title = "Profiles",
            onClose = onClose,
            closeFocusRequester = closeFocusRequester,
            center = {
                // My profiles ↔ Community view switch, centered between title and utilities.
                Box(
                    modifier = Modifier.weight(1f).padding(horizontal = PanelContentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    MinputGroupButton(
                        options = ProfileTab.entries,
                        selected = tab,
                        onSelect = { tab = it },
                        optionLabel = { it.label },
                        modifier = Modifier.widthIn(max = ProfileTabsMaxWidth).fillMaxWidth(),
                    )
                }
            },
            actions = {
                // Add · Options sit adjacent to Close at one rhythm — the group editor's
                // header utility treatment (cog·kebab·close).
                MinputIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "New profile",
                    onClick = { onOpenModal(ProfilePanelModal.ADD) },
                )
                MinputIconButton(
                    icon = Icons.Filled.Tune,
                    contentDescription = "Profile options",
                    onClick = { onOpenModal(ProfilePanelModal.OPTIONS) },
                )
            },
        )
        PanelDivider()
        if (tab == ProfileTab.COMMUNITY) {
            // Placeholder surface — browsing/importing community + Steam-sourced profiles
            // lands here later; the tab exists to pin down the UX.
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(PanelContentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Browse and import community profiles here — coming soon",
                    style = minputMiniTextStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        // Search + sort share one row: filter field left, sort tabs right.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = PanelContentPadding,
                    end = PanelContentPadding,
                    top = MinputPanelDividerContentGap,
                    bottom = 6.dp,
                ),
        ) {
            MinputTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search profiles",
                leadingIcon = Icons.Filled.Search,
                clearable = true,
                modifier = Modifier.weight(1f),
            )
            MinputGroupButton(
                options = ProfileSort.entries,
                selected = sort,
                onSelect = { sort = it },
                optionLabel = { it.label },
                modifier = Modifier.weight(1.4f),
            )
        }
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            profiles
        } else {
            profiles.filter { it.name.contains(trimmed, ignoreCase = true) }
        }
        val displayed = when (sort) {
            // No last-used tracking yet — "Recent" approximates with creation recency.
            ProfileSort.RECENT -> filtered.sortedByDescending { it.id }
            ProfileSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            // Order by the profile's associated app (auto-switch bindings): app-bound
            // profiles first, alphabetical by app label (package name until labels load),
            // then app-less profiles by name.
            ProfileSort.GAME_APP -> {
                val packagesByProfile = appBindings.entries.groupBy({ it.value }, { it.key })
                val labelByPackage = installedApps.associateBy({ it.packageName }, { it.label })
                fun appKey(profile: Profile): String? = packagesByProfile[profile.id]
                    ?.minOfOrNull { pkg -> (labelByPackage[pkg] ?: pkg).lowercase() }
                filtered.sortedWith(
                    compareBy<Profile> { appKey(it) == null }
                        .thenBy { appKey(it) ?: "" }
                        .thenBy { it.name.lowercase() },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = PanelContentPadding, vertical = 2.dp),
        ) {
            if (displayed.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(PanelRowHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No profiles match",
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
                                contentDescription = "Active profile",
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
                    )
                }
            }
        }
    }
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

/** Width cap for the header's My profiles ↔ Community tabs, so the pair stays a compact
 *  centered switch instead of swallowing the whole flexible middle. */
private val ProfileTabsMaxWidth = 210.dp

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
