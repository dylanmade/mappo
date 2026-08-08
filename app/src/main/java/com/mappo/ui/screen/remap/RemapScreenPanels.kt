package com.mappo.ui.screen.remap

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mappo.R
import com.mappo.data.model.Profile
import com.mappo.data.repository.InstalledAppsRepository.InstalledApp
import com.mappo.ui.compact.scaledLayout
import com.mappo.ui.minput.MinputIconButton
import com.mappo.ui.minput.MinputElevatedContainer
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputMorphModal
import com.mappo.ui.minput.MinputPillButton
import com.mappo.ui.minput.MinputPillContentPadding
import com.mappo.ui.minput.MinputPillIconSize
import com.mappo.ui.minput.MinputTextField
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputBoxContainer
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle
import com.mappo.ui.screen.AppPickerSheet
import com.mappo.ui.screen.softDropShadow
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList

/**
 * The two full-screen views summoned from the top bar's corner pills (or their physical
 * buttons: Select → profile, Start → options).
 */
internal enum class RemapPanel { PROFILE, OPTIONS }

/**
 * The two modals summoned from the profile panel's header utility buttons: ADD is the
 * new-profile form; OPTIONS holds settings that apply across all profiles. Both morph
 * open from their summoning icon button via [MinputMorphModal].
 */
internal enum class ProfilePanelModal { ADD, OPTIONS }

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
 * Full-screen morphing overlay for the profile / options panels. Same treatment as the
 * group editor's in-place morph ([RemapSimpleView]): the summoning pill's captured bounds
 * rect-lerp to the whole screen minus [EditorMargin] — but hosted at the SCREEN root, so
 * unlike the group editor this covers the top tab bar too. Content fades in with the morph.
 *
 * The caller (RemapControlsScreen) hosts this as the last child of its root Box with
 * `Modifier.matchParentSize()` and owns [openPanel] (user intent, rememberSaveable there).
 */
@Composable
internal fun RemapPanelOverlay(
    openPanel: RemapPanel?,
    onClose: () -> Unit,
    buttonBounds: (RemapPanel) -> Rect?,
    rootSize: IntSize,
    profiles: ImmutableList<Profile>,
    activeProfileId: Long?,
    onSelectProfile: (Profile) -> Unit,
    powerOn: Boolean,
    onPowerChange: (Boolean) -> Unit,
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
    // User intent (openPanel) vs. what's on screen mid-animation (visiblePanel) — the same
    // state pair as the group editor morph. Switching panels collapses the current one back
    // into its pill before expanding the other.
    var visiblePanel by remember { mutableStateOf(openPanel) }
    val progress = remember { Animatable(if (openPanel != null) 1f else 0f) }
    LaunchedEffect(openPanel) {
        val target = openPanel
        if (target == visiblePanel) {
            if (target != null && progress.value < 1f) {
                progress.animateTo(1f, tween(ExpandMillis, easing = FastOutSlowInEasing))
            }
            return@LaunchedEffect
        }
        if (visiblePanel != null) {
            progress.animateTo(0f, tween(CollapseMillis, easing = FastOutSlowInEasing))
            visiblePanel = null
        }
        if (target != null) {
            visiblePanel = target
            progress.animateTo(1f, tween(ExpandMillis, easing = FastOutSlowInEasing))
        }
    }

    // Composed after the screen's (and the group editor's) BackHandlers, so this wins while a
    // panel is up — back closes the panel, not the screen. (The modals' own BackHandlers
    // compose later still, so they win over this while a modal is up.)
    BackHandler(enabled = openPanel != null) { onClose() }

    // The overlay box's own coordinates + the raw coordinates of the modal-summoning header
    // buttons. Conversion to overlay space happens lazily at read time so callback ordering
    // between parent and child onGloballyPositioned passes can't hand the morph stale rects.
    var overlayCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val modalButtonCoords = remember { mutableStateMapOf<ProfilePanelModal, LayoutCoordinates>() }
    val modalOrigin: (ProfilePanelModal) -> Rect? = { m ->
        val root = overlayCoords?.takeIf { it.isAttached }
        val btn = modalButtonCoords[m]?.takeIf { it.isAttached }
        if (root != null && btn != null) root.localBoundingBoxOf(btn) else null
    }

    val vp = visiblePanel
    val origin = vp?.let { buttonBounds(it) }
    if (vp == null || origin == null || rootSize == IntSize.Zero) return

    val marginPx = with(LocalDensity.current) { EditorMargin.toPx() }
    val target = Rect(
        offset = Offset(marginPx, marginPx),
        size = Size(rootSize.width - marginPx * 2, rootSize.height - marginPx * 2),
    )
    val shape = RoundedCornerShape(GroupCorner)
    val container = minputBoxContainer()
    Box(modifier.onGloballyPositioned { overlayCoords = it }) {
        Box(
            modifier = Modifier
                // Per-frame rect is read in the LAYOUT phase; fades in the DRAW phase — the
                // no-recompose-per-frame lesson from the group-editor morph.
                .layout { measurable, constraints ->
                    val rect = lerp(origin, target, progress.value)
                    val placeable = measurable.measure(
                        Constraints.fixed(
                            rect.width.roundToInt().coerceAtLeast(1),
                            rect.height.roundToInt().coerceAtLeast(1),
                        ),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
                    }
                }
                .softDropShadow(cornerRadius = GroupCorner)
                .clip(shape)
                .background(container)
                .border(minputBevelBorder(container, GroupCorner), shape)
                .testTag("remap-panel:" + vp.name),
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value }) {
                when (vp) {
                    RemapPanel.PROFILE -> ProfilePanelContent(
                        profiles = profiles,
                        activeProfileId = activeProfileId,
                        onSelectProfile = onSelectProfile,
                        onClose = onClose,
                        onOpenModal = onOpenModal,
                        onModalButtonPositioned = { modal, coords ->
                            modalButtonCoords[modal] = coords
                        },
                    )
                    RemapPanel.OPTIONS -> OptionsPanelContent(
                        powerOn = powerOn,
                        onPowerChange = onPowerChange,
                        entries = optionsEntries,
                        onClose = onClose,
                    )
                }
            }
        }

        // The profile panel's modals morph from their header icon buttons and scrim over the
        // whole overlay. One instance per modal; [openModal] keeps them mutually exclusive.
        MinputMorphModal(
            open = openModal == ProfilePanelModal.ADD,
            onDismiss = onCloseModal,
            originBounds = { modalOrigin(ProfilePanelModal.ADD) },
            rootSize = rootSize,
            height = AddProfileModalHeight,
            modifier = Modifier.matchParentSize(),
            testTag = "profile-modal:ADD",
        ) {
            AddProfileModalContent(
                profiles = profiles,
                installedApps = installedApps,
                appBindings = appBindings,
                onLoadInstalledApps = onLoadInstalledApps,
                onCreateProfile = onCreateProfile,
                onClose = onCloseModal,
            )
        }
        MinputMorphModal(
            open = openModal == ProfilePanelModal.OPTIONS,
            onDismiss = onCloseModal,
            originBounds = { modalOrigin(ProfilePanelModal.OPTIONS) },
            rootSize = rootSize,
            height = ProfileOptionsModalHeight,
            modifier = Modifier.matchParentSize(),
            testTag = "profile-modal:OPTIONS",
        ) {
            ProfileOptionsModalContent(onClose = onCloseModal)
        }
    }
}

/**
 * Profile selection, v1: a basic list of the current profiles in the group-editor's
 * dimensionality. Selecting a profile activates it and closes the panel. (Add / duplicate /
 * delete and richer layout return in a later pass — this view is deliberately minimal.)
 */
@Composable
private fun ProfilePanelContent(
    profiles: ImmutableList<Profile>,
    activeProfileId: Long?,
    onSelectProfile: (Profile) -> Unit,
    onClose: () -> Unit,
    onOpenModal: (ProfilePanelModal) -> Unit,
    onModalButtonPositioned: (ProfilePanelModal, LayoutCoordinates) -> Unit,
) {
    // Live name filter for the list below. Lives here so it resets whenever the panel
    // closes (the panel content leaves composition) — each summon starts unfiltered.
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        PanelHeader(
            glyphRes = R.drawable.xbox_button_view,
            title = "Profiles",
            onClose = onClose,
            center = {
                MinputTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search profiles",
                    leadingIcon = Icons.Filled.Search,
                    clearable = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = PanelContentPadding),
                )
            },
            actions = {
                // Add · Options sit adjacent to Close at one rhythm — the group editor's
                // header utility treatment (cog·kebab·close).
                MinputIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "New profile",
                    onClick = { onOpenModal(ProfilePanelModal.ADD) },
                    modifier = Modifier.onGloballyPositioned {
                        onModalButtonPositioned(ProfilePanelModal.ADD, it)
                    },
                )
                MinputIconButton(
                    icon = Icons.Filled.Tune,
                    contentDescription = "Profile options",
                    onClick = { onOpenModal(ProfilePanelModal.OPTIONS) },
                    modifier = Modifier.onGloballyPositioned {
                        onModalButtonPositioned(ProfilePanelModal.OPTIONS, it)
                    },
                )
            },
        )
        // Header/content separation — the group editor's divider treatment.
        HorizontalDivider(Modifier.padding(horizontal = PanelContentPadding))
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            profiles
        } else {
            profiles.filter { it.name.contains(trimmed, ignoreCase = true) }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = PanelContentPadding, vertical = 2.dp),
        ) {
            if (filtered.isEmpty()) {
                item(key = "no_matches") {
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
            items(filtered, key = { it.id }) { profile ->
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
    entries: List<RemapOptionEntry>,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader(glyphRes = R.drawable.xbox_button_menu, title = "Options", onClose = onClose)
        // Same header/content divider as the profiles panel + group editor — one family.
        HorizontalDivider(Modifier.padding(horizontal = PanelContentPadding))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = PanelContentPadding, vertical = 2.dp),
        ) {
            item(key = "power") {
                PowerRow(powerOn = powerOn, onPowerChange = onPowerChange)
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
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelHeaderHeight)
            .padding(horizontal = PanelContentPadding),
    ) {
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
        )
    }
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
) {
    var name by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pickerOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        PanelHeader(title = "New profile", icon = Icons.Filled.Add, onClose = onClose)
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
private fun ProfileOptionsModalContent(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader(title = "Profile options", icon = Icons.Filled.Tune, onClose = onClose)
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

/** Header height — matches the group editor's sticky header. */
private val PanelHeaderHeight = 42.dp

/** Horizontal content inset of the panel's header + list. */
private val PanelContentPadding = 8.dp

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
