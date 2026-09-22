package com.mappo.ui.screen.remap

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
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
    // Bumped to re-seat the cursor on a tile after a tap has wiped focus. See [refocusTick].
    var editFocusTick by remember { mutableIntStateOf(0) }
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
            editFocusTick++
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
        editGroup = editGroup,
        editFocusTick = editFocusTick,
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
    /** Non-null in EDIT MODE: the rows are made of real command tiles instead of text runs. */
    edit: RowEditHost? = null,
) {
    val order = LocalCommandOrder.current
    val density = LocalDensity.current
    if (edit != null) {
        // The rows have gutters between them, and the tiles gaps between those — air that
        // belongs to no cell. Without a tolerance a finger crossing one resolves to nothing and
        // the drop target snaps back to the origin, visible as the landing marker flickering
        // home mid-drag. Same bargain the advanced table strikes.
        edit.moveState.hitTolerancePx =
            with(density) { maxOf(SummaryRowSpacing, RowTileGap).toPx() }
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
            floored = !split && edit == null,
            edit = edit,
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

/** One row of a group box, resolved: what it draws, and (in edit mode) what its tiles act on. */
private data class SimpleRow(
    val spec: SimpleRowSpec,
    val slots: List<RowSlot>,
    val bindingGroupId: Long? = null,
    val overridden: Boolean = false,
)

/** One drawn item on a row: a text run at rest, a real command TILE in edit mode. */
private sealed interface RowSlot {
    data class Text(val cell: AssignmentCell) : RowSlot

    /** [command] is null for the row's trailing "+", exactly as in the advanced table. */
    data class Tile(val key: CellKey, val command: RowCommand?) : RowSlot
}

/**
 * Resolve one row to what it draws.
 *
 * A stick's MOVEMENT row stays text even in edit mode: movement is the stick's MODE, not a
 * command anyone assigns ([RemapSimpleGroup.summaryRows]), so there is nothing there to tile.
 * The test is general rather than a special case — a summary row the editor doesn't own is not
 * editable, whichever row it turns out to be.
 */
@Composable
private fun simpleRowFor(
    group: RemapSimpleGroup,
    spec: SimpleRowSpec,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    editing: Boolean,
    order: CommandOrder,
): SimpleRow {
    if (!editing || spec !in group.rows) {
        return SimpleRow(
            spec = spec,
            slots = assignmentCells(viewingSet, viewingLayer, config, spec).map { RowSlot.Text(it) },
        )
    }
    val commands = rowCommandsFor(viewingSet, viewingLayer, spec, order)
    return SimpleRow(
        spec = spec,
        // The commands that exist, then the row's "+" — the same stack the table draws, and the
        // same slot indices, so stepping, moving and the test tags all carry over untouched.
        slots = (0 until rowSlotCount(commands.size)).map { slot ->
            RowSlot.Tile(CellKey(group, spec, slot), commands.getOrNull(slot))
        },
        bindingGroupId = viewingSet?.presetFor(spec.source)?.group?.group?.id,
        overridden = viewingLayer?.presetFor(spec.source)?.group?.inputByKey(spec.subInputKey) != null,
    )
}

/**
 * One command tile on a basic-view row.
 *
 * The tile itself is the advanced table's ([CommandTile] at [RowTileLook]); everything here is
 * the wiring it needs — which cell it is, what its menu does, and the drop marker underneath it.
 *
 * It carries NO swap displacement: the stage's [MoveOverlay] draws both ends of an exchange
 * above the whole view, so nothing in the grid moves while a carry is in flight.
 */
@Composable
private fun RowCommandTile(
    row: SimpleRow,
    slot: RowSlot.Tile,
    edit: RowEditHost,
    config: ControllerConfig?,
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
    val marker = when {
        !edit.moveState.active -> null
        edit.moveState.target == slot.key -> extras.dropZoneValid.copy(alpha = MoveMarkerAlpha)
        edit.moveState.origin == slot.key -> extras.dropZoneOrigin.copy(alpha = MoveMarkerAlpha)
        else -> null
    }
    Box(
        modifier = modifier.then(
            if (marker != null) {
                Modifier.clip(RoundedCornerShape(RowTileLook.corner)).background(marker)
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
            look = RowTileLook,
            modifier = Modifier.focusRequester(edit.focusHandle(slot.key)),
        )
    }
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
    Layout(
        modifier = modifier,
        contents = listOf(end, start),
    ) { (endMeasurables, startMeasurables), constraints ->
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
        layout(width, height) {
            endPlaceable.place(centre - gap / 2 - endPlaceable.width, 0)
            startPlaceable.place(centre + (gap - gap / 2), 0)
        }
    }
}

/**
 * One anchored table in its scrolling viewport.
 *
 * The viewport WRAPS the table (Dylan, 2026-09-16) — a group with few assignments makes a
 * small box — up to the space the box's column allows, beyond which it scrolls, cueing the
 * overflow with [MinputOverflowScroll]'s edge fades + chevrons. The chevrons sit out in the
 * box's padding ([OverflowChevronOutset]), nearer its rim.
 *
 * **A mirrored table scrolls in reverse.** Its rows read outward from a glyph pinned to the
 * box's right edge, so the resting position is the scroller's FAR end: `reverseScrolling`
 * makes value 0 mean "showing the right end", which is both the correct opening view and what
 * keeps the glyph column pinned where it belongs. The stick direction is mirrored to match, so
 * pushing the stick toward the content always reveals more of it.
 *
 * The right stick is a prototyping stand-in for real scroll controls, at Dylan's request; only
 * the focused box responds.
 */
@Composable
private fun ScrollingAssignmentTable(
    rows: List<SimpleRow>,
    anchor: RowAnchor,
    floored: Boolean,
    edit: RowEditHost?,
    config: ControllerConfig?,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val reversed = anchor == RowAnchor.END
    MinputOverflowScroll(
        state = scroll,
        reverseScrolling = reversed,
        chevronOutset = OverflowChevronOutset,
        modifier = modifier,
    ) {
        AssignmentTable(rows = rows, anchor = anchor, floored = floored, edit = edit, config = config)
    }
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
    /** Non-null in edit mode — the rows are tiles, and this is what they act through. */
    edit: RowEditHost?,
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
    // Edit mode's tiles are taller than the text they replace would be; every other rhythm of
    // the table (the row gap, the glyph column) is deliberately unchanged, so entering edit mode
    // wraps the rows rather than re-laying the view out (Dylan, 2026-09-22).
    val rowHeightDp = if (edit != null) maxOf(SummaryRowHeight, RowTileLook.height) else SummaryRowHeight
    Layout(
        modifier = modifier,
        content = {
            rows.forEachIndexed { rowIndex, row ->
                Box(Modifier.layoutId(GlyphSlot(rowIndex))) {
                    InputGlyphs.SubInputGlyph(row.spec.source, row.spec.subInputKey, size = SummaryGlyphSize)
                }
                row.slots.forEachIndexed { columnIndex, slot ->
                    val slotModifier = Modifier.layoutId(CellSlot(rowIndex, columnIndex))
                    when (slot) {
                        is RowSlot.Tile -> RowCommandTile(
                            row = row,
                            slot = slot,
                            // A Tile slot only exists where GroupRows was given a host.
                            edit = edit!!,
                            config = config,
                            modifier = slotModifier,
                        )
                        is RowSlot.Text -> {
                            val cell = slot.cell
                            Row(
                                modifier = slotModifier,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // The device glyph LEADS the name here exactly as it does on the
                                // advanced tile's output line, in the cell's own color so the
                                // pair reads as one object rather than a glyph beside some text.
                                cell.glyph?.let { output ->
                                    InputGlyphs.outputPainter(output)?.let { painter ->
                                        Icon(
                                            painter,
                                            contentDescription = null,
                                            modifier = Modifier.size(AssignmentOutputGlyphSize),
                                            tint = cell.color,
                                        )
                                        Spacer(Modifier.width(MinputGlyphLabelGap))
                                    }
                                }
                                Text(
                                    text = cell.text,
                                    style = if (cell.italic) {
                                        cellStyle.copy(fontStyle = FontStyle.Italic)
                                    } else cellStyle,
                                    color = cell.color,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                    // A divider separates two text runs. Tiles separate themselves — a rule
                    // between two capsules is a line drawn through a gap that already reads.
                    if (columnIndex > 0 &&
                        slot is RowSlot.Text &&
                        row.slots[columnIndex - 1] is RowSlot.Text
                    ) {
                        Box(Modifier.layoutId(DividerSlot(rowIndex, columnIndex)).background(dividerColor))
                    }
                }
            }
        },
    ) { measurables, _ ->
        val slots = measurables.associateBy { it.layoutId }
        val rowHeight = rowHeightDp.roundToPx()
        val spacing = SummaryRowSpacing.roundToPx()
        val glyph = SummaryGlyphSize.roundToPx()
        val glyphGap = AssignmentGlyphGap.roundToPx()
        val dividerPadding = AssignmentDividerPadding.roundToPx()
        val dividerWidth = AssignmentDividerWidth.roundToPx().coerceAtLeast(1)
        val dividerHeight = AssignmentDividerHeight.roundToPx().coerceAtMost(rowHeight)
        val dividerRun = dividerPadding * 2 + dividerWidth
        val tileGap = RowTileGap.roundToPx()
        val tileWidth = RowTileLook.width.roundToPx()
        val tileHeight = RowTileLook.height.roundToPx()

        /** The air before the slot at [column]: a divider's run between two text runs, the
         *  tiles' own gap wherever a tile is involved. */
        fun gapBefore(row: SimpleRow, column: Int): Int = when {
            column == 0 -> 0
            row.slots[column] is RowSlot.Text && row.slots[column - 1] is RowSlot.Text -> dividerRun
            else -> tileGap
        }

        val glyphs = rows.indices.map { slots[GlyphSlot(it)]?.measure(Constraints.fixed(glyph, glyph)) }
        val cells = rows.mapIndexed { rowIndex, row ->
            row.slots.mapIndexed { column, slot ->
                val measurable = slots[CellSlot(rowIndex, column)]
                when (slot) {
                    // Every tile the same width, exactly as in the table: equal widths are what
                    // let a run of commands be scanned rather than read.
                    is RowSlot.Tile -> measurable?.measure(Constraints.fixed(tileWidth, tileHeight))
                    // Unbounded: the natural width IS the cell width, and this table lives
                    // inside a scroller that it may overrun.
                    is RowSlot.Text -> measurable?.measure(Constraints())
                }
            }
        }
        val dividers = rows.mapIndexed { rowIndex, row ->
            row.slots.indices.map { column ->
                slots[DividerSlot(rowIndex, column)]?.measure(Constraints.fixed(dividerWidth, dividerHeight))
            }
        }
        val rowRuns = rows.mapIndexed { rowIndex, row ->
            cells[rowIndex].sumOf { it?.width ?: 0 } + row.slots.indices.sumOf { gapBefore(row, it) }
        }
        val width = glyph + glyphGap + maxOf(rowRuns.maxOrNull() ?: 0, assignmentFloor)
        val height = rows.size * rowHeight + (rows.size - 1).coerceAtLeast(0) * spacing

        layout(width, height) {
            rows.forEachIndexed { rowIndex, row ->
                val top = rowIndex * (rowHeight + spacing)
                glyphs[rowIndex]?.let { placeable ->
                    val x = if (anchor == RowAnchor.START) 0 else width - glyph
                    placeable.place(x, top + (rowHeight - placeable.height) / 2)
                }
                // Walk outward from the glyph: rightward for a START row, leftward for a
                // mirrored END one. `cursor` is the glyph-side edge of the next item.
                val outward = if (anchor == RowAnchor.START) 1 else -1
                var cursor = if (anchor == RowAnchor.START) glyph + glyphGap else width - glyph - glyphGap
                cells[rowIndex].forEachIndexed { column, cell ->
                    val gap = gapBefore(row, column)
                    if (gap > 0) {
                        dividers[rowIndex][column]?.let { placeable ->
                            val x = if (anchor == RowAnchor.START) {
                                cursor + dividerPadding
                            } else {
                                cursor - dividerPadding - placeable.width
                            }
                            placeable.place(x, top + (rowHeight - placeable.height) / 2)
                        }
                        cursor += outward * gap
                    }
                    if (cell == null) return@forEachIndexed
                    val x = if (anchor == RowAnchor.START) cursor else cursor - cell.width
                    cell.place(x, top + (rowHeight - cell.height) / 2)
                    cursor += outward * cell.width
                }
            }
        }
    }
}

/** [AssignmentTable]'s layout slot ids. */
private data class GlyphSlot(val row: Int)
private data class CellSlot(val row: Int, val column: Int)
private data class DividerSlot(val row: Int, val column: Int)

/** Height of one glyph + assignment row inside a group box. Also the height of an edit-mode
 *  tile ([RowTileLook]), which is what keeps the view's rhythm identical in both modes. */
internal val SummaryRowHeight = 17.dp

/** Vertical gap between a box's rows. */
internal val SummaryRowSpacing = 4.dp

/** The input glyph that anchors every row. */
private val SummaryGlyphSize = 14.dp

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

