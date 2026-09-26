package com.mappo.ui.screen.remap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
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
import androidx.compose.ui.unit.IntOffset
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
import com.mappo.ui.minput.MinputBoxStroke
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
import kotlinx.coroutines.launch
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
 * **Every card in the scene carries the full header** (Dylan, 2026-09-21). Only the card the
 * camera was parked on used to, on the reasoning that seven live mode pills, cogs, kebabs and
 * Close buttons are seven of everything. But with the scene now a canvas the user roams — by
 * finger as much as by d-pad — "the card the camera is on" stopped meaning "the card being
 * worked on", and a card you could see, scroll and edit tiles in but not change the mode of read
 * as broken rather than restful.
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
                // The utility group states its NAME, not a mode (Dylan, 2026-09-21). Start and
                // Select have no mode to pick — theirs is auto-managed by the repository's
                // bound/cleared rule — so a caption reading "MODE: SINGLE BUTTON" was naming an
                // internal state as though it were a choice.
                currentMode = primaryGroup?.mode.takeIf { group.headerShowsMode && validModes.isNotEmpty() },
                validModes = validModes,
                identity = group.headerLabel(),
                statesMode = group.headerShowsMode,
                enabled = group.headerShowsMode && editable && primaryGroup != null && validModes.size > 1,
                onPick = { mode -> primaryGroup?.let { callbacks.onSetBindingGroupMode(it.id, mode) } },
                modifier = Modifier.focusRequester(headerModePillFocus),
            )
            Spacer(Modifier.weight(1f))
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
            upTarget = tableUpTarget,
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
internal data class LabelEdit(
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
internal data class TypeEdit(val bindingId: Long, val current: ActivatorType)

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
    /** Does this pane's glyph column meet another one on the card's centre line? Its inner edge
     *  then tightens to [CentreGlyphInset], so the two columns read as one cluster. */
    val centred: Boolean,
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
    // The radius within which the grid claims a carried tile. Generous: the cards are spread
    // across the zoomed scene with air between them, and a tile crossing that air is still on
    // its way somewhere. Past it the move reads as abandoned — see [MoveModeState.outOfRange].
    moveState.hitTolerancePx = with(density) { MoveCancelDistance.toPx() }
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
    val endPane = Pane(endRows, mirrored = true, centred = true, scroll = endScroll) { endViewport = it }
    val startPane = Pane(
        rows = if (split) startRows else rows,
        mirrored = if (split) false else mirrored,
        centred = split,
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

    // Whichever table holds the drop target does the stepping; the others keep out of it.
    fun stepMoveTarget(dRow: Int, dCol: Int) = stepMoveTargetBy(
        moveState = moveState,
        stepTarget = stepTarget,
        focusHandle = focusHandle,
        haptic = haptic,
        dRow = dRow,
        dCol = dCol,
        owns = { it.group == group },
    )

    // Resolve the binding group that owns a row's source. Rows in a multi-source group
    // (shoulder = trigger + bumper) resolve independently.
    fun bindingGroupIdFor(spec: SimpleRowSpec): Long? =
        viewingSet?.presetFor(spec.source)?.group?.group?.id

    // The move being previewed. Everything visual (displacement, z-order) reads THESE rather
    // than moveState's fields directly. Scoped to this table: a command carried INTO another
    // group displaces nothing here.
    val previewOrigin = moveState.origin?.takeIf { it.group == group }
    val previewTarget = moveState.target?.takeIf { it.group == group }
    // Is the HOST drawing the tiles in flight above the stage? Then this table draws neither of
    // them: the overlay is standing in for both, and a card can't show a tile leaving it anyway
    // (see [LocalMoveOverlay]). A move that has been CALLED OFF still counts — its tiles are
    // flying home, and they would flash back into their slots the instant the state cleared.
    val overlayHosted = LocalMoveOverlay.current
    val overlayInFlight = overlayHosted &&
        (moveState.origin ?: moveState.returning?.first) != null

    // Is the button that lifted the current tile STILL held? Owned here rather than on the tile
    // because a held activate button auto-repeats while focus moves, so the release can arrive
    // at a different tile than the one that was lifted — there is no single tile that can
    // reliably see both ends of the press. The table sees all of it.
    var liftPress by remember { mutableStateOf(LiftPress.None) }

    fun commitMove(pair: Pair<CellKey, CellKey>?) {
        val (from, to) = pair ?: return
        liftPress = LiftPress.None
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
        // Out of range there is no destination to mark — the tile is going back where it came
        // from, so the origin reads BLUE and the carried tile carries the red.
        moveState.target == key && !moveState.outOfRange ->
            extras.dropZoneValid.copy(alpha = MoveMarkerAlpha)
        previewOrigin == key -> extras.dropZoneOrigin.copy(alpha = MoveMarkerAlpha)
        else -> null
    }

    // Where a tile sits while a move is in flight, as a grid-step offset from its own slot.
    // This is the swap PREVIEW: the lifted tile slides toward the drop target and the tile
    // currently there slides back into the vacated slot, so the exchange is visible before
    // it's committed — and visibly undone the moment the target moves on.
    fun displacementFor(key: CellKey): DpOffset {
        // The overlay carries both ends of the exchange; nothing in the grid moves.
        if (overlayInFlight) return DpOffset.Zero
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
        val headerH = with(density) { TableTopPadding.toPx() }

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
        // On the centre card the two glyph columns meet in the middle, so each drops its INNER
        // padding to [CentreGlyphInset] — the glyphs cluster on the centre line instead of
        // sitting a full column's padding apart across the split (Dylan, 2026-09-21).
        // A centred pane's INNER padding is the one that shrinks — and it is applied as padding
        // rather than by narrowing the box, so the glyph sits that far from the centre line
        // instead of merely re-centring in a narrower column (which only moves it half as far).
        // A mirrored pane's inner edge is its END; a normal one's is its START.
        val innerPad = if (p.centred) CentreGlyphInset else GlyphColumnPadding
        val glyphColumn: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(TileRowGap)) {
                p.rows.forEach { row ->
                    Box(
                        modifier = Modifier
                            .width(TableGlyphSize + GlyphColumnPadding + innerPad)
                            .height(TileHeight)
                            .padding(
                                start = if (p.mirrored) GlyphColumnPadding else innerPad,
                                end = if (p.mirrored) innerPad else GlyphColumnPadding,
                            ),
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
                // LAYER 0 — the tiles themselves.
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
                                    carried = moveState.carriedByOverlay(
                                        key = cellKey,
                                        hasCommand = command != null,
                                        hosted = overlayHosted,
                                    ),
                                    displacement = displacementFor(cellKey),
                                    previewOrigin = previewOrigin,
                                    onCommitMove = { commitMove(it) },
                                    onControllerLift = { liftPress = it },
                                    actions = {
                                        commandCellActions(
                                            cellKey = cellKey,
                                            command = command,
                                            subLabel = subLabel,
                                            title = title,
                                            bindingGroupId = groupId,
                                            overridden = row.overridden,
                                            editable = editable,
                                            callbacks = callbacks,
                                            moveState = moveState,
                                            onLabel = { labelTarget = it },
                                            onType = { typeTarget = it },
                                        )
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

                // LAYER 1 — the move markers, drawn OVER every tile (Dylan, 2026-09-25).
                //
                // They were underneath until now, on the theory that a marker belongs to the
                // SLOT rather than to whatever is standing in it. Over the top reads better
                // during a move: the pair of washes says which tile is going where, instead of
                // being hidden by the two tiles trading places on top of them.
                //
                // Still their own layer rather than a per-cell background: z-order between
                // tiles is per-Row (zIndex only orders siblings), so a marker drawn on a cell
                // could still end up under a neighbour sliding across it. Up here nothing can
                // get over one. Purely decorative — no pointer input, so it takes no touches
                // off the tiles beneath it.
                //
                // While the HOST is drawing the tiles in flight, it draws the markers too: its
                // overlay sits above this whole card, so a marker down here would be under the
                // very tiles it is trying to describe. Drawing both would also double the wash.
                if (!overlayInFlight) Column(verticalArrangement = Arrangement.spacedBy(TileRowGap)) {
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
                    moveModeKeyEvent(
                        event = event,
                        moveState = moveState,
                        // The target may have been carried into another group's table, which
                        // then owns the keys (focus followed it there).
                        owns = { it.group == group },
                        liftPress = liftPress,
                        onLiftPress = { liftPress = it },
                        onStep = { dRow, dCol -> stepMoveTarget(dRow, dCol) },
                        onCommit = { commitMove(it) },
                    )
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
                        .padding(
                            start = 8.dp,
                            end = 8.dp,
                            top = TableTopPadding,
                            bottom = TableBottomPadding,
                        ),
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
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = TableTopPadding, bottom = TableBottomPadding),
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

        CommandTileDialogs(
            labelTarget = labelTarget,
            typeTarget = typeTarget,
            config = config,
            callbacks = callbacks,
            onCloseLabel = { labelTarget = null },
            onCloseType = { typeTarget = null },
        )
    }
}

/**
 * The verbs one tile's menu offers, wired to the callbacks that carry them out.
 *
 * Shared by the advanced table and the basic view's EDIT MODE (Dylan, 2026-09-22): a tile is the
 * same object in both, so its menu has to be the same menu. One that differed by where the tile
 * happened to be drawn would be a different control wearing the same face.
 *
 * [onLabel] / [onType] hand back what the two dialog verbs want to edit, because the dialogs
 * outlive the menu that summoned them (and the tile, once it moves) — they belong to whoever
 * hosts the tiles. See [CommandTileDialogs], the other half.
 */
internal fun commandCellActions(
    cellKey: CellKey,
    command: RowCommand?,
    /** The sub-input's own name ("A Button"), for the editors this opens. */
    subLabel: String,
    /** That name qualified by the press type — what an editor opened from here is titled. */
    title: String,
    bindingGroupId: Long?,
    /** Does the viewed LAYER override this row? Only meaningful in layer view. */
    overridden: Boolean,
    editable: Boolean,
    callbacks: RemapGroupEditorCallbacks,
    moveState: MoveModeState<CellKey>,
    onLabel: (LabelEdit) -> Unit,
    onType: (TypeEdit) -> Unit,
): List<MinputAction> = if (!editable) {
    // Layer view is read-only here: editing routes to the full-screen editor (which materializes
    // the override onto the layer), and an input the layer actually overrides can be handed back
    // to base.
    layerTileActions(
        overridden = overridden,
        onEdit = { callbacks.onOpenInputEditor(cellKey.source, cellKey.inputKey, subLabel) },
        onClearOverride = { callbacks.onClearOverride(cellKey.source, cellKey.inputKey) },
    )
} else {
    tileActions(
        defined = command != null,
        clipboardOccupied = callbacks.clipboardOccupied,
        onEdit = {
            if (command != null) {
                callbacks.onEditCommand(command.id, command.output, title)
            } else if (bindingGroupId != null) {
                // The "+" makes the command first, then opens the picker on it — a new command
                // starts as a Regular Press and is retyped from the same menu.
                callbacks.onAddCommand(bindingGroupId, cellKey.inputKey, ActivatorType.FULL_PRESS, title)
            }
        },
        onLabel = {
            command?.let {
                onLabel(
                    LabelEdit(
                        bindingId = it.id,
                        label = it.binding.label.orEmpty(),
                        outputs = listOf(it.output),
                        showDeviceIcon = it.binding.showDeviceIcon,
                        showDeviceInitials = it.binding.showDeviceInitials,
                    ),
                )
            }
        },
        onType = { command?.let { onType(TypeEdit(it.id, it.type)) } },
        onSettings = { command?.let { callbacks.onConfigure(it.activator.id, title) } },
        onCopy = { command?.let { callbacks.onCopyCommand(it.id) } },
        onPaste = {
            if (bindingGroupId != null) {
                callbacks.onPasteCommand(command?.id, bindingGroupId, cellKey.inputKey)
            }
        },
        onMove = { moveState.pickUp(cellKey, byPointer = false) },
        onClear = { command?.let { callbacks.onDeleteCommand(it.id) } },
    )
}

/** The two dialogs a tile's menu can summon. Hosted by whoever draws the tiles, not by the tile:
 *  both outlive the menu, and one of them outlives a tile that has just been moved. */
@Composable
internal fun CommandTileDialogs(
    labelTarget: LabelEdit?,
    typeTarget: TypeEdit?,
    config: ControllerConfig?,
    callbacks: RemapGroupEditorCallbacks,
    onCloseLabel: () -> Unit,
    onCloseType: () -> Unit,
) {
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
            onClose = onCloseLabel,
        )
    }
    typeTarget?.let { target ->
        PressTypeDialog(
            current = target.current,
            onPick = { type -> callbacks.onSetPressType(target.bindingId, type) },
            onClose = onCloseType,
        )
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
 * **The "+"** ([output] null): unfilled, a dimmed plus inside a hairline ring ([tileOutline]).
 * The missing FILL is the signal — the create affordance shouldn't compete with the commands
 * beside it — while the ring still gives the empty slot a footprint to aim at.
 *
 * Output glyph and text deliberately do NOT take the press accent (readability); the container
 * tint, the press glyph and the "+" carry it.
 */
@Composable
internal fun CommandTile(
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
    /** Is the stage's [MoveOverlay] drawing this tile right now? Then the one in the grid goes
     *  invisible — its slot, its focus ring and its drop marker stay exactly where they are, and
     *  the tile itself is over in the overlay, where no card's edge can clip it. */
    carried: Boolean,
    /** Grid-step offset this tile should animate to while a move previews a swap. */
    displacement: DpOffset,
    /** The lifted cell of the move currently being previewed — live OR still committing.
     *  Drives z-order and keys the slide animation, so the visual survives the handover from
     *  "dragging" to "written, waiting for the reload". */
    previewOrigin: CellKey?,
    onCommitMove: (Pair<CellKey, CellKey>?) -> Unit,
    /** Reports a controller-driven lift, so the table can track whether the button that
     *  started it is still held. */
    onControllerLift: (LiftPress) -> Unit,
    actions: () -> List<MinputAction>,
    modifier: Modifier = Modifier,
    /** How big this tile is and how much it says — the table's, or the basic view's row tile. */
    look: TileLook = TableTileLook,
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

    // The tint's own alpha IS the strength (see PressTypePalette); a fully transparent tint
    // leaves the plain elevated container, which is exactly what the Press column wants.
    val container = if (output == null) Color.Transparent else colors.tile.compositeOver(MinputElevatedContainer)
    val shape = RoundedCornerShape(look.corner)

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
            onControllerLift(LiftPress.CommitsOnRelease)
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
            .width(look.width)
            .height(look.height)
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
                    // ── Coyote time (Dylan, 2026-09-26) ──
                    //
                    // A DIRECTION pressed while the hold is still ripening lifts the tile NOW
                    // rather than throwing the gesture away. Waiting out the long-press before
                    // you are allowed to start moving is the kind of delay that teaches people
                    // to distrust a control, so a user who has clearly committed — button down,
                    // already steering — gets the lift they were heading for.
                    //
                    // The lift is [LiftPress.Spent]: the press that caused it is still down, but
                    // they are done with it, so its release must not confirm anything. Returning
                    // false hands this very keystroke on to the host's move handler, which walks
                    // the drop target — so the first press both lifts and steps, which is what
                    // it would have done had the hold already ripened.
                    val steering = event.type == KeyEventType.KeyDown && event.key in MoveStepKeys
                    if (steering && movable && keyDownAt != 0L && !moveState.active) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        moveState.pickUp(cellKey, byPointer = false)
                        onControllerLift(LiftPress.Spent)
                        keyDownAt = 0L
                        sawOwnKeyDown = false
                        return@onKeyEvent false
                    }
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
                if (carried) alpha = 0f
            }
            .minputInteractiveMotion(pressInteraction)
            .clip(shape)
            .background(container, shape)
            .then(tileOutline(container, output != null, shape, look.corner))
            .focusProperties { canFocus = false }
            .clickable(
                interactionSource = pressInteraction,
                indication = minputIndication(),
                onClickLabel = if (output == null) "Assign command" else "Command options",
                onClick = ::activate,
            ),
        contentAlignment = Alignment.Center,
    ) {
        TileContent(
            colors = colors,
            pressType = pressType,
            output = output,
            label = label,
            outputText = outputText,
            showDeviceIcon = showDeviceIcon,
            look = look,
        )

        // Beside the tile, not over it: the cell IS the thing being acted on, and a menu
        // dropped on top of it hides the command you're deciding about. Mirrors to the start
        // side automatically for the rightmost columns. The menu measures this Box (its
        // anchor) itself — nothing to pass.
        MinputActionMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            // Built only when the menu is actually up. Every card's table composes at once now
            // (see RemapStage's `live`), and a closed menu's eight verbs — each with its own
            // callback — were being allocated for every tile in the scene on every composition.
            actions = if (menuOpen) actions() else emptyList(),
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
 * **Does the HOST draw the tile in flight?** (Dylan, 2026-09-21.)
 *
 * A card clips: its body scrolls in both directions and its rows sit beside a frozen glyph
 * column, so a tile lifted out of the top-right slot had its swollen corner sliced off by the
 * viewport's edge, and a tile carried toward another group simply stopped at its own card's rim —
 * the move committed correctly, but nothing on screen said so.
 *
 * The fix is to draw the tile being carried OUTSIDE every card, at the top of the stage, where
 * nothing clips it and the whole scene is one coordinate space ([MoveOverlay]). Where that
 * happens, the tiles in the grid that the overlay is standing in for hide themselves rather than
 * rendering twice. A host that doesn't draw the overlay keeps the old in-grid slide, which is
 * correct as far as its own card's edges.
 */
internal val LocalMoveOverlay = staticCompositionLocalOf { false }

/**
 * The tiles IN FLIGHT, drawn above the whole stage.
 *
 * Up to two: the tile being CARRIED, and — when it is hovering over a command rather than a row's
 * "+" — the tile it would displace, sliding the other way. Both are drawn here rather than in
 * their own cards because either end of the exchange may be in a different card, and a card
 * clips (see [LocalMoveOverlay]).
 *
 * Positions come from the move state's own cell registry, which is in WINDOW space — the one
 * space every card shares — converted into this overlay by [stageOrigin], and re-read every
 * frame: the registry is a plain map, and both ends of a flight keep moving (the camera pans
 * toward the group a command is carried into, and that card scrolls the destination slot into
 * view).
 *
 * **A tile in flight is positioned RELATIVE TO THE SLOT IT IS AIMED AT, never in absolute stage
 * coordinates** (Dylan, 2026-09-21). [carriedResidual] is how far it still has to go; it decays
 * to nothing over [MoveSlideMillis], and the slot it is measured from is re-read each frame. So
 * the tile lands exactly on its slot however far the scene has travelled underneath it, instead
 * of animating toward where the slot USED to be and then shuffling onto it once the pan
 * finished. Re-aiming mid-flight (the d-pad walking the target on) re-anchors the residual
 * rather than restarting the journey, so the tile never jumps back to where it was lifted from.
 */
@Composable
internal fun MoveOverlay(
    moveState: MoveModeState<CellKey>,
    /** The stage's own top-left in window space. */
    stageOrigin: Offset,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    modifier: Modifier = Modifier,
    /** The size the tiles in flight are drawn at — whichever tile the user actually picked up. */
    look: TileLook = TableTileLook,
) {
    val live = moveState.origin
    // A move that has been called off is still on screen: its tiles fly home rather than
    // teleporting, and the overlay keeps drawing them until `settled()` says they have arrived.
    val settling = moveState.returning
    val origin = live ?: settling?.first ?: return
    val hovered = (if (live != null) moveState.target else settling?.second) ?: origin
    val order = LocalCommandOrder.current
    val lifted = rowCommandsFor(viewingSet, viewingLayer, origin.row, order).getOrNull(origin.slot)
        ?: return
    fun commandAt(key: CellKey): RowCommand? =
        rowCommandsFor(viewingSet, viewingLayer, key.row, order).getOrNull(key.slot)

    fun homeOf(key: CellKey): Offset? =
        moveState.boundsOf(key)?.takeIf { !it.isEmpty }?.topLeft?.minus(stageOrigin)

    val start = homeOf(origin) ?: Offset.Zero
    var originHome by remember(origin) { mutableStateOf(start) }
    /** Where the carried tile is aimed — the slot it is measured FROM. */
    var aimHome by remember(origin) { mutableStateOf(start) }
    /** How far the carried tile still is from that slot. Always decaying toward zero. */
    val carriedResidual = remember(origin) { Animatable(Offset.Zero, Offset.VectorConverter) }

    /**
     * **Every cell this move has displaced, and how far each one is into the vacated slot** —
     * 1 sitting in it, 0 back where it belongs (Dylan, 2026-09-25).
     *
     * Only the CURRENT target used to be drawn, so walking the carry from one occupied slot to
     * the next made the previous tile teleport home the instant it stopped being the target. A
     * cell that stops being the target retreats instead, and the same Animatable is re-aimed
     * rather than restarted, so re-entering a slot mid-retreat reverses it from where it is.
     */
    val retreat = remember(origin) { mutableStateMapOf<CellKey, Animatable<Float, AnimationVector1D>>() }
    /** Their slots, re-read every frame exactly as [originHome] is. */
    val homes = remember(origin) { mutableStateMapOf<CellKey, Offset>() }

    LaunchedEffect(hovered, origin) {
        // A row's "+" displaces nothing: that is an ADD, and nothing comes back the other way.
        val displaced = hovered.takeIf { it != origin && commandAt(it) != null }
        if (displaced != null) {
            moveState.holdInFlight(displaced)
            // Seed its slot before the first frame: the grid tile is already hidden by the hold,
            // so a frame where this one has no position to draw at is a blink.
            homeOf(displaced)?.let { homes[displaced] = it }
            val slide = retreat.getOrPut(displaced) { Animatable(0f) }
            launch { slide.animateTo(1f, tween(MoveSlideMillis)) }
        }
        retreat.keys.toList().forEach { key ->
            if (key == displaced) return@forEach
            val slide = retreat.getValue(key)
            launch {
                slide.animateTo(0f, tween(MoveSlideMillis))
                retreat.remove(key)
                moveState.releaseInFlight(key)
            }
        }
    }
    // Whatever is still held when the overlay stops drawing has nobody left to release it, and a
    // cell held in flight forever is a permanently invisible tile.
    DisposableEffect(moveState) { onDispose { moveState.clearInFlight() } }

    LaunchedEffect(origin, stageOrigin) {
        var aimed = origin
        var wasLive = true
        while (isActive) {
            withFrameNanos { }
            val liveNow = moveState.origin
            val hoveredNow =
                (if (liveNow != null) moveState.target else moveState.returning?.second) ?: origin
            // Once the move is called off the tile is aimed at its OWN slot: the same flight,
            // run backwards.
            val aimNow = if (liveNow != null) hoveredNow else origin
            // A cell scrolled entirely out of its viewport registers an empty rect; keeping the
            // last real one stops a tile in flight from snapping to the stage's corner.
            homeOf(origin)?.let { originHome = it }
            retreat.keys.toList().forEach { key -> homeOf(key)?.let { homes[key] = it } }
            val next = homeOf(aimNow) ?: aimHome
            // **The move ENDING is a hand-off too.** Released back onto its own slot, the aim
            // never changed, so nothing launched the decay: the tile hung in mid-air for the
            // length of the slide and then teleported home when the overlay stopped drawing it
            // (Dylan, 2026-09-25).
            val ended = wasLive && liveNow == null
            wasLive = liveNow != null
            when {
                // Under the finger: the residual is simply whatever separates the finger from
                // the slot it happens to be over, so releasing anywhere leaves the tile exactly
                // where it is and the decay below carries it from there.
                //
                // Measured from the FINGER, not from the slot the tile was lifted out of — see
                // [MoveModeState.carriedTopLeft]. The lifted slot moves while the body
                // edge-scrolls under the carry, and can leave the viewport altogether.
                liveNow != null && moveState.pointerDriven -> {
                    aimed = aimNow
                    carriedResidual.snapTo(moveState.carriedTopLeft(stageOrigin) - next)
                }
                aimNow != aimed || ended -> {
                    // Re-anchor onto the new slot WITHOUT moving the tile: everything it still
                    // has to travel becomes residual.
                    carriedResidual.snapTo(aimHome + carriedResidual.value - next)
                    aimed = aimNow
                    launch { carriedResidual.animateTo(Offset.Zero, tween(MoveSlideMillis)) }
                }
            }
            aimHome = next
        }
    }

    // The tiles have arrived; let the state machine forget the move.
    LaunchedEffect(settling) {
        if (settling == null) return@LaunchedEffect
        delay(MoveSlideMillis.toLong())
        moveState.settled()
    }

    // Where the exchange's two ends are marked, in this overlay's own space. The stage draws
    // them here rather than leaving them to the cards because this layer is ABOVE every card:
    // a marker inside one would sit under the tiles it is describing (Dylan, 2026-09-25).
    val extras = LocalMappoExtraColors.current
    val outOfRange = live != null && moveState.outOfRange
    Box(modifier.fillMaxSize()) {
        // The displaced tiles first, then the markers, then the carried one. That order is the
        // whole point: a tile sliding into the vacated slot must read as going UNDER the blue
        // marker, so which slot it is heading for is visible — while the tile in your hand stays
        // on top of everything, because it is the thing you are holding.
        //
        // A PROGRESS value rather than an animated offset, so both endpoints can keep moving
        // (the camera pans, the cards scroll) without the animation restarting.
        retreat.forEach { (key, slide) ->
            val command = commandAt(key) ?: return@forEach
            val home = homes[key] ?: return@forEach
            FloatingTile(
                command = command,
                config = config,
                position = lerp(home, originHome, slide.value),
                look = look,
            )
        }
        FloatingTile(
            command = lifted,
            config = config,
            position = aimHome + carriedResidual.value,
            look = look,
            // Carried out past the grid's reach: letting go now puts it back, and saying so on
            // the tile itself is the only place the user is actually looking.
            wash = extras.dropZoneInvalid.takeIf { outOfRange },
        )
        // BOTH markers over the carried tile, drawn last (Dylan, 2026-09-25 / 09-26).
        //
        // The tile in your hand sits right on the slot it is landing in, so underneath it the
        // green was the one marker you could never see. And the blue over the ORIGIN slot is what
        // now says "this is the tile you just picked up" — at the instant of a lift the carried
        // tile is still on that slot, so it wears the blue. That replaced a slight upscale on
        // the lifted tile, which read oddly once the washes went on top of it.
        if (live != null) {
            MoveMarker(extras.dropZoneOrigin, originHome, look)
            // No destination to mark while the move is out of range: it is going home, and it is
            // wearing red instead.
            if (!outOfRange && hovered != origin) MoveMarker(extras.dropZoneValid, aimHome, look)
        }
    }
}

/** One of the exchange's ends, washed over whatever is standing in it. */
@Composable
private fun MoveMarker(color: Color, position: Offset, look: TileLook) {
    Box(
        Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(look.width)
            .height(look.height)
            .clip(RoundedCornerShape(look.corner))
            .background(color.copy(alpha = MoveMarkerAlpha)),
    )
}

/** One tile in flight: the same face it wears in the grid, placed in the overlay's own space. */
@Composable
private fun FloatingTile(
    command: RowCommand,
    config: ControllerConfig?,
    position: Offset,
    look: TileLook = TableTileLook,
    /** Painted over the tile — the cancel signal, when the carry has left the grid's reach. */
    wash: Color? = null,
) {
    val display = commandDisplay(command.binding, listOf(command.output), config)
    val colors = command.type.columnColors()
    val container = colors.tile.compositeOver(MinputElevatedContainer)
    val shape = RoundedCornerShape(look.corner)
    Box(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .width(look.width)
            .height(look.height)
            .clip(shape)
            .background(container, shape)
            .then(tileOutline(container, defined = true, shape = shape, corner = look.corner))
            .then(
                if (wash != null) {
                    // Over the content, inside the clip: the tile itself goes the wash's colour
                    // rather than wearing a badge.
                    Modifier.drawWithContent {
                        drawContent()
                        drawRect(wash.copy(alpha = MoveWashAlpha))
                    }
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        TileContent(
            colors = colors,
            pressType = command.type,
            output = command.output,
            label = display.label,
            outputText = display.text,
            showDeviceIcon = display.glyph != null,
            look = look,
        )
    }
}

/**
 * **A tile's CHROME with nothing on it** — the fill, the press-type tint, the ring, and the "+"
 * where the slot is still empty.
 *
 * It exists for the basic view's morph (Dylan, 2026-09-24), which fades the button in behind a
 * label the row is already drawing: the label must not be drawn twice, so the arriving tile
 * brings only its face. It is also, deliberately, the same face [CommandTile] wears — same
 * fill, same ring, same plus — so the handover to the real tile at the end of the travel shows
 * nothing at all.
 */
@Composable
internal fun TileChrome(
    pressType: ActivatorType?,
    /** Does this slot hold a command? A "+" is unfilled and wears the hairline ring instead. */
    defined: Boolean,
    look: TileLook,
    modifier: Modifier = Modifier,
) {
    val colors = (pressType ?: ActivatorType.FULL_PRESS).columnColors()
    val container = if (!defined) Color.Transparent else colors.tile.compositeOver(MinputElevatedContainer)
    val shape = RoundedCornerShape(look.corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(container, shape)
            .then(tileOutline(container, defined, shape, look.corner)),
        contentAlignment = Alignment.Center,
    ) {
        if (!defined) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(if (look.compact) RowTilePlusSize else EmptyTilePlusSize),
                tint = colors.plus,
            )
        }
    }
}

/**
 * A tile's ring: the family bevel on a command, a hairline outline on the row's "+".
 *
 * The "+" used to be strokeless — chrome-less by spec, so it wouldn't compete with the commands
 * beside it. It went the other way (Dylan, 2026-09-21): with nothing but a glyph, the slot had no
 * footprint, so neither the size of the thing you were about to create nor the fact that it is a
 * drop target read at all. The ring is faint enough to keep the hierarchy it was avoiding.
 */
@Composable
private fun tileOutline(
    container: Color,
    defined: Boolean,
    shape: RoundedCornerShape,
    corner: Dp = TileCorner,
): Modifier =
    if (defined) {
        Modifier.border(minputBevelBorder(container, corner), shape)
    } else {
        Modifier.border(
            MinputBoxStroke,
            MaterialTheme.colorScheme.outline.copy(alpha = EmptyTileOutlineAlpha),
            shape,
        )
    }

/**
 * What a tile shows — the "+", or a command's press glyph, label and name.
 *
 * Shared by the tile in the grid and by the floating copy the stage carries during a move
 * ([MoveOverlay]), so a tile in flight is the same object the user picked up.
 *
 * **The press glyph is positioned, not packed** (Dylan, 2026-09-21). It used to lead a Row, which
 * made it part of the tile's flex: a Long Press tile centred its name in what was left of the
 * tile rather than in the tile, so the same command sat at two different x-offsets depending on
 * its press type. The glyph is now pinned to the start edge and the name block is centred in the
 * tile, with the glyph's width reserved on BOTH sides so the two can never collide.
 */
@Composable
private fun TileContent(
    colors: PressTypeColors,
    pressType: ActivatorType?,
    output: BindingOutput?,
    label: String?,
    outputText: String,
    showDeviceIcon: Boolean,
    look: TileLook = TableTileLook,
) {
    if (output == null) {
        // The row's create affordance. A plus and nothing else: it is a slot, not a command.
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(if (look.compact) RowTilePlusSize else EmptyTilePlusSize),
            // Alpha rides in the palette color itself — no extra .alpha() here, or the
            // value in Theme.kt would stop being what renders.
            tint = colors.plus,
        )
        return
    }
    if (look.compact) {
        RowTileContent(
            colors = colors,
            pressType = pressType,
            output = output,
            label = label,
            outputText = outputText,
            showDeviceIcon = showDeviceIcon,
        )
        return
    }
    val pressGlyph = pressType?.pressIcon()
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = TileContentPadding)) {
        // The device glyph belongs to the COMMAND line, not to the tile (Dylan, 2026-09-19):
        // spanning both rows it read as an icon for the label as well, and left the label hanging
        // off the start of the thing it names. The label now sits centred OVER its command.
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                // Reserved symmetrically, so the name stays centred in the TILE. Regular Press
                // has no glyph and no gutter, and takes the whole width.
                .padding(horizontal = if (pressGlyph != null) TilePressGlyphSize + TilePressGlyphGap else 0.dp),
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
        if (pressGlyph != null) {
            Icon(
                pressGlyph,
                contentDescription = pressType.columnLabel(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = TilePressGlyphStartBias)
                    .size(TilePressGlyphSize),
                tint = colors.icon,
            )
        }
    }
}

/**
 * A tile's face at ROW scale — the basic view's edit mode (Dylan, 2026-09-22).
 *
 * Deliberately says less than the table's tile, in two ways:
 *
 *  - **One line, not a stack.** A labelled command shows its label INSTEAD of its output name
 *    rather than above it, which is exactly what the resting row it replaces already showed
 *    ([CommandDisplay.line]) — so entering edit mode puts a capsule around the row's own words
 *    instead of rewriting them. The device glyph follows the same rule it does at rest: it
 *    qualifies an output NAME, so a command the user has named goes bare
 *    ([CommandDisplay.lineGlyph]).
 *  - **No press-type glyph.** With this little tile to spend, the colour carries the press type
 *    on its own, and the text goes white to hold against it ([RowTileTintedText]).
 */
@Composable
private fun RowTileContent(
    colors: PressTypeColors,
    pressType: ActivatorType?,
    output: BindingOutput,
    label: String?,
    outputText: String,
    showDeviceIcon: Boolean,
) {
    // Regular Press has no tint — its tile IS the ordinary container — so it keeps the resting
    // view's own content colour rather than shouting in white.
    val tinted = pressType != null && pressType != ActivatorType.FULL_PRESS
    val content = if (tinted) RowTileTintedText else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = RowTileContentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (label == null && showDeviceIcon) {
            InputGlyphs.outputPainter(output)?.let { painter ->
                Icon(
                    painter,
                    contentDescription = null,
                    modifier = Modifier.size(TileOutputGlyphSize),
                    tint = content,
                )
                Spacer(Modifier.width(MinputGlyphLabelGap))
            }
        }
        Text(
            text = label ?: outputText,
            style = minputMiniTextStyle(),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
    // A screen-space step, converted into the row's own slot direction — see [slotsRunLeftward].
    val nextSlot = from.slot + dCol * from.group.slotStepFor(from.row)
    return CellKey(from.group, spec, nextSlot.coerceIn(0, lastSlot))
}

/**
 * Walk a controller-driven move's drop target one step, taking focus with it.
 *
 * Focus FOLLOWS the target because focus is cell-anchored: leaving the ring on the cell the tile
 * was lifted from strands it behind the move, and it means the tile the user then activates IS
 * the destination, so confirming needs no focus change of its own. The handle comes from the
 * host, so a destination in ANOTHER group is reachable the same way.
 *
 * [owns] lets a handler that only speaks for part of the view (one table in the zoomed scene)
 * keep out of a move whose target has been carried elsewhere.
 */
internal fun stepMoveTargetBy(
    moveState: MoveModeState<CellKey>,
    stepTarget: (CellKey, Int, Int) -> CellKey?,
    focusHandle: (CellKey) -> FocusRequester,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    dRow: Int,
    dCol: Int,
    owns: (CellKey) -> Boolean = { true },
) {
    val current = moveState.target ?: return
    if (!owns(current)) return
    val next = stepTarget(current, dRow, dCol) ?: return
    if (next == current) return
    // A tick per cell crossed: with no finger on the screen the haptic is the only confirmation
    // that the drop target actually moved.
    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    moveState.moveTargetTo(next)
    runCatching { focusHandle(next).requestFocus() }
}

/**
 * **What the release of the activate button that LIFTED a tile will mean** — the three ways a
 * controller move can be driven (Dylan, 2026-09-26).
 *
 *  - [CommitsOnRelease]: held through the lift and still held. Let go over a new slot and the
 *    move confirms — ordinary drag and drop.
 *  - [None]: no lifting press outstanding, either because it was released without moving (the
 *    user picked a tile up to look around with) or because the lift came some other way. A press
 *    now is the confirm.
 *  - [Spent]: the tile was lifted by a press the user had ALREADY stopped waiting on — they
 *    started navigating before the hold ripened, so the lift happened under them. Its release
 *    means nothing; a later press confirms, as in [None].
 */
internal enum class LiftPress { None, CommitsOnRelease, Spent }

/**
 * The keyboard while a CONTROLLER move is in flight: arrows walk the drop target, B / Escape
 * calls it off, the activate keys confirm.
 *
 * It belongs to whoever HOSTS the tiles rather than to the focused tile, because a held button
 * auto-repeats while focus moves — a press and its release can land on different tiles, so no
 * single tile sees both ends of the gesture. Pointer-driven moves never take this path; the
 * finger is already saying where to land.
 *
 * Returns whether the event was consumed. Consuming the arrows is what stops ordinary focus
 * traversal, leaving focus to track the drop target instead.
 */
internal fun moveModeKeyEvent(
    event: androidx.compose.ui.input.key.KeyEvent,
    moveState: MoveModeState<CellKey>,
    /** Is the current drop target one this handler speaks for? */
    owns: (CellKey) -> Boolean,
    /** What the release of the still-held lifting press will mean. */
    liftPress: LiftPress,
    onLiftPress: (LiftPress) -> Unit,
    onStep: (dRow: Int, dCol: Int) -> Unit,
    onCommit: (Pair<CellKey, CellKey>?) -> Unit,
): Boolean {
    if (!moveState.active || moveState.pointerDriven) return false
    val target = moveState.target ?: return false
    if (!owns(target)) return false

    // Activate FIRST, and on the key's RELEASE — which is why this sits above the key-down
    // filter below.
    if (event.key in TileActivateKeys) {
        if (event.type == KeyEventType.KeyUp) {
            val press = liftPress
            onLiftPress(LiftPress.None)
            val confirms = when (press) {
                // A press made AFTER the lift: pressing again is the confirm.
                LiftPress.None -> true
                // Releasing the button that LIFTED the tile confirms, provided the target moved
                // while it was held — ordinary drag-and-drop. Released without having moved, it
                // reads as the user taking their thumb off a tile they've picked up to look
                // around with, so the move stays live and a later press confirms.
                LiftPress.CommitsOnRelease -> moveState.target != moveState.origin
                // The lift the user never waited for: they were already navigating when it
                // happened, so this release is just the tail of a press they are done with.
                LiftPress.Spent -> false
            }
            if (confirms) onCommit(moveState.commit())
        }
        return true
    }

    if (event.type != KeyEventType.KeyDown) return true
    return when (event.key) {
        Key.DirectionUp -> { onStep(-1, 0); true }
        Key.DirectionDown -> { onStep(1, 0); true }
        Key.DirectionLeft -> { onStep(0, -1); true }
        Key.DirectionRight -> { onStep(0, 1); true }
        Key.Back, Key.Escape, Key.ButtonB -> {
            moveState.cancel()
            onLiftPress(LiftPress.None)
            true
        }
        else -> true // swallow the rest so focus can't wander mid-move
    }
}

/**
 * Is the stage's [MoveOverlay] standing in for the tile at [key] — and should the one in the
 * grid therefore go invisible?
 *
 * The tile being carried, the one it would displace, and any the carry has ALREADY displaced and
 * is still flying home ([MoveModeState.inFlight]) — walk a tile across three occupied slots and
 * all three of the tiles it disturbed are in the air at once. A move that has been CALLED OFF
 * still counts: its tiles are flying home and would flash back into their slots the instant the
 * state cleared.
 *
 * The current target is named explicitly as well as being held in flight, because the drawer
 * takes hold of it a frame after the target changes and a one-frame double is a flicker.
 */
internal fun MoveModeState<CellKey>.carriedByOverlay(
    key: CellKey,
    hasCommand: Boolean,
    hosted: Boolean,
): Boolean {
    if (!hosted) return false
    val flightOrigin = origin ?: returning?.first ?: return false
    val flightHovered = target ?: returning?.second
    return key == flightOrigin ||
        (hasCommand && key in inFlight) ||
        (hasCommand && key == flightHovered && flightHovered != flightOrigin)
}

/** The scrolling table of [group]'s editor — one per group in the scene. */
internal fun editorTableTestTag(group: RemapSimpleGroup): String = "group-editor-table:${group.name}"

/** Keys that activate a focused tile. Mirrors what Compose's own `clickable` accepts, plus the
 *  gamepad A button so a controller's primary action works without a d-pad center. */
/** The keys that walk a move's drop target — and, before a hold has ripened, the ones that mean
 *  "I have started moving, lift it now". See the coyote branch in [CommandTile]. */
internal val MoveStepKeys = setOf(
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
)

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
    /** The group's own name. Normally only a screen reader sees it — the glyph states the
     *  group on screen — but a group with no mode to offer prints it as the caption. */
    identity: String,
    /** Does this caption say what MODE the group is in? False for a group whose mode is not the
     *  user's to pick (the utility buttons), which prints its own name instead. It keeps the
     *  same glyph, treatment and inset either way, so the captions line up across the scene. */
    statesMode: Boolean,
    enabled: Boolean,
    onPick: (BindingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val caption = if (statesMode) {
        "$ModeLabelPrefix ${(currentMode?.displayNameFor(source) ?: ModeLabelDefault).uppercase()}"
    } else {
        identity.uppercase()
    }
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
                text = caption,
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
private val TileWidth = 134.dp

/** Cell height. Taller than the old 38dp command rows because a cell now stacks an overline
 *  label above the output text where the row had a separate label field beside it — and taller
 *  again on 2026-09-21 (Dylan), narrowing in the same pass: a tile reads as a chunkier key that
 *  way, and more of them fit across a row. */
private val TileHeight = 46.dp

/** Horizontal gap between columns, and between the glyph column and the body. */
private val TileGap = 4.dp

/** VERTICAL gap BETWEEN rows. Separate from [TileGap] on purpose: the rows want more air than
 *  the columns do — it gives the enlarged input glyphs room and uses up vertical space the
 *  full-height panel has going spare.
 *
 *  Strictly between: it is applied by an inner Column that holds ONLY the rows, so raising it
 *  can't also push the header away or pad the bottom of the table. Those are the table's own
 *  top/bottom padding, and they stay put.
 *
 *  Tightened from 16dp on 2026-09-21 (Dylan), in the same pass that made the tiles taller: the
 *  rows carry more weight of their own now and needed less air between them. */
private val TileRowGap = 11.dp

/** Input glyphs render LARGER here than in the old rows — with the press-type word gone from
 *  the cell, the glyph is the row's only identity, so it carries the weight of one. */
private val TableGlyphSize = 38.dp

/** Breathing room either side of the input glyph, inside the frozen column. */
private val GlyphColumnPadding = 11.dp

/** The frozen glyph column — derived, so widening the glyph or its padding can't leave the
 *  column too narrow for what it holds. */
private val GlyphColumnWidth = TableGlyphSize + GlyphColumnPadding * 2

/** The INNER padding of a glyph column on the centre card, where two panes meet: the two glyph
 *  columns sat a full [GlyphColumnPadding] apart on either side of [CentreSplitGap], which put a
 *  visible gulf down the middle of the card (Dylan, 2026-09-21; tightened again the same day).
 *  The outer side keeps its full padding, so only the meeting edge closes up. */
private val CentreGlyphInset = 2.dp

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

/** How far in from the tile's content edge the press glyph sits (Dylan, 2026-09-21). Flush
 *  against a capsule's start it read as crowded by the curve; the glyphs ink well inside their
 *  boxes, so a couple of dp buys the optical inset without a visible gap. */
private val TilePressGlyphStartBias = 3.dp

/** The press-type picker's row rhythm. */
private val TypeDialogTitleGap = 10.dp
private val TypeDialogRowCorner = 8.dp
private val TypeDialogRowPadding = 8.dp

/** The row's "+": present enough to invite a tap, faint enough that it doesn't read as a
 *  command. Its COLOR (and opacity) comes from `PressTypePalette`. */
private val EmptyTilePlusSize = 22.dp

/** How strongly the "+" tile's outline reads. A hairline ring (Dylan, 2026-09-21) that gives the
 *  create affordance a FOOTPRINT — a bare glyph floating in the row didn't say how big the thing
 *  you were about to make would be, nor that the slot was a drop target. Deliberately far below
 *  the bevel the commands wear: it marks an empty slot, not another command. */
private const val EmptyTileOutlineAlpha = 0.22f

/** How long a displaced tile takes to slide aside during a swap preview. */
private const val MoveSlideMillis = 200

/**
 * How far a carried tile may stray from every cell before the move reads as ABANDONED — carry it
 * further and the tile goes red, and letting go puts it back (Dylan, 2026-09-25).
 *
 * **Deliberately smaller than a tile**, so the open middle of the grid — the controller's own
 * column — is the cancel. That is the gesture: carry a tile out over the controller and let go.
 * Dylan asked for it explicitly after a first pass at 160dp, which was wide enough to reach
 * across that column and so left touch moves with no way to call them off at all.
 *
 * Measured from the tile's EDGES, not its centre, so "close-ish to a tile" still lands on it: a
 * tile whose rim is within this of a slot is claimed by that slot even while most of it hangs
 * over the gap.
 */
internal val MoveCancelDistance = 23.dp
/** How strongly the origin / landing markers wash their cell. Low enough to read as a marked
 *  SLOT rather than a filled tile. */
internal const val MoveMarkerAlpha = 0.3f

/** How strongly the cancel wash covers the tile it is refusing. Stronger than a slot marker —
 *  it has to change what the tile READS as, not annotate the space around it. */
private const val MoveWashAlpha = 0.55f

/** How close to the viewport edge a dragging finger must get before the table scrolls under
 *  it, and how far it scrolls per frame while it stays there. */
private val EdgeScrollZone = 28.dp
private val EdgeScrollStep = 6.dp

/**
 * Vertical breathing room inside the table.
 *
 * Split top from bottom on 2026-09-21 (Dylan): the caption above and the first row of tiles sat
 * too far apart, and the two were one value, so closing the gap under the header also shaved the
 * air under the last row.
 */
private val TableTopPadding = 2.dp
private val TableBottomPadding = 6.dp

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
    val table = TableTopPadding + TableBottomPadding +
        TileHeight * rows + TileRowGap * (rows - 1).coerceAtLeast(0)
    return EditorHeaderHeight + table +
        TableScrollbarGap + MinputScrollbarThickness + TableBottomGap
}

/** The shortest a card may be, in tile rows. See [advancedEditorHeight]. */
private const val MinTableRows = 2

// ── The basic view's tiles (edit mode, 2026-09-22) ───────────────────────────────────────────

/**
 * A row tile's width. Every tile in the view is this wide, exactly as the table's are: equal
 * widths are what let a column of commands be scanned rather than read.
 *
 * Sized to carry the same run of text the resting rows were floored at (`AssignmentMinChars`)
 * plus a device glyph, so entering edit mode widens a box but doesn't transform it.
 */
private val RowTileWidth = 88.dp

/**
 * A row tile's height and the gaps around it, **in PHYSICAL pixels** — Dylan's own measurement
 * off the device (2026-09-23), converted at runtime exactly as the layouts drawer's width is.
 * A dp figure would have been his number divided by whatever density he was measuring at.
 *
 * "42 high, stroke inclusive": the tile's bevel is an INNER stroke, so this is the whole thing.
 * The gap is the air between two buttons of one group, horizontally between tiles and
 * vertically between rows alike.
 */
private const val RowTileHeightPx = 42
private const val RowTileGapPx = 6

/** [RowTileHeightPx] in dp on this device. The row height of BOTH modes — see [rowTileLook]. */
@Composable
internal fun rowTileHeight(): Dp = with(LocalDensity.current) { RowTileHeightPx.toDp() }

/** [RowTileGapPx] in dp on this device: between tiles on a row, and between a group's rows. */
@Composable
internal fun rowTileGap(): Dp = with(LocalDensity.current) { RowTileGapPx.toDp() }

/** A row tile's text inset. Tighter than the table's: less tile to inset into. */
private val RowTileContentPadding = 6.dp

/** The "+" on a row tile, scaled to it the way [EmptyTilePlusSize] is to the table's. */
private val RowTilePlusSize = 14.dp

/**
 * The text on a TINTED row tile (Dylan, 2026-09-22).
 *
 * Edit mode drops the press-type glyph and lets the tile's own colour say which press type it
 * is, so the text has to hold against six saturated fills rather than one neutral surface —
 * hence white, explicitly, rather than a content token that tracks the surface underneath.
 * Regular Press keeps `onSurface`: its tile is the ordinary container, and white there would
 * make the commonest command the loudest thing on the view.
 */
private val RowTileTintedText = Color.White

/**
 * **How big a tile is, and how much it says.**
 *
 * The basic view's edit mode renders the SAME [CommandTile] the advanced table does (Dylan,
 * 2026-09-22) — same menu, same move, same press-type palette — at a smaller size and with less
 * on its face. That parity is the point of the experiment: if a tile in a row behaves like a
 * tile in a table, the table stops being a place you have to go.
 *
 * [compact] is the "less on its face" half: ONE line (the user's label if there is one, else the
 * command's name — [CommandDisplay.line], which is what the resting row already showed) and no
 * press-type glyph, the press type being carried by the tile's colour alone.
 */
internal class TileLook(
    val width: Dp,
    val height: Dp,
    val corner: Dp,
    val compact: Boolean,
)

/** The advanced table's tile: two lines, a press-type glyph, room for both. */
internal val TableTileLook = TileLook(TileWidth, TileHeight, TileCorner, compact = false)

/** The basic view's tile: one line, at the row height both modes share. */
@Composable
internal fun rowTileLook(): TileLook {
    val height = rowTileHeight()
    return remember(height) { TileLook(RowTileWidth, height, height / 2, compact = true) }
}

