package com.mappo.ui.screen.remap

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mappo.R
import com.mappo.data.model.steam.ActionLayerGraph
import com.mappo.data.model.steam.ActionSetGraph
import com.mappo.data.model.steam.ActivatorType
import com.mappo.data.model.steam.BindingGroupGraph
import com.mappo.data.model.steam.BindingMode
import com.mappo.data.model.steam.BindingOutput
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.data.model.steam.InputSource
import com.mappo.data.model.steam.displayLabel
import com.mappo.data.model.steam.displayNameFor
import com.mappo.ui.component.rightStickHorizontalScroll
import com.mappo.ui.glyph.InputGlyphs
import com.mappo.ui.screen.softDropShadow
import kotlin.math.roundToInt
import com.mappo.ui.minput.MinputBarEdgePadding
import com.mappo.ui.minput.MinputOverflowScroll
import com.mappo.ui.minput.MinputPod
import com.mappo.ui.minput.MinputPodGap
import com.mappo.ui.minput.MinputPodPlateCorner
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputBoxContainer
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMiniTextStyle

/**
 * The simplified remap view: a controller diagram flanked by one tappable box per input group,
 * each row showing an input glyph + every command assigned to that input. Tapping a box animates
 * it into the center of the screen (over the controller), morphing into the in-place advanced
 * editor ([RemapGroupEditor]); an invisible stand-in holds the group's home position and the box
 * animates back on close (or when another group is picked).
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
    // round-trip) vs. the group currently on screen mid-animation.
    var expandedGroup by rememberSaveable { mutableStateOf<RemapSimpleGroup?>(null) }
    var visibleGroup by remember { mutableStateOf(expandedGroup) }
    val progress = remember { Animatable(if (expandedGroup != null) 1f else 0f) }
    val boxBounds = remember { mutableStateMapOf<RemapSimpleGroup, Rect>() }
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    // Focus target for the expanded editor — without it, the tapped box's disappearance sends
    // focus hunting to the first focusable on screen (the top-left back button). Attached to
    // the editor's Close button (NOT the overlay container: a focused container that spatially
    // contains every child is a directional-search dead end — no child is "in a direction"
    // from it, so controller focus could never step inside).
    val editorFocus = remember { FocusRequester() }
    // Which group box should reclaim controller focus once the editor collapses back into it.
    // Starts non-null on a fresh entry (no editor restoring): seating focus on the top-left
    // group box makes the screen controller-ready immediately — the Select/Start panel
    // summons are preview key handlers that only fire while focus sits in this subtree, and
    // an unseated screen's first d-pad press used to default-hunt into the frame chrome.
    var returnFocusGroup by remember {
        mutableStateOf(if (expandedGroup == null) RemapSimpleGroup.LEFT_SHOULDER else null)
    }
    val inputModeManager = LocalInputModeManager.current

    // Basic-view flavor of the tap-focus-recovery below: a tap anywhere clears Compose focus
    // (touch-mode entry); with no editor open, re-seat the controller cursor on the top-left
    // group box (Left Trigger) so the next d-pad press navigates from a known home. Deferred
    // + touch-gated for the same reasons as the editor's recovery.
    var viewHadFocus by remember { mutableStateOf(false) }
    var baseRefocusTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(baseRefocusTick) {
        if (baseRefocusTick > 0 && inputModeManager.inputMode == InputMode.Touch &&
            expandedGroup == null && visibleGroup == null
        ) {
            returnFocusGroup = RemapSimpleGroup.LEFT_SHOULDER
        }
    }

    // Drives expand / collapse / switch-to-another-group. Switching collapses the current
    // editor back to its home box before expanding the next one.
    LaunchedEffect(expandedGroup) {
        val target = expandedGroup
        if (target == visibleGroup) {
            if (target != null && progress.value < 1f) {
                progress.animateTo(1f, tween(ExpandMillis, easing = FastOutSlowInEasing))
            }
            return@LaunchedEffect
        }
        if (visibleGroup != null) {
            val closing = visibleGroup
            progress.animateTo(0f, tween(CollapseMillis, easing = FastOutSlowInEasing))
            visibleGroup = null
            // Backing out (not switching groups): hand controller focus back to the home box.
            if (target == null) returnFocusGroup = closing
        }
        if (target != null) {
            visibleGroup = target
            progress.animateTo(1f, tween(ExpandMillis, easing = FastOutSlowInEasing))
        }
    }

    BackHandler(enabled = expandedGroup != null) { expandedGroup = null }

    // Move controller focus into the editor as it opens (see editorFocus above) — it lands on
    // the first command row's input button (Close when that isn't focusable), and the d-pad
    // walks the rows and header controls from there.
    // focusSeatEnabled is a key (not just a guard) so a seat deferred while a drawer held
    // focus fires when the drawers close — expandedGroup is saveable state, so a drawer-scroll
    // remount can land here with the editor already open.
    LaunchedEffect(visibleGroup, focusSeatEnabled) {
        if (visibleGroup != null && focusSeatEnabled) runCatching { editorFocus.requestFocus() }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { rootCoords = it; rootSize = it.size }
            .onFocusChanged { state ->
                if (state.hasFocus) {
                    viewHadFocus = true
                } else if (viewHadFocus && expandedGroup == null) {
                    baseRefocusTick++
                }
            },
    ) {
        // The band rides ONE plate (2026-08-30): every group box plus the controller image
        // between them sits on a single rectangular [MinputPod], so the input map reads as one
        // object floating over the view's lowest plane rather than eight boxes scattered on it.
        // The plate is centered in whatever height the view has — the flexed Gyro/Overlay strip
        // that used to claim the space below it retired the same day (see the file KDoc).
        Box(
            modifier = Modifier
                .fillMaxSize()
                // The plate's own inset from the screen edges. Horizontally it matches the top
                // bar's, so the plate's rim lines up with the identity pod's above it;
                // vertically it is the pod-to-pod gap, which is the whole distance to the
                // flush-bottomed top bar (that bar gives no vertical air by design).
                .padding(horizontal = MinputBarEdgePadding, vertical = MinputPodGap)
                // While the editor overlay is up, directional focus must not wander into
                // the plate underneath it — cancel any attempt to enter this subtree.
                .then(
                    if (visibleGroup != null) {
                        Modifier
                            .focusProperties { onEnter = { cancelFocusChange() } }
                            .focusGroup()
                    } else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
                // The input map, now in TWO sections on the plate (2026-09-11): the flanking
                // columns with the controller between them, and the centre group beneath them
                // on its own full-width row. The centre group moved out of the middle column
                // because its rows now carry every press type's assignment inline and mirror
                // around a centre line — it needs far more width than a third of the plate.
                MinputPod(
                    // A plate, not a capsule: the pill default would round this to a lozenge.
                    corner = MinputPodPlateCorner,
                    // FILLS the content section rather than hugging the band (2026-08-30,
                    // Dylan): this plate is the middle region, not chrome floating in it.
                    // That retires the IntrinsicSize.Min the band used to measure itself by
                    // — the sections take their height from the plate now.
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val box: @Composable (RemapSimpleGroup, Modifier) -> Unit = { group, boxModifier ->
                        GroupBox(
                            group = group,
                            viewingSet = viewingSet,
                            viewingLayer = viewingLayer,
                            config = config,
                            placeholder = group == visibleGroup,
                            placeholderSize = boxBounds[group]?.size,
                            onPositioned = { coords ->
                                rootCoords?.let { root -> boxBounds[group] = root.localBoundingBoxOf(coords) }
                            },
                            onOpenGroup = { expandedGroup = it },
                            requestFocus = focusSeatEnabled && group == returnFocusGroup,
                            onFocusHandled = { returnFocusGroup = null },
                            modifier = boxModifier,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(SectionGap),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // ── Flanks: the two side columns and the controller between them ──
                        // Takes all the slack the centre section leaves.
                        //
                        // BoxWithConstraints because the controller column is sized from the
                        // band's HEIGHT — see [ControllerColumnHeightRatio].
                        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                            // Clamped so a narrow band can't starve the flanking columns
                            // outright — the ratio is tuned against a landscape band.
                            val controllerWidth =
                                (maxHeight * ControllerColumnHeightRatio).coerceAtMost(maxWidth / 2)
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                // Wide gutter keeps the side group boxes off the controller image.
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                // Left column, counterclockwise start: shoulder → d-pad → stick.
                                // Its rows are MIRRORED (glyph at the box's inner edge, assignments
                                // running outward) so the flanks read as each other's reflection —
                                // see [anchorFor]. The +N badge gutters the columns used to reserve
                                // are gone with the badges themselves.
                                Column(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                                    horizontalAlignment = Alignment.End,
                                ) {
                                    box(RemapSimpleGroup.LEFT_SHOULDER, Modifier)
                                    box(RemapSimpleGroup.DPAD, Modifier)
                                    box(RemapSimpleGroup.LEFT_STICK, Modifier)
                                }
                                // Middle: just the controller image now — the Map CTA was removed
                                // 2026-09-11 (Dylan, "for now"; it was a UI-only stand-in for the
                                // input-mapping wizard) and the centre group box moved down to its
                                // own section.
                                //
                                // FIXED width, not a weight (Dylan, 2026-09-12): widening the
                                // screen must not grow the controller. Anything that later joins
                                // this column inherits that, by his instruction.
                                //
                                // sizeToIntrinsics=false: the image contributes no intrinsic
                                // height, so it never drives the band's measurement — it takes
                                // whatever the flanking group boxes leave.
                                Box(
                                    Modifier
                                        .width(controllerWidth)
                                        .fillMaxHeight()
                                        .padding(vertical = 8.dp)
                                        .paint(
                                            painter = painterResource(R.drawable.controller_placeholder),
                                            sizeToIntrinsics = false,
                                            contentScale = ContentScale.Fit,
                                        ),
                                )
                                // Right column: shoulder → face buttons → stick. Glyph at the
                                // box's inner (start) edge, assignments running outward.
                                Column(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                                    horizontalAlignment = Alignment.Start,
                                ) {
                                    box(RemapSimpleGroup.RIGHT_SHOULDER, Modifier)
                                    box(RemapSimpleGroup.FACE, Modifier)
                                    box(RemapSimpleGroup.RIGHT_STICK, Modifier)
                                }
                        }
                        }
                        // ── Centre: the utility group, centred beneath the flanks ──
                        // Wraps both ways (it may use up to the plate's width before scrolling),
                        // so the flanks keep everything it doesn't need.
                        box(RemapSimpleGroup.UTILITY, Modifier)
                    }
                }
        }

        // ── The morphing editor overlay ───────────────────────────────────
        val vg = visibleGroup
        val origin = vg?.let { boxBounds[it] }
        if (vg != null && origin != null && rootSize != IntSize.Zero) {
            // Target = nearly the whole view (a slim margin keeps the edges peeking through) —
            // controller-image-sized proved too cramped a viewing experience.
            //
            // A wrap-the-content height was trialled 2026-09-11 (sizing from
            // [advancedEditorHeight] so a two-row group didn't leave dead space below it) and
            // REVERTED the same day — Dylan preferred the full-height panel. The height
            // helper stays; it's exact and cheap if this comes back.
            val marginPx = with(LocalDensity.current) { EditorMargin.toPx() }
            val target = Rect(
                offset = Offset(marginPx, marginPx),
                size = Size(rootSize.width - marginPx * 2, rootSize.height - marginPx * 2),
            )
            val shape = RoundedCornerShape(GroupCorner)
            val container = minputBoxContainer()
            // Recompose only when the animation starts/ends; the per-frame rect is read in the
            // LAYOUT phase (the layout modifier below) and the fades in the DRAW phase
            // (graphicsLayer) — recomposing every frame is what made the morph jitter.
            val midFlight by remember { derivedStateOf { progress.value < 1f } }
            // TopStart-anchored overlay layer: the panel is placed at absolute root coords.
            // (Placing it straight in the center-aligned root Box offset the rect from the
            // CENTER slot — every morph appeared to launch from the bottom-right.)
            //
            // Focus recovery: any TAP flips the window into touch mode, which CLEARS Compose
            // focus — after that, d-pad navigation inside the editor was dead until it was
            // reopened. When the editor subtree loses all focus while still open, re-request
            // the editor's default target so the controller always has a live cursor. The
            // request is DEFERRED through state + LaunchedEffect — focus-loss also fires while
            // the composition is being disposed, and a synchronous requestFocus mid-detach
            // corrupts the node lifecycle ("Must run runDetachLifecycle()..."); an effect
            // simply never runs on a disposing composition. (Guarded on expandedGroup so a
            // closing editor doesn't fight the return-focus-to-box handoff.)
            var editorHadFocus by remember(vg) { mutableStateOf(false) }
            var refocusTick by remember(vg) { mutableIntStateOf(0) }
            LaunchedEffect(refocusTick) {
                // Touch mode only: a tap is the thing that CLEARS focus wholesale. In key
                // mode a subtree loss means focus legitimately moved elsewhere (e.g. up to
                // the set/layer tabs) — recovering would yank it straight back.
                if (refocusTick > 0 && inputModeManager.inputMode == InputMode.Touch) {
                    runCatching { editorFocus.requestFocus() }
                }
            }
            Box(
                Modifier
                    .matchParentSize()
                    .onFocusChanged { state ->
                        if (state.hasFocus) {
                            editorHadFocus = true
                        } else if (editorHadFocus && expandedGroup == vg) {
                            refocusTick++
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val rect = lerp(origin, target, progress.value)
                            val placeable = measurable.measure(
                                androidx.compose.ui.unit.Constraints.fixed(
                                    rect.width.roundToInt().coerceAtLeast(1),
                                    rect.height.roundToInt().coerceAtLeast(1),
                                ),
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
                            }
                        }
                        // offsetY 0: the offset shadow paints a dark band hugging the bottom
                        // edge that reads as the surface overextending.
                        .softDropShadow(cornerRadius = GroupCorner, offsetY = 0.dp)
                        .clip(shape)
                        .background(container)
                        .border(minputBevelBorder(container, GroupCorner), shape)
                        .testTag("group-editor"),
                ) {
                    // Crossfade: the box's summary rows dissolve into the editor as it grows.
                    if (midFlight) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer { alpha = 1f - progress.value }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            GroupRows(vg, viewingSet, viewingLayer, config)
                        }
                    }
                    RemapGroupEditor(
                        group = vg,
                        viewingSet = viewingSet,
                        viewingLayer = viewingLayer,
                        config = config,
                        callbacks = editorCallbacks,
                        onClose = { expandedGroup = null },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = progress.value },
                        focusRequester = editorFocus,
                    )
                }
            }
        }
    }
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
    LEFT_STICK(
        listOf(
            SimpleRowSpec(InputSource.LEFT_JOYSTICK, "dpad_up"),
            SimpleRowSpec(InputSource.LEFT_JOYSTICK, "dpad_left"),
            SimpleRowSpec(InputSource.LEFT_JOYSTICK, "dpad_right"),
            SimpleRowSpec(InputSource.LEFT_JOYSTICK, "dpad_down"),
            SimpleRowSpec(InputSource.LEFT_JOYSTICK, "click"),
        ),
    ),
    UTILITY(
        listOf(
            SimpleRowSpec(InputSource.SWITCH_START, "click"),
            SimpleRowSpec(InputSource.SWITCH_SELECT, "click"),
        ),
    ),
    RIGHT_STICK(
        listOf(
            SimpleRowSpec(InputSource.RIGHT_JOYSTICK, "dpad_up"),
            SimpleRowSpec(InputSource.RIGHT_JOYSTICK, "dpad_left"),
            SimpleRowSpec(InputSource.RIGHT_JOYSTICK, "dpad_right"),
            SimpleRowSpec(InputSource.RIGHT_JOYSTICK, "dpad_down"),
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
     * stick's four cardinal directions collapse into ONE movement row (Dylan, 2026-09-16): the
     * box reads "L-Stick Move" + "L-Stick Click" rather than five rows, four of which say the
     * stick moves. The directions stay individually bindable in the editor.
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
        RowAssignment(type, binding.label?.takeIf { it.isNotBlank() } ?: output.displayLabel(config))
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
    InputSource.SWITCH_START -> "Start Button"
    InputSource.SWITCH_SELECT -> "Select Button"
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
private fun GroupRows(
    group: RemapSimpleGroup,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    // Whether this box currently owns controller focus, and so whether the right stick should
    // drive its scrollers. See [ScrollingAssignmentTable].
    stickScrollEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val summaryRows = group.summaryRows
    val endRows = summaryRows.filter { group.anchorFor(it) == RowAnchor.END }
    val startRows = summaryRows.filter { group.anchorFor(it) == RowAnchor.START }
    val table: @Composable (List<SimpleRowSpec>, RowAnchor, Modifier) -> Unit = { specs, anchor, m ->
        ScrollingAssignmentTable(
            specs = specs,
            rows = specs.map { assignmentCells(viewingSet, viewingLayer, config, it) },
            anchor = anchor,
            stickScrollEnabled = stickScrollEnabled,
            modifier = m,
        )
    }
    if (endRows.isEmpty() || startRows.isEmpty()) {
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
    stickScrollEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val reversed = anchor == RowAnchor.END
    rightStickHorizontalScroll(scroll, enabled = stickScrollEnabled, invert = reversed)
    MinputOverflowScroll(
        state = scroll,
        reverseScrolling = reversed,
        chevronOutset = OverflowChevronOutset,
        modifier = modifier,
    ) {
        AssignmentTable(specs = specs, rows = rows, anchor = anchor)
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
    modifier: Modifier = Modifier,
) {
    // "N characters" measured off digits: they're tabular in every face Mappo ships, so the
    // floor is stable rather than depending on which letters a command happens to use.
    val measurer = rememberTextMeasurer()
    val cellStyle = minputMiniTextStyle()
    val assignmentFloor = remember(measurer, cellStyle) {
        measurer.measure(
            text = "0".repeat(AssignmentMinChars),
            style = cellStyle,
            softWrap = false,
        ).size.width
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

/**
 * One tappable group box (the accent-tinted petal-card treatment). While the group is
 * expanded into the editor, [placeholder] renders a same-size invisible stand-in instead — the
 * spot the editor animates back to.
 */
@Composable
private fun GroupBox(
    group: RemapSimpleGroup,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    placeholder: Boolean,
    placeholderSize: Size?,
    onPositioned: (LayoutCoordinates) -> Unit,
    onOpenGroup: (RemapSimpleGroup) -> Unit,
    // One-shot: reclaim controller focus (the editor just collapsed back into this box).
    requestFocus: Boolean = false,
    onFocusHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(GroupCorner)
    if (placeholder && placeholderSize != null) {
        // Invisible same-size stand-in, sized to the box's last measured bounds: holds the
        // home position (and the animate-back rect) while the group lives in the editor. The
        // old dashed outline moved to ui/component/DashedPlaceholderBox — the expanded editor
        // covers the whole band now, so drawing the dashes bought nothing.
        val density = LocalDensity.current
        Box(
            modifier = modifier
                .onGloballyPositioned(onPositioned)
                .size(
                    width = with(density) { placeholderSize.width.toDp() },
                    height = with(density) { placeholderSize.height.toDp() },
                ),
        )
        return
    }
    // Shared box treatment (same identity as the home flower's petal cards) — also the basis
    // the pill controls now copy, via the minputBoxContainer/remapBoxOutline helpers.
    val container = minputBoxContainer()
    val focusRequester = remember { FocusRequester() }
    if (requestFocus) {
        // LaunchedEffect (not an inline call): the box may be freshly recomposed from its
        // placeholder branch, and the focus target only exists after this composition lands.
        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
            onFocusHandled()
        }
    }
    val interaction = remember { MutableInteractionSource() }
    // The box is one focus target (it opens the editor), so "this box has focus" is exactly
    // "the stick should scroll this box's rows".
    var focused by remember { mutableStateOf(false) }
    Box(
        // WRAPS its rows (Dylan, 2026-09-16) — a group with no alternate press types makes a
        // narrow box instead of a column-wide one with dead space. The rows scroll once they
        // outgrow the column.
        modifier = modifier
            // Bounds capture must sit OUTSIDE the lift layer: localBoundingBoxOf maps
            // through graphicsLayer transforms, so capturing inside it would bake the
            // focus offset (historically the 1.05× focus grow) into the placeholder/
            // morph-origin rect — the stand-in came out displaced/oversized and the
            // neighboring boxes jumped during the morph.
            .onGloballyPositioned(onPositioned)
            .minputInteractiveMotion(interaction)
            .clip(shape)
            .background(container)
            .border(minputBevelBorder(container, GroupCorner), shape)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
            ) { onOpenGroup(group) }
            .testTag("simple-group:${group.name}")
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        GroupRows(group, viewingSet, viewingLayer, config, stickScrollEnabled = focused)
    }
}

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

/** Gutter at the centre group's centre line, keeping its two halves' glyphs off each other. */
private val CentreSplitGap = 10.dp

/** Gap between the flanking-columns section and the centre group's section beneath it. */
private val SectionGap = 10.dp

/** Floor on a table's assignment run, in characters — the width of the fixed column it
 *  replaced (2026-09-16), so a box keeps that minimum. */
private const val AssignmentMinChars = 14

/** How far each overflow chevron sits out past the scroller, into the box's 8dp padding. */
private val OverflowChevronOutset = 6.dp

/**
 * The controller column's width, as a fraction of the flanking band's HEIGHT.
 *
 * **It is sized from the height on purpose.** The band's height is the one dimension that does
 * NOT change between the 1:1 screen and the expanded one (or with the layouts drawer open), so
 * taking the width from it pins the controller at the size it has in 1:1 — Dylan's ask — on any
 * device, and hands every extra pixel of a wider screen to the flanking columns and the centre
 * group instead. A hardcoded dp would have done the "doesn't scale" half and got the size wrong
 * on anything but one device.
 *
 * 0.385 reproduced the 1.1-of-3.1 weight share it replaced; Dylan widened it by hand to 0.40
 * on 2026-09-16. The image is width-bound at this ratio, so it scales with it. **This is the knob for the controller's size now that nothing else drives it.**
 */
private const val ControllerColumnHeightRatio = 0.40f

// The group editor's morph values — canonical in the library (MinputDefaults.kt); these
// are the remap package's aliases. The layout/options panels no longer morph (they're
// MinputModals now, fade + settle) but still share GroupCorner/EditorMargin framing so
// the remap surfaces read as one family.
internal val GroupCorner = com.mappo.ui.minput.MinputMorphCorner
internal const val ExpandMillis = com.mappo.ui.minput.MinputMorphExpandMillis
internal const val CollapseMillis = com.mappo.ui.minput.MinputMorphCollapseMillis
/** Inset between the expanded editor (or full-screen panel) and its host's edges. */
internal val EditorMargin = 10.dp
