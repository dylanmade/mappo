package com.mappo.ui.screen.remap

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.mappo.data.model.steam.ActionLayerGraph
import com.mappo.data.model.steam.ActionSetGraph
import com.mappo.data.model.steam.ActivatorType
import com.mappo.data.model.steam.BindingGroupGraph
import com.mappo.data.model.steam.BindingMode
import com.mappo.data.model.steam.BindingOutput
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.data.model.steam.InputSource
import com.mappo.data.model.steam.displayNameFor
import com.mappo.ui.component.MoveModeState
import com.mappo.ui.glyph.InputGlyphs
import com.mappo.ui.screen.displayLabel as activatorDisplayLabel
import com.mappo.ui.theme.LocalMappoExtraColors
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputOverflowScroll
import com.mappo.ui.minput.MinputPod
import com.mappo.ui.minput.minputMiniTextStyle
import kotlin.math.roundToInt

/**
 * The simplified remap view: a controller diagram flanked by one tappable box per input group,
 * each row showing an input glyph + every command assigned to that input.
 *
 * **The view is one STAGE that zooms** (Dylan, 2026-09-17). This file owns the state — which
 * group is open, how far along the zoom is, where controller focus sits — and the resting
 * CONTENT of a group box; [RemapStage] owns the elements and the travel, and RemapZoomScene.kt
 * owns the zoomed geometry. Activating a box does not open anything: every element simply moves
 * and scales from its place in the basic grid to its place in the advanced layout, its contents
 * crossfading from summary rows to the full table on the way. Backing out runs it in reverse,
 * into whichever box the camera ended on.
 *
 * Two earlier attempts are worth not repeating. A near-fullscreen MODAL editor (before
 * 2026-09-17) hid the controller and every other group — isolating to read, and a lot of
 * closing and reopening to move a command between groups. Its replacement kept the basic plate
 * and a separate zoomed scene as two layers and crossfaded them while both scaled: Dylan
 * reviewed that one frame by frame and it was, exactly as it looked, two screens rather than a
 * zoom.
 *
 * **Restructured 2026-09-11 to follow the advanced view's table** ([RemapGroupEditor]), in three
 * moves:
 *
 * 1. A row no longer shows only its standard press with a "+N" badge for the rest. Every press
 *    type the user has assigned now renders inline, in the table's column order, tinted with
 *    that column's identity color — the basic view as the table's compacted twin. There are no
 *    visible empty slots here: assignments close up rank (see [AssignmentTable]).
 * 2. The LEFT column's rows MIRROR the right column's instead of matching it — glyph at the
 *    box's inner edge, assignments running outward. The centre group mirrors per ROW around its
 *    own centre line and moved to its own section beneath the flanks, because inline
 *    assignments need far more room than a third of the plate ([anchorFor]).
 *
 * Refined 2026-09-16: flex rows with a divider between each assignment; boxes that wrap their
 * rows (above a floor); fade + chevron overflow cues ([MinputOverflowScroll]) in place of
 * scrollbars; one movement row per stick ([RemapSimpleGroup.summaryRows]).
 * 3. Row text resolution was fixed to mean what it says: the command's label, else the output's
 *    name ([rowAssignments]).
 *
 * The **Map** CTA was removed the same day (Dylan, "for now") — it was a UI-only stand-in for
 * the future input-mapping wizard, so nothing behind it was lost.
 *
 * Box styling: accent-tinted rounded boxes + bevel border (the treatment born on the retired
 * d-pad flower home's petal cards, now owned by the remap chrome). The whole band rides one
 * rectangular [MinputPod] plate (2026-08-30).
 *
 * The Inherit / Overlay / Gyro strip that used to sit beneath the band was REMOVED 2026-08-30
 * (Dylan). **Inherit is gone for good** — set-to-set inheritance is being replaced by a
 * "Switch set" (swap the whole action set) / "Stack set" (Steam-style action-set layering)
 * pair, neither built yet. Overlay and Gyro are only homeless: both get new homes shortly.
 * Re-creating the gyro picker is a `ModePillDropdown` over
 * `SourceModeCatalog.modesValidFor(InputSource.GYRO)` — see RemapPills.kt, which still
 * carries the pill and its width constant.
 */
@Composable
internal fun RemapSimpleView(
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    editorCallbacks: RemapGroupEditorCallbacks,
    modifier: Modifier = Modifier,
    // Gates the controller-focus seat below. While a side drawer is open, focus belongs to
    // the drawer cards — but browsing can swap this view in and out of composition (the
    // no-layout state ↔ controls flip), and an ungated entry-seat on remount stole focus
    // from the drawer mid-scroll. The pending seat is NOT consumed while gated, so it still
    // fires once the drawers close and the screen becomes controller-ready then.
    focusSeatEnabled: Boolean = true,
    /** A command the edit-mode cursor should land on once it exists — see [RemapStage]. */
    seatCommand: Long? = null,
    onSeatCommand: (Long?) -> Unit = {},
) {
    // The group whose editor should be open (user intent — survives the command-picker
    // round-trip) vs. the group currently on stage, which outlives it through the collapse.
    var expandedGroup by rememberSaveable { mutableStateOf<RemapSimpleGroup?>(null) }
    var visibleGroup by remember { mutableStateOf(expandedGroup) }
    /**
     * **EDIT MODE** — the group whose selection turned the basic view's rows into tiles (Dylan,
     * 2026-09-22, as an experiment against the zoom).
     *
     * The mode is view-WIDE: every group's rows reformat, not just this one's. Keeping the whole
     * controller legible while one part of it is edited is the point, and a command can be
     * carried between groups only if the group it is going to is made of tiles too. This names
     * the group only so the cursor knows where to land.
     *
     * Null while zoomed: the two are alternative ways of editing the same thing, and the zoom's
     * tables register the same cell keys these tiles do.
     */
    var editGroup by rememberSaveable { mutableStateOf<RemapSimpleGroup?>(null) }
    /**
     * Edit mode as it is ON SCREEN, which outlives [editGroup] through the morph out — the same
     * arrangement [visibleGroup] has for the zoom, and for the same reason: the rows are still
     * travelling home after the intent has gone.
     */
    var editVisible by remember { mutableStateOf(editGroup) }
    /** 0 = the resting rows, 1 = tiles. The whole travel is this one number; see [EditPhase]. */
    val editProgress = remember { Animatable(if (editGroup != null) 1f else 0f) }
    var editSettled by remember { mutableStateOf(true) }
    /** One-shot: seat the cursor on this group's first tile, once there IS one to seat it on. */
    var editSeat by remember { mutableStateOf<RemapSimpleGroup?>(null) }
    // Bumped to re-seat the cursor on a tile after a tap has wiped focus. See [refocusTick].
    var editFocusTick by remember { mutableIntStateOf(0) }

    // The morph in and out. Focus is seated only once the tiles are REAL — mid-travel they are
    // inert ghosts with nothing to focus (see EditPhase).
    LaunchedEffect(editGroup) {
        val target = editGroup
        if (target != null) {
            editVisible = target
            // **Only if there is a morph to run** (Dylan, 2026-09-25). Creating a command leaves
            // for the full-screen picker, and the screen that comes back is already in edit mode
            // with the travel at its end — but animating 1f to 1f still runs for the spec's full
            // duration, holding `editSettled` false the whole time. Everything that waits for the
            // tiles to be real waited with it: the cursor's seat, and the pan to the group the
            // new command landed in, which is the "solid second" before the camera moved.
            if (editProgress.value < 1f) {
                editSettled = false
                editProgress.animateTo(1f, tween(EditMorphMillis, easing = FastOutSlowInEasing))
                editSettled = true
            }
            editSeat = target
        } else if (editVisible != null) {
            if (editProgress.value > 0f) {
                editSettled = false
                editProgress.animateTo(0f, tween(EditMorphMillis, easing = FastOutSlowInEasing))
                editSettled = true
            }
            editVisible = null
        }
    }
    // Where the camera has travelled since the zoom began — the group being edited NOW, which
    // is what the zoom collapses back into and hands focus to. Distinct from [expandedGroup],
    // which stays the group it was opened from.
    var cameraGroup by rememberSaveable { mutableStateOf(expandedGroup) }
    // 0 = the basic grid, 1 = zoomed onto [cameraGroup]. The whole travel is one number: the
    // stage interpolates every element's rect between its two geometries by it.
    val progress = remember { Animatable(if (expandedGroup != null) 1f else 0f) }
    // False for the duration of a travel, so the stage can skip per-frame-expensive chrome.
    var settled by remember { mutableStateOf(true) }
    // Focus target inside the opened group's table — without it, a tap that opens a group
    // leaves the cursor on the box behind it.
    val editorFocus = remember { FocusRequester() }
    // Which group box should reclaim controller focus once the zoom collapses back into it.
    // Starts non-null on a fresh entry: seating focus on the top-left group box makes the
    // screen controller-ready immediately — the Select/Start panel summons are preview key
    // handlers that only fire while focus sits in this subtree, and an unseated screen's first
    // d-pad press used to default-hunt into the frame chrome.
    var returnFocusGroup by remember {
        mutableStateOf(if (expandedGroup == null) RemapSimpleGroup.LEFT_SHOULDER else null)
    }
    val inputModeManager = LocalInputModeManager.current

    // Focus recovery. Any TAP flips the window into touch mode, which CLEARS Compose focus —
    // after that, d-pad navigation was dead until something was reopened. When this subtree
    // loses all focus, re-seat the cursor: on the top-left box while zoomed out, on the open
    // table while zoomed in. DEFERRED through state + LaunchedEffect, because focus-loss also
    // fires while a composition is being disposed and a synchronous requestFocus mid-detach
    // corrupts the node lifecycle ("Must run runDetachLifecycle()..."); an effect simply never
    // runs on a disposing composition.
    var viewHadFocus by remember { mutableStateOf(false) }
    var refocusTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(refocusTick) {
        // Touch mode only: a tap is the thing that clears focus wholesale. In key mode a
        // subtree loss means focus legitimately moved elsewhere (up to the set/layer tabs, say)
        // — recovering would yank it straight back.
        if (refocusTick == 0 || inputModeManager.inputMode != InputMode.Touch) return@LaunchedEffect
        // In edit mode there is no box left to recover onto — the tiles are the focus targets,
        // and the stage knows where they are.
        if (editGroup != null) {
            if (editSettled) editFocusTick++
            return@LaunchedEffect
        }
        if (expandedGroup == null) {
            if (visibleGroup == null) returnFocusGroup = RemapSimpleGroup.LEFT_SHOULDER
        } else {
            runCatching { editorFocus.requestFocus() }
        }
    }

    // Drives zoom in / zoom out / travel to another group. Switching groups collapses back to
    // the grid first, then zooms into the next one.
    LaunchedEffect(expandedGroup) {
        val target = expandedGroup
        if (target == visibleGroup) {
            if (target != null && progress.value < 1f) {
                settled = false
                progress.animateTo(1f, tween(ExpandMillis, easing = FastOutSlowInEasing))
                settled = true
            }
            return@LaunchedEffect
        }
        if (visibleGroup != null) {
            // Collapse into the box of wherever the CAMERA ended up, not the one it came in
            // through: after travelling to another group, that group's box is the one the
            // elements are actually over.
            val closing = cameraGroup ?: visibleGroup
            settled = false
            progress.animateTo(0f, tween(CollapseMillis, easing = FastOutSlowInEasing))
            settled = true
            visibleGroup = null
            cameraGroup = null
            // Backing out (not switching groups): hand controller focus back to that box.
            if (target == null) returnFocusGroup = closing
        }
        if (target != null) {
            visibleGroup = target
            cameraGroup = target
            settled = false
            progress.animateTo(1f, tween(ExpandMillis, easing = FastOutSlowInEasing))
            settled = true
        }
    }

    // Back leaves whichever editing mode is up: the tiles go away and the rows resume their
    // ordinary flex, or the zoom collapses. Edit mode hands the cursor back to the box it was
    // entered from — the tile it was on is about to stop existing.
    BackHandler(enabled = expandedGroup != null || editGroup != null) {
        if (editGroup != null) {
            returnFocusGroup = editGroup
            editGroup = null
        } else {
            expandedGroup = null
        }
    }

    // Move controller focus into the table as the zoom starts — it lands on the first input's
    // Press cell, and the d-pad walks the grid and the header from there.
    // focusSeatEnabled is a key (not just a guard) so a seat deferred while a drawer held focus
    // fires when the drawers close — expandedGroup is saveable state, so a drawer-scroll
    // remount can land here with a group already open.
    LaunchedEffect(visibleGroup, focusSeatEnabled) {
        if (visibleGroup != null && focusSeatEnabled) runCatching { editorFocus.requestFocus() }
    }

    RemapStage(
        focus = cameraGroup ?: visibleGroup,
        progress = { progress.value },
        settled = settled,
        viewingSet = viewingSet,
        viewingLayer = viewingLayer,
        config = config,
        callbacks = editorCallbacks,
        editGroup = editVisible,
        editPhase = when {
            editVisible == null -> EditPhase.REST
            editSettled -> EditPhase.EDIT
            else -> EditPhase.MORPH
        },
        editProgress = { editProgress.value },
        editSettled = editSettled,
        editSeatGroup = editSeat,
        onEditSeated = { editSeat = null },
        editFocusTick = editFocusTick,
        seatCommand = seatCommand,
        onSeatCommand = onSeatCommand,
        // Selecting a box EDITS IN PLACE; holding it opens the advanced view it used to open.
        onOpenGroup = { editGroup = it },
        onOpenAdvanced = {
            editGroup = null
            expandedGroup = it
        },
        onLookAt = { cameraGroup = it },
        onClose = { expandedGroup = null },
        focusSeatGroup = returnFocusGroup.takeIf { focusSeatEnabled },
        onFocusSeated = { returnFocusGroup = null },
        entryFocus = editorFocus,
        modifier = modifier.onFocusChanged { state ->
            if (state.hasFocus) viewHadFocus = true else if (viewHadFocus) refocusTick++
        },
    )
}

/** One display row in a group box: which sub-input to summarize. */
internal data class SimpleRowSpec(val source: InputSource, val subInputKey: String)

/**
 * The simple view's input groups. [rows] are the advanced editor's table rows — every bindable
 * sub-input the group owns; [summaryRows] are what the group's basic-view box shows.
 */
internal enum class RemapSimpleGroup(val rows: List<SimpleRowSpec>) {
    LEFT_SHOULDER(
        listOf(
            SimpleRowSpec(InputSource.LEFT_TRIGGER, "full_pull"),
            SimpleRowSpec(InputSource.LEFT_BUMPER, "click"),
        ),
    ),
    DPAD(
        listOf(
            SimpleRowSpec(InputSource.DPAD, "dpad_up"),
            SimpleRowSpec(InputSource.DPAD, "dpad_left"),
            SimpleRowSpec(InputSource.DPAD, "dpad_right"),
            SimpleRowSpec(InputSource.DPAD, "dpad_down"),
        ),
    ),
    // A stick's EDITOR shows only its click (Dylan, 2026-09-17). The four cardinal rows the
    // table used to carry were the Dpad-on-stick mode's sub-inputs, offered whatever mode the
    // stick was actually in — and "the stick moves" isn't a command a user assigns here at all:
    // it's what the stick's MODE dropdown decides. The rows stay in the schema (and in a VDF
    // import) — the table just doesn't offer them while what to do about stick movement is
    // still open. The basic view's box is unchanged; see [summaryRows].
    LEFT_STICK(
        listOf(
            SimpleRowSpec(InputSource.LEFT_JOYSTICK, "click"),
        ),
    ),
    UTILITY(
        listOf(
            SimpleRowSpec(InputSource.SWITCH_START, "click"),
            SimpleRowSpec(InputSource.SWITCH_SELECT, "click"),
        ),
    ),
    // Click only — see [LEFT_STICK].
    RIGHT_STICK(
        listOf(
            SimpleRowSpec(InputSource.RIGHT_JOYSTICK, "click"),
        ),
    ),
    FACE(
        listOf(
            SimpleRowSpec(InputSource.BUTTON_DIAMOND, "button_y"),
            SimpleRowSpec(InputSource.BUTTON_DIAMOND, "button_x"),
            SimpleRowSpec(InputSource.BUTTON_DIAMOND, "button_b"),
            SimpleRowSpec(InputSource.BUTTON_DIAMOND, "button_a"),
        ),
    ),
    RIGHT_SHOULDER(
        listOf(
            SimpleRowSpec(InputSource.RIGHT_TRIGGER, "full_pull"),
            SimpleRowSpec(InputSource.RIGHT_BUMPER, "click"),
        ),
    ),
    ;

    /**
     * The rows this group's basic-view box summarizes — the editor's [rows], except that a
     * stick also shows a movement row: the box reads "L-Stick Move" + "L-Stick Click". Movement
     * is the stick's MODE, not an assignable command, so it appears here and nowhere in the
     * editor (Dylan, 2026-09-17 — the basic view keeps saying what the stick does; where that
     * finally gets edited is still being decided).
     *
     * The movement row is keyed [StickMoveKey], a UI-only sub-input no binding group ever
     * holds, so it always shows its RESTING label ([rowRestingLabel]): the stick's own name at
     * device default, otherwise the name of the mode it's in.
     */
    /**
     * Does this group's card header state a MODE, or just its own name?
     *
     * Every group but one picks a mode: Button Pad, Directional Pad, Joystick, Trigger. The
     * utility buttons don't (Dylan, 2026-09-21) — Start and Select are single buttons whose
     * intercept mode the repository manages from whether they are bound at all
     * (`syncAuxButtonMode`), so there is nothing there for a user to choose and a caption
     * reading "MODE: SINGLE BUTTON" named an internal state as though it were a decision. The
     * header keeps its glyph, its treatment and its inset either way.
     */
    val headerShowsMode: Boolean get() = this != UTILITY

    val summaryRows: List<SimpleRowSpec>
        get() = when (this) {
            LEFT_STICK, RIGHT_STICK -> {
                val source = rows.first().source
                listOf(SimpleRowSpec(source, StickMoveKey), SimpleRowSpec(source, "click"))
            }
            else -> rows
        }
}

/**
 * The basic view's whole-stick movement row key. UI-only — not a Steam sub-input, and never
 * written to a binding group; see [RemapSimpleGroup.summaryRows].
 */
internal const val StickMoveKey = "move"

/**
 * One assignment shown on a basic-view row: which press type it belongs to, and the text.
 *
 * The basic view is now the advanced table's COMPACTED twin (2026-09-11): every press type the
 * user has assigned appears inline on the row, tinted with that column's identity color, in
 * place of the old "+N" badge that only said how many there were. Unlike the table there are no
 * visible empty slots — assignments close up rank, so a Press + Down input reads as two
 * adjacent cells, not two cells with three gaps between them.
 */
internal data class RowAssignment(
    val type: ActivatorType,
    val text: String,
    /** The output whose device glyph leads the text, or null when this command hides it. */
    val glyph: BindingOutput?,
)

/**
 * Resolve one row's assignments — every command on the row, in the same order the advanced
 * table stacks them ([rowCommandsFor], auto-sorted by press type). Layer view resolves
 * override→base per sub-input (ghost semantics), same as the table.
 *
 * A row can hold SEVERAL commands of one press type (2026-09-20), and shows them all: the basic
 * view is the table compacted, not a summary of it.
 *
 * **A row reads exactly as the advanced tile does** — same device glyph, same initials, same
 * label — because both resolve through [commandDisplay] (Dylan, 2026-09-20). The basic view is
 * the table's compacted twin, so the only difference is that a row has ONE line for what the
 * tile spreads over two: the user's label when it has one, else the command's own name.
 *
 * **Text precedence: the command's user label when it has one, else the output's own display
 * name.** That's Dylan's spec, and the fix for a long-standing mismatch: until 2026-09-11 this
 * resolution early-returned the input's PHYSICAL name whenever the binding group's mode was
 * `DEVICE_DEFAULT`, and only the aux sources (bumpers, Start/Select) ever leave that mode
 * automatically — so a command assigned from the advanced table, which never consulted the
 * mode, showed up here as "A Button", corresponding to neither the output nor the label. The
 * mode now decides only the RESTING label ([rowRestingLabel]): what a row with no assignments
 * at all says.
 */
internal fun rowAssignments(
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    spec: SimpleRowSpec,
    order: CommandOrder,
): List<RowAssignment> = rowCommandsFor(viewingSet, viewingLayer, spec, order).map { command ->
    val display = commandDisplay(command.binding, listOf(command.output), config)
    RowAssignment(command.type, display.line, display.lineGlyph)
}

/**
 * What a row with NO assignments says.
 *
 * **Every string here comes from the unified vocabulary** — a mode's own `displayNameFor`, or
 * the one constant below (Dylan, 2026-09-21). It used to answer with a hardcoded physical name
 * per sub-input ("A Button", "L-Stick Click", "D-Pad Up"), which was the visible half of a
 * bigger problem: a fresh layout seeded nothing, so those names WERE the basic view, and they
 * corresponded to no binding the advanced view could show. Layouts now seed real bindings for
 * every input (`ControllerConfigRepository.DEFAULT_INPUT_SOURCE_SEEDS`), so a row printing its
 * own hardware name is no longer the normal case — it is the exception, and it should say which
 * exception it is:
 *
 *  - the source passes through untouched → the device-default string;
 *  - the source is intercepted and silenced → "None";
 *  - the mode doesn't bind this sub-input at all (a stick's movement row, which IS the mode) →
 *    the mode's own name, which is what the user picked;
 *  - a real row the user has emptied → [UnassignedLabel].
 */
internal fun rowRestingLabel(
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    spec: SimpleRowSpec,
): String {
    val layerGroup: BindingGroupGraph? = viewingLayer?.presetFor(spec.source)?.group
    val baseGroup: BindingGroupGraph? = viewingSet?.presetFor(spec.source)?.group
    val effective = layerGroup ?: baseGroup
        ?: return BindingMode.DEVICE_DEFAULT.displayNameFor(spec.source)
    val hasRow = layerGroup?.inputByKey(spec.subInputKey) != null ||
        baseGroup?.inputByKey(spec.subInputKey) != null
    val mode = effective.group.mode
    return when {
        mode == BindingMode.NONE || mode == BindingMode.DEVICE_DEFAULT -> mode.displayNameFor(spec.source)
        // A row that EXISTS and is empty is the user's own doing; the mode is beside the point.
        hasRow -> UnassignedLabel
        else -> mode.displayNameFor(spec.source)
    }
}

/** What an existing but empty row says. The one string on the basic view that names no mode and
 *  no output, because there is neither. */
internal const val UnassignedLabel = "Unassigned"

/** Which edge of its box a row's glyph anchors to — and so which way its assignments extend. */
internal enum class RowAnchor { START, END }

/**
 * Row anchoring, per Dylan's 2026-09-11 restructure: the LEFT column's groups now MIRROR the
 * right column's rather than matching it — glyph at the box's inner (right) edge, assignments
 * running outward to the left — so both flanks read as extending away from the controller
 * between them.
 *
 * The centre group is anchored per ROW instead of per box: its left-hand inputs (Select) sit on
 * the box's left half mirrored, its right-hand inputs (Start) on the right half normal, and the
 * two meet at the box's centre line. That's what makes it the "centred" group, and why it gets
 * the whole plate width in its own section beneath the flanks.
 */
internal fun RemapSimpleGroup.anchorFor(spec: SimpleRowSpec): RowAnchor = when (this) {
    RemapSimpleGroup.LEFT_SHOULDER, RemapSimpleGroup.DPAD, RemapSimpleGroup.LEFT_STICK ->
        RowAnchor.END
    RemapSimpleGroup.UTILITY ->
        if (spec.source == InputSource.SWITCH_SELECT) RowAnchor.END else RowAnchor.START
    else -> RowAnchor.START
}

/** One resolved cell of a group box's assignment table: its text, the color that says which
 *  press type it came from, and whether it's a resting label rather than a real assignment. */
private data class AssignmentCell(
    val text: String,
    val color: Color,
    val italic: Boolean = false,
    /** The command's device glyph, leading its text exactly as it does in the advanced tile.
     *  Null on a resting label (no command there to own a device) and on a command the user
     *  has NAMED — see [CommandDisplay.lineGlyph]. */
    val glyph: BindingOutput? = null,
)

/**
 * Resolve a row to its display cells — the assignments when it has any, otherwise the single
 * resting label.
 *
 * Press keeps the plain content color (its palette entry is deliberately neutral, and the
 * standard press is the row's subject); every alternate wears its column's HEADER color from
 * the advanced table, which is the whole cue for which press type it is.
 *
 * The resting label carries the same color as a real assignment and is set in ITALIC instead
 * (Dylan, 2026-09-12 — a dimmed color was tried first and reverted): "nothing is assigned here,
 * this is the hardware default" still reads differently at a glance, without the row looking
 * disabled.
 */
@Composable
private fun assignmentCells(
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    spec: SimpleRowSpec,
): List<AssignmentCell> {
    val assignments = rowAssignments(viewingSet, viewingLayer, config, spec, LocalCommandOrder.current)
    if (assignments.isEmpty()) {
        return listOf(
            AssignmentCell(
                text = rowRestingLabel(viewingSet, viewingLayer, spec),
                color = MaterialTheme.colorScheme.onSurface,
                italic = true,
            ),
        )
    }
    return assignments.map { assignment ->
        AssignmentCell(
            text = assignment.text,
            color = if (assignment.type == ActivatorType.FULL_PRESS) {
                MaterialTheme.colorScheme.onSurface
            } else {
                assignment.type.columnColors().header
            },
            glyph = assignment.glyph,
        )
    }
}

/**
 * The glyph + assignment rows of one group (shared by the box and the morph crossfade).
 *
 * A group whose rows all anchor the same way is ONE table, and the box wraps it. The centre
 * group anchors its rows both ways, so it splits into two tables meeting at the box's centre
 * line — see [anchorFor] and [CentreSplit]. Splitting by anchor rather than special-casing
 * `UTILITY` keeps this general: give any group a mixed set of anchors and it lays out the same
 * way.
 */
@Composable
internal fun GroupRows(
    group: RemapSimpleGroup,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    modifier: Modifier = Modifier,
    /** Non-null once EDIT MODE owns these rows — mid-morph included. */
    edit: RowEditHost? = null,
    /** Which of the two shapes the rows are in, or the travel between them. */
    phase: EditPhase = EditPhase.REST,
    /** The travel's position, read in the layout and draw phases only. */
    progress: () -> Float = { 0f },
) {
    val order = LocalCommandOrder.current
    val density = LocalDensity.current
    if (edit != null) {
        // The radius within which the grid claims a carried tile — generous, because the thing
        // it has to span is the air between one input group and the next, not the cracks between
        // tiles. Past it the move reads as abandoned; see [MoveModeState.outOfRange].
        edit.moveState.hitTolerancePx = with(density) { MoveCancelDistance.toPx() }
    }
    val rows = group.summaryRows.map { spec ->
        simpleRowFor(group, spec, viewingSet, viewingLayer, config, edit != null, order)
    }
    val endRows = rows.filter { group.anchorFor(it.spec) == RowAnchor.END }
    val startRows = rows.filter { group.anchorFor(it.spec) == RowAnchor.START }
    val split = endRows.isNotEmpty() && startRows.isNotEmpty()
    val table: @Composable (List<SimpleRow>, RowAnchor, Modifier) -> Unit = { rs, anchor, m ->
        ScrollingAssignmentTable(
            rows = rs,
            anchor = anchor,
            // A SPLIT group is a centre-column one, and its box is sized by the column rather
            // than by its content (see RemapStage), so the character floor has nothing to
            // protect and everything to break: two halves each claiming a full assignment run
            // outgrew the column and cued an overflow the text didn't have (Dylan, 2026-09-19).
            // In edit mode it is meaningless either way — a tile carries its own width.
            floored = !split,
            edit = edit,
            phase = phase,
            progress = progress,
            config = config,
            modifier = m,
        )
    }
    if (!split) {
        table(rows, group.anchorFor(rows.first().spec), modifier)
    } else {
        CentreSplit(
            modifier = modifier,
            end = { table(endRows, RowAnchor.END, Modifier) },
            start = { table(startRows, RowAnchor.START, Modifier) },
        )
    }
}

/**
 * **What EDIT MODE needs to make a basic-view row editable in place** (Dylan, 2026-09-22).
 *
 * The experiment: rather than travelling to a separate view to work on a group, the rows
 * reformat into the very [CommandTile] the advanced table is built from — same menu, same
 * press-type palette, same carry — while every other group stays on screen and does the same.
 * The whole point is that nothing is re-implemented here: a tile behaving *almost* like the
 * table's would be worse than the trip it saves.
 *
 * The state is the STAGE's (see RemapStage), not the row's: one move state spans every group, so
 * a command can be carried across the view, and the tile in flight is drawn above everything by
 * the shared [MoveOverlay] — a box clips, and a carried tile has to be able to leave it.
 */
internal class RowEditHost(
    val moveState: MoveModeState<CellKey>,
    val focusHandle: (CellKey) -> FocusRequester,
    val onCommitMove: (Pair<CellKey, CellKey>?) -> Unit,
    val onControllerLift: () -> Unit,
    val callbacks: RemapGroupEditorCallbacks,
    /** False in layer view, where the tiles are read-only and route edits to the full editor. */
    val editable: Boolean,
    val onLabel: (LabelEdit) -> Unit,
    val onType: (TypeEdit) -> Unit,
)

/**
 * One row of a group box, resolved for BOTH modes at once: the text runs the resting view draws,
 * and the tiles edit mode draws. The morph between them interpolates slot by slot, so a row has
 * to know both of its shapes at every moment (Dylan, 2026-09-24).
 *
 * The two lists line up by INDEX, which is what lets a label travel into its own tile: both come
 * from the same `rowCommandsFor` order, so slot *i* is the same command either way. [tiles] runs
 * one longer — the trailing "+" has no resting counterpart and grows out of nothing — and is
 * EMPTY for a row edit mode doesn't own (a stick's movement row), which therefore never morphs.
 */
private data class SimpleRow(
    val spec: SimpleRowSpec,
    val rest: List<AssignmentCell>,
    val tiles: List<RowTile>,
    val bindingGroupId: Long? = null,
    val overridden: Boolean = false,
) {
    /** Does this row have a second shape to travel to at all? */
    val morphs: Boolean get() = tiles.isNotEmpty()
    val slotCount: Int get() = maxOf(rest.size, tiles.size)
    fun restAt(index: Int): AssignmentCell? = rest.getOrNull(index)
    fun tileAt(index: Int): RowTile? = tiles.getOrNull(index)
}

/** One tile slot of a row. [command] is null for the trailing "+", as in the advanced table. */
private data class RowTile(val key: CellKey, val command: RowCommand?)

/**
 * Resolve one row to what it draws in BOTH modes.
 *
 * A stick's MOVEMENT row gets no tiles: movement is the stick's MODE, not a command anyone
 * assigns ([RemapSimpleGroup.summaryRows]), so there is nothing there to tile. The test is
 * general rather than a special case — a summary row the editor doesn't own is not editable,
 * whichever row it turns out to be.
 */
@Composable
private fun simpleRowFor(
    group: RemapSimpleGroup,
    spec: SimpleRowSpec,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    editable: Boolean,
    order: CommandOrder,
): SimpleRow {
    val rest = assignmentCells(viewingSet, viewingLayer, config, spec)
    if (!editable || spec !in group.rows) return SimpleRow(spec = spec, rest = rest, tiles = emptyList())
    val commands = rowCommandsFor(viewingSet, viewingLayer, spec, order)
    return SimpleRow(
        spec = spec,
        rest = rest,
        // The commands that exist, then the row's "+" — the same stack the table draws, and the
        // same slot indices, so stepping, moving and the test tags all carry over untouched.
        tiles = (0 until rowSlotCount(commands.size)).map { slot ->
            RowTile(CellKey(group, spec, slot), commands.getOrNull(slot))
        },
        bindingGroupId = viewingSet?.presetFor(spec.source)?.group?.group?.id,
        overridden = viewingLayer?.presetFor(spec.source)?.group?.inputByKey(spec.subInputKey) != null,
    )
}

/**
 * One command tile on a basic-view row.
 *
 * The tile itself is the advanced table's ([CommandTile] at [rowTileLook]); everything here is
 * the wiring it needs — which cell it is, what its menu does, and the drop marker underneath it.
 *
 * It carries NO swap displacement: the stage's [MoveOverlay] draws both ends of an exchange
 * above the whole view, so nothing in the grid moves while a carry is in flight.
 */
@Composable
private fun RowCommandTile(
    row: SimpleRow,
    slot: RowTile,
    edit: RowEditHost,
    config: ControllerConfig?,
    look: TileLook,
    modifier: Modifier,
) {
    val command = slot.command
    val display = command?.let { commandDisplay(it.binding, listOf(it.output), config) }
    val type = command?.type ?: ActivatorType.FULL_PRESS
    val subLabel = RemapSections.labelFor(row.spec.source, row.spec.subInputKey)
    val title = "$subLabel · ${type.activatorDisplayLabel()}"
    val extras = LocalMappoExtraColors.current
    // GREEN marks where the lifted tile will land, BLUE where it came from; green wins when
    // they're the same cell, which is how "put it back" reads as a destination rather than an
    // absence of one.
    // While the stage draws the tiles in flight it draws the markers too — its overlay is above
    // this whole view, so a marker here would sit under the very tiles it describes, and drawing
    // both would double the wash (see [MoveOverlay]).
    val marker = when {
        LocalMoveOverlay.current -> null
        !edit.moveState.active -> null
        // Out of range there is no destination: the tile is going home, and it carries the red.
        edit.moveState.target == slot.key && !edit.moveState.outOfRange ->
            extras.dropZoneValid.copy(alpha = MoveMarkerAlpha)
        edit.moveState.origin == slot.key -> extras.dropZoneOrigin.copy(alpha = MoveMarkerAlpha)
        else -> null
    }
    val markerShape = RoundedCornerShape(look.corner)
    Box(
        modifier = modifier.then(
            // OVER the tile, not behind it (Dylan, 2026-09-25): during a move the pair of
            // washes is what says which tile is going where, and a marker under the tile
            // standing in that slot says it to nobody. Drawn after the content rather than as a
            // background, which keeps it in the draw phase and out of hit testing.
            if (marker != null) {
                Modifier.drawWithContent {
                    drawContent()
                    val outline = markerShape.createOutline(size, layoutDirection, this)
                    drawOutline(outline, color = marker)
                }
            } else Modifier,
        ),
    ) {
        CommandTile(
            colors = type.columnColors(),
            // The "+" slot wears no press type: it isn't a command yet, and colouring it would
            // claim one.
            pressType = type.takeIf { command != null },
            output = command?.output,
            label = display?.label,
            outputText = display?.text.orEmpty(),
            showDeviceIcon = display?.glyph != null,
            enabled = edit.editable && row.bindingGroupId != null,
            cellKey = slot.key,
            moveState = edit.moveState,
            carried = edit.moveState.carriedByOverlay(
                key = slot.key,
                hasCommand = command != null,
                hosted = LocalMoveOverlay.current,
            ),
            displacement = DpOffset.Zero,
            previewOrigin = edit.moveState.origin,
            onCommitMove = edit.onCommitMove,
            onControllerLift = edit.onControllerLift,
            actions = {
                commandCellActions(
                    cellKey = slot.key,
                    command = command,
                    subLabel = subLabel,
                    title = title,
                    bindingGroupId = row.bindingGroupId,
                    overridden = row.overridden,
                    editable = edit.editable,
                    callbacks = edit.callbacks,
                    moveState = edit.moveState,
                    onLabel = edit.onLabel,
                    onType = edit.onType,
                )
            },
            look = look,
            modifier = Modifier.focusRequester(edit.focusHandle(slot.key)),
        )
    }
}

/**
 * **The morph between the two modes** (Dylan, 2026-09-24).
 *
 * Entering edit mode used to be a cut: every element at its resting place on one frame and at
 * its edit-mode place on the next, with the scroller landing wherever the new width put it. It
 * now runs as one short travel in two acts, which is how Dylan asked for it —
 *
 *  1. **The labels travel.** Each cell widens from its text's own width to the tile's, and the
 *     label rides to the centre of where its tile will be. Nothing else appears yet; the row is
 *     still just words moving apart.
 *  2. **The buttons arrive behind them.** Only once the labels are home does the tile chrome
 *     fade in underneath — the fill, the press-type tint, the ring, the "+".
 *
 * The label itself never fades: it is the same text throughout, which is what makes the two
 * modes read as one view rearranging rather than two views swapping.
 */
internal enum class EditPhase {
    /** The resting view: text runs and dividers, and no tile exists anywhere. */
    REST,

    /** Mid-travel, either way. Nothing here is interactive — see [RowTileGhost]. */
    MORPH,

    /** Edit mode proper: real [CommandTile]s, focusable and carryable. */
    EDIT,
}

/** How far the labels have travelled — act one, finishing before the chrome starts. Shared
 *  with the STAGE, whose grid must interpolate on exactly the same curve as the rows inside it. */
internal fun editTravelAt(progress: Float): Float =
    (progress.coerceIn(0f, 1f) / EditTravelSpan).coerceIn(0f, 1f)

/** How far the tile chrome has arrived — act two. Reversed on the way out, so the buttons
 *  leave first and the labels walk home after them. */
private fun editChromeAt(progress: Float): Float =
    ((progress.coerceIn(0f, 1f) - EditChromeStart) / (1f - EditChromeStart)).coerceIn(0f, 1f)

/** Where act one ends and act two begins, as fractions of the travel. They overlap slightly:
 *  a hard handover reads as two animations rather than one. */
private const val EditTravelSpan = 0.62f
private const val EditChromeStart = 0.55f

/**
 * A tile mid-morph: its chrome, fading in behind a label that is being drawn by the cell itself.
 *
 * Deliberately inert — no focus, no gestures, no menu. The real [CommandTile] takes over the
 * instant the travel lands, and because the two are the same size and the same face by then,
 * the handover is invisible. Keeping them separate is what lets the resting view stay exactly
 * as it was: no focusable tile ever exists outside edit mode.
 */
@Composable
private fun RowTileGhost(
    tile: RowTile,
    look: TileLook,
    progress: () -> Float,
    modifier: Modifier,
) {
    TileChrome(
        pressType = tile.command?.type,
        defined = tile.command != null,
        look = look,
        modifier = modifier.graphicsLayer { alpha = editChromeAt(progress()) },
    )
}

/**
 * A resting cell's text — the thing that travels.
 *
 * While the morph runs it also carries the tile's own colour on top of its resting one, faded in
 * with the chrome: an alternate press type reads in its column colour at rest and in white on
 * the tinted tile, and a label that changed colour the moment the buttons landed would undo the
 * continuity the travel exists to create. Drawn as an opaque base with the second colour over
 * it, never as two half-transparent copies, which would wash the text out mid-travel.
 */
@Composable
private fun MorphingCellText(
    cell: AssignmentCell,
    style: TextStyle,
    /** The colour this text wears once it is a tile's label, when that differs. */
    editColor: Color?,
    /** True for a resting label the "+" replaces: it has no tile to become, so it bows out. */
    fadesOut: Boolean,
    progress: () -> Float,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.then(
            if (fadesOut) Modifier.graphicsLayer { alpha = 1f - editChromeAt(progress()) } else Modifier,
        ),
    ) {
        AssignmentCellRow(cell, cell.color, style)
        if (editColor != null && editColor != cell.color) {
            Box(Modifier.graphicsLayer { alpha = editChromeAt(progress()) }) {
                AssignmentCellRow(cell, editColor, style)
            }
        }
    }
}

/** One resting cell: its command's device glyph, then its name — the advanced tile's own output
 *  line, which is why the two views print a command identically. */
@Composable
private fun AssignmentCellRow(cell: AssignmentCell, color: Color, style: TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        cell.glyph?.let { output ->
            InputGlyphs.outputPainter(output)?.let { painter ->
                Icon(
                    painter,
                    contentDescription = null,
                    modifier = Modifier.size(AssignmentOutputGlyphSize),
                    tint = color,
                )
                Spacer(Modifier.width(MinputGlyphLabelGap))
            }
        }
        Text(
            text = cell.text,
            style = if (cell.italic) style.copy(fontStyle = FontStyle.Italic) else style,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** What a command's label reads as once it is a tile's — white on a tinted tile, the ordinary
 *  content colour on Regular Press. Mirrors `RowTileContent`, which owns the rule. */
@Composable
private fun tileLabelColor(command: RowCommand?): Color {
    val tinted = command != null && command.type != ActivatorType.FULL_PRESS
    return if (tinted) Color.White else MaterialTheme.colorScheme.onSurface
}

/**
 * Two tables meeting at a centre line: [end] (mirrored) on the left half, [start] on the right.
 *
 * Shared with the ADVANCED view (2026-09-20): the centre group's card splits the same way its
 * box does, so the utility glyphs sit on the card's centre line with their commands radiating
 * outward, and the two views read as the same object at two scales.
 *
 * A custom layout because the halves must stay EQUAL while the whole wraps its content — a pair
 * of weighted children would fill the box, and a plain Row would put the centre line wherever
 * the left table happened to end. Each half is as wide as the wider of the two tables (each
 * capped at half the space available, beyond which it scrolls), and each table hugs the centre
 * line within its half.
 */
@Composable
internal fun CentreSplit(
    end: @Composable () -> Unit,
    start: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Both halves' intrinsics combine the way their measurements do, so the stage can ask the
    // centre group's box how wide it will be at either end of a morph — see AssignmentTable.
    fun IntrinsicMeasureScope.span(
        halves: List<List<IntrinsicMeasurable>>,
        height: Int,
        editing: Boolean,
    ): Int {
        val gap = CentreSplitGap.roundToPx()
        val widest = halves.flatten().maxOfOrNull {
            if (editing) it.maxIntrinsicWidth(height) else it.minIntrinsicWidth(height)
        } ?: 0
        return widest * 2 + gap
    }
    Layout(
        modifier = modifier,
        contents = listOf(end, start),
        measurePolicy = object : MultiContentMeasurePolicy {
            override fun IntrinsicMeasureScope.minIntrinsicWidth(
                measurables: List<List<IntrinsicMeasurable>>,
                height: Int,
            ): Int = span(measurables, height, editing = false)

            override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                measurables: List<List<IntrinsicMeasurable>>,
                height: Int,
            ): Int = span(measurables, height, editing = true)

            override fun MeasureScope.measure(
                measurables: List<List<Measurable>>,
                constraints: Constraints,
            ): MeasureResult = measureSplit(measurables[0], measurables[1], constraints)
        },
    )
}

/** [CentreSplit]'s measure, split out so its policy object stays readable. */
private fun MeasureScope.measureSplit(
    endMeasurables: List<Measurable>,
    startMeasurables: List<Measurable>,
    constraints: Constraints,
): MeasureResult {
    val gap = CentreSplitGap.roundToPx()
    val halfMax = if (constraints.hasBoundedWidth) {
        ((constraints.maxWidth - gap) / 2).coerceAtLeast(0)
    } else {
        Constraints.Infinity
    }
    val childConstraints = Constraints(maxWidth = halfMax, maxHeight = constraints.maxHeight)
    val endPlaceable = endMeasurables.single().measure(childConstraints)
    val startPlaceable = startMeasurables.single().measure(childConstraints)
    val half = maxOf(endPlaceable.width, startPlaceable.width)
    val width = (half * 2 + gap).coerceIn(constraints.minWidth, constraints.maxWidth)
    val height = maxOf(endPlaceable.height, startPlaceable.height)
        .coerceIn(constraints.minHeight, constraints.maxHeight)
    val centre = width / 2
    return layout(width, height) {
        endPlaceable.place(centre - gap / 2 - endPlaceable.width, 0)
        startPlaceable.place(centre + (gap - gap / 2), 0)
    }
}

/**
 * One anchored table.
 *
 * It used to be a scrolling viewport of its own (Dylan, 2026-09-16), wrapping its rows up to the
 * width its column allowed and scrolling beyond it with [MinputOverflowScroll]'s edge fades and
 * chevrons. **The scroller moved out to the BODY on 2026-09-22** (Dylan) along with the group
 * cards: with no card there is nothing to clip against, so a table is simply as wide as its rows
 * and the whole grid scrolls as one. The cues did not disappear — they are the same cues, once,
 * around the whole body (see RemapStage).
 */
@Composable
private fun ScrollingAssignmentTable(
    rows: List<SimpleRow>,
    anchor: RowAnchor,
    floored: Boolean,
    edit: RowEditHost?,
    phase: EditPhase,
    progress: () -> Float,
    config: ControllerConfig?,
    modifier: Modifier = Modifier,
) {
    AssignmentTable(
        rows = rows,
        anchor = anchor,
        floored = floored,
        edit = edit,
        phase = phase,
        progress = progress,
        config = config,
        modifier = modifier,
    )
}

/**
 * The table: a glyph column plus, per row, that row's assignments laid end to end outward from
 * the glyph, a divider between each pair.
 *
 * **Rows FLEX** (Dylan, 2026-09-16, retiring the same day's fixed 14-character columns): each
 * cell is its text's natural width, nothing ellipsizes, and rows don't align their cells with
 * each other — the per-row dividers carry the separation the column grid used to. Each divider
 * keeps [AssignmentDividerPadding] of air on both sides so the text never crowds it.
 *
 * The glyph column still aligns across rows, and the table's assignment run is at least
 * [AssignmentMinChars] characters wide — the width a box had when it carried one fixed column,
 * kept as its floor so a sparse box doesn't shrink to a sliver. A mirrored table's glyphs pin
 * to its right edge at that width.
 *
 * A custom [Layout] rather than nested Rows because the glyph must pin to the table's
 * outward-facing edge at the table's width, which is only known once every row is measured.
 */
@Composable
private fun AssignmentTable(
    rows: List<SimpleRow>,
    anchor: RowAnchor,
    /** Whether the assignment run keeps its [AssignmentMinChars] floor. See [GroupRows]. */
    floored: Boolean,
    /** Non-null once edit mode owns these rows — including mid-morph, when its tiles are still
     *  ghosts. Only [EditPhase.EDIT] actually builds real tiles from it. */
    edit: RowEditHost?,
    phase: EditPhase,
    /** The morph's position, read in the LAYOUT and DRAW phases so the travel neither
     *  recomposes the rows nor re-resolves a single command. */
    progress: () -> Float,
    config: ControllerConfig?,
    modifier: Modifier = Modifier,
) {
    // "N characters" measured off digits: they're tabular in every face Mappo ships, so the
    // floor is stable rather than depending on which letters a command happens to use.
    val measurer = rememberTextMeasurer()
    val cellStyle = minputMiniTextStyle()
    val assignmentFloor = remember(measurer, cellStyle, floored) {
        if (!floored) {
            0
        } else {
            measurer.measure(
                text = "0".repeat(AssignmentMinChars),
                style = cellStyle,
                softWrap = false,
            ).size.width
        }
    }
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    // ONE rhythm across both modes (Dylan, 2026-09-23): the row height is the tile height
    // whether or not a tile is drawn in it, and the glyph is the same size either way. The
    // resting view and edit mode are the same view with different cells, so a row that changed
    // height between them read as the whole grid resettling every time edit mode was entered.
    val look = rowTileLook()
    val rowHeightDp = look.height
    val rowGapDp = rowTileGap()
    // Every resting cell's natural width, measured from the TEXT rather than from the laid-out
    // children. That makes it available for the intrinsics below, which are asked for a width
    // the children have not been measured at — and, during a morph, may not even be composed in.
    val density = LocalDensity.current
    val restSpans = remember(rows, measurer, cellStyle, density) {
        val glyphRun = with(density) {
            (AssignmentOutputGlyphSize + MinputGlyphLabelGap).roundToPx()
        }
        rows.map { row ->
            IntArray(row.slotCount) { column ->
                val cell = row.restAt(column) ?: return@IntArray 0
                val style = if (cell.italic) cellStyle.copy(fontStyle = FontStyle.Italic) else cellStyle
                val text = measurer.measure(cell.text, style = style, softWrap = false).size.width
                if (cell.glyph != null) glyphRun + text else text
            }
        }
    }
    /**
     * The table's width at each end of the morph, offered as INTRINSICS.
     *
     * The stage plans the travel from these (RemapStage's `EditCameraAnchor`): it has to know
     * where the grid will END UP before the first frame of it, so it can work out once — rather
     * than chase frame by frame — how far to hold the view against the change. `min` is the
     * resting width, `max` the edit-mode one; a Box's padding and [CentreSplit] pass both
     * through, so the stage can ask the whole group box for them.
     */
    fun span(editing: Boolean): Int {
        val metrics = with(density) { tableMetrics(rowHeightDp, rowGapDp, look.width) }
        val runs = rows.mapIndexed { rowIndex, row ->
            val plan = rowPlan(row, metrics) { column -> restSpans[rowIndex][column] }
            if (editing) plan.editRun else plan.restRun
        }
        val run = runs.maxOrNull() ?: 0
        return metrics.glyph + metrics.glyphGap + if (editing) run else maxOf(run, assignmentFloor)
    }
    Layout(
        modifier = modifier,
        measurePolicy = object : MeasurePolicy {
            override fun IntrinsicMeasureScope.minIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>,
                height: Int,
            ): Int = span(editing = false)

            override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>,
                height: Int,
            ): Int = span(editing = true)

            override fun MeasureScope.measure(
                measurables: List<Measurable>,
                constraints: Constraints,
            ): MeasureResult = measureTable(
                measurables = measurables,
                rows = rows,
                anchor = anchor,
                assignmentFloor = assignmentFloor,
                rowHeightDp = rowHeightDp,
                rowGapDp = rowGapDp,
                tileWidthDp = look.width,
                progress = progress,
            )
        },
        content = {
            rows.forEachIndexed { rowIndex, row ->
                Box(Modifier.layoutId(GlyphSlot(rowIndex))) {
                    InputGlyphs.SubInputGlyph(row.spec.source, row.spec.subInputKey, size = SummaryGlyphSize)
                }
                for (column in 0 until row.slotCount) {
                    val restCell = row.restAt(column)
                    val tile = row.tileAt(column)
                    // The travelling label. Present in every phase but EDIT, where the real tile
                    // draws its own — and in EDIT for a row that has no tiles to travel to.
                    if (restCell != null && (phase != EditPhase.EDIT || !row.morphs)) {
                        MorphingCellText(
                            cell = restCell,
                            style = cellStyle,
                            editColor = if (phase == EditPhase.MORPH && tile?.command != null) {
                                tileLabelColor(tile.command)
                            } else null,
                            fadesOut = phase == EditPhase.MORPH && tile?.command == null && row.morphs,
                            progress = progress,
                            modifier = Modifier.layoutId(RestSlot(rowIndex, column)),
                        )
                    }
                    if (tile != null) {
                        when (phase) {
                            EditPhase.EDIT -> RowCommandTile(
                                row = row,
                                slot = tile,
                                // A tile is only built where GroupRows was given a host.
                                edit = edit!!,
                                config = config,
                                look = look,
                                modifier = Modifier.layoutId(TileSlot(rowIndex, column)),
                            )
                            EditPhase.MORPH -> RowTileGhost(
                                tile = tile,
                                look = look,
                                progress = progress,
                                modifier = Modifier.layoutId(TileSlot(rowIndex, column)),
                            )
                            // At rest a tile does not exist at all — which is what keeps the
                            // resting view free of focusable cells.
                            EditPhase.REST -> Unit
                        }
                    }
                    // A divider separates two text runs. Tiles separate themselves — a rule
                    // between two capsules is a line drawn through a gap that already reads —
                    // so it thins away with the travel.
                    if (column > 0 && restCell != null && row.restAt(column - 1) != null &&
                        phase != EditPhase.EDIT
                    ) {
                        Box(
                            Modifier
                                .layoutId(DividerSlot(rowIndex, column))
                                .graphicsLayer { alpha = 1f - editTravelAt(progress()) }
                                .background(dividerColor),
                        )
                    }
                }
            }
        },
    )
}

/** [AssignmentTable]'s measure, split out only so its policy object stays readable. */
private fun MeasureScope.measureTable(
    measurables: List<Measurable>,
    rows: List<SimpleRow>,
    anchor: RowAnchor,
    assignmentFloor: Int,
    rowHeightDp: Dp,
    rowGapDp: Dp,
    tileWidthDp: Dp,
    progress: () -> Float,
): MeasureResult {
        val slots = measurables.associateBy { it.layoutId }
        val metrics = tableMetrics(rowHeightDp, rowGapDp, tileWidthDp)
        val glyphs = rows.indices.map {
            slots[GlyphSlot(it)]?.measure(Constraints.fixed(metrics.glyph, metrics.glyph))
        }
        // The resting text at its NATURAL width — the width each label travels FROM. Unbounded:
        // the table lives inside a scroller it may overrun.
        val restCells = rows.mapIndexed { rowIndex, row ->
            (0 until row.slotCount).map { column ->
                slots[RestSlot(rowIndex, column)]?.measure(Constraints())
            }
        }
        // **Both shapes, laid out in full, once** (Dylan, 2026-09-24) — then interpolated. The
        // first cut derived each frame's widths from the frame before it, and the rounding,
        // the shifting argmax and the scroller's own clamping compounded into a visible shake.
        // A run is now a LERP BETWEEN TWO FIXED LAYOUTS, which cannot drift: every cell's
        // offset, every gap and the table's own width all come from the same two ends.
        val plans = rows.mapIndexed { rowIndex, row ->
            rowPlan(row, metrics) { column -> restCells[rowIndex][column]?.width ?: 0 }
        }
        val travel = editTravelAt(progress())
        // The width is a lerp of the two ENDPOINT widths, floors included — never a max() taken
        // afresh each frame. A max of a rising run against a falling floor dips before it
        // climbs, and that dip is a reversal: the box narrows for a few frames in the middle of
        // widening, and the whole grid steps back and forth around it. Everything placed here
        // has to be a monotonic function of the travel, or the motion shakes.
        val restWidth = metrics.glyph + metrics.glyphGap +
            maxOf(plans.maxOfOrNull { it.restRun } ?: 0, assignmentFloor)
        val editWidth = metrics.glyph + metrics.glyphGap + (plans.maxOfOrNull { it.editRun } ?: 0)
        val width = lerpPx(restWidth, editWidth, travel)
        val height = rows.size * metrics.rowHeight +
            (rows.size - 1).coerceAtLeast(0) * metrics.rowGap

        val tilePlaceables = rows.mapIndexed { rowIndex, row ->
            (0 until row.slotCount).map { column ->
                slots[TileSlot(rowIndex, column)]?.measure(
                    Constraints.fixed(
                        plans[rowIndex].widthAt(column, travel).coerceAtLeast(0),
                        metrics.rowHeight,
                    ),
                )
            }
        }
        val dividers = rows.mapIndexed { rowIndex, row ->
            (0 until row.slotCount).map { column ->
                slots[DividerSlot(rowIndex, column)]
                    ?.measure(Constraints.fixed(metrics.dividerWidth, metrics.dividerHeight))
            }
        }

        return layout(width, height) {
            rows.forEachIndexed { rowIndex, row ->
                val plan = plans[rowIndex]
                val top = rowIndex * (metrics.rowHeight + metrics.rowGap)
                glyphs[rowIndex]?.let { placeable ->
                    val x = if (anchor == RowAnchor.START) 0 else width - metrics.glyph
                    placeable.place(x, top + (metrics.rowHeight - placeable.height) / 2)
                }
                // A run reads OUTWARD from the glyph: rightward for a START row, leftward for a
                // mirrored END one. Offsets are measured along that direction, so mirroring is
                // one subtraction here and nothing anywhere else.
                fun screenX(offset: Int, itemWidth: Int): Int = if (anchor == RowAnchor.START) {
                    metrics.glyph + metrics.glyphGap + offset
                } else {
                    width - metrics.glyph - metrics.glyphGap - offset - itemWidth
                }
                for (column in 0 until row.slotCount) {
                    val cell = plan.widthAt(column, travel)
                    val left = plan.offsetAt(column, travel)
                    tilePlaceables[rowIndex][column]?.place(screenX(left, cell), top)
                    restCells[rowIndex][column]?.let { placeable ->
                        // The label rides CENTRED in its cell, which is what carries it to the
                        // middle of the tile as the cell grows around it.
                        placeable.place(
                            screenX(left + (cell - placeable.width) / 2, placeable.width),
                            top + (metrics.rowHeight - placeable.height) / 2,
                        )
                    }
                    dividers[rowIndex][column]?.let { placeable ->
                        val previousEnd = plan.offsetAt(column - 1, travel) +
                            plan.widthAt(column - 1, travel)
                        val gapCentre = (previousEnd + left) / 2
                        placeable.place(
                            screenX(gapCentre - placeable.width / 2, placeable.width),
                            top + (metrics.rowHeight - placeable.height) / 2,
                        )
                    }
                }
            }
        }
}

/** The fixed measurements a table lays out against, resolved once per pass. */
private class TableMetrics(
    val rowHeight: Int,
    val rowGap: Int,
    val glyph: Int,
    val glyphGap: Int,
    val dividerWidth: Int,
    val dividerHeight: Int,
    val dividerRun: Int,
    val tileWidth: Int,
    val tileGap: Int,
)

private fun Density.tableMetrics(rowHeight: Dp, rowGap: Dp, tileWidth: Dp): TableMetrics {
    val height = rowHeight.roundToPx()
    val dividerWidth = AssignmentDividerWidth.roundToPx().coerceAtLeast(1)
    val dividerPadding = AssignmentDividerPadding.roundToPx()
    return TableMetrics(
        rowHeight = height,
        rowGap = rowGap.roundToPx(),
        glyph = SummaryGlyphSize.roundToPx(),
        glyphGap = AssignmentGlyphGap.roundToPx(),
        dividerWidth = dividerWidth,
        dividerHeight = AssignmentDividerHeight.roundToPx().coerceAtMost(height),
        dividerRun = dividerPadding * 2 + dividerWidth,
        tileWidth = tileWidth.roundToPx(),
        tileGap = rowGap.roundToPx(),
    )
}

/**
 * One row laid out at BOTH ends of the morph: where every cell starts and how wide it is, as
 * text and as tiles. Everything the travel needs is a lerp between two entries of these arrays,
 * so no frame is ever derived from the frame before it.
 */
private class RowPlan(
    private val restOffsets: IntArray,
    private val restWidths: IntArray,
    private val editOffsets: IntArray,
    private val editWidths: IntArray,
    val restRun: Int,
    val editRun: Int,
) {
    fun widthAt(column: Int, travel: Float): Int =
        if (column < 0) 0 else lerpPx(restWidths[column], editWidths[column], travel)

    fun offsetAt(column: Int, travel: Float): Int =
        if (column < 0) 0 else lerpPx(restOffsets[column], editOffsets[column], travel)
}

/** Lay one row out at both ends. [restWidthOf] gives a slot's text width, already measured. */
private fun rowPlan(row: SimpleRow, metrics: TableMetrics, restWidthOf: (Int) -> Int): RowPlan {
    val slots = row.slotCount
    val restOffsets = IntArray(slots)
    val restWidths = IntArray(slots)
    val editOffsets = IntArray(slots)
    val editWidths = IntArray(slots)
    var restCursor = 0
    var editCursor = 0
    for (column in 0 until slots) {
        val hasRest = row.restAt(column) != null
        val hasTile = row.tileAt(column) != null
        if (hasRest && restCursor > 0) restCursor += metrics.dividerRun
        restOffsets[column] = restCursor
        restWidths[column] = if (hasRest) restWidthOf(column) else 0
        restCursor += restWidths[column]
        // A row edit mode does not own keeps its text shape at both ends, so it never moves.
        if (!row.morphs) {
            editOffsets[column] = restOffsets[column]
            editWidths[column] = restWidths[column]
            editCursor = restCursor
            continue
        }
        if (hasTile && editCursor > 0) editCursor += metrics.tileGap
        editOffsets[column] = editCursor
        editWidths[column] = if (hasTile) metrics.tileWidth else 0
        editCursor += editWidths[column]
    }
    return RowPlan(restOffsets, restWidths, editOffsets, editWidths, restCursor, editCursor)
}

private fun lerpPx(from: Int, to: Int, travel: Float): Int =
    from + ((to - from) * travel).roundToInt()

/** [AssignmentTable]'s layout slot ids. */
private data class GlyphSlot(val row: Int)

/** A slot's two occupants: the text that travels, and the tile that arrives behind it. Both
 *  exist at once mid-morph, which is why they need separate ids. */
private data class RestSlot(val row: Int, val column: Int)
private data class TileSlot(val row: Int, val column: Int)
private data class DividerSlot(val row: Int, val column: Int)

// A row's HEIGHT and the gap between rows are the tile's — see [rowTileHeight] / [rowTileGap],
// which are specified in device pixels and shared by both modes.

/** The input glyph that anchors every row — the SAME size in both modes (Dylan, 2026-09-23).
 *  Raised from 14dp on 2026-09-22 to sit with the taller tiles; the resting view gets it too,
 *  because the glyph is the row's only identity in either mode. */
private val SummaryGlyphSize = 18.dp

/** The DEVICE glyph leading one command's text — the advanced tile's scale
 *  (TileOutputGlyphSize), since the two views print the same command. */
private val AssignmentOutputGlyphSize = 14.dp

/** Gap between the glyph and its first assignment column. */
private val AssignmentGlyphGap = 5.dp

/** Air on EACH side of the divider between a row's assignments. */
private val AssignmentDividerPadding = 6.dp

/** Thickness of the divider between a row's assignments. */
private val AssignmentDividerWidth = 1.dp

/** Height of that divider — a little short of the row, so it separates without caging. */
private val AssignmentDividerHeight = 11.dp

/** Gutter at the centre group's centre line, keeping its two halves' glyphs off each other.
 *  Tightened from 10dp on 2026-09-17 (Dylan): the utility box's two glyphs sat too far apart
 *  for a pair that reads as one cluster. */
internal val CentreSplitGap = 4.dp

/** Floor on a table's assignment run, in characters, so a sparse box doesn't shrink to a
 *  sliver. Started as the width of the fixed column it replaced (14 chars, 2026-09-16);
 *  narrowed to 10 on 2026-09-18 (Dylan: the boxes' minimum width was too wide). Command names
 *  longer than this still get their full width — this is a floor, not a cap. */
private const val AssignmentMinChars = 10

/** How far each overflow chevron sits out past the scroller, into the box's 8dp padding. */
private val OverflowChevronOutset = 6.dp

// The group editor's morph values — canonical in the library (MinputDefaults.kt); these
// are the remap package's aliases. The layout/options panels no longer morph (they're
// MinputModals now, fade + settle) but still share GroupCorner/EditorMargin framing so
// the remap surfaces read as one family.
internal val GroupCorner = com.mappo.ui.minput.MinputMorphCorner
internal const val ExpandMillis = com.mappo.ui.minput.MinputMorphExpandMillis
internal const val CollapseMillis = com.mappo.ui.minput.MinputMorphCollapseMillis
/** Inset between the expanded editor (or full-screen panel) and its host's edges. */
internal val EditorMargin = 10.dp

/** How long the resting rows take to become tiles, and back (Dylan, 2026-09-24). A shade
 *  quicker than the zoom's own travel: nothing changes place here, the cells only widen. */
internal const val EditMorphMillis = 260

