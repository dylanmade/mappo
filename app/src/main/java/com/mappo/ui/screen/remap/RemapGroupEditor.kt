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
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.DropdownMenu
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
import com.mappo.ui.minput.MinputPanelHeaderHeight
import com.mappo.ui.minput.MinputPillIconSize
import com.mappo.ui.minput.MinputScrollbar
import com.mappo.ui.minput.MinputScrollbarThickness
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputIndication
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle
import androidx.compose.ui.graphics.vector.ImageVector
import com.mappo.ui.minput.MinputButton
import com.mappo.ui.minput.MinputDialog
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
    // ── Command ops (see ControllerConfigRepository's input-row block) ──
    /** Add a command of [type] to a row and open the command picker on it — what the row's
     *  trailing "+" tile does. The creation has to land first: the picker edits a binding. */
    val onAddCommand: (
        bindingGroupId: Long,
        inputKey: String,
        type: ActivatorType,
        title: String,
    ) -> Unit,
    /** Delete one command. Its activator goes too if that leaves it empty. */
    val onDeleteCommand: (bindingId: Long) -> Unit,
    /** Which press type a command fires on. */
    val onSetPressType: (bindingId: Long, type: ActivatorType) -> Unit,
    val onCopyCommand: (bindingId: Long) -> Unit,
    /** Paste over [bindingId], or APPEND to the row when it is null (the "+" tile's Paste). */
    val onPasteCommand: (bindingId: Long?, bindingGroupId: Long, inputKey: String) -> Unit,
    /**
     * Carry a command onto a row — within a group or across them, which is the same operation
     * since a command only points at its row.
     *
     * [swapWithBindingId] is the command it lands ON, which goes back the other way; null means
     * it landed on the row's "+" and is simply ADDED there (Dylan, 2026-09-20). Either way the
     * moved command keeps its own press type, and the auto-sort decides where in the row it
     * comes to rest.
     */
    val onMoveCommand: (
        bindingId: Long,
        toBindingGroupId: Long,
        toInputKey: String,
        swapWithBindingId: Long?,
    ) -> Unit,
    /** Whether anything has been copied this session — greys Paste rather than hiding it. */
    val clipboardOccupied: Boolean,
    // ── Group-level ops (2026-09-16): carrying commands between groups, whole-group copy/reset ──
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
 * **The order a row's commands are auto-sorted into** — the order the table's fixed press-type
 * columns used to run in, kept as a sort when the columns went (Dylan, 2026-09-20). Also the
 * order the type picker lists them in.
 *
 * Note this is NOT [pressTypeOrder] (the canonical Steam render order): Chord comes before the
 * Down/Up edge triggers because the edge pair reads as a tail-end special case.
 *
 * `SOFT_PRESS` is absent by design: it's a sub-input (the trigger's "soft_press" row), not an
 * activator the user picks. See `feedback_soft_press_unified_to_soft_pull`.
 */
internal val pressTypeSortOrder = listOf(
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
    /**
     * Which TILE of the row, counting outward from the input glyph. A row holds its commands
     * followed by one "+" slot, so the last index is always the add affordance (2026-09-20) —
     * where the six fixed press-type columns used to be, and why this replaced the
     * `ActivatorType` a cell used to be keyed by. What a slot HOLDS is data, not identity:
     * resolve it through the row's commands (see [rowCommandsFor]).
     */
    val slot: Int,
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
    stepTarget: ((CellKey, Int, Int) -> CellKey?)? = null,
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
    // A standalone editor resolves its own move: which command was lifted, and what (if
    // anything) it landed on. The scene passes its own, which can reach across groups.
    val order = LocalCommandOrder.current
    // How many tiles each row has — the commands plus its "+". Rows differ in length now, so
    // the stepper is handed this rather than assuming a fixed column count.
    val slotsOf: (RemapSimpleGroup, SimpleRowSpec) -> Int = { _, spec ->
        rowSlotCount(rowCommandsFor(viewingSet, viewingLayer, spec, order).size)
    }
    val stepper = stepTarget
        ?: { key: CellKey, dRow: Int, dCol: Int -> stepCellWithinGroup(key, dRow, dCol, slotsOf) }
    val commitMove = onMoveCommitted ?: { from: CellKey, to: CellKey ->
        val lifted = rowCommandsFor(viewingSet, viewingLayer, from.row, order).getOrNull(from.slot)
        val landedOn = rowCommandsFor(viewingSet, viewingLayer, to.row, order).getOrNull(to.slot)
        val bindingGroupId = viewingSet?.presetFor(to.source)?.group?.group?.id
        if (lifted != null && bindingGroupId != null) {
            callbacks.onMoveCommand(lifted.id, bindingGroupId, to.inputKey, landedOn?.id)
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
        // No rule under the header (Dylan, 2026-09-20): the card's own edge already separates
        // it from the view, and the tiles below carry enough weight of their own that a line
        // between them and the caption was only more ink.

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
            stepTarget = stepper,
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
/** One row of the table, resolved: its spec, whether a layer overrides it, and its commands. */
private data class RowCommands(
    val spec: SimpleRowSpec,
    val overridden: Boolean,
    val commands: List<RowCommand>,
)

/** What the "Type" verb is editing: one command, and the press type it currently fires on. */
private data class TypeEdit(val bindingId: Long, val current: ActivatorType)

/**
 * One pane of a card's body: the rows it draws, which side its frozen glyph column sits on,
 * and the horizontal scroller it reads through.
 *
 * Most cards are ONE pane. The centre group is two (2026-09-20), meeting at the card's centre
 * line — see [CentreSplit], which its basic-view box uses for the same reason.
 */
private data class Pane(
    val rows: List<RowCommands>,
    val mirrored: Boolean,
    val scroll: ScrollState,
    val onViewport: (Rect) -> Unit,
)

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
    // card's right edge, the commands running outward to the left from it. Only the ORDER and
    // the side change — the header bar above stays as it is. A slot's INDEX always counts
    // outward from the glyph, so everything keyed on it (stepping, scroll offsets) is untouched
    // by mirroring.
    val mirrored = group.editorMirrored()
    val order = LocalCommandOrder.current
    val vScroll = rememberScrollState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    // The grid has gutters between rows (and between tiles), which belong to no cell. Without
    // a tolerance a finger crossing one resolves to nothing and the drop target snaps back to
    // the origin — visible as the landing marker flickering home mid-drag.
    moveState.hitTolerancePx = with(density) { maxOf(TileRowGap, TileGap).toPx() }
    // What the "Label" and "Type" verbs are editing. Both are summoned from a tile's menu, and
    // both live here rather than on the tile so they survive the menu closing.
    var labelTarget by remember { mutableStateOf<LabelEdit?>(null) }
    var typeTarget by remember { mutableStateOf<TypeEdit?>(null) }

    // Every row's commands, resolved ONCE for the whole composition — the tiles, the slot
    // counts the d-pad clamps against, the move's identities and the drop rules all read this
    // one list, so they cannot disagree about what a row holds.
    val rows = group.rows.map { spec ->
        val layerGroupInput = viewingLayer?.presetFor(spec.source)?.group?.inputByKey(spec.subInputKey)
        val baseGroupInput = viewingSet?.presetFor(spec.source)?.group?.inputByKey(spec.subInputKey)
        RowCommands(
            spec = spec,
            overridden = layerGroupInput != null,
            commands = (layerGroupInput ?: baseGroupInput).rowCommands(order),
        )
    }
    // The CENTRE group's card SPLITS like its basic-view box (Dylan, 2026-09-20): rows
    // anchored END draw on the left half reading outward, rows anchored START on the right
    // half, and their glyph columns meet on the card's centre line. Every other card is one
    // pane, mirrored or not as its flank dictates — so this reads the same [anchorFor] the
    // basic view does rather than special-casing the utility group.
    val endRows = rows.filter { group.anchorFor(it.spec) == RowAnchor.END }
    val startRows = rows.filter { group.anchorFor(it.spec) == RowAnchor.START }
    val split = endRows.isNotEmpty() && startRows.isNotEmpty()
    val endScroll = rememberScrollState()
    val startScroll = rememberScrollState()
    var endViewport by remember { mutableStateOf<Rect?>(null) }
    var startViewport by remember { mutableStateOf<Rect?>(null) }
    val endPane = Pane(endRows, mirrored = true, scroll = endScroll) { endViewport = it }
    val startPane = Pane(
        rows = if (split) startRows else rows,
        mirrored = if (split) false else mirrored,
        scroll = startScroll,
    ) { startViewport = it }
    /** Which way a row's slots run on screen: outward from its own pane's glyph column. */
    fun paneMirroredFor(spec: SimpleRowSpec): Boolean =
        if (split) group.anchorFor(spec) == RowAnchor.END else mirrored
    fun scrollFor(spec: SimpleRowSpec): ScrollState =
        if (split && group.anchorFor(spec) == RowAnchor.END) endScroll else startScroll

    fun rowAt(spec: SimpleRowSpec): RowCommands? = rows.firstOrNull { it.spec == spec }
    /** The command a cell holds, or null for the row's trailing "+" slot. */
    fun commandAt(key: CellKey): RowCommand? =
        rowAt(key.row)?.commands?.getOrNull(key.slot)

    fun cellAt(key: CellKey): Pair<Int, Int>? {
        val r = group.rows.indexOf(key.row).takeIf { it >= 0 } ?: return null
        return r to key.slot
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

    // The move being previewed. Everything visual (displacement, z-order) reads THESE rather
    // than moveState's fields directly. Scoped to this table: a command carried INTO another
    // group displaces nothing here.
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
    }

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
    fun displacementFor(key: CellKey): DpOffset {
        val origin = previewOrigin ?: return DpOffset.Zero
        val target = previewTarget ?: return DpOffset.Zero
        if (origin == target) return DpOffset.Zero
        val (originRow, originCol) = cellAt(origin) ?: return DpOffset.Zero
        val (targetRow, targetCol) = cellAt(target) ?: return DpOffset.Zero
        val (row, col) = cellAt(key) ?: return DpOffset.Zero
        // A mirrored pane lays its slots out right-to-left, so a step toward a higher slot
        // index moves a tile the other way on screen. Taken from the LIFTED tile's pane: a
        // carry between the centre card's two halves crosses panes, and the preview follows
        // the tile being carried.
        val stepX = (TileWidth + TileGap) * if (paneMirroredFor(origin.row)) -1 else 1
        val stepY = TileHeight + TileRowGap
        return when (key) {
            // The lifted tile rides to the target. On the POINTER path it follows the finger
            // instead (raw translation in CommandTile), so no grid animation there.
            origin ->
                if (moveState.pointerDriven) DpOffset.Zero
                else DpOffset(stepX * (targetCol - col), stepY * (targetRow - row))
            // The displaced occupant takes the vacated slot — but only if there IS one.
            // Dropping onto a row's "+" is an ADD, not a swap, so nothing comes back the
            // other way.
            target ->
                if (commandAt(target) != null) {
                    DpOffset(stepX * (originCol - col), stepY * (originRow - row))
                } else DpOffset.Zero
            else -> DpOffset.Zero
        }
    }

    // Keep the drop target on screen. A controller move walks the target with the d-pad and
    // will happily walk it off the visible slots; there is no free hand to scroll with, so
    // the table follows the target instead.
    LaunchedEffect(moveState.target, moveState.active) {
        val target = moveState.target.takeIf { moveState.active && it?.group == group }
            ?: return@LaunchedEffect
        val (row, col) = cellAt(target) ?: return@LaunchedEffect
        val hScroll = scrollFor(target.row)
        val stepX = with(density) { (TileWidth + TileGap).toPx() }
        val stepY = with(density) { (TileHeight + TileRowGap).toPx() }
        val cellW = with(density) { TileWidth.toPx() }
        val cellH = with(density) { TileHeight.toPx() }
        // The scroller's content starts after the table's own top padding, applied INSIDE
        // verticalScroll. (There is no column-header row any more — see AdvancedTable's KDoc.)
        val headerH = with(density) { TableVerticalPadding.toPx() }

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
    // under the pointer, so without this a touch user simply cannot reach a slot that isn't
    // already on screen. Holding near an edge scrolls, and the hit test re-runs each frame so
    // the target keeps up with the cells moving under a stationary finger.
    LaunchedEffect(moveState.pointerDriven) {
        if (!moveState.pointerDriven) return@LaunchedEffect
        val zone = with(density) { EdgeScrollZone.toPx() }
        val step = with(density) { EdgeScrollStep.toPx() }
        while (isActive && moveState.pointerDriven) {
            withFrameNanos { }
            val x = moveState.pointerWindow.x
            // Each pane scrolls on its own, so the finger's x picks which one it is reaching
            // out of — on the centre card the two halves run in opposite directions.
            val panes = if (split) {
                listOf(endViewport to endPane, startViewport to startPane)
            } else {
                listOf(startViewport to startPane)
            }
            panes.forEach { (viewport, p) ->
                if (viewport == null) return@forEach
                if (x < viewport.left - zone || x > viewport.right + zone) return@forEach
                // Scroll VALUE runs from the glyph outward either way (a mirrored pane's
                // scroller is reversed), so the edge that increases it is the outward one —
                // right normally, left when the pane is mirrored.
                val direction = if (p.mirrored) -1 else 1
                val delta = when {
                    x > viewport.right - zone -> step * direction
                    x < viewport.left + zone -> -step * direction
                    else -> 0f
                }
                if (delta != 0f) {
                    p.scroll.scrollBy(delta)
                    moveState.refreshTargetAtPointer()
                }
            }
        }
    }

    /** The slot indices of a row, laid out in the order they are DRAWN. */
    fun slotOrder(row: RowCommands, paneMirrored: Boolean): List<Int> {
        val slots = (0 until rowSlotCount(row.commands.size)).toList()
        return if (paneMirrored) slots.reversed() else slots
    }

    // ONE PANE of the card: a frozen glyph column plus the tiles reading outward from it.
    //
    // A card normally has one. The CENTRE group has TWO (2026-09-20), meeting at the card's
    // centre line — its glyphs in the middle with their commands radiating outward, exactly as
    // its basic-view box reads ([CentreSplit]). Which side a pane's glyphs sit on is its own
    // [Pane.mirrored], not the card's: the centre card's left pane is mirrored and its right
    // pane is not.
    val pane: @Composable (Pane, Modifier) -> Unit = { p, paneModifier ->
        val glyphColumn: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(TileRowGap)) {
                p.rows.forEach { row ->
                    Box(
                        modifier = Modifier.width(GlyphColumnWidth).height(TileHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        InputGlyphs.SubInputGlyph(
                            source = row.spec.source,
                            subInputKey = row.spec.subInputKey,
                            size = TableGlyphSize,
                        )
                    }
                }
            }
        }
        Row(
            modifier = paneModifier,
            // A mirrored pane reads toward its glyphs, so it sits at the card's END edge
            // rather than leaving its gap there (Dylan, 2026-09-20).
            horizontalArrangement = if (p.mirrored) Arrangement.End else Arrangement.Start,
        ) {
            if (!p.mirrored) {
                glyphColumn()
                Spacer(Modifier.width(TileGap))
            }
            MinputOverflowScroll(
                state = p.scroll,
                orientation = Orientation.Horizontal,
                // A mirrored pane reads outward from a glyph pinned to its right edge, so its
                // resting position is the scroller's far end — same bargain the basic view's
                // mirrored rows strike.
                reverseScrolling = p.mirrored,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .onGloballyPositioned { p.onViewport(it.boundsInWindow()) },
            ) {
                // LAYER 0 — the move markers, drawn beneath EVERY tile.
                //
                // They live in their own layer rather than on the cells because z-order
                // between tiles is per-Row (zIndex only orders siblings), so a marker drawn on
                // the origin CELL sat above the tile sliding into it. Down here nothing can get
                // underneath a tile.
                Column(verticalArrangement = Arrangement.spacedBy(TileRowGap)) {
                    p.rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                            slotOrder(row, p.mirrored).forEach { slot ->
                                val marker = moveMarkerFor(CellKey(group, row.spec, slot))
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
                                )
                            }
                        }
                    }
                }

                // LAYER 1 — the tiles themselves.
                Column(verticalArrangement = Arrangement.spacedBy(TileRowGap)) {
                    p.rows.forEach { row ->
                        val spec = row.spec
                        val subLabel = RemapSections.labelFor(spec.source, spec.subInputKey)
                        val groupId = bindingGroupIdFor(spec)
                        val topRow = spec == group.rows.first()

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
                            slotOrder(row, p.mirrored).forEach { slot ->
                                val cellKey = CellKey(group, spec, slot)
                                val command = row.commands.getOrNull(slot)
                                // How this command prints — the SAME resolution the basic
                                // view's rows use, so one binding reads the same way in
                                // both (see [commandDisplay]).
                                val display = command?.let {
                                    commandDisplay(it.binding, listOf(it.output), config)
                                }
                                val type = command?.type ?: ActivatorType.FULL_PRESS
                                val title = "$subLabel · ${type.activatorDisplayLabel()}"

                                CommandTile(
                                    colors = type.columnColors(),
                                    // The "+" slot wears no press type: it isn't a command
                                    // yet, and colouring it would claim one.
                                    pressType = type.takeIf { command != null },
                                    output = command?.output,
                                    label = display?.label,
                                    outputText = display?.text.orEmpty(),
                                    showDeviceIcon = display?.glyph != null,
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
                                                overridden = row.overridden,
                                                onEdit = {
                                                    callbacks.onOpenInputEditor(spec.source, spec.subInputKey, subLabel)
                                                },
                                                onClearOverride = {
                                                    callbacks.onClearOverride(spec.source, spec.subInputKey)
                                                },
                                            )
                                        } else {
                                            tileActions(
                                                defined = command != null,
                                                clipboardOccupied = callbacks.clipboardOccupied,
                                                onEdit = {
                                                    if (command != null) {
                                                        callbacks.onEditCommand(command.id, command.output, title)
                                                    } else if (groupId != null) {
                                                        // The "+" makes the command first, then
                                                        // opens the picker on it — a new command
                                                        // starts as a Regular Press and is
                                                        // retyped from the same menu.
                                                        callbacks.onAddCommand(
                                                            groupId,
                                                            spec.subInputKey,
                                                            ActivatorType.FULL_PRESS,
                                                            title,
                                                        )
                                                    }
                                                },
                                                onLabel = {
                                                    command?.let {
                                                        labelTarget = LabelEdit(
                                                            bindingId = it.id,
                                                            label = it.binding.label.orEmpty(),
                                                            outputs = listOf(it.output),
                                                            showDeviceIcon = it.binding.showDeviceIcon,
                                                            showDeviceInitials = it.binding.showDeviceInitials,
                                                        )
                                                    }
                                                },
                                                onType = {
                                                    command?.let { typeTarget = TypeEdit(it.id, it.type) }
                                                },
                                                onSettings = {
                                                    command?.let { callbacks.onConfigure(it.activator.id, title) }
                                                },
                                                onCopy = { command?.let { callbacks.onCopyCommand(it.id) } },
                                                onPaste = {
                                                    if (groupId != null) {
                                                        callbacks.onPasteCommand(command?.id, groupId, spec.subInputKey)
                                                    }
                                                },
                                                onMove = { moveState.pickUp(cellKey, byPointer = false) },
                                                onClear = { command?.let { callbacks.onDeleteCommand(it.id) } },
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .focusRequester(focusHandle(cellKey))
                                        .then(
                                            // Top row escapes UP to the header; every other
                                            // row steps normally.
                                            if (topRow) {
                                                Modifier.focusProperties { up = upTarget }
                                            } else Modifier,
                                        )
                                        .then(
                                            if (focusRequester != null && topRow && slot == 0) {
                                                Modifier.focusRequester(focusRequester)
                                            } else Modifier,
                                        ),
                                )
                            }
                        }
                    }
                }
            }
            if (p.mirrored) {
                Spacer(Modifier.width(TileGap))
                glyphColumn()
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
                    // the key-down filter below.
                    if (event.key in TileActivateKeys) {
                        if (event.type == KeyEventType.KeyUp) {
                            val wasLiftingPress = liftHeld
                            liftHeld = false
                            // Releasing the button that LIFTED the tile confirms, provided the
                            // target moved while it was held — ordinary drag-and-drop. Released
                            // without having moved, it reads as the user taking their thumb off
                            // a tile they've picked up to look around with, so the move stays
                            // live and a later press confirms.
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = TableVerticalPadding),
                ) {
                    if (split) {
                        CentreSplit(
                            modifier = Modifier.fillMaxWidth(),
                            end = { pane(endPane, Modifier) },
                            start = { pane(startPane, Modifier) },
                        )
                    } else {
                        pane(startPane, Modifier.fillMaxWidth())
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
        // Under the body, inset to the table's own margin. Outside the vertical scroller so it
        // stays at the card's floor instead of scrolling away with the rows it describes — and
        // one bar PER PANE, since the centre card's halves scroll independently.
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = TableScrollbarGap),
            horizontalArrangement = Arrangement.spacedBy(CentreSplitGap),
        ) {
            if (split) {
                MinputScrollbar(
                    state = endPane.scroll,
                    orientation = Orientation.Horizontal,
                    reverse = true,
                    modifier = Modifier.weight(1f),
                )
            }
            MinputScrollbar(
                state = startPane.scroll,
                orientation = Orientation.Horizontal,
                // Value 0 is the RIGHT end on a mirrored pane, so its thumb starts there too.
                reverse = startPane.mirrored,
                modifier = Modifier.weight(1f),
            )
        }
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
        typeTarget?.let { target ->
            PressTypeDialog(
                current = target.current,
                onPick = { type -> callbacks.onSetPressType(target.bindingId, type) },
                onClose = { typeTarget = null },
            )
        }
    }
}

/**
 * The "Type" verb's picker: which press type a command fires on.
 *
 * A stop-gap by design (Dylan, 2026-09-20) — the per-command configuration page will own this
 * eventually. It lives in the tile menu so the press-type LOOK can be worked on now: each type
 * shows the glyph and color the tile will wear, so picking one is picking a tile.
 */
@Composable
private fun PressTypeDialog(
    current: ActivatorType,
    onPick: (ActivatorType) -> Unit,
    onClose: () -> Unit,
) {
    MinputDialog(onDismissRequest = onClose) {
        Text(
            text = "PRESS TYPE",
            style = minputOverlineTextStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(TypeDialogTitleGap))
        pressTypeSortOrder.forEach { type ->
            val colors = type.columnColors()
            val selected = type == current
            val shape = RoundedCornerShape(TypeDialogRowCorner)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (selected) colors.tile else Color.Transparent, shape)
                    .clickable(onClickLabel = type.columnLabel()) {
                        onPick(type)
                        onClose()
                    }
                    .padding(horizontal = TypeDialogRowPadding, vertical = TypeDialogRowPadding / 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The glyph box is reserved even for Regular Press, which has none by design —
                // the names must still line up with each other.
                Box(
                    modifier = Modifier.size(TilePressGlyphSize),
                    contentAlignment = Alignment.Center,
                ) {
                    type.pressIcon()?.let { icon ->
                        Icon(icon, contentDescription = null, tint = colors.icon)
                    }
                }
                Spacer(Modifier.width(MinputGlyphLabelGap))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = type.columnLabel(),
                        style = minputMiniTextStyle(),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = type.helperText(),
                        style = minputOverlineTextStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Current",
                        modifier = Modifier.size(MinputPillIconSize),
                        tint = colors.icon,
                    )
                }
            }
        }
        Spacer(Modifier.height(TypeDialogTitleGap))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        ) {
            MinputButton(text = "Cancel", onClick = onClose)
        }
    }
}

/**
 * One tile — a command, or the row's trailing "+".
 *
 * **A command** ([output] non-null): a capsule on its press type's accent-tinted surface, led
 * by that press type's glyph, carrying a stack of (optional) label over the output line. The
 * leading glyph is how a tile says which press type it is now that the columns are gone (Dylan,
 * 2026-09-20): it is left-aligned, larger than the output's own device glyph, and wears the
 * palette's [PressTypeColors.icon] rather than the tile's tint so it reads ON the tile instead
 * of dissolving into it. **Regular Press deliberately has no glyph** — it is the ordinary case,
 * and an icon for "nothing special" is noise on the tile the user sees most.
 *
 * **The "+"** ([output] null): transparent, strokeless, a dimmed plus. The absence of any
 * chrome is the signal — the create affordance shouldn't compete with the commands beside it.
 *
 * Output glyph and text deliberately do NOT take the press accent (readability); the container
 * tint, the press glyph and the "+" carry it.
 */
@Composable
private fun CommandTile(
    colors: PressTypeColors,
    /** The press type whose glyph leads the tile, or null for Regular Press and the "+". */
    pressType: ActivatorType?,
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
        if (output == null) {
            // The row's create affordance. A plus and nothing else: it is a slot, not a command.
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(EmptyTilePlusSize),
                // Alpha rides in the palette color itself — no extra .alpha() here, or the
                // value in Theme.kt would stop being what renders.
                tint = colors.plus,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = TileContentPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The press type's own glyph, at the tile's start edge. Absent on Regular Press,
                // and the text simply takes the whole tile then rather than sitting beside a gap.
                pressType?.pressIcon()?.let { icon ->
                    Icon(
                        icon,
                        contentDescription = pressType.columnLabel(),
                        modifier = Modifier.size(TilePressGlyphSize),
                        tint = colors.icon,
                    )
                    Spacer(Modifier.width(TilePressGlyphGap))
                }
                // The device glyph belongs to the COMMAND line, not to the tile (Dylan,
                // 2026-09-19): spanning both rows it read as an icon for the label as well, and
                // left the label hanging off the start of the thing it names. The label now sits
                // centred OVER its command.
                Column(
                    modifier = Modifier.weight(1f),
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
 * **Label, Type and Settings are additions to the specified list** (Edit / Copy / Paste / Move
 * / Clear): the table dropped the row's label field and cog, and without these the tile's label
 * would be unsettable, activator settings — crucially the CHORD PARTNER, without which a Chord
 * command can't function — would be unreachable, and with the press-type columns gone there
 * would be nowhere to say what a command fires on. Type is explicitly a stop-gap until the
 * per-command configuration page lands (Dylan, 2026-09-20).
 */
internal fun tileActions(
    defined: Boolean,
    clipboardOccupied: Boolean,
    onEdit: () -> Unit,
    onLabel: () -> Unit,
    onType: () -> Unit,
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
        MinputAction("Type", Icons.Filled.TouchApp, onClick = onType),
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
 * Addressable per cell: "cell:<GROUP>:<SOURCE>:<sub-input key>:<slot>".
 *
 * The GROUP is in the tag because the zoomed scene holds every group's table at once
 * (2026-09-17) and sub-input keys repeat across groups — "dpad_up" belongs to the d-pad and to
 * both sticks. Without it, three cells answer to one tag. The SLOT replaced the activator type
 * when rows became stacks (2026-09-20), so a tag names a POSITION in a row, not a press type.
 */
internal fun cellTestTag(key: CellKey): String =
    "cell:${key.group.name}:${key.source.name}:${key.inputKey}:${key.slot}"

/**
 * The cell one grid step from [from], staying inside its own group: the single-table stepping
 * every editor had before the zoomed scene, and what a standalone editor still does. A step off
 * an edge goes nowhere. (The scene's own stepper crosses into the neighbouring group instead —
 * see RemapZoomScene.)
 *
 * [slots] reports how many tiles a row has, INCLUDING its trailing "+". Rows are no longer the
 * same length as each other (2026-09-20), so the stepper has to ask rather than assume: a row
 * with one command has two stops, the row under it may have five, and a step between them
 * clamps to what is actually there.
 */
internal fun stepCellWithinGroup(
    from: CellKey,
    dRow: Int,
    dCol: Int,
    slots: (RemapSimpleGroup, SimpleRowSpec) -> Int,
): CellKey? {
    val rows = from.group.rows
    val row = rows.indexOf(from.row).takeIf { it >= 0 } ?: return null
    val nextRow = (row + dRow).coerceIn(0, rows.lastIndex)
    val spec = rows[nextRow]
    val lastSlot = (slots(from.group, spec) - 1).coerceAtLeast(0)
    return CellKey(from.group, spec, (from.slot + dCol).coerceIn(0, lastSlot))
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

/** Canonical Steam render order for press types. The advanced table uses [pressTypeSortOrder]
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

/**
 * The glyph a press type wears on its tiles and in the type picker (Dylan's set, 2026-09-20).
 *
 * **Regular Press has none** — it is the default case, and marking it would put an icon on
 * nearly every tile in the view saying only "ordinary". Material icons throughout, for the same
 * reason the dropdown arrow is Material's: iconography is platform chrome, not minput's.
 */
internal fun ActivatorType.pressIcon(): ImageVector? = when (this) {
    ActivatorType.FULL_PRESS -> null
    ActivatorType.LONG_PRESS -> Icons.Filled.HourglassEmpty
    ActivatorType.DOUBLE_PRESS -> Icons.Filled.KeyboardDoubleArrowDown
    ActivatorType.CHORDED_PRESS -> Icons.Filled.Workspaces
    ActivatorType.START_PRESS -> Icons.Filled.ArrowDownward
    ActivatorType.RELEASE_PRESS -> Icons.Filled.ArrowUpward
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

/** The PRESS-TYPE glyph leading a tile. Larger than the output's device glyph (Dylan,
 *  2026-09-20): it identifies the tile, where the device glyph only qualifies its name. */
private val TilePressGlyphSize = 19.dp

/** Air between that glyph and the command it fronts. */
private val TilePressGlyphGap = 6.dp

/** The press-type picker's row rhythm. */
private val TypeDialogTitleGap = 10.dp
private val TypeDialogRowCorner = 8.dp
private val TypeDialogRowPadding = 8.dp

/** The row's "+": present enough to invite a tap, faint enough that it doesn't read as a
 *  command. Its COLOR (and opacity) comes from `PressTypePalette`. */
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
 *
 * **Floored at [MinTableRows] rows** (Dylan, 2026-09-20): a one-row group — either stick — made
 * a card barely taller than a single tile, which is a small thing to aim a controller at and a
 * hard one to pan to. A card is at least two rows tall whether it has two rows or not.
 */
internal fun advancedEditorHeight(group: RemapSimpleGroup): Dp {
    val rows = group.rows.size.coerceAtLeast(MinTableRows)
    val table = TableVerticalPadding * 2 +
        TileHeight * rows + TileRowGap * (rows - 1).coerceAtLeast(0)
    return EditorHeaderHeight + table +
        TableScrollbarGap + MinputScrollbarThickness + TableBottomGap
}

/** The shortest a card may be, in tile rows. See [advancedEditorHeight]. */
private const val MinTableRows = 2

