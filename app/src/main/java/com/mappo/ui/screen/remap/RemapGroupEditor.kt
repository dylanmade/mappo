package com.mappo.ui.screen.remap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mappo.R
import com.mappo.data.model.steam.ActionLayerGraph
import com.mappo.data.model.steam.ActionSetGraph
import com.mappo.data.model.steam.ActivatorGraph
import com.mappo.data.model.steam.ActivatorType
import com.mappo.data.model.steam.BindingMode
import com.mappo.data.model.steam.BindingOutput
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.data.model.steam.displayNameFor
import com.mappo.data.model.steam.InputSource
import com.mappo.service.input.modes.SourceModeCatalog
import com.mappo.ui.component.MoveModeState
import com.mappo.ui.component.moveModeCell
import com.mappo.ui.component.moveModeLongPressSource
import com.mappo.ui.component.rememberMoveModeState
import com.mappo.ui.glyph.InputGlyphs
import com.mappo.ui.screen.displayLabel as activatorDisplayLabel
import com.mappo.ui.screen.remap.settings.SourceModeSettingsSchema
import com.mappo.ui.theme.LocalMappoExtraColors
import com.mappo.ui.theme.PressTypeColors
import com.mappo.ui.minput.MinputAction
import com.mappo.ui.minput.MinputActionMenu
import com.mappo.ui.minput.MinputDropdownMenu
import com.mappo.ui.minput.MinputMenuPlacement
import com.mappo.ui.minput.MinputElevatedContainer
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputIconButton
import com.mappo.ui.minput.MinputOverflowScroll
import com.mappo.ui.minput.MinputPanelDividerInset
import com.mappo.ui.minput.MinputPanelHeaderHeight
import com.mappo.ui.minput.MinputPillIconSize
import com.mappo.ui.minput.MinputScrollbar
import com.mappo.ui.minput.MinputScrollbarThickness
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputIndication
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * The expanded ("advanced") in-place editor a group box grows into.
 *
 * **Rebuilt 2026-09-11 as a TABLE.** The old form was a vertical list of command rows that grew
 * downward as the user added commands, each row carrying its own press-type pill, output button,
 * label field, cog and kebab. The table replaces it: one row per physical input, one COLUMN per
 * press type, and a cell at every intersection. Reading "what does Y do when I hold it" is now a
 * lookup instead of a scan, and the grid is fixed-size — it can't grow unboundedly, which is what
 * made the list form hard to design the basic views against.
 *
 * The trade, accepted deliberately: a cell holds at most ONE command, so the old "several
 * commands of the same press type on one input" affordance is gone from the UI. The schema still
 * allows it (see the cell-ops block in `ControllerConfigRepository`); the table just doesn't
 * surface it.
 *
 * Anatomy: sticky header (group identity · flow arrow · mode pill · cog/kebab/close) over an
 * inset divider, then the table — a frozen glyph column and a horizontally scrolling body
 * carrying the press-type header row and every cell.
 *
 * Two changes on 2026-09-17, both making a card match the basic-view box it grows out of:
 * overflow is cued by [MinputOverflowScroll]'s edge fades + chevrons rather than scrollbars,
 * and a LEFT-flank group's table is MIRRORED — glyph column at the card's right edge, press
 * columns running outward to the left (see [RemapSimpleGroup.editorMirrored]). The header bar
 * above it is not mirrored. A column's INDEX still counts outward from the glyph either way,
 * so everything keyed on it (scroll offsets, d-pad stepping) is untouched by mirroring; only
 * the rendered order and the sign of a column step flip.
 *
 * Base-set view edits inline; layer view resolves override→base, renders read-only, and routes
 * cell taps to the full-screen editor (which materializes the override).
 */
internal class RemapGroupEditorCallbacks(
    val onSetBindingGroupMode: (bindingGroupId: Long, mode: BindingMode) -> Unit,
    val onOpenModeSettings: (bindingGroupId: Long, source: InputSource) -> Unit,
    val onEditCommand: (bindingId: Long, current: BindingOutput, title: String) -> Unit,
    val onOpenInputEditor: (inputSource: InputSource, groupInputKey: String, label: String) -> Unit,
    val onClearOverride: (inputSource: InputSource, groupInputKey: String) -> Unit,
    /** The label editor's whole commit: the user label (blank clears it) and how the command
     *  prints — its device glyph and the device initials on its name. */
    val onSetLabel: (
        bindingId: Long,
        label: String,
        showDeviceIcon: Boolean,
        showDeviceInitials: Boolean,
    ) -> Unit,
    val onResetGroup: (bindingGroupId: Long) -> Unit,
    val onConfigure: (activatorId: Long, title: String) -> Unit,
    // ── Cell ops (see ControllerConfigRepository's advanced-table block) ──
    /** Ensure the cell exists, then open the command picker against it. Drives both the empty
     *  tile's "New" and the defined tile's "Edit" — the difference is only whether the ensure
     *  step has anything to do. */
    val onAssignCell: (
        bindingGroupId: Long,
        inputKey: String,
        type: ActivatorType,
        current: BindingOutput,
        title: String,
    ) -> Unit,
    val onClearCell: (bindingGroupId: Long, inputKey: String, type: ActivatorType) -> Unit,
    val onCopyCell: (bindingGroupId: Long, inputKey: String, type: ActivatorType) -> Unit,
    val onPasteCell: (bindingGroupId: Long, inputKey: String, type: ActivatorType) -> Unit,
    val onMoveCell: (
        bindingGroupId: Long,
        fromKey: String, fromType: ActivatorType,
        toKey: String, toType: ActivatorType,
    ) -> Unit,
    /** Whether anything has been copied this session — greys Paste rather than hiding it. */
    val clipboardOccupied: Boolean,
    // ── Group-level ops (2026-09-16): carrying commands between groups, whole-group copy/reset ──
    /** [onMoveCell] across binding groups — a command carried from one input group to another. */
    val onMoveCellAcross: (
        fromBindingGroupId: Long, fromKey: String, fromType: ActivatorType,
        toBindingGroupId: Long, toKey: String, toType: ActivatorType,
    ) -> Unit = { _, _, _, _, _, _ -> },
    /** The group menu's clipboard, or null when nothing has been copied. */
    val groupClipboard: com.mappo.data.repository.ControllerConfigRepository.InputGroupSnapshot? = null,
    /** Copy a group: its rows as (bindingGroupId, sub-input key) in display order, the binding
     *  group whose mode + settings it carries, and which halves to take. */
    val onCopyGroup: (rows: List<Pair<Long, String>>, settingsGroupId: Long?, inputs: Boolean, settings: Boolean) -> Unit =
        { _, _, _, _ -> },
    val onPasteGroup: (rows: List<Pair<Long, String>>, settingsGroupId: Long?) -> Unit = { _, _ -> },
    /** Reset each binding group to its fresh-layout seed. */
    val onResetGroups: (bindingGroupIds: List<Long>) -> Unit = {},
)

/**
 * The table's press-type columns, in the user's specified order. Note this is NOT
 * [pressTypeOrder] (the canonical Steam render order) — the table puts Chord before the
 * Down/Up edge triggers because the edge pair reads as a tail-end special case.
 *
 * `SOFT_PRESS` is absent by design: it's a sub-input (the trigger's "soft_press" row), not an
 * activator the user picks. See `feedback_soft_press_unified_to_soft_pull`.
 */
internal val pressTypeColumns = listOf(
    ActivatorType.FULL_PRESS,
    ActivatorType.LONG_PRESS,
    ActivatorType.DOUBLE_PRESS,
    ActivatorType.CHORDED_PRESS,
    ActivatorType.START_PRESS,
    ActivatorType.RELEASE_PRESS,
)

/**
 * The three column colors for a press type — header, tile tint, empty-cell "+".
 *
 * **To retune the palette, edit `PressTypePalette` in `ui/theme/Theme.kt`**; nothing here
 * derives or blends on top of those values, so what's written there is what renders.
 * `FULL_PRESS` maps to the neutral `press` entry (transparent tile = the ordinary surface).
 */
@Composable
internal fun ActivatorType.columnColors(): PressTypeColors {
    val palette = LocalMappoExtraColors.current.pressTypes
    return when (this) {
        ActivatorType.FULL_PRESS, ActivatorType.SOFT_PRESS -> palette.press
        ActivatorType.LONG_PRESS -> palette.long
        ActivatorType.DOUBLE_PRESS -> palette.double
        ActivatorType.CHORDED_PRESS -> palette.chord
        ActivatorType.START_PRESS -> palette.down
        ActivatorType.RELEASE_PRESS -> palette.up
    }
}

/**
 * Which cell a table coordinate names. The move-mode key type, and the identity the tile menus
 * act on.
 *
 * It carries its GROUP (2026-09-17) because the zoomed scene shows every group's table at once
 * and one move state spans them all: a command lifted from the d-pad can be carried to the face
 * buttons, so a cell's identity has to say which table it belongs to.
 *
 * And it names its row by the WHOLE [SimpleRowSpec], not by the sub-input key: a key is unique
 * only within one binding group, and a group's table can span two. The utility group is the
 * case that proved it — Start and Select are both a "click", so keying by the string alone gave
 * the two rows ONE identity, and with it one test tag, one focus handle and a d-pad step that
 * could never reach the second row.
 */
internal data class CellKey(
    val group: RemapSimpleGroup,
    val row: SimpleRowSpec,
    val type: ActivatorType,
) {
    val inputKey: String get() = row.subInputKey
    val source: InputSource get() = row.source
}

@Composable
internal fun RemapGroupEditor(
    group: RemapSimpleGroup,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    callbacks: RemapGroupEditorCallbacks,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    // Whether this editor is the one being USED. The zoomed scene (2026-09-17) holds every
    // group's editor at once, and only the group the camera is on wears the interactive header:
    // seven live mode pills, cogs, kebabs and Close buttons would be seven of everything for a
    // screen reader, and the chrome of a card you are only seeing the edge of is noise. A
    // resting card keeps its identity and its mode, as text, and its table stays focusable —
    // that is how the d-pad walks into it and makes it the live one.
    chrome: Boolean = true,
    // ── Moves (2026-09-17) ──
    // A host showing SEVERAL editors at once (the zoomed scene) owns the move: one state
    // spanning every table, stepping that can cross from one to the next, a commit that knows
    // both ends' binding groups, and focus handles it can reach any cell through. On its own,
    // an editor keeps the single-table behaviour these defaults describe.
    moveState: MoveModeState<CellKey> = rememberMoveModeState(),
    stepTarget: (CellKey, Int, Int) -> CellKey? = ::stepCellWithinGroup,
    onMoveCommitted: ((from: CellKey, to: CellKey) -> Unit)? = null,
    focusHandle: ((CellKey) -> FocusRequester)? = null,
    // Landing spot for controller focus when the editor opens (and after a tap wipes focus):
    // the TOP-LEFT tile — the first input's Press cell — falling back to the always-present
    // Close button when the table can't take focus (layer view). Directional focus can't step
    // INTO an overlay from a focused container that spatially contains everything, so the
    // simple view requests focus here explicitly.
    focusRequester: FocusRequester? = null,
) {
    // The header's mode pill edits the group's PRIMARY source (first row's source). Multi-source
    // groups (shoulder = trigger + bumper, utility = Start + Select) keep their secondary
    // sources' modes reachable through the cells' full-screen editor for now.
    val primarySource = group.rows.first().source
    val primaryGroup = viewingLayer?.presetFor(primarySource)?.group?.group
        ?: viewingSet?.presetFor(primarySource)?.group?.group
    val validModes = SourceModeCatalog.modesValidFor(primarySource)
    val modeName = primaryGroup?.mode?.displayNameFor(primarySource) ?: "mode"
    val editable = viewingLayer == null
    var headerMore by remember { mutableStateOf(false) }

    // Escape hatch for d-pad UP out of the table's top row: the table is a focus group whose
    // bounds contain every tile, so an unresolved UP search would pick the group itself as
    // "above" and re-enter at its first focusable, trapping focus. Top-row tiles route UP
    // explicitly, set DIRECTLY on each tile's own node — an ancestor-cascaded route proved to
    // win over per-child overrides, collapsing every route to one target.
    // A focus handle per cell, so focus can FOLLOW a committed move to the destination.
    // Leaving it on the origin (which now holds the swapped-in command, or nothing at all)
    // read as the cursor snapping backwards.
    val ownFocusHandles = remember(group) { mutableStateMapOf<CellKey, FocusRequester>() }
    val cellFocusHandle = focusHandle
        ?: { key -> ownFocusHandles.getOrPut(key) { FocusRequester() } }
    val commitMove = onMoveCommitted ?: { from: CellKey, to: CellKey ->
        val bindingGroupId = viewingSet?.presetFor(from.source)?.group?.group?.id
        if (bindingGroupId != null) {
            callbacks.onMoveCell(bindingGroupId, from.inputKey, from.type, to.inputKey, to.type)
        }
    }
    val headerKebabFocus = remember { FocusRequester() }
    val headerModePillFocus = remember { FocusRequester() }
    val headerCloseFocus = remember { FocusRequester() }
    val modePillFocusable = primaryGroup != null && validModes.isNotEmpty() &&
        editable && validModes.size > 1
    val headerCogFocusable = primaryGroup != null &&
        SourceModeSettingsSchema.hasSettings(primarySource, primaryGroup.mode)
    val tableUpTarget = if (modePillFocusable) headerModePillFocus else headerKebabFocus

    Column(modifier = modifier) {
        // ── Sticky header ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = EditorHeaderHeight)
                // Same horizontal inset as the table — the header's chrome must sit flush
                // with the table's columns.
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Group identity + mode, as ONE control (Dylan, 2026-09-19): the card's caption
            // used to read "BUTTON PAD > MODE: BUTTON PAD", naming the group twice with a flow
            // arrow between, and then kept the hardware glyph outside the button it belongs to.
            // The glyph is the group's identity AND the button's leading icon; the caption says
            // the rest.
            ModeDropdownLabel(
                source = primarySource,
                currentMode = primaryGroup?.mode.takeIf { validModes.isNotEmpty() },
                validModes = validModes,
                identity = group.headerLabel(),
                // A resting card states its mode; only the live one lets you change it.
                enabled = chrome && editable && primaryGroup != null && validModes.size > 1,
                onPick = { mode -> primaryGroup?.let { callbacks.onSetBindingGroupMode(it.id, mode) } },
                modifier = Modifier.focusRequester(headerModePillFocus),
            )
            Spacer(Modifier.weight(1f))
            if (chrome) {
                MinputIconButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = "Configure $modeName",
                    onClick = { primaryGroup?.let { callbacks.onOpenModeSettings(it.id, primarySource) } },
                    enabled = headerCogFocusable,
                )
                Box {
                    RowKebab(
                        onClick = { headerMore = true },
                        contentDescription = "Group options",
                        modifier = Modifier.focusRequester(headerKebabFocus),
                    )
                    DropdownMenu(expanded = headerMore, onDismissRequest = { headerMore = false }) {
                        RichMenuItem(
                            title = "Import $modeName",
                            helper = "Bring in a mode and inputs from another layout.",
                            icon = Icons.Filled.Download,
                            // Future: layout import. Inert while the acquisition flow lands.
                            onClick = { headerMore = false },
                        )
                        RichMenuItem(
                            title = "Reset $modeName",
                            helper = "Return this group to its defaults.",
                            icon = Icons.Filled.RestartAlt,
                            enabled = editable && primaryGroup != null,
                            onClick = {
                                headerMore = false
                                primaryGroup?.let { callbacks.onResetGroup(it.id) }
                            },
                        )
                    }
                }
                // No spacer: cog·kebab·close sit adjacent at one rhythm.
                MinputIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close",
                    onClick = onClose,
                    modifier = Modifier
                        .focusRequester(headerCloseFocus)
                        .then(
                            if (focusRequester != null && !editable) {
                                Modifier.focusRequester(focusRequester)
                            } else Modifier,
                        ),
                )
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = MinputPanelDividerInset))

        AdvancedTable(
            group = group,
            viewingSet = viewingSet,
            viewingLayer = viewingLayer,
            config = config,
            callbacks = callbacks,
            editable = editable,
            // A resting card has no header controls to route UP into, and an unattached
            // requester would throw the moment focus searched that way.
            upTarget = if (chrome) tableUpTarget else FocusRequester.Default,
            focusRequester = focusRequester.takeIf { editable },
            moveState = moveState,
            stepTarget = stepTarget,
            onMoveCommitted = commitMove,
            focusHandle = cellFocusHandle,
        )
    }
}

/** What the label editor is editing: the command's label, the name it falls back to, and how
 *  it prints. */
private data class LabelEdit(
    val bindingId: Long,
    val label: String,
    val outputs: List<BindingOutput>,
    val showDeviceIcon: Boolean,
    val showDeviceInitials: Boolean,
)

/**
 * The table proper.
 *
 * Two side-by-side columns inside ONE vertical scroller: a frozen glyph column, then the body
 * in a horizontal scroller. Freezing the glyphs is the whole point of splitting them out — a
 * user scrolled to the Up column must still be able to see which button the row belongs to.
 * Alignment between the two halves is structural, not synchronized: both use the same fixed
 * [TileHeight] and [TileRowGap], so they can't drift.
 */
@Composable
private fun AdvancedTable(
    group: RemapSimpleGroup,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    callbacks: RemapGroupEditorCallbacks,
    editable: Boolean,
    upTarget: FocusRequester,
    focusRequester: FocusRequester?,
    // Moves are SCENE-wide (2026-09-17): the state, the stepping, the commit and the focus
    // handles all belong to whoever hosts the tables, because a command can be carried out of
    // this one. Standalone hosts get the single-table behaviour from the defaults.
    moveState: MoveModeState<CellKey>,
    stepTarget: (CellKey, Int, Int) -> CellKey?,
    onMoveCommitted: (CellKey, CellKey) -> Unit,
    focusHandle: (CellKey) -> FocusRequester,
) {
    // The flanking groups on the LEFT of the controller read as the right flank's reflection
    // (Dylan, 2026-09-17), exactly as their basic-view boxes do: input glyph pinned to the
    // card's right edge, the press columns running outward to the left from it. Only the
    // ORDER and the side change — the header bar above stays as it is.
    val mirrored = group.editorMirrored()
    val columns = remember(mirrored) { if (mirrored) pressTypeColumns.reversed() else pressTypeColumns }
    // Which way a column step moves a tile on screen. Column INDEX always counts outward from
    // the glyph, so everything keyed on the index (scroll offsets, stepping) is unchanged by
    // mirroring; only what the user SEES flips.
    val columnDirection = if (mirrored) -1 else 1
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    // The grid has gutters between rows (and between columns), which belong to no cell. Without
    // a tolerance a finger crossing one resolves to nothing and the drop target snaps back to
    // the origin — visible as the landing marker flickering home mid-drag. Sized to the widest
    // gutter so any point inside one resolves to whichever cell it's nearest, and generously
    // enough to keep working if the gaps grow.
    moveState.hitTolerancePx = with(density) { maxOf(TileRowGap, TileGap).toPx() }
    // What the "Label" verb is editing. The table has no resting label FIELD any more (the cell
    // renders the label as overline text), so the editor dialog is summoned directly rather
    // than by a MinputTextField pill.
    var labelTarget by remember { mutableStateOf<LabelEdit?>(null) }

    // Does a cell actually hold a command? An EMPTY cell is behaviorally empty as far as a
    // move is concerned — it's a slot, not a tile — so it must not slide around during a swap
    // preview. (It did: the "+" glyphs shuffled with everything else, which read as though
    // blank space were being dragged about.)
    fun isDefined(key: CellKey): Boolean {
        val groupInput = viewingLayer?.presetFor(key.source)?.group?.inputByKey(key.inputKey)
            ?: viewingSet?.presetFor(key.source)?.group?.inputByKey(key.inputKey)
        val activator = groupInput?.firstActivatorOfType(key.type) ?: return false
        return activator.bindings.firstOrNull() != null &&
            activator.primaryOutput != BindingOutput.Unbound
    }

    fun cellAt(key: CellKey): Pair<Int, Int>? {
        val r = group.rows.indexOf(key.row).takeIf { it >= 0 } ?: return null
        val c = pressTypeColumns.indexOf(key.type).takeIf { it >= 0 } ?: return null
        return r to c
    }

    fun stepMoveTarget(dRow: Int, dCol: Int) {
        val current = moveState.target ?: return
        // Whichever table holds the drop target does the stepping; the others keep out of it.
        if (current.group != group) return
        val next = stepTarget(current, dRow, dCol) ?: return
        if (next == current) return
        // A tick per cell crossed: with no finger on the screen the haptic is the only
        // confirmation that the drop target actually moved.
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        moveState.moveTargetTo(next)
        // Focus FOLLOWS the drop target. Focus is cell-anchored, so this keeps the ring on
        // the cell being aimed at instead of stranding it on the one the tile was lifted
        // from — and it means the tile the user then activates IS the destination, so the
        // confirm needs no focus change of its own. The handle comes from the host, so a
        // destination in ANOTHER group is reachable the same way.
        runCatching { focusHandle(next).requestFocus() }
    }

    // Resolve the binding group that owns a row's source. Rows in a multi-source group
    // (shoulder = trigger + bumper) resolve independently.
    fun bindingGroupIdFor(spec: SimpleRowSpec): Long? =
        viewingSet?.presetFor(spec.source)?.group?.group?.id

    // The move being previewed. Everything visual (displacement, z-order, vacated slot) reads
    // THESE rather than moveState's fields directly, so there is one place to change if the
    // preview ever needs to outlive the gesture again.
    //
    // It does NOT need to today: `MainViewModel.moveInputCell` applies the move optimistically
    // to the rendered config, so by the frame the preview drops, the cells already hold their
    // new contents. An earlier attempt held the preview across the DB roundtrip instead; that
    // only moved the problem, since the held displacement would then be applied on top of the
    // already-correct data.
    // Scoped to this table: a command carried INTO another group displaces nothing here, and
    // the vacated slot belongs to whichever table the command was lifted from.
    val previewOrigin = moveState.origin?.takeIf { it.group == group }
    val previewTarget = moveState.target?.takeIf { it.group == group }

    // Is the button that lifted the current tile STILL held? Owned here rather than on the tile
    // because a held activate button auto-repeats while focus moves, so the release can arrive
    // at a different tile than the one that was lifted — there is no single tile that can
    // reliably see both ends of the press. The table sees all of it.
    var liftHeld by remember { mutableStateOf(false) }

    fun commitMove(pair: Pair<CellKey, CellKey>?) {
        val (from, to) = pair ?: return
        liftHeld = false
        onMoveCommitted(from, to)
        // No focus handling here, deliberately. On the controller path focus already TRACKS the
        // drop target (see stepMoveTarget), so by the time a move commits it is on the
        // destination — nothing to move, and nothing that could lag a frame behind the data.
        // On the touch path there is no focus ring to maintain, and seating one on a drop would
        // put a controller cursor on screen in the middle of a finger gesture.
    }

    // Viewport of the horizontally scrolling body, in window space — the frame the pointer's
    // position is compared against for edge-scrolling.
    var hViewport by remember { mutableStateOf<Rect?>(null) }


    // Does this cell show the empty-slot "+"? Every cell with no command — plus the slot a
    // lifted tile has vacated, which would otherwise read as a hole in the grid while you
    // carry its tile somewhere else. (When the drop target is occupied, that tile slides in
    // and covers this anyway; the "+" layer sits underneath.)
    fun showsSlot(key: CellKey): Boolean =
        !isDefined(key) || (previewOrigin != null && key == previewOrigin)


    // GREEN marks where the lifted tile will land, BLUE where it was picked up from; green wins
    // when they're the same cell, which is how "put it back where I found it" reads as a real
    // destination rather than an absence of one.
    val extras = LocalMappoExtraColors.current
    fun moveMarkerFor(key: CellKey): Color? = when {
        !moveState.active -> null
        moveState.target == key -> extras.dropZoneValid.copy(alpha = MoveMarkerAlpha)
        previewOrigin == key -> extras.dropZoneOrigin.copy(alpha = MoveMarkerAlpha)
        else -> null
    }

    // Where a tile sits while a move is in flight, as a grid-step offset from its own slot.
    // This is the swap PREVIEW: the lifted tile slides toward the drop target and the tile
    // currently there slides back into the vacated slot, so the exchange is visible before
    // it's committed — and visibly undone the moment the target moves on.
    //
    // Grid steps rather than measured positions because every cell is a fixed size; there is
    // nothing to measure.
    fun displacementFor(key: CellKey): DpOffset {
        val origin = previewOrigin ?: return DpOffset.Zero
        val target = previewTarget ?: return DpOffset.Zero
        if (origin == target) return DpOffset.Zero
        val (originRow, originCol) = cellAt(origin) ?: return DpOffset.Zero
        val (targetRow, targetCol) = cellAt(target) ?: return DpOffset.Zero
        val (row, col) = cellAt(key) ?: return DpOffset.Zero
        // Mirrored tables lay their columns out right-to-left, so a step toward a higher
        // column index moves a tile the other way on screen.
        val stepX = (TileWidth + TileGap) * columnDirection
        val stepY = TileHeight + TileRowGap
        return when (key) {
            // The lifted tile rides to the target. On the POINTER path it follows the finger
            // instead (raw translation in CommandTile), so no grid animation there.
            origin ->
                if (moveState.pointerDriven) DpOffset.Zero
                else DpOffset(stepX * (targetCol - col), stepY * (targetRow - row))
            // The displaced occupant takes the vacated slot — but only if there IS one.
            // Dropping onto empty space is a relocation, not a swap, so nothing comes back
            // the other way.
            target ->
                if (isDefined(target)) DpOffset(stepX * (originCol - col), stepY * (originRow - row))
                else DpOffset.Zero
            else -> DpOffset.Zero
        }
    }

    // Keep the drop target on screen. A controller move walks the target with the d-pad and
    // will happily walk it off the visible columns; there is no free hand to scroll with, so
    // the table follows the target instead.
    LaunchedEffect(moveState.target, moveState.active) {
        val target = moveState.target.takeIf { moveState.active && it?.group == group }
            ?: return@LaunchedEffect
        val (row, col) = cellAt(target) ?: return@LaunchedEffect
        val stepX = with(density) { (TileWidth + TileGap).toPx() }
        val stepY = with(density) { (TileHeight + TileRowGap).toPx() }
        val cellW = with(density) { TileWidth.toPx() }
        val cellH = with(density) { TileHeight.toPx() }
        // The scroller's content starts after the table's own top padding (applied INSIDE
        // verticalScroll) and the column-header row.
        val headerH = with(density) { (TableVerticalPadding + ColumnHeaderHeight + HeaderToRowsGap).toPx() }

        val left = col * stepX
        if (left < hScroll.value) {
            hScroll.animateScrollTo(left.roundToInt())
        } else if (left + cellW > hScroll.value + hScroll.viewportSize) {
            hScroll.animateScrollTo((left + cellW - hScroll.viewportSize).roundToInt())
        }

        val top = headerH + row * stepY
        if (top < vScroll.value) {
            vScroll.animateScrollTo(top.roundToInt())
        } else if (top + cellH > vScroll.value + vScroll.viewportSize) {
            vScroll.animateScrollTo((top + cellH - vScroll.viewportSize).roundToInt())
        }
    }

    // Edge-scroll during a FINGER drag: the target is resolved by hit-testing whatever is
    // under the pointer, so without this a touch user simply cannot reach a column that isn't
    // already on screen. Holding near an edge scrolls, and the hit test re-runs each frame so
    // the target keeps up with the cells moving under a stationary finger.
    LaunchedEffect(moveState.pointerDriven) {
        if (!moveState.pointerDriven) return@LaunchedEffect
        val zone = with(density) { EdgeScrollZone.toPx() }
        val step = with(density) { EdgeScrollStep.toPx() }
        while (isActive && moveState.pointerDriven) {
            withFrameNanos { }
            val viewport = hViewport ?: continue
            val x = moveState.pointerWindow.x
            // Scroll VALUE runs from the glyph outward either way (the mirrored scroller is
            // reversed), so the edge that increases it is the outward one — right normally,
            // left when the table is mirrored.
            val delta = when {
                x > viewport.right - zone -> step * columnDirection
                x < viewport.left + zone -> -step * columnDirection
                else -> 0f
            }
            if (delta != 0f) {
                hScroll.scrollBy(delta)
                moveState.refreshTargetAtPointer()
            }
        }
    }

    // The frozen glyph column ("column zero"). A lambda because a MIRRORED table places it on
    // the other side of the body — see [mirrored].
    val glyphColumn: @Composable () -> Unit = {
        Column {
            // "Input" heads the glyphs the way each press type heads its column — the header row
            // now names every column of the table, its frozen one included (Dylan, 2026-09-18).
            Box(
                modifier = Modifier.width(GlyphColumnWidth).height(ColumnHeaderHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Input".uppercase(),
                    style = minputOverlineTextStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(HeaderToRowsGap))
            Column(verticalArrangement = Arrangement.spacedBy(TileRowGap)) {
                group.rows.forEach { spec ->
                    Box(
                        modifier = Modifier.width(GlyphColumnWidth).height(TileHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        InputGlyphs.SubInputGlyph(
                            source = spec.source,
                            subInputKey = spec.subInputKey,
                            size = TableGlyphSize,
                        )
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // While a CONTROLLER move is in flight the table owns the WHOLE keyboard:
                // arrows walk the drop target, B/Escape cancels, and the activate keys confirm.
                // The activate keys belong here rather than on the focused tile because a held
                // button auto-repeats while focus moves, so a press and its release can land on
                // different tiles — no single tile sees both ends of the gesture. Pointer-driven
                // moves don't take this path; the finger is already saying where to land.
                .onKeyEvent { event ->
                    if (!moveState.active || moveState.pointerDriven) return@onKeyEvent false
                    // The target may have been carried into another group's table, which then
                    // owns the keys (focus followed it there).
                    if (moveState.target?.group != group) return@onKeyEvent false

                    // Activate FIRST, and on the key's release — which is why this sits above
                    // the key-down filter below. (It didn't, once, and the filter ate every
                    // confirm before this branch could see it.)
                    if (event.key in TileActivateKeys) {
                        if (event.type == KeyEventType.KeyUp) {
                            val wasLiftingPress = liftHeld
                            liftHeld = false
                            // Releasing the button that LIFTED the tile confirms, provided the
                            // target moved while it was held — ordinary drag-and-drop. Released
                            // without having moved, it reads as the user taking their thumb off
                            // a tile they've picked up to look around with, so the move stays
                            // live and a later press confirms. That later press is also how a
                            // tile gets put back down exactly where it came from.
                            val movedWhileHeld = moveState.target != moveState.origin
                            if (!wasLiftingPress || movedWhileHeld) {
                                commitMove(moveState.commit())
                            }
                        }
                        return@onKeyEvent true
                    }

                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent true
                    when (event.key) {
                        // Consuming the arrows is what stops normal focus traversal — focus
                        // tracks the drop target instead (see stepMoveTarget).
                        Key.DirectionUp -> { stepMoveTarget(-1, 0); true }
                        Key.DirectionDown -> { stepMoveTarget(1, 0); true }
                        Key.DirectionLeft -> { stepMoveTarget(0, -1); true }
                        Key.DirectionRight -> { stepMoveTarget(0, 1); true }
                        Key.Back, Key.Escape, Key.ButtonB -> {
                            moveState.cancel()
                            liftHeld = false
                            true
                        }
                        else -> true // swallow the rest so focus can't wander mid-move
                    }
                },
        ) {
            MinputOverflowScroll(
                state = vScroll,
                orientation = Orientation.Vertical,
                // On the scrollable node, not the container — test scroll-to-node and
                // accessibility scroll actions both need the semantics to sit where the
                // scroll modifier is.
                scrollModifier = Modifier.testTag(editorTableTestTag(group)),
                modifier = Modifier.fillMaxSize(),
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = TableVerticalPadding),
            ) {
                if (!mirrored) {
                    glyphColumn()
                    Spacer(Modifier.width(TileGap))
                }

                // ── Scrolling body: header row + cells ────────────────────
                MinputOverflowScroll(
                    state = hScroll,
                    orientation = Orientation.Horizontal,
                    // A mirrored table reads outward from a glyph pinned to its right edge, so
                    // its resting position is the scroller's far end — same bargain the basic
                    // view's mirrored rows strike.
                    reverseScrolling = mirrored,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .onGloballyPositioned { hViewport = it.boundsInWindow() },
                ) {
                    // LAYER 0 — the empty-slot "+" glyphs, drawn beneath EVERY tile.
                    //
                    // They live in their own layer rather than inside the empty cells because
                    // z-order between tiles is per-Row (zIndex only orders siblings), so a
                    // tile sliding into another row would draw UNDER that row's cells — and a
                    // "+" would sit on top of a command. A slot is background; it can never be
                    // above a tile now, by construction.
                    Column {
                        Spacer(Modifier.height(ColumnHeaderHeight + HeaderToRowsGap))
                        Column(verticalArrangement = Arrangement.spacedBy(TileRowGap)) {
                        group.rows.forEach { spec ->
                            Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                                columns.forEach { type ->
                                    val key = CellKey(group, spec, type)
                                    // Move markers live HERE, in the background layer, not on
                                    // the cell: z-order between cells is per-Row, and the
                                    // lifted tile's row outranks every other, so a marker
                                    // drawn on the origin CELL sat above the tile sliding into
                                    // it. Down here nothing can get underneath a tile.
                                    val marker = moveMarkerFor(key)
                                    Box(
                                        modifier = Modifier
                                            .width(TileWidth)
                                            .height(TileHeight)
                                            .then(
                                                if (marker != null) {
                                                    Modifier
                                                        .clip(RoundedCornerShape(TileCorner))
                                                        .background(marker)
                                                } else Modifier,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (showsSlot(key)) {
                                            Icon(
                                                Icons.Filled.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(EmptyTilePlusSize),
                                                // Alpha rides in the palette color itself —
                                                // no extra .alpha() here, or the value in
                                                // Theme.kt would stop being what renders.
                                                tint = type.columnColors().plus,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }

                    // LAYER 1 — header + the tiles themselves.
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                            columns.forEach { type -> PressColumnHeader(type) }
                        }
                        // Fixed lead-in, independent of [TileRowGap]: spacing the rows apart
                        // must not also push the header row away from them.
                        Spacer(Modifier.height(HeaderToRowsGap))
                        Column(verticalArrangement = Arrangement.spacedBy(TileRowGap)) {
                        group.rows.forEachIndexed { rowIndex, spec ->
                            // Layer view resolves override→base (ghost semantics): the layer's own
                            // group input wins when it exists, else the base set's shows through.
                            val layerGroupInput = viewingLayer?.presetFor(spec.source)?.group?.inputByKey(spec.subInputKey)
                            val baseGroupInput = viewingSet?.presetFor(spec.source)?.group?.inputByKey(spec.subInputKey)
                            val groupInput = layerGroupInput ?: baseGroupInput
                            val subLabel = RemapSections.labelFor(spec.source, spec.subInputKey)
                            val groupId = bindingGroupIdFor(spec)

                            Row(
                                modifier = Modifier.zIndex(
                                    when (spec) {
                                        previewOrigin?.row -> 10f
                                        previewTarget?.row -> 5f
                                        else -> 0f
                                    },
                                ),
                                horizontalArrangement = Arrangement.spacedBy(TileGap),
                            ) {
                                columns.forEach { type ->
                                    val cellKey = CellKey(group, spec, type)
                                    val activator = groupInput?.firstActivatorOfType(type)
                                    val binding = activator?.bindings?.firstOrNull()
                                    val output = activator?.primaryOutput ?: BindingOutput.Unbound
                                    val defined = binding != null && output != BindingOutput.Unbound
                                    val title = "$subLabel · ${type.activatorDisplayLabel()}"
                                    // How this command prints — the SAME resolution the basic
                                    // view's rows use ([commandDisplay]), so one binding reads
                                    // the same way in both. The label appears only when the user
                                    // typed something other than the command's own name (Dylan,
                                    // 2026-09-19): the editor's AUTO state IS that name, so a
                                    // label repeating it means "auto", not a second line saying
                                    // what the first already says.
                                    val outputs = activator?.outputs.orEmpty()
                                    val display = commandDisplay(binding, outputs, config)

                                    CommandTile(
                                        colors = type.columnColors(),
                                        output = output.takeIf { defined },
                                        label = display.label,
                                        outputText = display.text,
                                        showDeviceIcon = display.glyph != null,
                                        enabled = editable && groupId != null,
                                        cellKey = cellKey,
                                        moveState = moveState,
                                        displacement = displacementFor(cellKey),
                                        previewOrigin = previewOrigin,
                                        onCommitMove = { commitMove(it) },
                                        onControllerLift = { liftHeld = true },
                                        actions = {
                                            if (!editable) {
                                                // Layer view is read-only here: editing routes to
                                                // the full-screen editor (which materializes the
                                                // override onto the layer), and an input the layer
                                                // actually overrides can be handed back to base.
                                                layerTileActions(
                                                    overridden = layerGroupInput != null,
                                                    onEdit = {
                                                        callbacks.onOpenInputEditor(spec.source, spec.subInputKey, subLabel)
                                                    },
                                                    onClearOverride = {
                                                        callbacks.onClearOverride(spec.source, spec.subInputKey)
                                                    },
                                                )
                                            } else {
                                                tileActions(
                                                    defined = defined,
                                                    clipboardOccupied = callbacks.clipboardOccupied,
                                                    onEdit = {
                                                        if (groupId != null) {
                                                            callbacks.onAssignCell(groupId, spec.subInputKey, type, output, title)
                                                        }
                                                    },
                                                    onLabel = {
                                                        binding?.let {
                                                            labelTarget = LabelEdit(
                                                                bindingId = it.id,
                                                                label = it.label.orEmpty(),
                                                                outputs = outputs,
                                                                showDeviceIcon = it.showDeviceIcon,
                                                                showDeviceInitials = it.showDeviceInitials,
                                                            )
                                                        }
                                                    },
                                                    onSettings = { activator?.let { callbacks.onConfigure(it.activator.id, title) } },
                                                    onCopy = {
                                                        if (groupId != null) callbacks.onCopyCell(groupId, spec.subInputKey, type)
                                                    },
                                                    onPaste = {
                                                        if (groupId != null) callbacks.onPasteCell(groupId, spec.subInputKey, type)
                                                    },
                                                    onMove = { moveState.pickUp(cellKey, byPointer = false) },
                                                    onClear = {
                                                        if (groupId != null) callbacks.onClearCell(groupId, spec.subInputKey, type)
                                                    },
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .focusRequester(focusHandle(cellKey))
                                            .then(
                                                // Top row escapes UP to the header; every other
                                                // row steps normally.
                                                if (rowIndex == 0) {
                                                    Modifier.focusProperties { up = upTarget }
                                                } else Modifier,
                                            )
                                            .then(
                                                if (focusRequester != null && rowIndex == 0 &&
                                                    type == ActivatorType.FULL_PRESS
                                                ) {
                                                    Modifier.focusRequester(focusRequester)
                                                } else Modifier,
                                            ),
                                    )
                                }
                            }
                        }
                        }
                    }
                }
                if (mirrored) {
                    Spacer(Modifier.width(TileGap))
                    glyphColumn()
                }
            }
            }
            // Scrollbars ALONGSIDE the fades and chevrons (Dylan, 2026-09-18) rather than
            // instead of them: a card is a dense grid inside a viewport that usually can't show
            // all of it, and the bar is the part that says HOW MUCH more and WHERE — which a
            // fade at the rim can't. Indicators only; see MinputScrollbar.
            MinputScrollbar(
                state = vScroll,
                orientation = Orientation.Vertical,
                modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = TableVerticalPadding),
            )
        }
        MinputScrollbar(
            state = hScroll,
            orientation = Orientation.Horizontal,
            // Value 0 is the RIGHT end on a mirrored table, so its thumb starts there too.
            reverse = mirrored,
            // Under the body, inset to the table's own margin. It sits outside the vertical
            // scroller so it stays put at the card's floor instead of scrolling away with the
            // rows it describes.
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = TableScrollbarGap),
        )
        Spacer(Modifier.height(TableBottomGap))

        labelTarget?.let { target ->
            CommandLabelDialog(
                label = target.label,
                outputs = target.outputs,
                config = config,
                showDeviceIcon = target.showDeviceIcon,
                showDeviceInitials = target.showDeviceInitials,
                onCommit = { text, icons, initials ->
                    callbacks.onSetLabel(target.bindingId, text, icons, initials)
                },
                onClose = { labelTarget = null },
            )
        }
    }
}

/**
 * Column header: the press type's full name in the overline treatment, wearing the column accent
 * so the header reads as the head of its colored stack.
 *
 * The concept glyph that used to lead it went on 2026-09-18 (Dylan), along with the abbreviated
 * names — six icons across a row read as a toolbar, and the words now say the whole thing
 * ([columnLabel]).
 */
@Composable
private fun PressColumnHeader(type: ActivatorType) {
    Box(
        modifier = Modifier.width(TileWidth).height(ColumnHeaderHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = type.columnLabel().uppercase(),
            style = minputOverlineTextStyle(),
            // The neutral column takes the THEME token rather than the palette's approximation
            // of it, so "REGULAR PRESS" matches the "INPUT" caption and the header's MODE
            // caption exactly (Dylan, 2026-09-19). Every other column wears its own accent.
            color = if (type == ActivatorType.FULL_PRESS) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                type.columnColors().header
            },
            maxLines = 1,
        )
    }
}

/**
 * One cell.
 *
 * **Defined** ([output] non-null): a tall rounded button on the column's accent-tinted surface,
 * carrying the output glyph ahead of a stack of (optional) label over output text. The label
 * takes the overline treatment above the output's ordinary mini text — no separate label FIELD
 * exists any more, which is what bought the height.
 *
 * **Empty** ([output] null): transparent, strokeless, a dimmed "+" in the column accent. The
 * absence of any chrome is the signal — an empty cell shouldn't compete with real assignments
 * for attention across a six-column row.
 *
 * Output glyph and text deliberately do NOT take the column accent (readability); only the
 * container tint, the header, and the empty "+" carry it.
 */
@Composable
private fun CommandTile(
    colors: PressTypeColors,
    output: BindingOutput?,
    label: String?,
    /** The command's name as it should print — device initials already applied. */
    outputText: String,
    /** Whether the output device's glyph leads that name. Per command, from its Binding. */
    showDeviceIcon: Boolean,
    enabled: Boolean,
    cellKey: CellKey,
    moveState: MoveModeState<CellKey>,
    /** Grid-step offset this tile should animate to while a move previews a swap. */
    displacement: DpOffset,
    /** The lifted cell of the move currently being previewed — live OR still committing.
     *  Drives z-order and keys the slide animation, so the visual survives the handover from
     *  "dragging" to "written, waiting for the reload". */
    previewOrigin: CellKey?,
    onCommitMove: (Pair<CellKey, CellKey>?) -> Unit,
    /** Reports a controller-driven lift, so the table can track whether the button that
     *  started it is still held. */
    onControllerLift: () -> Unit,
    actions: () -> List<MinputAction>,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // TWO interaction sources, deliberately.
    //
    // Press/hover belong to the TILE — they happen while it's at rest, and their ripple should
    // be clipped to its shape and travel with it. FOCUS belongs to the CELL: it marks a
    // position in the grid, not an object being carried. Sharing one source put the focus
    // highlight on the translated node, so during a move the ring rode along with the lifted
    // tile and then snapped back to the origin with it on commit — read as the focus flashing
    // back to the old location.
    val pressInteraction = remember { MutableInteractionSource() }
    val focusInteraction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    // Live move state — the finger is down / the tile is lifted RIGHT NOW.
    val isOrigin = moveState.origin == cellKey
    val isTarget = moveState.active && moveState.target == cellKey
    // Preview state — spans the live move AND the commit that hasn't reloaded yet.
    val isPreviewOrigin = previewOrigin == cellKey
    val previewing = previewOrigin != null
    val density = LocalDensity.current

    // Swap preview, animated so the exchange reads as motion rather than a jump.
    //
    // Keyed on the CURRENT LIFT (`moveState.origin`) so every move starts from a fresh
    // Animatable at zero — otherwise a new drag inherits the previous one's in-flight
    // tween-back and the tile starts from somewhere it was never at. Same reasoning, and the
    // same fix, as `ReorderableTabBar`'s per-drag `Animatable`.
    // Keyed on the PREVIEW's origin, not the live one: keying on the live value would recreate
    // the Animatable at zero the instant the move committed, snapping the tile home a frame
    // before its new content arrives — the flash this whole preview exists to prevent.
    val slide = remember(previewOrigin, cellKey) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val slideTarget = with(density) { Offset(displacement.x.toPx(), displacement.y.toPx()) }
    LaunchedEffect(previewOrigin, slideTarget) {
        if (previewing) slide.animateTo(slideTarget, tween(MoveSlideMillis))
    }
    // The lifted tile swells slightly — the "picked up" read.
    val lift by animateFloatAsState(
        if (isOrigin) MoveLiftScale else 1f,
        label = "cell-lift",
    )

    // The tint's own alpha IS the strength (see PressTypePalette); a fully transparent tint
    // leaves the plain elevated container, which is exactly what the Press column wants.
    val container = if (output == null) Color.Transparent else colors.tile.compositeOver(MinputElevatedContainer)
    val shape = RoundedCornerShape(TileCorner)

    // Controller hold-to-move: a key-down starts a timer; crossing the long-press threshold
    // while still held lifts the tile instead of opening the menu. Hardware auto-repeat
    // re-delivers KeyDown, so the timestamp is only taken on the first one.
    // Only a cell that HOLDS something can be picked up. An empty cell is a slot, not a tile —
    // lifting one produced a move with nothing in it, which then "committed" a no-op over
    // whatever the user aimed at.
    val movable = enabled && output != null
    var keyDownAt by remember { mutableLongStateOf(0L) }
    // Did THIS tile see the press that this release belongs to? A held activate button keeps
    // auto-repeating while focus moves, so the release lands on whatever tile focus ended on —
    // and without this, that tile opened its menu for a press the user never made on it.
    var sawOwnKeyDown by remember { mutableStateOf(false) }
    LaunchedEffect(keyDownAt) {
        if (keyDownAt == 0L) return@LaunchedEffect
        delay(viewConfiguration.longPressTimeoutMillis)
        if (keyDownAt != 0L && !moveState.active) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            moveState.pickUp(cellKey, byPointer = false)
            onControllerLift()
        }
    }

    /**
     * What activating this tile means — shared by the pointer tap and the key-up path.
     *
     * Written to be safe to run TWICE and in either order, because both `clickable` (which
     * handles Enter/DpadCenter itself) and this file's own key handler can fire for one
     * physical press, and their relative order depends on modifier-chain details that are not
     * worth depending on. The gamepad A button is why the key handler exists at all —
     * `clickable` doesn't recognize it.
     *
     * The idempotence trick: during a controller move, confirming only does something when the
     * target has actually MOVED off the origin. That makes the key-up which merely ends the
     * lifting hold a no-op, with no "swallow the next activation" flag to keep in sync.
     */
    fun activate() {
        if (moveState.active && !moveState.pointerDriven) {
            // Activating any tile while a controller move is in flight is a DROP — including
            // the origin, which is how you put a tile back where you found it.
            moveState.moveTargetTo(cellKey)
            onCommitMove(moveState.commit())
            return
        }
        menuOpen = true
    }

    // OUTER: the cell's natural layout slot. Hosts the gesture, the bounds registration, the
    // focus target and the z-order — and carries NO graphicsLayer. That separation is the
    // whole trick, and it is not optional: pointerInput reports positions in POST-transform
    // local coordinates, so translating the same node that detects the drag makes the tile
    // chase a finger that appears stationary to it — a feedback loop of lag, flicker and
    // wrong drops. `ReorderableTabBar` and the keyboard button grid are both built this way
    // for exactly this reason; this tile got it wrong once already.
    //
    // It also means the registered bounds are the RESTING slot, which is what a grid wants:
    // "which cell is under the finger" must not change as tiles animate around.
    Box(
        modifier = modifier
            .testTag(cellTestTag(cellKey))
            .width(TileWidth)
            .height(TileHeight)
            // Lifted tiles ride above their neighbours. zIndex orders SIBLINGS only, so the
            // owning Row carries one too (see AdvancedTable).
            .zIndex(if (isPreviewOrigin) 10f else if (isTarget) 5f else 0f)
            .moveModeCell(moveState, cellKey)
            // A pending hold dies with focus. Without this, holding the activate button and
            // then d-padding away left the timer running on the tile behind you: it lifted a
            // tile you were no longer looking at, and the release — now delivered to whatever
            // had focus — dropped it there.
            .onFocusChanged {
                if (!it.isFocused) {
                    keyDownAt = 0L
                    sawOwnKeyDown = false
                }
            }
            // Every tile is a focus stop, editable or not — a read-only layer view still
            // needs controller navigation to reach its menus.
            .focusable(interactionSource = focusInteraction)
            .onKeyEvent { event ->
                // While a CONTROLLER move is in flight the table owns the activate keys; see
                // its handler. Returning false lets them bubble up to it.
                if (moveState.active && !moveState.pointerDriven) return@onKeyEvent false
                if (event.key !in TileActivateKeys) {
                    // ANY other key while the activate button is held abandons the press
                    // entirely — both the pending hold and the claim on the eventual release. A
                    // half-committed lift is the worst state this control can be in, so the
                    // gesture is treated as fragile on purpose: it survives holding still and
                    // nothing else. (Focus loss disarms it too, below.)
                    keyDownAt = 0L
                    sawOwnKeyDown = false
                    return@onKeyEvent false
                }
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        // Arm only on the INITIAL press. Held keys auto-repeat their KeyDown,
                        // so without this check a press that began on another tile re-armed the
                        // hold here the moment focus arrived — lifting a tile the user had
                        // merely navigated onto, mid-hold.
                        val initialPress = event.nativeKeyEvent.repeatCount == 0
                        if (initialPress) sawOwnKeyDown = true
                        if (movable && initialPress && !moveState.active && keyDownAt == 0L) {
                            keyDownAt = System.currentTimeMillis()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        keyDownAt = 0L
                        val ownPress = sawOwnKeyDown
                        sawOwnKeyDown = false
                        // Only a release whose press this tile actually saw activates it.
                        if (ownPress) activate()
                        true
                    }
                    else -> false
                }
            }
            .then(
                if (movable) {
                    Modifier.moveModeLongPressSource(
                        state = moveState,
                        key = cellKey,
                        onCommit = onCommitMove,
                    )
                } else Modifier,
            ),
    ) {
    // INNER: visual transform only, decoupled from gesture detection. Also where the tap
    // lives, so the ripple is clipped to the tile's shape and travels with it; its own
    // focusability is switched off so the OUTER stays the single focus target (which is what
    // the call site's focusRequester attaches to).
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                when {
                    // Pointer path: raw, unanimated — the tile belongs under the finger.
                    isOrigin && moveState.pointerDriven -> {
                        translationX = moveState.dragOffset.x
                        translationY = moveState.dragOffset.y
                    }
                    // Nothing in flight (and nothing committing): rest. Reaching this state
                    // is a SNAP, never an animation — a tween back to the resting slot would
                    // read exactly like the move being rejected.
                    !previewing -> {
                        translationX = 0f
                        translationY = 0f
                    }
                    else -> {
                        translationX = slide.value.x
                        translationY = slide.value.y
                    }
                }
                scaleX = lift
                scaleY = lift
            }
            .minputInteractiveMotion(pressInteraction)
            .clip(shape)
            .background(container, shape)
            // Empty cells stay strokeless by spec; defined ones wear the family bevel.
            .then(
                if (output != null) {
                    Modifier.border(minputBevelBorder(container, TileCorner), shape)
                } else Modifier,
            )
            .focusProperties { canFocus = false }
            .clickable(
                interactionSource = pressInteraction,
                indication = minputIndication(),
                onClickLabel = if (output == null) "Assign command" else "Command options",
                onClick = ::activate,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // An empty cell draws NOTHING — it is a transparent, interactive slot. Its "+" is
        // painted by the table's background slot layer, a whole layer below every tile, so a
        // tile sliding past during a move can never end up underneath one.
        if (output != null) {
            // The device glyph belongs to the COMMAND line, not to the tile (Dylan,
            // 2026-09-19): spanning both rows it read as an icon for the label as well, and
            // left the label hanging off the start of the thing it names. The label now sits
            // centred OVER its command.
            Column(
                modifier = Modifier.padding(horizontal = TileContentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (label != null) {
                    Text(
                        text = label.uppercase(),
                        style = minputOverlineTextStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (showDeviceIcon) {
                        InputGlyphs.outputPainter(output)?.let { painter ->
                            Icon(
                                painter,
                                contentDescription = null,
                                modifier = Modifier.size(TileOutputGlyphSize),
                                tint = LocalContentColor.current,
                            )
                            Spacer(Modifier.width(MinputGlyphLabelGap))
                        }
                    }
                    Text(
                        text = outputText,
                        style = minputMiniTextStyle(),
                        color = MaterialTheme.colorScheme.onSurface,
                        // ALWAYS one line, label or no label. A wrapped command pushed the
                        // glyph off-centre and made a labelled tile and an unlabelled one
                        // read as different components; ellipsis is the honest overflow.
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Beside the tile, not over it: the cell IS the thing being acted on, and a menu
        // dropped on top of it hides the command you're deciding about. Mirrors to the start
        // side automatically for the rightmost columns. The menu measures this Box (its
        // anchor) itself — nothing to pass.
        MinputActionMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            actions = actions(),
            placement = MinputMenuPlacement.End,
            caret = true,
        )
    }

    // The FOCUS layer: pinned to the slot (no transform), clipped to the tile's shape, drawn
    // over the tile. Focus marks a POSITION in the grid, not the object being carried —
    // rendering it on the moving node is what made the ring ride along with a lifted tile and
    // snap back with it.
    Box(
        Modifier
            .matchParentSize()
            .clip(shape)
            .indication(focusInteraction, minputIndication()),
    )
    }
}

/**
 * The tile menu's verbs. Icons, no helper text (per the design) — these are self-evident, and
 * the two-line [RichMenuItem] form is reserved for actions that genuinely need tutorializing.
 *
 * Paste stays LISTED but greyed when nothing has been copied, so the menu's shape doesn't
 * shift between cells.
 *
 * **Label and Settings are additions to the specified list** (Edit / Copy / Paste / Move /
 * Clear): the table dropped the row's label field and cog, and without these two the tile's
 * label would be unsettable and activator settings — crucially the CHORD PARTNER, without
 * which a Chord cell can't function — would be unreachable.
 */
internal fun tileActions(
    defined: Boolean,
    clipboardOccupied: Boolean,
    onEdit: () -> Unit,
    onLabel: () -> Unit,
    onSettings: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onMove: () -> Unit,
    onClear: () -> Unit,
): List<MinputAction> = if (!defined) {
    listOf(
        MinputAction("New", Icons.Filled.Add, onClick = onEdit),
        MinputAction("Paste", Icons.Filled.ContentPaste, enabled = clipboardOccupied, onClick = onPaste),
    )
} else {
    listOf(
        MinputAction("Edit", Icons.Filled.Edit, onClick = onEdit),
        MinputAction("Label", Icons.AutoMirrored.Filled.Label, onClick = onLabel),
        MinputAction("Settings", Icons.Filled.Settings, onClick = onSettings),
        MinputAction("Copy", Icons.Filled.ContentCopy, onClick = onCopy),
        MinputAction("Paste", Icons.Filled.ContentPaste, enabled = clipboardOccupied, onClick = onPaste),
        MinputAction("Move", Icons.Filled.OpenWith, onClick = onMove),
        MinputAction("Clear", Icons.AutoMirrored.Filled.Backspace, destructive = true, onClick = onClear),
    )
}

/** Layer view's cut-down menu. Editing on a layer must go through the full-screen editor so
 *  the override materializes onto the layer rather than mutating the base set's row, and
 *  Clear-override only means anything where an override actually exists. */
internal fun layerTileActions(
    overridden: Boolean,
    onEdit: () -> Unit,
    onClearOverride: () -> Unit,
): List<MinputAction> = listOf(
    MinputAction("Edit on this layer", Icons.Filled.Edit, onClick = onEdit),
    MinputAction(
        "Clear override",
        Icons.Filled.Delete,
        enabled = overridden,
        destructive = true,
        onClick = onClearOverride,
    ),
)

/** Stable test handle for a cell. */
/**
 * Addressable per cell: "cell:<GROUP>:<sub-input key>:<ACTIVATOR_TYPE>".
 *
 * The GROUP is in the tag because the zoomed scene holds every group's table at once
 * (2026-09-17) and sub-input keys repeat across groups — "dpad_up" belongs to the d-pad and to
 * both sticks. Without it, three cells answer to one tag.
 */
internal fun cellTestTag(key: CellKey): String =
    "cell:${key.group.name}:${key.source.name}:${key.inputKey}:${key.type.name}"

/**
 * The cell one grid step from [from], staying inside its own group: the single-table stepping
 * every editor had before the zoomed scene, and what a standalone editor still does. A step off
 * an edge goes nowhere. (The scene's own stepper crosses into the neighbouring group instead —
 * see RemapZoomScene.)
 */
internal fun stepCellWithinGroup(from: CellKey, dRow: Int, dCol: Int): CellKey? {
    val rows = from.group.rows
    val row = rows.indexOf(from.row).takeIf { it >= 0 } ?: return null
    val column = pressTypeColumns.indexOf(from.type).takeIf { it >= 0 } ?: return null
    val nextRow = (row + dRow).coerceIn(0, rows.lastIndex)
    val nextColumn = (column + dCol).coerceIn(0, pressTypeColumns.lastIndex)
    return CellKey(from.group, rows[nextRow], pressTypeColumns[nextColumn])
}

/** The scrolling table of [group]'s editor — one per group in the scene. */
internal fun editorTableTestTag(group: RemapSimpleGroup): String = "group-editor-table:${group.name}"

/** Keys that activate a focused tile. Mirrors what Compose's own `clickable` accepts, plus the
 *  gamepad A button so a controller's primary action works without a d-pad center. */
internal val TileActivateKeys = setOf(
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.ButtonA,
)

/** Header identity label for a group. User-specified wording; Title Case is the deliberate
 *  exception to the sentence-case doctrine (hardware names read as proper nouns). */
internal fun RemapSimpleGroup.headerLabel(): String = when (this) {
    RemapSimpleGroup.LEFT_SHOULDER -> "Left Trigger"
    RemapSimpleGroup.LEFT_STICK -> "Left Joystick"
    RemapSimpleGroup.DPAD -> "Directional Pad"
    // "Button Pad" (not "Face Buttons") — matches the mode name of the same concept.
    RemapSimpleGroup.FACE -> "Button Pad"
    RemapSimpleGroup.RIGHT_SHOULDER -> "Right Trigger"
    RemapSimpleGroup.RIGHT_STICK -> "Right Joystick"
    RemapSimpleGroup.UTILITY -> "Utility Buttons"
}

/**
 * Does this group's table read right-to-left — glyph column on the card's RIGHT, press columns
 * running outward to the left?
 *
 * The groups on the LEFT of the controller do (Dylan, 2026-09-17), so that a card and the basic
 * view box it grew out of have the same shape, and so the two flanks read as each other's
 * reflection around the controller between them — the same rule [RemapSimpleGroup.anchorFor]
 * applies to the basic view's rows. The centre group stays normal: it has no flank to mirror.
 */
internal fun RemapSimpleGroup.editorMirrored(): Boolean = when (this) {
    RemapSimpleGroup.LEFT_SHOULDER, RemapSimpleGroup.DPAD, RemapSimpleGroup.LEFT_STICK -> true
    else -> false
}

/**
 * The header's mode indicator — "MODE: <input mode>", with a filled downward arrow when it can
 * be changed.
 *
 * It replaced the mode PILL on 2026-09-18 (Dylan). The pill made a second button of what is
 * really the group's own caption, sitting beside the group identity it describes; this is the
 * indicator itself, made to open the menu. A card that can't be edited from here — a resting one
 * in the zoomed scene, a layer view, a source with only one valid mode — keeps the caption and
 * drops the arrow, so the affordance is never claimed where there is nothing to pick.
 */
@Composable
private fun ModeDropdownLabel(
    source: InputSource,
    // Null when the group has no binding group yet, or its source has no modes at all: the
    // caption then states the device default and opens nothing.
    currentMode: BindingMode?,
    validModes: List<BindingMode>,
    /** The group's own name, for a screen reader — the glyph is all that states it on screen. */
    identity: String,
    enabled: Boolean,
    onPick: (BindingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val label = currentMode?.displayNameFor(source) ?: ModeLabelDefault
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clip(RoundedCornerShape(ModeLabelCorner))
                .then(
                    if (enabled) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = minputIndication(),
                            onClickLabel = "Change input mode",
                        ) { open = true }
                    } else Modifier,
                )
                .padding(horizontal = ModeLabelPadding, vertical = ModeLabelVerticalPadding),
        ) {
            // The Kenney prompt is single-color, so it tints down to the overline treatment
            // safely.
            Icon(
                InputGlyphs.sourcePainter(source),
                contentDescription = identity,
                modifier = Modifier.size(MinputPillIconSize),
                tint = color,
            )
            Spacer(Modifier.width(MinputGlyphLabelGap))
            Text(
                text = "$ModeLabelPrefix ${label.uppercase()}",
                style = minputOverlineTextStyle(),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (enabled) {
                Spacer(Modifier.width(ModeArrowGap))
                Icon(
                    // The STANDARD Material dropdown arrow. A dropdown wears the platform's own
                    // indicator, never a shape borrowed from elsewhere and rotated into place
                    // (Dylan, 2026-09-19).
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(ModeDropdownArrowSize),
                    tint = color,
                )
            }
        }
        if (currentMode != null) {
            MinputDropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                current = currentMode,
                options = validModes,
                optionLabel = { it.displayNameFor(source) },
                onPick = onPick,
                optionIcon = { InputGlyphs.modePainter(it) },
            )
        }
    }
}

// ── Shared press-type vocabulary (moved from the retired detail-pane editor) ─────────────────

/** Canonical Steam render order for press types. The advanced table uses [pressTypeColumns]
 *  instead — same set, user-specified column order. */
internal val pressTypeOrder = listOf(
    ActivatorType.FULL_PRESS,
    ActivatorType.LONG_PRESS,
    ActivatorType.DOUBLE_PRESS,
    ActivatorType.START_PRESS,
    ActivatorType.RELEASE_PRESS,
    ActivatorType.CHORDED_PRESS,
)

/**
 * The table's column names. User-specified wording (Dylan, 2026-09-18), replacing the one-word
 * abbreviations the columns carried while they also had an icon apiece.
 *
 * NB: START_PRESS reads "Down Press" and RELEASE_PRESS "Up Release" — Mappo wording, for the
 * activators that fire on the down / up edge. VDF import/export must map Steam's "Start Press" ↔
 * "Down Press" and "Release Press" ↔ "Up Release".
 */
internal fun ActivatorType.columnLabel(): String = when (this) {
    ActivatorType.FULL_PRESS -> "Regular Press"
    ActivatorType.LONG_PRESS -> "Long Press"
    ActivatorType.DOUBLE_PRESS -> "Double Press"
    ActivatorType.START_PRESS -> "Down Press"
    ActivatorType.RELEASE_PRESS -> "Up Release"
    ActivatorType.CHORDED_PRESS -> "Chord Press"
    ActivatorType.SOFT_PRESS -> "Soft Press"
}

internal fun ActivatorType.helperText(): String = when (this) {
    ActivatorType.FULL_PRESS -> "Fires on a normal press."
    ActivatorType.LONG_PRESS -> "Fires when held past the long-press time."
    ActivatorType.DOUBLE_PRESS -> "Fires on two quick presses."
    ActivatorType.START_PRESS -> "Fires the instant the button goes down."
    ActivatorType.RELEASE_PRESS -> "Fires when the button is let go."
    ActivatorType.CHORDED_PRESS -> "Fires only while another button is held."
    ActivatorType.SOFT_PRESS -> "Fires on a soft (partial) pull."
}

internal fun ActivatorType.pressIcon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    ActivatorType.FULL_PRESS -> Icons.Filled.TouchApp
    ActivatorType.LONG_PRESS -> Icons.Filled.Timer
    ActivatorType.DOUBLE_PRESS -> Icons.Filled.Repeat
    ActivatorType.START_PRESS -> Icons.Filled.Bolt
    ActivatorType.RELEASE_PRESS -> Icons.AutoMirrored.Filled.Logout
    ActivatorType.CHORDED_PRESS -> Icons.Filled.Link
    ActivatorType.SOFT_PRESS -> Icons.Filled.Adjust
}

/** The shared kebab ("more" button) used by the editor header. */
@Composable
internal fun RowKebab(
    onClick: () -> Unit,
    contentDescription: String = "Options",
    modifier: Modifier = Modifier,
) {
    MinputIconButton(
        icon = Icons.Filled.MoreVert,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
    )
}

/** A `DropdownMenuItem` with a leading icon and two-line title + helper text — the
 *  tutorializing form, for menus whose actions aren't self-evident. Self-evident verb menus
 *  use `MinputActionMenu` instead. [titleContent] swaps in a custom title composable; [title]
 *  still names the item for readers of the code. */
@Composable
internal fun RichMenuItem(
    title: String,
    helper: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    selected: Boolean = false,
    titleContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    androidx.compose.material3.DropdownMenuItem(
        enabled = enabled,
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        text = {
            Column {
                if (titleContent != null) {
                    titleContent()
                } else {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(helper, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else null,
        onClick = onClick,
    )
}

// Header height is the family standard shared with the panel surfaces.
private val EditorHeaderHeight = MinputPanelHeaderHeight

/** What the header's mode caption is prefixed with. Uppercase at the source: the caption is set
 *  in the overline treatment, and the mode's own name is uppercased beside it. */
private const val ModeLabelPrefix = "MODE:"

/** The mode caption's dropdown arrow. Material's own glyph inks well inside its box, so it
 *  takes the pill family's icon scale rather than the caption's cap height. */
private val ModeDropdownArrowSize = MinputPillIconSize

/** What the caption says when the group has no mode of its own. */
private const val ModeLabelDefault = "Default"

/** Air between the caption and its arrow. Material's glyph carries its own generous padding, so
 *  a normal label gap reads as a gulf (Dylan, 2026-09-19). */
private val ModeArrowGap = 1.dp

/** Hit area around the mode caption: enough for the press ripple to read as a button's without
 *  the caption drifting from the identity it follows. */
private val ModeLabelPadding = 6.dp
private val ModeLabelVerticalPadding = 3.dp
private val ModeLabelCorner = 6.dp

// ── Table metrics ────────────────────────────────────────────────────────────────────────────

/** GOVERNING VARIABLE for column width. Every cell, and the header above it, is exactly this
 *  wide — a table whose columns flexed to content would put the same press type at a different
 *  x-offset on every row, destroying the scan the table exists to enable. */
private val TileWidth = 148.dp

/** Cell height. Taller than the old 38dp command rows because a cell now stacks an overline
 *  label above the output text where the row had a separate label field beside it. */
private val TileHeight = 40.dp

/** Horizontal gap between columns, and between the glyph column and the body. */
private val TileGap = 4.dp

/** VERTICAL gap BETWEEN rows. Separate from [TileGap] on purpose: the rows want more air than
 *  the columns do — it gives the enlarged input glyphs room and uses up vertical space the
 *  full-height panel has going spare.
 *
 *  Strictly between: it is applied by an inner Column that holds ONLY the rows, so raising it
 *  can't also push the header away or pad the bottom of the table. Those are
 *  [HeaderToRowsGap] and [TableVerticalPadding], and they stay put. */
private val TileRowGap = 16.dp

/** Fixed gap from the press-type header row down to the first tile row. Deliberately NOT
 *  [TileRowGap] — the header's distance from the grid is a separate design decision. */
private val HeaderToRowsGap = 4.dp

/** The press-type header row's height. */
private val ColumnHeaderHeight = 20.dp

/** Input glyphs render LARGER here than in the old rows — with the press-type word gone from
 *  the cell, the glyph is the row's only identity, so it carries the weight of one. */
private val TableGlyphSize = 38.dp

/** Breathing room either side of the input glyph, inside the frozen column. */
private val GlyphColumnPadding = 11.dp

/** The frozen glyph column — derived, so widening the glyph or its padding can't leave the
 *  column too narrow for what it holds. */
private val GlyphColumnWidth = TableGlyphSize + GlyphColumnPadding * 2

/** FULLY rounded (Dylan, 2026-09-18): half the tile's height, so a cell is a capsule. An
 *  absolute radius rather than a percentage, per the minput rule — a percentage turns anything
 *  taller than it is wide into a lozenge. */
private val TileCorner = TileHeight / 2
private val TileContentPadding = 8.dp
private val TileOutputGlyphSize = 14.dp

/** The empty cell's "+": present enough to invite a tap, faint enough that a row of empties
 *  doesn't read as content. Its COLOR (and opacity) comes from `PressTypePalette`. */
private val EmptyTilePlusSize = 22.dp

/** How long a displaced tile takes to slide aside during a swap preview. */
private const val MoveSlideMillis = 200

/** How much a lifted tile swells while it's being carried. */
private const val MoveLiftScale = 1.06f
/** How strongly the origin / landing markers wash their cell. Low enough to read as a marked
 *  SLOT rather than a filled tile. */
private const val MoveMarkerAlpha = 0.3f

/** How close to the viewport edge a dragging finger must get before the table scrolls under
 *  it, and how far it scrolls per frame while it stays there. */
private val EdgeScrollZone = 28.dp
private val EdgeScrollStep = 6.dp

/** Vertical breathing room inside the table, above the header row and below the last row. */
private val TableVerticalPadding = 6.dp

/** Air under the table, so the last row isn't flush with the card's edge. */
private val TableBottomGap = 4.dp

/** Gap between the table's last row and the horizontal scrollbar beneath it. The bar came back
 *  on 2026-09-18 (Dylan), joining the fade + chevron cues rather than replacing them. */
private val TableScrollbarGap = 3.dp

/**
 * The height the advanced editor wants for [group] — header + divider + the table's own rows.
 *
 * Computable rather than measured because every part of the table is a fixed size, which is
 * what lets the editor's HOST size itself to the content instead of filling the screen (a
 * two-row group used to leave most of a screen empty below it). Callers should still clamp to
 * the space available; the table scrolls vertically if it doesn't fit.
 */
internal fun advancedEditorHeight(group: RemapSimpleGroup): Dp {
    val rows = group.rows.size
    val table = TableVerticalPadding * 2 +
        ColumnHeaderHeight + HeaderToRowsGap +
        TileHeight * rows + TileRowGap * (rows - 1).coerceAtLeast(0)
    return EditorHeaderHeight + EditorDividerHeight + table +
        TableScrollbarGap + MinputScrollbarThickness + TableBottomGap
}

/** The inset divider under the header is a hairline; counted so the height math is exact. */
private val EditorDividerHeight = 1.dp
