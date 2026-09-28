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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GridView
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
import com.mappo.data.model.Layout
import com.mappo.data.repository.InstalledAppsRepository.InstalledApp
import com.mappo.data.settings.MoveCommitGesture
import com.mappo.data.settings.MinVisibleTiles
import com.mappo.data.settings.TileReveal
import com.mappo.data.settings.TextSize
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Radar
import com.composables.icons.lucide.Type
import com.mappo.ui.compact.scaledLayout
import com.mappo.ui.minput.MinputIconButton
import com.mappo.ui.minput.MinputElevatedContainer
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputModal
import com.mappo.ui.minput.MinputPanelDividerContentGap
import com.mappo.ui.minput.MinputPanelDividerInset
import com.mappo.ui.minput.MinputPanelHeaderHeight
import com.mappo.ui.minput.MinputPanelTitleInset
import com.mappo.ui.minput.MinputButton
import com.mappo.ui.minput.MinputPillContentPadding
import com.mappo.ui.minput.MinputPillDropdown
import com.mappo.ui.minput.MinputPillIconSize
import com.mappo.ui.minput.MinputTextField
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle
import kotlinx.collections.immutable.ImmutableList

/**
 * The full-screen overlay panels of the controls screen. Only OPTIONS remains (physical
 * Start / the top bar's Layout settings pill): the former layout-selection panel — layout
 * selection — was upgraded into the browse chain of proper routes (2026-08-14). Rescoped
 * 2026-08-20: the GLOBAL app options (power, text size, the destination rows) moved to
 * the wordmark drawer ([com.mappo.ui.screen.home.MappoDrawerContent]); this panel is now
 * "Layout settings" — the entries scoped to the layout being edited.
 */
internal enum class RemapPanel { OPTIONS }

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
 * The options panel: a full-bleed [MinputModal] over the whole screen (top bar included) —
 * a separate raised interface above the controls view, inset by [EditorMargin] so the
 * screen edges peek through. Converted from the morph treatment 2026-08-08: the
 * pill-to-panel rect-lerp read awkwardly (tiny cornered summons becoming large centered
 * surfaces), so the panel shares the modal's standard fade + settle. The group editor
 * keeps the morph — it genuinely lives inside the screen and collapses back into its
 * home box.
 *
 * The caller (RemapControlsScreen) hosts this as the last child of its root Box with
 * `Modifier.matchParentSize()` and owns [openPanel] (user intent, rememberSaveable there).
 */
@Composable
internal fun RemapPanelOverlay(
    openPanel: RemapPanel?,
    onClose: () -> Unit,
    optionsEntries: List<RemapOptionEntry>,
    modifier: Modifier = Modifier,
) {
    // Focus seat for the panel — MinputModal owns the seat/trap/recovery; the requester
    // attaches to the surface's Close button via [PanelHeader].
    val optionsCloseFocus = remember { FocusRequester() }

    Box(modifier) {
        MinputModal(
            open = openPanel == RemapPanel.OPTIONS,
            onDismiss = onClose,
            margin = EditorMargin,
            focusSeat = optionsCloseFocus,
            testTag = "remap-panel:OPTIONS",
            modifier = Modifier.matchParentSize(),
        ) {
            LayoutSettingsPanelContent(
                entries = optionsEntries,
                onClose = onClose,
                closeFocusRequester = optionsCloseFocus,
            )
        }
    }
}

/** Sort-direction toggle: the icon shows the CURRENT direction; tapping flips it. Shared
 *  by the applications / layouts filter rows. */
@Composable
internal fun SortDirectionButton(ascending: Boolean, onToggle: () -> Unit) {
    MinputIconButton(
        icon = if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
        contentDescription = if (ascending) "Sorted ascending" else "Sorted descending",
        onClick = onToggle,
    )
}

/**
 * The Layout settings panel: the entries scoped to the layout being edited (Edit overlay
 * today; more per-layout settings land here as they exist). The global rows this panel
 * used to carry live in the wordmark drawer now.
 */
@Composable
private fun LayoutSettingsPanelContent(
    entries: List<RemapOptionEntry>,
    onClose: () -> Unit,
    closeFocusRequester: FocusRequester? = null,
) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader(
            glyphRes = R.drawable.xbox_button_menu,
            title = "Layout settings",
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
                // layouts panel's search/sort row.
                top = MinputPanelDividerContentGap,
                bottom = 2.dp,
            ),
        ) {
            items(entries, key = { it.id }) { entry ->
                OptionEntryRow(entry = entry, onClose = onClose)
            }
        }
    }
}

/** One destination row shared by the Layout settings panel and the wordmark drawer. */
@Composable
internal fun OptionEntryRow(entry: RemapOptionEntry, onClose: () -> Unit) {
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

/**
 * Sticky panel header, matching the group editor's header anatomy: leading identity (a
 * physical button's prompt glyph — rendered UNTINTED, fixed hardware colors — or a tinted
 * vector [icon]), overline title, an optional [center] control filling the flexible middle
 * (it owns the weight), then optional utility [actions] adjacent to Close at one rhythm.
 * Proper VIEWS use the shared [RemapTopBar] instead (2026-08-15) — this header is for the
 * panel/modal surfaces stacked over them.
 */
@Composable
internal fun PanelHeader(
    title: String,
    onClose: (() -> Unit)? = null,
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
        if (onClose != null) {
            MinputIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Close",
                onClick = onClose,
                // Controller-focus seat for the surface (the group-editor pattern: focus
                // lands on a real button, never a container).
                modifier = if (closeFocusRequester != null) {
                    Modifier.focusRequester(closeFocusRequester)
                } else Modifier,
            )
        }
    }
}

/**
 * The header/content divider shared by every panel surface — the group editor's treatment,
 * at the family's standard inset.
 */
@Composable
internal fun PanelDivider() {
    HorizontalDivider(Modifier.padding(horizontal = MinputPanelDividerInset))
}

/**
 * One compact panel row. Hand-rolled (not ListItem) to match the group editor's sub-Material
 * row dimensionality — a deliberate, annotated deviation shared by the whole remap chrome.
 * [active] raises the row to the topmost plane (the selection treatment on this surface).
 */
@Composable
internal fun PanelRow(
    onClick: () -> Unit,
    active: Boolean = false,
    content: @Composable RowScope.() -> Unit,
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
 * lockstep via the caller. Internal: lives in the wordmark drawer (2026-08-20).
 */
@Composable
internal fun PowerRow(powerOn: Boolean, onPowerChange: (Boolean) -> Unit) {
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
 * How a controller move is confirmed: let go of the activate button, or press it again. See
 * [MoveCommitGesture] — it settles what the three ways of PICKING a tile up used to each decide
 * for themselves.
 *
 * Here in the wordmark drawer because that is where the global options live until a real options
 * screen exists; it is not layout-scoped, so it does not belong in Layout settings.
 */
@Composable
internal fun MoveCommitRow(current: MoveCommitGesture, onPick: (MoveCommitGesture) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelPowerRowHeight)
            .padding(horizontal = MinputPillContentPadding),
    ) {
        Icon(
            Icons.Filled.OpenWith,
            contentDescription = null,
            modifier = Modifier.size(PanelRowIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(MinputGlyphLabelGap))
        Text(
            text = "Place a moved tile",
            style = minputMiniTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        MinputPillDropdown(
            current = current,
            options = MoveCommitGesture.entries,
            optionLabel = { it.label },
            onPick = onPick,
            onClickLabel = "Change how a moved tile is placed",
        )
    }
}

/**
 * How much of the controls view turns into tiles when a group is opened — every group, or just
 * the one being worked on. See [TileReveal]; it is an experiment on top of the edit-mode
 * experiment, kept as a setting precisely so the two can be compared on the device.
 *
 * Global rather than layout-scoped, so it sits in the wordmark drawer beside its neighbours.
 */
@Composable
internal fun TileRevealRow(current: TileReveal, onPick: (TileReveal) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelPowerRowHeight)
            .padding(horizontal = MinputPillContentPadding),
    ) {
        Icon(
            Icons.Filled.GridView,
            contentDescription = null,
            modifier = Modifier.size(PanelRowIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(MinputGlyphLabelGap))
        Text(
            text = "Show tiles for",
            style = minputMiniTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        MinputPillDropdown(
            current = current,
            options = TileReveal.entries,
            optionLabel = { it.label },
            onPick = onPick,
            onClickLabel = "Change which groups show tiles",
        )
    }
}

/**
 * **How far opening an input group pans the view, in tiles** — the floor under the "frame the
 * group" rule, so a group already on screen still answers being opened with some camera
 * movement. See [MinVisibleTiles].
 *
 * Global rather than layout-scoped, so it sits in the wordmark drawer beside its neighbours.
 */
@Composable
internal fun MinVisibleTilesRow(current: MinVisibleTiles, onPick: (MinVisibleTiles) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelPowerRowHeight)
            .padding(horizontal = MinputPillContentPadding),
    ) {
        Icon(
            Icons.Filled.Straighten,
            contentDescription = null,
            modifier = Modifier.size(PanelRowIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(MinputGlyphLabelGap))
        Text(
            text = "Minimum pan on open",
            style = minputMiniTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        MinputPillDropdown(
            current = current,
            options = MinVisibleTiles.entries,
            optionLabel = { it.label },
            onPick = onPick,
            onClickLabel = "Change how far opening a group pans the view",
        )
    }
}

/**
 * Text-size row: the app-level font scale, enforced independently of the OS setting (the
 * UI is tuned against the OS "Small" scale — see [TextSize]). Same anatomy as the settings
 * rows around it: glyph + label left, minput pill dropdown right.
 */
@Composable
internal fun TextSizeRow(current: TextSize, onPick: (TextSize) -> Unit) {
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
 * Font-debug overlay toggle (dev tooling): shows/hides the floating font picker
 * ([com.mappo.ui.component.FontDebugOverlay]) in the window corner for live font
 * testing. Same switch treatment as [PowerRow] — the drawer's two switch rows must read
 * as siblings.
 */
@Composable
internal fun FontDebugRow(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelPowerRowHeight)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onEnabledChange(!enabled) }
            .padding(horizontal = MinputPillContentPadding),
    ) {
        Icon(
            Lucide.Type,
            contentDescription = null,
            modifier = Modifier.size(PanelRowIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(MinputGlyphLabelGap))
        Text(
            text = "Font debug overlay",
            style = minputMiniTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        // Strip the 48dp interactive halo + scale down so the switch fits the compact row.
        // The row owns the tap target; the switch itself stays interactive for thumb drags.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.scaledLayout(0.8f),
            )
        }
    }
}

/**
 * Auto-detect toggle: when on, foregrounding an application switches Mappo to the layout
 * bound to it ([com.mappo.service.autoswitch.ApplicationAutoSwitcher]).
 *
 * Rehomed here 2026-08-30 (Dylan) from the remap controls top bar, where it rode a pod of
 * its own beside Edit overlay. It never belonged there: the bar's other controls all act on
 * the layout ON SCREEN, while this is one app-wide switch — and activating a layout manually
 * turns it off from anywhere, so a control scoped to one view read as narrower than it is.
 * Same switch treatment as [PowerRow] / [FontDebugRow]; the drawer's switch rows must read
 * as siblings.
 */
@Composable
internal fun AutoDetectRow(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelPowerRowHeight)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onEnabledChange(!enabled) }
            .padding(horizontal = MinputPillContentPadding),
    ) {
        Icon(
            // Radar — the app-watching sweep. (The bar form carried no glyph at all, just an
            // "AUTO" overline; a settings row needs one to sit with its siblings.)
            Lucide.Radar,
            contentDescription = null,
            modifier = Modifier.size(PanelRowIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(MinputGlyphLabelGap))
        Text(
            text = "Auto-detect layout",
            style = minputMiniTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        // Strip the 48dp interactive halo + scale down so the switch fits the compact row.
        // The row owns the tap target; the switch itself stays interactive for thumb drags.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.scaledLayout(0.8f),
            )
        }
    }
}

/**
 * The new-layout form (2026-08-26, ex new-layout): a name plus the ONE application the
 * layout belongs to — displayed, not picked: a layout is only ever associated with one
 * application (the multi-app "associated apps" picker retired with the layout concept),
 * and creation always happens inside an application context (the drawers' cards, the
 * no-layout state). Form state is deliberately un-hoisted — the modal's content leaves
 * composition on close, so every summon starts a fresh form.
 *
 * [applicationLabel] null = no application context: the layout is created unassigned.
 */
@Composable
internal fun AddLayoutModalContent(
    applicationLabel: String?,
    onCreate: (name: String) -> Unit,
    onClose: () -> Unit,
    // Attached to the Close button; the hosting MinputModal owns the seat/recovery.
    closeFocusRequester: FocusRequester? = null,
) {
    var name by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        PanelHeader(
            title = "New layout",
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
                placeholder = "Layout name",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Application",
                    style = minputMiniTextStyle(),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(MinputGlyphLabelGap))
                Text(
                    text = applicationLabel ?: "None",
                    style = minputMiniTextStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(PanelContentPadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        ) {
            MinputButton(text = "Cancel", onClick = onClose)
            MinputButton(
                text = "Create",
                onClick = { onCreate(name.trim()); onClose() },
                enabled = name.isNotBlank(),
                filled = true,
                elevated = true,
            )
        }
    }
}

/**
 * Options that apply across ALL layouts. Deliberately empty for now — the surface and its
 * summon exist so content can land here without another chrome pass.
 */
@Composable
internal fun LayoutOptionsModalContent(
    onClose: () -> Unit,
    // Attached to the Close button; the hosting MinputModal owns the seat/recovery.
    closeFocusRequester: FocusRequester? = null,
) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader(
            title = "Layout options",
            icon = Icons.Filled.Tune,
            onClose = onClose,
            closeFocusRequester = closeFocusRequester,
        )
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(PanelContentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Options that apply to all layouts will live here",
                style = minputMiniTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Horizontal content inset of the panel's header + list (also the divider inset). */
internal val PanelContentPadding = 8.dp

/** Compact list-row height (between the editor's 38dp rows and the 32dp tabs). */
internal val PanelRowHeight = 32.dp

/** The power row runs slightly taller so the scaled switch keeps breathing room. */
private val PanelPowerRowHeight = 36.dp

/** Leading glyph edge inside panel rows. */
internal val PanelRowIconSize = 16.dp

/** Target height of the new-layout modal: header + name field + apps row + footer. */
internal val AddLayoutModalHeight = 172.dp

/** Target height of the (for-now empty) all-layouts options modal. */
internal val LayoutOptionsModalHeight = 120.dp
