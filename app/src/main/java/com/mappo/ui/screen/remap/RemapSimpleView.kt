package com.mappo.ui.screen.remap

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mappo.data.model.steam.ActionLayerGraph
import com.mappo.data.model.steam.ActionSetGraph
import com.mappo.data.model.steam.ActivatorType
import com.mappo.data.model.steam.BindingGroupGraph
import com.mappo.data.model.steam.BindingMode
import com.mappo.data.model.steam.BindingOutput
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.data.model.steam.InputSource
import com.mappo.data.model.steam.displayLabel
import com.mappo.data.model.steam.displayName
import com.mappo.data.model.steam.displayNameFor
import com.mappo.ui.glyph.InputGlyphs
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

    BackHandler(enabled = expandedGroup != null) { expandedGroup = null }

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
        onOpenGroup = { expandedGroup = it },
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
internal data class RowAssignment(val type: ActivatorType, val text: String)

/**
 * Resolve one row's assignments, in the advanced table's column order ([pressTypeColumns]).
 * Layer view resolves override→base per sub-input (ghost semantics), same as the table.
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
): List<RowAssignment> {
    val groupInput = viewingLayer?.presetFor(spec.source)?.group?.inputByKey(spec.subInputKey)
        ?: viewingSet?.presetFor(spec.source)?.group?.inputByKey(spec.subInputKey)
        ?: return emptyList()
    return pressTypeColumns.mapNotNull { type ->
        val activator = groupInput.firstActivatorOfType(type) ?: return@mapNotNull null
        val binding = activator.bindings.firstOrNull() ?: return@mapNotNull null
        val output = activator.primaryOutput
        if (output == BindingOutput.Unbound) return@mapNotNull null
        // The command's own name, with the device initials only if the command keeps them
        // ([Binding.showDeviceInitials], set in the label editor) — one command reads the same
        // way here and in the advanced table.
        val name = if (binding.showDeviceInitials) output.displayLabel(config) else output.displayName(config)
        RowAssignment(type, binding.label?.takeIf { it.isNotBlank() } ?: name)
    }
}

/**
 * What a row with NO assignments says: `None` mode → "None"; an active mode that has no
 * bindable row for this sub-input at all (the analog modes) → the mode's own name; anything
 * else — device default, or a seeded-but-unbound row — → the input's physical name.
 */
internal fun rowRestingLabel(
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    spec: SimpleRowSpec,
): String {
    val layerGroup: BindingGroupGraph? = viewingLayer?.presetFor(spec.source)?.group
    val baseGroup: BindingGroupGraph? = viewingSet?.presetFor(spec.source)?.group
    val effective = layerGroup ?: baseGroup ?: return defaultRowLabel(spec)
    val hasRow = layerGroup?.inputByKey(spec.subInputKey) != null ||
        baseGroup?.inputByKey(spec.subInputKey) != null
    return when {
        effective.group.mode == BindingMode.NONE -> "None"
        effective.group.mode == BindingMode.DEVICE_DEFAULT || hasRow -> defaultRowLabel(spec)
        else -> effective.group.mode.displayNameFor(spec.source)
    }
}

/**
 * The resting label for an unchanged/unlabeled input — its own physical name rather than a
 * generic "Default". User-specified wording; Title Case button names are a deliberate
 * exception to the sentence-case doctrine (they read as proper nouns).
 */
internal fun defaultRowLabel(spec: SimpleRowSpec): String = when (spec.source) {
    InputSource.LEFT_TRIGGER -> "Left Trigger"
    InputSource.RIGHT_TRIGGER -> "Right Trigger"
    InputSource.LEFT_BUMPER -> "Left Bumper"
    InputSource.RIGHT_BUMPER -> "Right Bumper"
    // No "Button" suffix on these two (Dylan, 2026-09-17): every other default here names a
    // control whose word needs the noun ("Left Trigger", "A Button"), but Start and Select are
    // the names printed on the hardware.
    InputSource.SWITCH_START -> "Start"
    InputSource.SWITCH_SELECT -> "Select"
    InputSource.DPAD -> when (spec.subInputKey) {
        "dpad_up" -> "D-Pad Up"
        "dpad_left" -> "D-Pad Left"
        "dpad_right" -> "D-Pad Right"
        "dpad_down" -> "D-Pad Down"
        else -> "D-Pad"
    }
    InputSource.LEFT_JOYSTICK, InputSource.RIGHT_JOYSTICK -> {
        val stick = if (spec.source == InputSource.LEFT_JOYSTICK) "L-Stick" else "R-Stick"
        when (spec.subInputKey) {
            "dpad_up" -> "$stick Up"
            "dpad_left" -> "$stick Left"
            "dpad_right" -> "$stick Right"
            "dpad_down" -> "$stick Down"
            "click" -> "$stick Click"
            StickMoveKey -> "$stick Move"
            else -> stick
        }
    }
    InputSource.BUTTON_DIAMOND -> when (spec.subInputKey) {
        "button_y" -> "Y Button"
        "button_x" -> "X Button"
        "button_b" -> "B Button"
        "button_a" -> "A Button"
        else -> "Button"
    }
    else -> "Default"
}

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
private data class AssignmentCell(val text: String, val color: Color, val italic: Boolean = false)

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
    val assignments = rowAssignments(viewingSet, viewingLayer, config, spec)
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
) {
    val summaryRows = group.summaryRows
    val endRows = summaryRows.filter { group.anchorFor(it) == RowAnchor.END }
    val startRows = summaryRows.filter { group.anchorFor(it) == RowAnchor.START }
    val split = endRows.isNotEmpty() && startRows.isNotEmpty()
    val table: @Composable (List<SimpleRowSpec>, RowAnchor, Modifier) -> Unit = { specs, anchor, m ->
        ScrollingAssignmentTable(
            specs = specs,
            rows = specs.map { assignmentCells(viewingSet, viewingLayer, config, it) },
            anchor = anchor,
            // A SPLIT group is a centre-column one, and its box is sized by the column rather
            // than by its content (see RemapStage), so the character floor has nothing to
            // protect and everything to break: two halves each claiming a full assignment run
            // outgrew the column and cued an overflow the text didn't have (Dylan, 2026-09-19).
            floored = !split,
            modifier = m,
        )
    }
    if (!split) {
        table(summaryRows, group.anchorFor(summaryRows.first()), modifier)
    } else {
        CentreSplit(
            modifier = modifier,
            end = { table(endRows, RowAnchor.END, Modifier) },
            start = { table(startRows, RowAnchor.START, Modifier) },
        )
    }
}

/**
 * Two tables meeting at a centre line: [end] (mirrored) on the left half, [start] on the right.
 *
 * A custom layout because the halves must stay EQUAL while the whole wraps its content — a pair
 * of weighted children would fill the box, and a plain Row would put the centre line wherever
 * the left table happened to end. Each half is as wide as the wider of the two tables (each
 * capped at half the space available, beyond which it scrolls), and each table hugs the centre
 * line within its half.
 */
@Composable
private fun CentreSplit(
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
    specs: List<SimpleRowSpec>,
    rows: List<List<AssignmentCell>>,
    anchor: RowAnchor,
    floored: Boolean,
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
        AssignmentTable(specs = specs, rows = rows, anchor = anchor, floored = floored)
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
    specs: List<SimpleRowSpec>,
    rows: List<List<AssignmentCell>>,
    anchor: RowAnchor,
    /** Whether the assignment run keeps its [AssignmentMinChars] floor. See [GroupRows]. */
    floored: Boolean,
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
    Layout(
        modifier = modifier,
        content = {
            specs.forEachIndexed { rowIndex, spec ->
                Box(Modifier.layoutId(GlyphSlot(rowIndex))) {
                    InputGlyphs.SubInputGlyph(spec.source, spec.subInputKey, size = SummaryGlyphSize)
                }
                rows[rowIndex].forEachIndexed { columnIndex, cell ->
                    Text(
                        text = cell.text,
                        style = if (cell.italic) cellStyle.copy(fontStyle = FontStyle.Italic) else cellStyle,
                        color = cell.color,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.layoutId(CellSlot(rowIndex, columnIndex)),
                    )
                    if (columnIndex > 0) {
                        Box(Modifier.layoutId(DividerSlot(rowIndex, columnIndex)).background(dividerColor))
                    }
                }
            }
        },
    ) { measurables, _ ->
        val slots = measurables.associateBy { it.layoutId }
        val rowHeight = SummaryRowHeight.roundToPx()
        val spacing = SummaryRowSpacing.roundToPx()
        val glyph = SummaryGlyphSize.roundToPx()
        val glyphGap = AssignmentGlyphGap.roundToPx()
        val dividerPadding = AssignmentDividerPadding.roundToPx()
        val dividerWidth = AssignmentDividerWidth.roundToPx().coerceAtLeast(1)
        val dividerHeight = AssignmentDividerHeight.roundToPx().coerceAtMost(rowHeight)
        val divider = dividerPadding * 2 + dividerWidth

        val glyphs = specs.indices.map { slots[GlyphSlot(it)]?.measure(Constraints.fixed(glyph, glyph)) }
        // Unbounded: the natural width IS the cell width, and this table lives inside a
        // scroller that it may overrun.
        val cells = rows.mapIndexed { row, rowCells ->
            rowCells.indices.map { column -> slots[CellSlot(row, column)]?.measure(Constraints()) }
        }
        val dividers = rows.mapIndexed { row, rowCells ->
            rowCells.indices.map { column ->
                slots[DividerSlot(row, column)]?.measure(Constraints.fixed(dividerWidth, dividerHeight))
            }
        }
        val rowRuns = cells.map { rowCells ->
            rowCells.sumOf { it?.width ?: 0 } + divider * (rowCells.size - 1).coerceAtLeast(0)
        }
        val width = glyph + glyphGap + maxOf(rowRuns.maxOrNull() ?: 0, assignmentFloor)
        val height = specs.size * rowHeight + (specs.size - 1).coerceAtLeast(0) * spacing

        layout(width, height) {
            specs.indices.forEach { row ->
                val top = row * (rowHeight + spacing)
                glyphs[row]?.let { placeable ->
                    val x = if (anchor == RowAnchor.START) 0 else width - glyph
                    placeable.place(x, top + (rowHeight - placeable.height) / 2)
                }
                // Walk outward from the glyph: rightward for a START row, leftward for a
                // mirrored END one. `cursor` is the glyph-side edge of the next item.
                val outward = if (anchor == RowAnchor.START) 1 else -1
                var cursor = if (anchor == RowAnchor.START) glyph + glyphGap else width - glyph - glyphGap
                cells[row].forEachIndexed { column, cell ->
                    if (column > 0) {
                        dividers[row][column]?.let { placeable ->
                            val x = if (anchor == RowAnchor.START) {
                                cursor + dividerPadding
                            } else {
                                cursor - dividerPadding - placeable.width
                            }
                            placeable.place(x, top + (rowHeight - placeable.height) / 2)
                        }
                        cursor += outward * divider
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

/** Height of one glyph + assignment row inside a group box. */
private val SummaryRowHeight = 17.dp

/** Vertical gap between a box's rows. */
private val SummaryRowSpacing = 4.dp

/** The input glyph that anchors every row. */
private val SummaryGlyphSize = 14.dp

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
private val CentreSplitGap = 4.dp

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

