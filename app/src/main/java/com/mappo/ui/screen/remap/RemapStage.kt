package com.mappo.ui.screen.remap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.paint
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mappo.R
import com.mappo.data.model.steam.ActionLayerGraph
import com.mappo.data.model.steam.ActionSetGraph
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.ui.component.rememberMoveModeState
import androidx.compose.material3.MaterialTheme
import com.mappo.ui.minput.MinputBarEdgePadding
import com.mappo.ui.minput.MinputOverflowScroll
import com.mappo.ui.minput.MinputScrollbar
import com.mappo.ui.minput.MinputPod
import com.mappo.ui.minput.MinputPodGap
import com.mappo.ui.minput.MinputPodPlateCorner
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputBoxContainer
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.screen.softDropShadow
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The remap controls view — basic AND advanced — as ONE set of elements that zoom between two
 * geometries.
 *
 * **This is a real zoom, not a transition between two screens** (Dylan, 2026-09-17, after
 * frame-by-frame review of the first attempt): "it should not be a facsimile of zooming; it
 * should be an actual smooth zoom using the existing elements". The first cut kept the basic
 * plate and the zoomed scene as separate layers and crossfaded them while both scaled — which
 * is exactly what it looked like. There is now one plate, one controller image, and one card
 * per input group, and they travel:
 *
 * - every element has a REST rect (the basic 3 × 3 grid) and a ZOOM rect (the advanced scene
 *   under a camera parked on the open group), and its live rect is the two interpolated by
 *   `progress`. Nothing is created or destroyed on the way;
 * - a card's CONTENTS are measured once at their own natural size — the basic summary rows at
 *   rest size, the advanced table at card size — and placed through a scaling layer, so they
 *   zoom like the vector artwork they are instead of re-laying out every frame. The two
 *   crossfade over the middle of the travel;
 * - the controller image shares one aspect ratio across both geometries, so it scales
 *   uniformly rather than re-fitting.
 *
 * ```
 *  REST (progress 0)                     ZOOM (progress 1, camera on the d-pad)
 *  ┌──────────────────────────┐          ┌──────────────────────────┐
 *  │  LT│RT      ▲       LB│RB│          │ D-PAD table         │ ◀── controller
 *  │ dpad│face  ███  face│   │    ⟶      │ (the same card,     │     (the same
 *  │  LS│ util │RS        │   │          │  grown)             │      image, grown)
 *  └──────────────────────────┘          └──────────────────────────┘
 * ```
 *
 * The REST grid is a 3 × 3 matrix: the two flanks, the controller between them, the utility
 * group under it. Every cell ANCHORS TOWARD THE CENTRE one (Dylan, 2026-09-17) — the top band
 * sits on the bottom of its row, the bottom band on the top of its own, and each flank hugs its
 * inner edge — so the eight boxes cluster around the controller instead of being flung to the
 * four corners of the plate.
 *
 * The ZOOM geometry, the camera and the navigation map live in RemapZoomScene.kt.
 */
@Composable
internal fun RemapStage(
    // The group the camera is on, or null for the plain basic view. Non-null through a close
    // animation too: the host clears it once the travel is over.
    focus: RemapSimpleGroup?,
    progress: () -> Float,
    // False while a zoom is travelling. Expensive per-frame chrome (the blurred card shadows,
    // which re-rasterize on every size change) is skipped until it settles.
    settled: Boolean,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    callbacks: RemapGroupEditorCallbacks,
    /** A group box was activated — in the experiment's wiring, enter EDIT MODE on it. */
    onOpenGroup: (RemapSimpleGroup) -> Unit,
    /** A group box was HELD — zoom into its advanced card. The way into the separate view while
     *  edit mode is being tried out against it (Dylan, 2026-09-22). */
    onOpenAdvanced: (RemapSimpleGroup) -> Unit,
    onLookAt: (RemapSimpleGroup) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The group EDIT MODE was entered from, or null for the plain basic view (Dylan,
     * 2026-09-22). Non-null turns every group's rows into command tiles in place — see
     * [RowEditHost]. It decides only where the cursor lands; the mode itself is view-wide,
     * because the experiment is to keep the whole controller in view while editing one part
     * of it.
     */
    editGroup: RemapSimpleGroup? = null,
    /** Which shape the rows are in, or the travel between them. */
    editPhase: EditPhase = EditPhase.REST,
    /** That travel's position, read in the layout and draw phases only. */
    editProgress: () -> Float = { 0f },
    /** False while the rows are morphing either way. */
    editSettled: Boolean = true,
    /** One-shot: seat the cursor on this group's first tile now that the tiles are real. */
    editSeatGroup: RemapSimpleGroup? = null,
    onEditSeated: () -> Unit = {},
    /** Bumped when the view loses focus in edit mode, to re-seat the cursor on a tile. */
    editFocusTick: Int = 0,
    /**
     * A command the cursor should land on AS SOON AS IT EXISTS, named by its binding id.
     *
     * Seating by GROUP can only ever mean "that group's first tile", which is where the cursor
     * went after every add and every move — make a command on the button pad while editing from
     * the right trigger and you were returned to the right trigger (Dylan, 2026-09-24). A
     * command keeps its id through both operations, so the id is the one handle that says
     * exactly which tile the user just acted on, wherever the row's sort order puts it. Adding
     * goes out through the full-screen picker and back, which is why the claim is held saveably
     * by the screen rather than here.
     */
    seatCommand: Long? = null,
    /** Claim a command for the cursor, or clear the claim with null. */
    onSeatCommand: (Long?) -> Unit = {},
    // One-shot: the basic box that should reclaim controller focus (the zoom just collapsed
    // back into it, or the screen is being seated for the first time).
    focusSeatGroup: RemapSimpleGroup? = null,
    onFocusSeated: () -> Unit = {},
    // Landing spot for controller focus inside the opened group's table.
    entryFocus: FocusRequester? = null,
) {
    val groups = remember { RemapSimpleGroup.values().toList() }
    val zoomed = focus != null
    val editing = editGroup != null

    // ONE move state for every table: a command lifted in any of them can be carried to any
    // other. Every cell registers its window bounds here, so a finger crossing from one card to
    // the next resolves targets as it goes.
    val moveState = rememberMoveModeState<CellKey>()
    // Frames a group when its tiles change — a tile added, cleared or carried in. Declared up
    // here because the cursor-seating effects below are what claim it. See [ReframePlan].
    val reframe = remember { ReframePlan() }
    val cellFocus = remember { mutableStateMapOf<CellKey, FocusRequester>() }
    val focusHandle: (CellKey) -> FocusRequester = { key ->
        cellFocus.getOrPut(key) { FocusRequester() }
    }
    val order = LocalCommandOrder.current
    // How many tiles a row shows — its commands plus the "+". The stepper asks for this because
    // rows are variable-length stacks now (2026-09-20), not six fixed press-type columns.
    val slotsOf: (RemapSimpleGroup, SimpleRowSpec) -> Int = { _, spec ->
        rowSlotCount(rowCommandsFor(viewingSet, viewingLayer, spec, order).size)
    }
    val stepTarget: (CellKey, Int, Int) -> CellKey? = { key, dRow, dCol ->
        stepCellAcrossGroups(key, dRow, dCol, slotsOf)
    }
    val onMoveCommitted: (CellKey, CellKey) -> Unit = { from, to ->
        // Rows resolve INDIVIDUALLY: one card can span two sources (the shoulder is a trigger
        // plus a bumper), and those are separate binding groups. A command only points at its
        // row, so carrying one across groups is the same operation as moving it within one.
        val lifted = rowCommandsFor(viewingSet, viewingLayer, from.row, order).getOrNull(from.slot)
        val landedOn = rowCommandsFor(viewingSet, viewingLayer, to.row, order).getOrNull(to.slot)
        val toId = viewingSet?.presetFor(to.source)?.group?.group?.id
        if (lifted != null && toId != null) {
            // A null landing command means the row's "+": an ADD, not a swap.
            callbacks.onMoveCommand(lifted.id, toId, to.inputKey, landedOn?.id)
            // The cursor goes WITH the command. Two steps, because the move is a round trip
            // through the repository: the destination cell exists NOW, so take it immediately
            // and keep the cursor inside the body while the write comes back — otherwise focus
            // has nowhere to be and the window lights its first focusable, the layouts button
            // top left. Then the claim below lands it on the command itself, wherever the row's
            // sort order has put it (Dylan, 2026-09-24).
            runCatching { focusHandle(to).requestFocus() }
            onSeatCommand(lifted.id)
        }
    }

    // ── EDIT MODE (2026-09-22) ───────────────────────────────────────────────────────────────
    //
    // The basic view's rows become the same tiles the advanced tables are made of, everywhere at
    // once. Everything they need already exists on the stage — one move state spanning every
    // group, one focus handle per cell, one stepper that crosses between groups — because the
    // zoomed scene needed exactly the same things. What is new here is only the part a card
    // normally owns: the two dialogs a tile's menu summons, and the keyboard while a carried
    // tile is in flight.
    var labelTarget by remember { mutableStateOf<LabelEdit?>(null) }
    var typeTarget by remember { mutableStateOf<TypeEdit?>(null) }
    var liftPress by remember { mutableStateOf(LiftPress.None) }
    val haptic = LocalHapticFeedback.current
    val commitMove: (Pair<CellKey, CellKey>?) -> Unit = { pair ->
        liftPress = LiftPress.None
        pair?.let { (from, to) -> onMoveCommitted(from, to) }
    }
    val editHost = editGroup?.let {
        RowEditHost(
            moveState = moveState,
            focusHandle = focusHandle,
            onCommitMove = commitMove,
            onControllerLift = { liftPress = it },
            callbacks = callbacks,
            editable = viewingLayer == null,
            onLabel = { labelTarget = it },
            onType = { typeTarget = it },
        )
    }
    // Seat the cursor on the group the user selected — without it, entering edit mode leaves
    // focus on the box that is no longer a focus target. It waits for the morph to land: a tile
    // mid-travel is an inert ghost with no focus to take. Re-runs on [editFocusTick] too, since
    // a tap clears Compose focus wholesale and in edit mode there is no box to recover onto.
    LaunchedEffect(editSeatGroup, editFocusTick) {
        // A named command outranks a group: it says which tile, not merely which neighbourhood,
        // and it is the whole reason the cursor stopped being returned to where it came in.
        if (seatCommand != null) return@LaunchedEffect
        val group = editSeatGroup ?: editGroup.takeIf { editFocusTick > 0 && editSettled }
            ?: return@LaunchedEffect
        // The tiles compose on this frame; their requesters attach with them.
        withFrameNanos { }
        runCatching { focusHandle(CellKey(group, group.rows.first(), 0)).requestFocus() }
        onEditSeated()
    }
    // Where a command sits in the tiled rows right now, if it is on screen at all.
    fun locate(commandId: Long): CellKey? = groups.firstNotNullOfOrNull { group ->
        group.rows.firstNotNullOfOrNull { row ->
            rowCommandsFor(viewingSet, viewingLayer, row, order)
                .indexOfFirst { it.id == commandId }
                .takeIf { it >= 0 }
                ?.let { slot -> CellKey(group, row, slot) }
        }
    }
    // The claimed command, seated the moment it turns up. Re-runs on the config precisely
    // because the command is NOT there yet when the claim is made: an add and a move are both
    // writes that come back a frame or several later — an add having gone out to the picker and
    // returned in between. The claim is cleared when it lands, so an ordinary edit elsewhere
    // never drags the cursor back here.
    LaunchedEffect(seatCommand, editGroup, editSettled, viewingSet, viewingLayer) {
        val claimed = seatCommand ?: return@LaunchedEffect
        // No tiles to seat on: the claim is stale (edit mode was left while it was outstanding).
        if (editGroup == null) return@LaunchedEffect onSeatCommand(null)
        if (!editSettled) return@LaunchedEffect
        val cell = locate(claimed) ?: return@LaunchedEffect
        // Bring the whole group into view, "+" tile and all — not merely the tile itself, which
        // is all focus does on its own (Dylan, 2026-09-25: "the camera and scroll migrate to the
        // very edge of that group, including the empty tiles"). The travel overrides that minimal
        // scroll rather than racing it: while it runs, the view's position is its own function of
        // the travel, whatever the scroller is doing underneath. Claimed BEFORE the frame wait —
        // it reads the layout, not the focus requesters, so it need not wait for those to attach.
        reframe.frameGroup = cell.group
        reframe.frameTick++
        withFrameNanos { }
        runCatching { focusHandle(cell).requestFocus() }
        onSeatCommand(null)
    }

    val painter = painterResource(R.drawable.controller_placeholder)
    // The image's own proportions, used by BOTH geometries so the picture scales uniformly
    // across the zoom instead of re-fitting inside cells of different shapes.
    val aspect = remember(painter) {
        val size = painter.intrinsicSize
        if (size.isSpecified && size.width > 0f) size.height / size.width else DefaultControllerAspect
    }
    val interactions = remember { groups.associateWith { MutableInteractionSource() } }

    // Where the stage itself sits in the window. The move state registers cells in WINDOW space
    // — the one space every card shares — so this is what converts a cell's rect into a position
    // in the overlay that draws the tile in flight (see [MoveOverlay]).
    var stageOrigin by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier
            .clipToBounds()
            .onGloballyPositioned { stageOrigin = it.positionInWindow() },
    ) {
        val viewportW = maxWidth
        val viewportH = maxHeight
        val scene = remember(viewportW, viewportH, aspect) { sceneGeometry(viewportW, viewportH, aspect) }
        val density = LocalDensity.current
        // The window's own size in pixels. The stage measures against THIS rather than its
        // incoming constraints, which the body scroller leaves unbounded across.
        val viewportWPx = with(density) { viewportW.roundToPx() }
        val viewportHPx = with(density) { viewportH.roundToPx() }
        // The BODY's one scroller. Whether the right stick belongs to it is not asked here any
        // more: every scroller that can scroll puts itself forward and the arbiter picks (see
        // [com.mappo.ui.component.StickScrollArbiter]). Zoomed, this measures exactly one
        // viewport and so isn't a candidate at all, which is what the old `!zoomed` said by
        // hand; at rest with nothing focused it is the only candidate, which is the case the
        // old focus-only gate got wrong.
        val bodyScroll = rememberScrollState()
        // Keeps the view still while the rows change shape underneath it — see [EditCameraAnchor].
        val editMorph = remember { EditMorphPlan() }
        val reframeTravel = remember { Animatable(1f) }
        // What the LAYOUT is displacing the content by, in pixels — the sum of whatever travels
        // are running. The scroll CUES read it, because the scroller's own value says nothing
        // about a displacement the scroller is not the one applying (see MinputScrollbar).
        val contentShift = remember { mutableFloatStateOf(0f) }
        // Where the resting grid wants the view to sit — the scroll that puts the controller
        // dead centre (see [gridSpan]). Published from the layout, because only the layout knows
        // how wide the boxes came out; -1 until the first pass has run.
        val restCentreScroll = remember { mutableIntStateOf(-1) }
        LaunchedEffect(reframe.request.intValue) {
            if (reframe.request.intValue == 0) return@LaunchedEffect
            reframe.arm(reframe.request.intValue)
            reframeTravel.snapTo(0f)
            reframeTravel.animateTo(1f, tween(EditMorphMillis, easing = FastOutSlowInEasing))
            val landed = reframe.handOff() ?: return@LaunchedEffect
            bodyScroll.scrollTo(landed.coerceIn(0, bodyScroll.maxValue))
        }
        // The travel is over: fold the shift the anchor has been applying into the scroller
        // itself, so the two agree about where the content is and the user can still reach both
        // ends of it. Nothing moves — the scroll gains exactly what the shift gives up.
        LaunchedEffect(editSettled) {
            if (!editSettled) return@LaunchedEffect
            withFrameNanos { }
            val landed = editMorph.handOff() ?: return@LaunchedEffect
            bodyScroll.scrollTo(landed.coerceIn(0, bodyScroll.maxValue))
        }

        // ── Carrying a tile to the edge scrolls the body under it (Dylan, 2026-09-25) ────────
        //
        // With the whole grid one scroller and no card clipping anything, a command can only be
        // taken somewhere off screen if the view follows the finger there. The controller path
        // gets this free from focus bring-into-view; the pointer path had nothing, so a tile
        // could only ever be dropped within the window it was lifted in.
        //
        // The scroll re-resolves the drop target as it goes: the finger can sit still while the
        // content moves beneath it, and what is under the finger changes without the finger.
        val carryingByPointer = moveState.pointerDriven && moveState.active
        val carryBandPx = with(density) { CarryEdgeBand.toPx() }
        val carryPlateauPx = with(density) { CarryEdgePlateau.toPx() }
        val carrySpeedPx = with(density) { CarryEdgeSpeed.toPx() }
        LaunchedEffect(carryingByPointer) {
            if (!carryingByPointer) return@LaunchedEffect
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                // Per SECOND, off the frame clock — the first cut moved a fixed step per frame,
                // which is both frame-rate dependent and, at 60fps, a sprint (Dylan: "scrolls
                // too quickly"). SQUARED across the band as well, so most of the band creeps and
                // only the last few pixels of the rim travel at speed.
                val seconds = (now - previous) / 1_000_000_000f
                previous = now
                val ramp = carryEdgePush(
                    x = moveState.pointerWindow.x - stageOrigin.x,
                    viewportW = viewportWPx.toFloat(),
                    band = carryBandPx,
                    plateau = carryPlateauPx,
                )
                if (ramp != 0f) {
                    // Squared over the ramp so the outer part of the band still creeps; the
                    // plateau is already saturated, so this costs nothing at speed.
                    bodyScroll.scrollBy(ramp * abs(ramp) * carrySpeedPx * seconds)
                    // The cells moved under a stationary finger, so the drop target has to be
                    // re-resolved. The tile itself needs nothing: it is drawn from the finger's
                    // own position (see MoveModeState.carriedTopLeft).
                    moveState.refreshTargetAtPointer()
                }
            }
        }

        // The resting view opens with the controller centred. The grid says which scroll that
        // is ([GridSpan.centreScroll]) — it is NOT the middle of the range, since the range is
        // only symmetric when the two flanks are — and a scroller opens at its start, so the
        // view has to be put there. Once only: after that the view is the user's, and the
        // scrollbar and right stick move it like any other.
        var restSeeded by remember { mutableStateOf(false) }
        LaunchedEffect(bodyScroll.maxValue, restCentreScroll.intValue, editGroup) {
            if (restSeeded || editGroup != null) return@LaunchedEffect
            val max = bodyScroll.maxValue
            if (max <= 0 || max == Int.MAX_VALUE) return@LaunchedEffect
            val centre = restCentreScroll.intValue
            if (centre < 0) return@LaunchedEffect
            restSeeded = true
            // Only a view nobody has touched. The range is not known until the body has been
            // measured, so this necessarily runs a frame or more after the first one — long
            // enough that a quick flick can beat it here, and being re-centred out from under
            // a scroll already in progress is worse than opening off-centre once.
            if (bodyScroll.value == 0) bodyScroll.scrollTo(centre.coerceIn(0, max))
        }

        // The camera: where the scene sits under the viewport once zoomed. Opening SNAPS it (the
        // zoom itself carries that motion); moving between groups while zoomed PANS.
        val camera = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        var cameraSeated by remember { mutableStateOf(false) }
        val cameraTarget = remember(scene, focus) {
            val group = focus ?: return@remember null
            with(density) {
                val rect = scene.cards.getValue(group)
                Offset(
                    x = cameraAxis(rect.x.toPx(), rect.width.toPx(), viewportW.toPx(), scene.width.toPx()),
                    y = cameraAxis(rect.y.toPx(), rect.height.toPx(), viewportH.toPx(), scene.height.toPx()),
                )
            }
        }
        /**
         * **Has the user taken the camera into their own hands?**
         *
         * The camera parks on a group, which is right for a d-pad and wrong for a finger: a
         * canvas that keeps re-centring itself on the nearest group fights every drag (Dylan,
         * 2026-09-21). So parking is a GAMEPAD behaviour. A pan hands the camera to the user and
         * it stays theirs — the view is a free canvas, and each drag leaves it exactly where they
         * let go — until the gamepad is used again, at which point the camera is handed back and
         * resumes following focus.
         *
         * Note it is the PAN that flips this, not touch in general: tapping a tile, opening a
         * menu, scrolling a table all leave the camera doing what it was doing.
         */
        var touchNavigating by remember { mutableStateOf(false) }
        LaunchedEffect(cameraTarget) {
            val target = cameraTarget ?: run {
                cameraSeated = false
                touchNavigating = false
                return@LaunchedEffect
            }
            // Seating the camera on open is not "following focus" — it is where the zoom lands —
            // so it happens either way.
            if (!cameraSeated) {
                cameraSeated = true
                camera.snapTo(target)
            } else if (!touchNavigating) {
                camera.animateTo(target, tween(CameraMillis, easing = FastOutSlowInEasing))
            }
        }

        // ── Touch panning (2026-09-21, Dylan) ────────────────────────────────────────────────
        //
        // The camera parks on a GROUP, which is the right model for a d-pad: it follows focus,
        // and there is no free-roaming cursor to get lost with. A finger has no focus to follow,
        // so that left touch users tapping the sliver of a neighbouring card to get to it, when
        // the instinct is to drag the scene. The scene is now draggable — the camera is the same
        // camera and obeys the same [clampCameraAxis] bounds, so both ways of navigating reach
        // exactly the same views, and nothing about the gamepad path changes.
        val panScope = rememberCoroutineScope()
        val panEnabled = zoomed && !moveState.active
        fun clampCamera(value: Offset): Offset = with(density) {
            Offset(
                x = clampCameraAxis(value.x, viewportW.toPx(), scene.width.toPx()),
                y = clampCameraAxis(value.y, viewportH.toPx(), scene.height.toPx()),
            )
        }
        /** Drag the scene by [delta]; returns the part of it the scene's edges actually allowed. */
        fun pan(delta: Offset): Offset {
            val from = camera.value
            val to = clampCamera(from - delta)
            if (from == to) return Offset.Zero
            touchNavigating = true
            panScope.launch { camera.snapTo(to) }
            return from - to
        }
        /**
         * Adopt whichever group the viewport has come to rest over.
         *
         * The camera does NOT move for this — [touchNavigating] is set by then, so the view stays
         * exactly where the finger left it. What it does is keep the SCENE's idea of where the
         * user is looking in step with the picture, so that handing back to the gamepad lands
         * somewhere sensible rather than wherever the cursor was left before the pan began.
         */
        fun settleOnNearestGroup() {
            val centre = with(density) {
                camera.value + Offset(viewportW.toPx() / 2f, viewportH.toPx() / 2f)
            }
            val nearest = groups.minByOrNull { group ->
                val rect = scene.cards.getValue(group)
                with(density) {
                    val dx = rect.x.toPx() + rect.width.toPx() / 2f - centre.x
                    val dy = rect.y.toPx() + rect.height.toPx() / 2f - centre.y
                    dx * dx + dy * dy
                }
            }
            if (nearest != null && nearest != focus) onLookAt(nearest)
        }
        /**
         * **Is a finger actually dragging right now?**
         *
         * The gate on the nested-scroll route below, and it is not optional. Compose scrolls a
         * newly focused node into view through the very same `scrollable` machinery a finger
         * uses — `ContentInViewNode` dispatches it as `NestedScrollSource.UserInput` — so a card
         * whose table has nothing left to scroll hands the leftover straight to this connection.
         * Ungated, walking the d-pad from one card to the next SNAPPED the camera, which cancels
         * the pan that focus had just started: the camera sat where it was while focus carried
         * on without it (Dylan, 2026-09-21). Checking the `source` can't tell the two apart;
         * only the presence of a finger can.
         *
         * Observed in the Initial pass and never consumed, so nothing downstream is disturbed.
         */
        var dragging by remember { mutableStateOf(false) }
        // Drags that START on a card belong to that card's own scrollers first; the scene takes
        // only what they leave — which is what makes "keep dragging past the end of a table"
        // carry on into the scene instead of stopping dead.
        val panConnection = remember(scene, panEnabled) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset = if (panEnabled && dragging && available != Offset.Zero) {
                    pan(available)
                } else {
                    Offset.Zero
                }
            }
        }

        /**
         * Which groups' advanced tables exist right now: NONE at rest, ALL of them once zoomed.
         *
         * Zoomed in, the scene is one canvas the user pans around (by d-pad or by finger), so
         * every card on it has to be a real, finished card — a table that materializes as you
         * arrive at it is the thing that reads as the view still loading (Dylan, 2026-09-21).
         * They used to arrive one per frame, nearest first, and the reason has expired: the table
         * that made seven of them too expensive pre-exposed a tile for every (input × press type)
         * intersection, and a row is now just the commands that exist (see [rowCommands]).
         *
         * **What survives is the one-frame deferral, and it is load-bearing.** Composing the
         * tables on the frame the box is TAPPED is what made opening a group feel sluggish — the
         * frame that should be starting the zoom spends itself building tables instead. The
         * opened group's table still arrives immediately (it is the one being zoomed into, and
         * the crossfade needs it); everything else lands one frame later, while the zoom is
         * already travelling. And nothing is composed at all while the basic view is at rest, so
         * the screen still costs what it always did to show.
         *
         * If a big configuration ever makes that second frame drop, the fix is inside the table —
         * the rows are plain Columns, and going lazy there would spend the effort where the tiles
         * actually are — not by staggering the cards again.
         */
        val live = remember { mutableStateListOf<RemapSimpleGroup>() }
        LaunchedEffect(focus) {
            val open = focus
            if (open == null) {
                live.clear()
                return@LaunchedEffect
            }
            if (open !in live) live.add(open)
            if (live.size == groups.size) return@LaunchedEffect
            withFrameNanos { }
            live.addAll(groups.filter { it !in live })
        }

        val slots = buildList<@Composable () -> Unit> {
            add {
                MinputPod(corner = MinputPodPlateCorner, modifier = Modifier.fillMaxSize()) {}
            }
            add {
                Box(
                    Modifier.fillMaxSize().testTag(ControllerImageTestTag).paint(
                        painter = painter,
                        sizeToIntrinsics = false,
                        contentScale = ContentScale.Fit,
                    ),
                )
            }
            groups.forEach { group ->
                add { StageCardChrome(settled, zoomed, progress) }
            }
            groups.forEach { group ->
                add {
                    StageBasicContent(
                        group = group,
                        viewingSet = viewingSet,
                        viewingLayer = viewingLayer,
                        config = config,
                        interaction = interactions.getValue(group),
                        // A zoomed-out box is the control; a zoomed-in one is just the ghost
                        // under its own table, and must not answer to taps or hold focus. In
                        // EDIT MODE the tiles inside it are the controls, so the box steps back
                        // the same way — otherwise it would swallow taps meant for a tile and
                        // sit in the d-pad's path between them.
                        // **A box stays a focus target until its tiles are real, and becomes one
                        // again the moment they stop being** (Dylan, 2026-09-24). Switched off
                        // the instant edit mode opened, the boxes left the whole body with
                        // nothing focusable for the length of the morph — 260ms in which the
                        // window falls back to its first focusable and the layouts button, top
                        // left, visibly lights up before the cursor arrives on a tile.
                        interactive = !zoomed && (!editing || !editSettled),
                        editPhase = editPhase,
                        editProgress = editProgress,
                        seatFocus = focusSeatGroup == group,
                        onFocusSeated = onFocusSeated,
                        onOpenGroup = onOpenGroup,
                        onOpenAdvanced = onOpenAdvanced,
                        edit = editHost,
                    )
                }
            }
            groups.forEach { group ->
                add {
                    if (group in live) {
                        StageAdvancedContent(
                            group = group,
                            focused = group == focus,
                            viewingSet = viewingSet,
                            viewingLayer = viewingLayer,
                            config = config,
                            callbacks = callbacks,
                            onLookAt = { if (group != focus) onLookAt(group) },
                            onClose = onClose,
                            moveState = moveState,
                            stepTarget = stepTarget,
                            onMoveCommitted = onMoveCommitted,
                            focusHandle = focusHandle,
                            focusRequester = entryFocus.takeIf { group == focus },
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                /*
                 * Handing the camera back to the gamepad.
                 *
                 * Any hardware key means the user has put the screen down and picked the pad up,
                 * so the camera resumes following focus. But focus is still on whatever tile it
                 * was on before the panning started — possibly a screen away from what the user
                 * is now looking at — and left alone, the first d-pad press would yank the
                 * camera back there. So the cursor is seated into the group the pan came to rest
                 * over FIRST, in the preview pass, before the focus system sees the key: the
                 * press then steps from where the user is looking.
                 *
                 * Nothing is consumed — this only observes.
                 */
                .onPreviewKeyEvent { event ->
                    if (touchNavigating && event.type == KeyEventType.KeyDown) {
                        touchNavigating = false
                        focus?.let { group ->
                            runCatching {
                                focusHandle(CellKey(group, group.rows.first(), 0)).requestFocus()
                            }
                        }
                    }
                    false
                }
                // While a CONTROLLER move is in flight in EDIT MODE, the stage owns the whole
                // keyboard: arrows walk the drop target, B/Escape calls it off, the activate
                // keys confirm. It sits at the stage rather than on a group because a carried
                // tile crosses groups freely — there is no card here to hand the keys to, which
                // is the point of the experiment.
                .onKeyEvent { event ->
                    if (!editing) return@onKeyEvent false
                    moveModeKeyEvent(
                        event = event,
                        moveState = moveState,
                        owns = { true },
                        liftPress = liftPress,
                        onLiftPress = { liftPress = it },
                        onStep = { dRow, dCol ->
                            stepMoveTargetBy(moveState, stepTarget, focusHandle, haptic, dRow, dCol)
                        },
                        onCommit = commitMove,
                    )
                }
                // Watch for a finger past touch slop, consuming nothing. See [dragging].
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        var downAt: Offset? = null
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull()
                            if (change == null || !change.pressed) {
                                downAt = null
                                dragging = false
                                continue
                            }
                            val from = downAt ?: change.position.also { downAt = it }
                            if ((change.position - from).getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                            }
                        }
                    }
                }
                .nestedScroll(panConnection)
                // And drags that start anywhere ELSE — the plate, the controller, the gaps
                // between cards — pan directly. Nothing is consumed until the gesture has passed
                // touch slop without a card claiming it, so taps, tile menus and the long-press
                // carry are all untouched.
                .pointerInput(panEnabled) {
                    if (!panEnabled) return@pointerInput
                    detectDragGestures(
                        onDragEnd = { settleOnNearestGroup() },
                    ) { change, delta ->
                        change.consume()
                        pan(delta)
                    }
                },
        ) {
        // ── The BODY is the scroller (Dylan, 2026-09-22) ─────────────────────────────────────
        //
        // The group cards are gone, and with them the seven little scrollers that lived inside
        // them: a box no longer clips its own rows, it simply IS as wide as they are, and what
        // overruns the window is scrolled here, once, for the whole grid. The cues are the ones
        // the boxes used to wear (edge fade + chevrons), plus the bar beneath.
        //
        // Zoomed, the stage measures exactly one viewport wide, so this has nothing to scroll
        // and quietly gets out of the camera's way.
        CompositionLocalProvider(LocalMoveOverlay provides true) {
            MinputOverflowScroll(
                state = bodyScroll,
                orientation = Orientation.Horizontal,
                chevronOutset = BodyChevronOutset,
                // On the SCROLLING node, not the container: scroll semantics live where the
                // scroll modifier is, which is what a test drives the body through.
                scrollModifier = Modifier.testTag(ControlsBodyTestTag),
                modifier = Modifier.fillMaxSize(),
                // The morph moves the content without moving the scroller; say so, or the
                // fade and chevron describe a view that isn't on screen.
                contentShift = { contentShift.floatValue },
            ) {
            Layout(contents = slots) { measurables, _ ->
                // The VIEWPORT, not the incoming constraints: the stage now sits inside a
                // horizontal scroller (see the body scroll below), which hands its child an
                // unbounded width. Both geometries are framed against the window either way —
                // the camera's, and the grid's centring.
                val viewport = viewportWPx
                val height = viewportHPx
                val p = progress().coerceIn(0f, 1f)
                val camOffset = camera.value
                val count = groups.size

                val plateM = measurables[0].single()
                val controllerM = measurables[1].single()
                val chromeM = List(count) { measurables[2 + it].single() }
                val basicM = List(count) { measurables[2 + count + it].single() }
                val advancedM: List<Measurable?> = List(count) { measurables[2 + count * 2 + it].firstOrNull() }

                // ── REST: the 3 × 3 grid, inside the plate's inset ────────────────────────────
                val edgeX = MinputBarEdgePadding.roundToPx()
                val edgeY = MinputPodGap.roundToPx()
                val viewportGridW = (viewport - edgeX * 2).coerceAtLeast(0)
                val gridH = (height - edgeY * 2).coerceAtLeast(0)
                val columnGap = GridColumnGap.roundToPx()
                val rowGap = GridRowGap.roundToPx()
                val centreW = (gridH * ControllerColumnHeightRatio).roundToInt()
                    .coerceAtMost(viewportGridW / 2)

                val restBasic = arrayOfNulls<androidx.compose.ui.layout.Placeable>(count)
                // **Every box now WRAPS its content, with no width cap at all** (Dylan,
                // 2026-09-22). The group cards are gone, so there is no card left to clip
                // against and nothing to scroll inside: the BODY scrolls as one instead, and a
                // box is simply as wide as what it holds. That includes the centre column's
                // utility box, which used to be pinned to the column and scroll — accepting
                // that a wide one now reaches across its neighbours, which Dylan called for
                // explicitly while this is being tried out.
                groups.forEach { group ->
                    val index = groups.indexOf(group)
                    restBasic[index] = basicM[index].measure(Constraints(maxHeight = gridH))
                }
                fun restOf(group: RemapSimpleGroup) = restBasic[groups.indexOf(group)]!!

                    // The image is INSET in its column (Dylan, 2026-09-21): drawn at the column's
                // full width it ran flush into the gutters either side and, being the tallest
                // thing in its band, into the band gaps above and below — reading as artwork
                // jammed against the grid rather than sitting in it. The COLUMN keeps its width
                // (it also sizes the utility box); only the picture inside it shrinks.
                val controllerRestW = (centreW * ControllerImageFraction).roundToInt()
                val controllerRestH = (controllerRestW * aspect).roundToInt()
                val bandHeights = GridBands.mapIndexed { index, band ->
                    var tallest = maxOf(restOf(band.left).height, restOf(band.right).height)
                    if (index == ControllerBand) tallest = maxOf(tallest, controllerRestH)
                    if (index == UtilityBand) tallest = maxOf(tallest, restOf(RemapSimpleGroup.UTILITY).height)
                    tallest
                }
                // The whole matrix is CENTRED in the plate and its bands are packed: the grid used
                // to hand every spare pixel to the middle band, which pushed the shoulder row to the
                // top of the screen and the stick row to the bottom, far from the controller they
                // belong to.
                val bandTotal = bandHeights.sum() + rowGap * 2
                val bandTop = IntArray(3)
                bandTop[0] = edgeY + ((gridH - bandTotal) / 2).coerceAtLeast(0)
                for (band in 1..2) bandTop[band] = bandTop[band - 1] + bandHeights[band - 1] + rowGap
                // ── The grid, at BOTH ends of the morph (Dylan, 2026-09-24) ─────────────────
                //
                // Each side column is as wide as its widest box; the grid is that plus the
                // controller's column. When it all fits, the whole matrix is CENTRED in the
                // viewport exactly as it was when the columns split the width evenly — when it
                // doesn't, the surplus is what the body scroller scrolls.
                //
                // **Both ends are worked out once, at the start of a travel, and every frame in
                // between is a straight interpolation of the two.** Deriving each frame from the
                // one before it — the columns from the boxes' current widths, the camera from
                // the scroller's current value, which the scroller was itself clamping against
                // the width being reported — chased its own tail into a visible shake.
                val travel = editTravelAt(editProgress())
                val measuredWidths = IntArray(count) { restBasic[it]!!.width }
                if (editMorph.begin(settled = editSettled, travel = travel, measured = measuredWidths)) {
                    // The OTHER end, asked of the rows themselves: their two widths are
                    // intrinsics (see AssignmentTable), so the end being travelled to is known
                    // before a single frame of it has been drawn.
                    val entering = travel < 0.5f
                    editMorph.captureOtherEnd(
                        entering = entering,
                        widths = IntArray(count) { index ->
                            val measurable = basicM[index]
                            if (entering) {
                                measurable.maxIntrinsicWidth(gridH)
                            } else {
                                measurable.minIntrinsicWidth(gridH)
                            }
                        },
                    )
                }
                fun spanOf(widths: IntArray): GridSpan {
                    fun column(pick: (GridBand) -> RemapSimpleGroup) =
                        GridBands.maxOf { widths[groups.indexOf(pick(it))] }
                    return gridSpan(
                        leftW = column { it.left },
                        rightW = column { it.right },
                        centreW = centreW,
                        columnGap = columnGap,
                        edgeX = edgeX,
                        viewportGridW = viewportGridW,
                    )
                }
                val restWidths = editMorph.restWidths ?: measuredWidths
                val editWidths = editMorph.editWidths ?: measuredWidths
                val restSpan = spanOf(restWidths)
                val editSpan = spanOf(editWidths)
                restCentreScroll.intValue = restSpan.centreScroll
                fun spanAt(at: Float) = lerpGridSpan(restSpan, editSpan, at)
                fun maxScrollAt(at: Float) = (spanAt(at).totalW - viewport).coerceAtLeast(0)
                // Where the view should sit at each end of the travel, settled once.
                //
                //  - ON THE WAY IN it goes to the group being EDITED, by the shortest distance
                //    that brings it into view — nothing at all when it is already there, which
                //    is the usual case and the motionless one Dylan asked for. It used to
                //    inherit whatever the scroll happened to be, so opening the left trigger
                //    after a session spent on the right left you looking at the right (Dylan,
                //    2026-09-24: "very unintuitive").
                //  - ON THE WAY OUT it keeps the controller's column where it is, which is what
                //    makes the rows collapse back around it rather than sliding out from under.
                editMorph.captureScrolls(
                    settled = editSettled,
                    travel = travel,
                    scroll = bodyScroll.value.coerceIn(0, maxScrollAt(travel)),
                ) { entering, from ->
                    if (entering) {
                        editGroup?.let { group ->
                            editScrollTarget(
                                editSpan, group, editWidths, groups, centreW, viewport, edgeX, from,
                            )
                        } ?: from
                    } else {
                        // The rest end that holds the controller still.
                        (from + restSpan.centreX - editSpan.centreX).coerceIn(0, maxScrollAt(0f))
                    }
                }
                val grid = spanAt(travel)
                val morphShift = editMorph.shiftAt(
                    settled = editSettled,
                    travel = travel,
                    scroll = bodyScroll.value,
                    maxScrollAt = ::maxScrollAt,
                )
                val shift = morphShift + reframe.shiftAt(
                    // Only while edit mode is SETTLED: mid-morph the grid is meant to be
                    // changing shape, and that travel already owns the view.
                    active = editSettled && editGroup != null,
                    centreX = grid.centreX,
                    widths = measuredWidths,
                    scroll = bodyScroll.value,
                    max = maxScrollAt(travel),
                    virtual = bodyScroll.value + morphShift,
                    progress = reframeTravel.value,
                    groupAt = { groups[it] },
                    targetFor = { group, at ->
                        editScrollTarget(
                            grid, group, measuredWidths, groups, centreW, viewport, edgeX, at,
                        )
                    },
                )
                contentShift.floatValue = shift.toFloat()
                val leftColumnW = grid.leftW
                val gridW = grid.gridW
                val restTotalW = grid.totalW
                val startX = grid.startX - shift
                val centreX = grid.centreX - shift
                val rightX = grid.rightX - shift

                // Anchored toward the centre cell: the top band sits on the FLOOR of its row, the
                // bottom band on the CEILING of its own, and the middle band centres on the
                // controller. With the flanks already hugging their inner edges, that makes each
                // corner box point at the controller.
                fun restTop(band: Int, itemHeight: Int): Int = when (band) {
                    0 -> bandTop[0] + bandHeights[0] - itemHeight
                    2 -> bandTop[2]
                    else -> bandTop[band] + (bandHeights[band] - itemHeight) / 2
                }
                val restRects = HashMap<RemapSimpleGroup, StageRect>(count)
                GridBands.forEachIndexed { band, row ->
                    val left = restOf(row.left)
                    restRects[row.left] = StageRect(
                        left = startX + leftColumnW - left.width,
                        top = restTop(band, left.height),
                        width = left.width,
                        height = left.height,
                    )
                    val right = restOf(row.right)
                    restRects[row.right] = StageRect(rightX, restTop(band, right.height), right.width, right.height)
                }
                val utility = restOf(RemapSimpleGroup.UTILITY)
                restRects[RemapSimpleGroup.UTILITY] = StageRect(
                    left = centreX + (centreW - utility.width) / 2,
                    top = restTop(UtilityBand, utility.height),
                    width = utility.width,
                    height = utility.height,
                )
                val controllerRest = StageRect(
                    left = centreX + (centreW - controllerRestW) / 2,
                    top = restTop(ControllerBand, controllerRestH),
                    width = controllerRestW,
                    height = controllerRestH,
                )
                val plateRest = StageRect(edgeX - shift, edgeY, gridW, gridH)

                // ── ZOOM: the scene under the camera ─────────────────────────────────────────
                fun sceneRect(rect: SceneRect) = StageRect(
                    left = rect.x.roundToPx() - camOffset.x.roundToInt(),
                    top = rect.y.roundToPx() - camOffset.y.roundToInt(),
                    width = rect.width.roundToPx(),
                    height = rect.height.roundToPx(),
                )
                val zoomRects = groups.associateWith { sceneRect(scene.cards.getValue(it)) }
                val controllerZoom = sceneRect(scene.controller)
                val plateZoom = StageRect(
                    left = -camOffset.x.roundToInt(),
                    top = -camOffset.y.roundToInt(),
                    width = scene.width.roundToPx(),
                    height = scene.height.roundToPx(),
                )

                // ── The travel ───────────────────────────────────────────────────────────────
                val current = groups.associateWith { lerpRect(restRects.getValue(it), zoomRects.getValue(it), p) }
                val controllerNow = lerpRect(controllerRest, controllerZoom, p)
                val plateNow = lerpRect(plateRest, plateZoom, p)
                val fade = crossfadeAt(p)

                val platePlaceable = plateM.measure(Constraints.fixed(plateNow.width, plateNow.height))
                // Measured at its ZOOM size and scaled down to wherever it is now: one bitmap,
                // scaled uniformly, rather than a fresh fit on every frame.
                val controllerPlaceable = controllerM.measure(
                    Constraints.fixed(controllerZoom.width.coerceAtLeast(1), controllerZoom.height.coerceAtLeast(1)),
                )
                val chromePlaceables = groups.map { group ->
                    val rect = current.getValue(group)
                    chromeM[groups.indexOf(group)].measure(
                        Constraints.fixed(rect.width.coerceAtLeast(0), rect.height.coerceAtLeast(0)),
                    )
                }
                val advancedPlaceables = groups.map { group ->
                    val rect = zoomRects.getValue(group)
                    advancedM[groups.indexOf(group)]?.measure(
                        Constraints.fixed(rect.width.coerceAtLeast(1), rect.height.coerceAtLeast(1)),
                    )
                }

                // NO card recedes (Dylan, 2026-09-21). Cards the camera was not on used to fade
                // back to mark the one being edited; with the scene now a canvas the user roams
                // freely — by finger as much as by d-pad — every card is somewhere they may be
                // heading, and dimming what someone is reaching for reads as the view resisting
                // them. The exceptions that had already accumulated (all lit while a command is
                // being carried, all lit while the scene is dragged) were most of the time.

                // The stage is as wide as the RESTING grid, which may overrun the window — that
                // surplus is what the body scroller scrolls. Zoomed it is exactly the viewport:
                // the scene is framed by the camera, so there is nothing left to scroll, and the
                // scroller's own maximum collapses to zero as the travel lands.
                val width = lerpInt(restTotalW, viewport, p)

                layout(width, height) {
                    platePlaceable.place(plateNow.left, plateNow.top)
                    val controllerScale =
                        if (controllerZoom.width <= 0) 1f else controllerNow.width.toFloat() / controllerZoom.width
                    controllerPlaceable.placeWithLayer(
                        x = controllerNow.centerX - controllerPlaceable.width / 2,
                        y = controllerNow.centerY - controllerPlaceable.height / 2,
                    ) {
                        scaleX = controllerScale
                        scaleY = controllerScale
                    }

                    // The focused card last. Cards don't overlap once the zoom has landed, but
                    // they pass through each other on the way — the rest grid and the scene put
                    // them in different places — and the one being opened should travel over its
                    // neighbours rather than under them.
                    val order = groups.sortedBy { if (it == focus) 1 else 0 }
                    order.forEach { group ->
                        val index = groups.indexOf(group)
                        val rect = current.getValue(group)
                        chromePlaceables[index].place(rect.left, rect.top)

                        val basic = restBasic[index]!!
                        contentPlacement(basic, rect, containScale(basic.width, basic.height, rect), 1f - fade)

                        val advanced = advancedPlaceables[index] ?: return@forEach
                        val zoom = zoomRects.getValue(group)
                        val advancedScale = containScale(zoom.width, zoom.height, rect)
                        contentPlacement(advanced, rect, advancedScale, fade)
                    }
                }
            }
            }
        }

        // HOW MUCH more and WHERE, which a fade at the rim can't say. It belongs to the resting
        // view only — zoomed, the camera is the navigation — so it fades out with the travel.
        MinputScrollbar(
            state = bodyScroll,
            orientation = Orientation.Horizontal,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = MinputBarEdgePadding)
                .graphicsLayer { alpha = 1f - crossfadeAt(progress()) },
            contentShift = { contentShift.floatValue },
        )

        // ABOVE everything: the tile being carried, and the one it would displace. Outside every
        // card on purpose — a card clips, so a tile lifted from a slot at its rim was sliced by
        // the viewport's edge, and one carried toward another group stopped dead at its own
        // card's border even though the move itself crossed (Dylan, 2026-09-21).
        MoveOverlay(
            moveState = moveState,
            stageOrigin = stageOrigin,
            viewingSet = viewingSet,
            viewingLayer = viewingLayer,
            config = config,
            // Whichever tile the user actually picked up: a row's in edit mode, a table's
            // otherwise. The two modes never run at once.
            look = if (editing) rowTileLook() else TableTileLook,
        )

        // Edit mode's tiles have no card to host the dialogs their menus summon, so the stage
        // does. Both outlive the menu, and the label editor outlives a tile that has just moved.
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
}

/**
 * The scale that fits content of [contentW] x [contentH] inside [rect] — the SMALLER of the two
 * ratios.
 *
 * A card changes proportions across the zoom (a wide table, a squat box), so no single scale can
 * track both of its edges. Taking the smaller keeps the content inside its own card the whole
 * way; taking the width would have the summary rows bursting through the card's floor halfway
 * through the crossfade. Exact at both ends, where the content and its card are the same shape.
 */
private fun containScale(contentW: Int, contentH: Int, rect: StageRect): Float {
    if (contentW <= 0 || contentH <= 0) return 1f
    return minOf(rect.width.toFloat() / contentW, rect.height.toFloat() / contentH)
}

/** Place one of a card's two contents, scaled about its own centre and faded by its share of
 *  the crossfade. Split out only so the placement block above stays readable. */
private fun androidx.compose.ui.layout.Placeable.PlacementScope.contentPlacement(
    placeable: androidx.compose.ui.layout.Placeable,
    rect: StageRect,
    scale: Float,
    alpha: Float,
) {
    placeable.placeWithLayer(
        x = rect.centerX - placeable.width / 2,
        y = rect.centerY - placeable.height / 2,
    ) {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

/**
 * A group's chrome, which is now two different things at the two ends of the travel (Dylan,
 * 2026-09-22).
 *
 * **Zoomed, it is a CARD** — fill, bevel and (once the travel has settled) its shadow. Drawn at
 * the card's live size rather than scaled with its contents, so the corner radius and the bevel
 * stay the width they were designed at the whole way.
 *
 * **At rest, the card is GONE** and a thin inner line takes its place. Dylan asked for the
 * backing cards off the basic view: seven filled, bevelled, shadowed plates around seven small
 * clusters of text was a lot of container for very little content, and with the body now one
 * scrolling canvas the cards were also the thing doing the clipping. The line still demarcates
 * a group — you can see where one ends and the next begins — and it is deliberately a VECTOR
 * stroke rather than a border modifier, because it is the genesis of the connector lines that
 * will eventually run from each group to the buttons it governs on the controller image.
 *
 * Both alphas are read in the DRAW phase, so the travel repaints them without recomposing.
 */
@Composable
private fun StageCardChrome(
    settled: Boolean,
    zoomed: Boolean,
    progress: () -> Float,
) {
    val container = minputBoxContainer()
    val shape = RoundedCornerShape(GroupCorner)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = crossfadeAt(progress()) }
            // The blurred shadow re-rasterizes whenever the rect changes, which during a zoom is
            // every frame for every card — and at rest there is no card to cast it.
            .then(
                if (settled && zoomed) {
                    Modifier.softDropShadow(cornerRadius = GroupCorner, offsetY = 0.dp)
                } else Modifier,
            )
            .clip(shape)
            .background(container)
            .border(minputBevelBorder(container, GroupCorner), shape),
    )
}

/**
 * The single vector line that marks a group at rest (Dylan, 2026-09-22, corrected 2026-09-23).
 *
 * **One line, on the edge that FACES THE CONTROLLER** — not a border around the group. A full
 * ring was the first cut and read as the card it had just replaced, drawn in outline; the point
 * is a mark that says "this group belongs to that part of the controller", which is why it is a
 * drawn stroke rather than a border modifier: the connector lines that will eventually run from
 * here to the buttons themselves start at this edge.
 *
 * **It is drawn with the group's CONTENT, not with the card chrome beside it**, and that is
 * load-bearing. `minputInteractiveMotion` tracks focus on its own node, so the chrome (never
 * focusable) and the content (focusable) lift by different amounts the moment a group takes
 * focus — invisible while the chrome was a filled card behind the rows, but a hairline that
 * slid out from under them once it became a line. Same node, same lift, no gap.
 */
private fun Modifier.groupEdgeLine(
    group: RemapSimpleGroup,
    color: Color,
    alpha: () -> Float,
): Modifier = drawBehind {
    val strength = alpha()
    if (strength <= 0f) return@drawBehind
    val stroke = GroupOutlineWidth.toPx()
    val inset = GroupOutlineInset.toPx() + stroke / 2f
    // How far the line stops short of each end, so it reads as a deliberate mark rather than
    // a wall boxing the group in.
    val trim = GroupOutlineEndTrim.toPx()
    val (from, to) = when (group.controllerEdge()) {
        GroupEdge.TOP -> Offset(trim, inset) to Offset(size.width - trim, inset)
        GroupEdge.START -> Offset(inset, trim) to Offset(inset, size.height - trim)
        GroupEdge.END ->
            Offset(size.width - inset, trim) to Offset(size.width - inset, size.height - trim)
    }
    if (to.x < from.x || to.y < from.y) return@drawBehind
    drawLine(
        color = color.copy(alpha = strength),
        start = from,
        end = to,
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

/** Which edge of a group faces the controller image — where its [groupEdgeLine] is drawn. The
 *  flanks face inward; the utility group sits under the controller and faces up at it. */
private enum class GroupEdge { START, END, TOP }

private fun RemapSimpleGroup.controllerEdge(): GroupEdge = when (this) {
    RemapSimpleGroup.LEFT_SHOULDER, RemapSimpleGroup.DPAD, RemapSimpleGroup.LEFT_STICK ->
        GroupEdge.END
    RemapSimpleGroup.UTILITY -> GroupEdge.TOP
    else -> GroupEdge.START
}

/**
 * How far through the travel the two contents (and the two chromes) have traded places.
 *
 * Kept to the middle of the zoom: a table scaled down to box size is illegible and summary rows
 * blown up to card size are a blur, so neither wants to be the thing on screen at its own
 * extreme.
 */
private fun crossfadeAt(progress: Float): Float =
    ((progress.coerceIn(0f, 1f) - CrossfadeStart) / CrossfadeSpan).coerceIn(0f, 1f)

/** One group's basic-view content: the glyph + assignment rows, and (while zoomed out) the tap
 *  target that opens it. The card's surface is [StageCardChrome], a sibling. */
@Composable
private fun StageBasicContent(
    group: RemapSimpleGroup,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    interaction: MutableInteractionSource,
    interactive: Boolean,
    seatFocus: Boolean,
    onFocusSeated: () -> Unit,
    onOpenGroup: (RemapSimpleGroup) -> Unit,
    onOpenAdvanced: (RemapSimpleGroup) -> Unit,
    /** Non-null once edit mode owns this box's rows, mid-morph included. */
    edit: RowEditHost?,
    editPhase: EditPhase,
    editProgress: () -> Float,
) {
    val focusRequester = remember { FocusRequester() }
    if (seatFocus && interactive) {
        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
            onFocusSeated()
        }
    }
    // The group's own line goes ACCENT where it holds the controller cursor. With no card there
    // is no plate left to carry a focus indication, and a view navigated by d-pad has to say
    // where the cursor is. In EDIT MODE the group is not the thing being navigated — its tiles
    // are, each with its own focus ring — so the line stays quiet and the cursor is read off
    // whichever tile is lit.
    val focused by interaction.collectIsFocusedAsState()
    val lit = focused && edit == null
    val lineColor = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val lineAlpha = if (lit) GroupOutlineFocusAlpha else GroupOutlineAlpha
    Box(
        modifier = Modifier
            // **The group lifts as ONE only while it IS one** (Dylan, 2026-09-23). In edit mode
            // the individual tiles are the controls, and each already carries this same motion —
            // so a group-level lift on top of it moved the whole d-pad every time the cursor
            // landed on one of its commands.
            .then(if (edit == null) Modifier.minputInteractiveMotion(interaction) else Modifier)
            // Clipped so the tap ripple takes the card's shape: the fill and the bevel belong to
            // the chrome sibling, but the indication is drawn here.
            .clip(RoundedCornerShape(GroupCorner))
            .groupEdgeLine(group, lineColor) { lineAlpha }
            .then(
                if (interactive) {
                    Modifier
                        .focusRequester(focusRequester)
                        // HOLD opens the advanced view, TAP edits in place (Dylan, 2026-09-22 —
                        // the experiment). A hold going one level deeper is the gesture this
                        // view already teaches on its tiles, where holding lifts one.
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = LocalIndication.current,
                            onLongClick = { onOpenAdvanced(group) },
                            onClick = {
                                // Take the cursor before handing it on. A tap flips the window
                                // into touch mode, which clears Compose focus outright, so
                                // without this there is nothing focused to hand over FROM and
                                // the morph runs with the cursor parked outside the body.
                                runCatching { focusRequester.requestFocus() }
                                onOpenGroup(group)
                            },
                        )
                        // The gamepad's half of the same gesture. `clickable` has no notion of a
                        // held KEY — it fires on release — so the hold is timed here, and both
                        // ends of the press are consumed so the release can't also count as a
                        // tap. Sits AFTER the clickable in the chain, which makes it the inner
                        // node and so the first to see a key.
                        .holdToOpen(
                            onHold = { onOpenAdvanced(group) },
                            onTap = { onOpenGroup(group) },
                        )
                } else Modifier,
            )
            .testTag("simple-group:${group.name}")
            .padding(horizontal = 8.dp, vertical = 6.dp),
        // Every box wraps its own rows now, so centring costs nothing and covers the case where
        // one is ever given more room than it asked for.
        contentAlignment = Alignment.Center,
    ) {
        GroupRows(group, viewingSet, viewingLayer, config, edit = edit, phase = editPhase, progress = editProgress)
    }
}

/**
 * Hold the activate button to go one level deeper; release it without holding to act normally.
 *
 * The gamepad counterpart to `combinedClickable`'s `onLongClick`, which only knows about
 * fingers. Modelled on the tile's own hold-to-lift, down to arming only on the INITIAL key-down
 * (hardware auto-repeat re-delivers it) and to disarming on any other key — a half-committed
 * hold is the worst state this control can be in, so the gesture survives holding still and
 * nothing else.
 */
private fun Modifier.holdToOpen(onHold: () -> Unit, onTap: () -> Unit): Modifier = composed {
    val viewConfiguration = LocalViewConfiguration.current
    val haptic = LocalHapticFeedback.current
    var downAt by remember { mutableLongStateOf(0L) }
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(downAt) {
        if (downAt == 0L) return@LaunchedEffect
        delay(viewConfiguration.longPressTimeoutMillis)
        if (downAt != 0L) {
            held = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onHold()
        }
    }
    onKeyEvent { event ->
        if (event.key !in TileActivateKeys) {
            downAt = 0L
            held = false
            return@onKeyEvent false
        }
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (event.nativeKeyEvent.repeatCount == 0) {
                    downAt = System.currentTimeMillis()
                    held = false
                }
                true
            }
            KeyEventType.KeyUp -> {
                downAt = 0L
                // A hold has already acted; the release that ends it is not also a tap.
                if (!held) onTap()
                held = false
                true
            }
            else -> false
        }
    }
}

/** One group's advanced table, sized to its card in the zoomed geometry. */
@Composable
private fun StageAdvancedContent(
    group: RemapSimpleGroup,
    focused: Boolean,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    callbacks: RemapGroupEditorCallbacks,
    onLookAt: () -> Unit,
    onClose: () -> Unit,
    moveState: com.mappo.ui.component.MoveModeState<CellKey>,
    stepTarget: (CellKey, Int, Int) -> CellKey?,
    onMoveCommitted: (CellKey, CellKey) -> Unit,
    focusHandle: (CellKey) -> FocusRequester,
    focusRequester: FocusRequester?,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Focus LEADS the camera: stepping the d-pad into this card's table pans to it.
            .onFocusChanged { if (it.hasFocus) onLookAt() }
            // The touch equivalent, observed in the Initial pass and never consumed: a finger
            // reaching into a half-visible card brings it over without taking the press away
            // from whatever tile it landed on.
            //
            // Only a press that STAYS PUT counts (2026-09-21). A press that travels is a scroll
            // or a pan of the scene, and pulling the camera onto this card the moment such a
            // gesture began fought the finger for the rest of it.
            .pointerInput(group) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    var travelled = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        travelled = maxOf(travelled, (change.position - down.position).getDistance())
                        if (!change.pressed) break
                    }
                    if (travelled <= viewConfiguration.touchSlop) onLookAt()
                }
            }
            // The focused card carries the editor's identity, so anything asking for "the open
            // editor" gets the one the camera is on.
            .then(if (focused) Modifier.testTag("group-editor") else Modifier),
    ) {
        // Which card the right stick scrolls is no longer asked here either. The cursor's own
        // card wins because its table is the deepest scroller holding focus — the rule this
        // used to state by hand (Dylan, 2026-09-21: the stick is a reach of the same hand that
        // moved the cursor, so the card holding the cursor is the one it means). Under a finger
        // nothing holds focus, seven cards claim at once, and none of them move.
        Box(Modifier.fillMaxSize().testTag(zoomCardTestTag(group))) {
            RemapGroupEditor(
                group = group,
                viewingSet = viewingSet,
                viewingLayer = viewingLayer,
                config = config,
                callbacks = callbacks,
                onClose = onClose,
                modifier = Modifier.fillMaxSize(),
                moveState = moveState,
                stepTarget = stepTarget,
                onMoveCommitted = onMoveCommitted,
                focusHandle = focusHandle,
                focusRequester = focusRequester,
            )
        }
    }
}

/**
 * How hard a carried tile at [x] pulls the body along, in [-1, 1] — negative toward the start.
 *
 * Zero outside [band]. Inside it, the pull grows from nothing at the band's inner lip to FULL by
 * the time the finger is within [plateau] of the edge, and stays there — including past the edge
 * entirely, which is where a finger that has run out of glass ends up. See [CarryEdgeBand] for
 * why the plateau exists.
 */
internal fun carryEdgePush(x: Float, viewportW: Float, band: Float, plateau: Float): Float {
    val fromEdge = minOf(x, viewportW - x)
    if (fromEdge >= band) return 0f
    // A plateau as wide as the band would leave no ramp at all to divide by.
    val reach = (band - plateau).coerceAtLeast(1f)
    val depth = ((band - fromEdge) / reach).coerceIn(0f, 1f)
    return if (x < viewportW - x) -depth else depth
}

/**
 * The carry edge-scroll's shape (see [RemapStage]).
 *
 * [CarryEdgeBand] is how near the window's edge a carried tile starts pulling the body along;
 * [CarryEdgePlateau] is the inner strip of that band where the pull is already at FULL speed,
 * so topping out doesn't mean putting the finger on the glass at the very rim — which, with a
 * tile held under it, is somewhere between awkward and impossible (Dylan, 2026-09-25: "it feels
 * a little difficult to achieve full drag scroll speed"). [CarryEdgeSpeed] is that full speed,
 * per second.
 */
private val CarryEdgeBand = 120.dp
private val CarryEdgePlateau = 44.dp
private val CarryEdgeSpeed = 400.dp

/** The body's one scroller — the handle a test drives it by. */
internal const val ControlsBodyTestTag = "controls-body"

/** The picture of the device, which the resting view is arranged around — the handle a test
 *  asks "is the controller centred" by. */
internal const val ControllerImageTestTag = "controller-image"

/** The grid's horizontal metrics at one end of the morph — everything the placement needs. */
private class GridSpan(
    val leftW: Int,
    val gridW: Int,
    val totalW: Int,
    val startX: Int,
    val centreX: Int,
    val rightX: Int,
    /** The scroll value at which the controller sits dead centre in the window. */
    val centreScroll: Int,
)

private fun gridSpan(
    leftW: Int,
    rightW: Int,
    centreW: Int,
    columnGap: Int,
    edgeX: Int,
    viewportGridW: Int,
): GridSpan {
    // **Centre the CONTROLLER, not the content** (Dylan, 2026-09-25). Centring the whole matrix
    // only puts the controller in the middle when the two flanks happen to be the same width,
    // and they rarely are — so the picture of the device the entire view is arranged around sat
    // off to one side.
    //
    // What makes that possible without inventing content is padding the grid by exactly the
    // shortfall, measured against the WINDOW: whichever side has less than half a viewport
    // between the controller's middle and its own outer edge is padded up to half a viewport,
    // and the other side is padded not at all. The first attempt widened both columns to the
    // wider of the two, which is a padding rule that never looks at the window — it handed the
    // short side a slab of emptiness the user could then scroll out into, with the bar
    // promising content that was not there (Dylan: "not at all acceptable or tenable").
    //
    // The pad is therefore never scrollable space. Padding one side puts the controller-centred
    // position at THAT END of the scroll range — 0 when the pad leads, the maximum when it
    // trails — so the resting view is already as far as the scroller goes that way and there is
    // nothing to scroll into. When both sides overflow the window, nothing is padded at all and
    // the centred position is an ordinary interior scroll.
    //
    // Both ends of the morph are built by this same function, and they have to be: the rest and
    // edit grids are two coordinate systems a scroll position is carried between, so a rule
    // applied to one and not the other puts the travel's endpoints apart and the view lands
    // where neither geometry meant.
    val contentW = leftW + columnGap + centreW + columnGap + rightW
    // The controller's middle, as a distance from the content's leading edge.
    val toCentre = leftW + columnGap + centreW / 2
    val half = viewportGridW / 2
    val padLeading = (half - toCentre).coerceAtLeast(0)
    val padTrailing = (half - (contentW - toCentre)).coerceAtLeast(0)
    // The floor covers the rounding when both pads apply: two halves of an odd viewport are a
    // pixel short of it, and a grid narrower than the window it fills has no valid scroll range.
    val gridW = maxOf(contentW + padLeading + padTrailing, viewportGridW)
    val startX = edgeX + padLeading
    val centreX = startX + leftW + columnGap
    return GridSpan(
        leftW = leftW,
        gridW = gridW,
        totalW = gridW + edgeX * 2,
        startX = startX,
        centreX = centreX,
        rightX = centreX + centreW + columnGap,
        centreScroll = (padLeading + toCentre - half).coerceIn(0, gridW - viewportGridW),
    )
}

private fun lerpGridSpan(a: GridSpan, b: GridSpan, t: Float): GridSpan = when {
    t <= 0f -> a
    t >= 1f -> b
    else -> GridSpan(
        leftW = lerpInt(a.leftW, b.leftW, t),
        gridW = lerpInt(a.gridW, b.gridW, t),
        totalW = lerpInt(a.totalW, b.totalW, t),
        startX = lerpInt(a.startX, b.startX, t),
        centreX = lerpInt(a.centreX, b.centreX, t),
        rightX = lerpInt(a.rightX, b.rightX, t),
        centreScroll = lerpInt(a.centreScroll, b.centreScroll, t),
    )
}

/**
 * The scroll that brings [group] into view at the edit end, starting [from] where the view
 * already is — and staying exactly there when the group is already fully visible, which is the
 * common case and the one that has to stay motionless.
 *
 * A group too wide for the window shows its INNER edge, the side its glyph column sits on: that
 * is where its rows read from, and it is the side nearest the controller the group belongs to.
 */
/** Where a group's box starts, in the grid's own coordinates — the anchor a re-frame measures
 *  a shape change against, and the left edge [editScrollTarget] works from. */
private fun groupLeftIn(
    span: GridSpan,
    group: RemapSimpleGroup,
    widths: IntArray,
    groups: List<RemapSimpleGroup>,
    centreW: Int,
): Int {
    val width = widths[groups.indexOf(group)]
    return when {
        // A left-flank box is right-aligned to its column, so its inner edge is its right one.
        GridBands.any { it.left == group } -> span.startX + span.leftW - width
        group == RemapSimpleGroup.UTILITY -> span.centreX + (centreW - width) / 2
        else -> span.rightX
    }
}

private fun editScrollTarget(
    span: GridSpan,
    group: RemapSimpleGroup,
    widths: IntArray,
    groups: List<RemapSimpleGroup>,
    centreW: Int,
    viewport: Int,
    /** The grid's own margin, inside which there is nothing left to see. */
    edgeX: Int,
    from: Int,
): Int {
    val width = widths[groups.indexOf(group)]
    val onLeft = GridBands.any { it.left == group }
    val left = groupLeftIn(span, group, widths, groups, centreW)
    val right = left + width
    val target = when {
        // Too wide to show at once: the glyph side, whichever side that is.
        width > viewport && onLeft -> right - viewport
        width > viewport && group == RemapSimpleGroup.UTILITY -> (left + right - viewport) / 2
        width > viewport -> left
        right > from + viewport -> right - viewport
        left < from -> left
        else -> from
    }
    val max = (span.totalW - viewport).coerceAtLeast(0)
    // **Go the whole way when what is left is only the margin** (Dylan, 2026-09-25). A group's
    // box stops short of the grid's edge by [edgeX], so framing the outermost one lands that far
    // from the end of the scroll — close enough to look landed, far enough that the scroller
    // still says there is more and the edge fade and chevron stay lit over blank margin.
    return when {
        target <= edgeX -> 0
        target >= max - edgeX -> max
        else -> target
    }.coerceIn(0, max)
}

/**
 * **What holds the view still while the rows change shape** (Dylan, 2026-09-24).
 *
 * Entering edit mode makes every box wider, which widens the side columns, which pushes the
 * controller — and everything past it — along, while the scroller keeps its value. Left alone,
 * the grid lurches sideways as the tiles arrive.
 *
 * **The fix is to stop recalculating.** At the first frame of a travel this captures BOTH ends
 * of it — the box widths it is leaving (measured) and the ones it is heading for (the rows'
 * INTRINSICS), and the scroll position each end should sit at. Every frame after is a pure
 * function of one number, the travel. Nothing is measured against the previous frame, and
 * nothing reads a value it also influences.
 *
 * Plain fields, not snapshot state: it is written and read entirely inside the layout phase,
 * where the correction has to come from the same numbers in the same pass. What the cues need
 * out of it is published by the stage, which sums this travel's shift with any other's.
 */
private class EditMorphPlan {
    var restWidths: IntArray? = null
        private set
    var editWidths: IntArray? = null
        private set
    private var scrollAtRest = 0f
    private var scrollAtEdit = 0f
    private var scrollsKnown = false
    private var pending: Int? = null
    private var target: Int? = null


    /** True on the one frame a travel begins, when the other end still needs capturing. */
    fun begin(settled: Boolean, travel: Float, measured: IntArray): Boolean {
        if (settled) {
            // Keep the plan alive until the scroller has taken over the shift it is holding.
            if (pending == null) {
                restWidths = null
                editWidths = null
                scrollsKnown = false
            }
            return false
        }
        if (restWidths != null) return false
        val entering = travel < 0.5f
        if (entering) restWidths = measured else editWidths = measured
        return true
    }

    fun captureOtherEnd(entering: Boolean, widths: IntArray) {
        if (entering) editWidths = widths else restWidths = widths
    }

    /** Where the view sits at each end. [targetFor] is asked once, on the capture frame. */
    fun captureScrolls(
        settled: Boolean,
        travel: Float,
        scroll: Int,
        targetFor: (entering: Boolean, from: Int) -> Int,
    ) {
        if (settled || scrollsKnown) return
        val entering = travel < 0.5f
        val here = scroll.toFloat()
        val there = targetFor(entering, scroll).toFloat()
        scrollAtRest = if (entering) here else there
        scrollAtEdit = if (entering) there else here
        scrollsKnown = true
    }

    fun shiftAt(settled: Boolean, travel: Float, scroll: Int, maxScrollAt: (Float) -> Int): Int {
        if (settled) return pending ?: 0
        val max = maxScrollAt(travel)
        val wanted = (scrollAtRest + (scrollAtEdit - scrollAtRest) * travel).coerceIn(0f, max.toFloat())
        target = wanted.roundToInt()
        // Take off what the scroller will contribute THIS frame — its value as IT will clamp it,
        // against the very width being reported here. Reading its raw value instead left a
        // one-frame disagreement every time the content narrowed past it, which is the wobble
        // Dylan saw on the way out of edit mode from a mid-scrolled view. What this guarantees
        // is that `wanted` — a pure function of the travel — is what ends up on screen.
        val effective = scroll.coerceIn(0, max)
        return (wanted - effective).roundToInt().also { pending = it }
    }

    /**
     * The scroll position the scroller should take over, once the travel has landed — and the
     * end of the plan.
     *
     * **It resets here, not on some later layout pass.** Clearing it in [begin] instead meant
     * clearing it only if a measure happened to run while settled, which it need not: the
     * hand-off often scrolls to where the scroller already is, nothing is invalidated, and no
     * measure follows. The plan then survived into the NEXT travel, which reused its captured
     * widths and — the part that showed — its captured scroll target. So the first group opened
     * behaved, and every one after it went back to wherever the last session had been left
     * (Dylan, 2026-09-24).
     */
    fun handOff(): Int? {
        val landed = target ?: return null
        target = null
        pending = null
        restWidths = null
        editWidths = null
        scrollsKnown = false
        return landed
    }
}

/**
 * **A tile arriving or leaving re-frames the view, the same way opening a group does** (Dylan,
 * 2026-09-24).
 *
 * A group's box is as wide as its tiles, and the columns are as wide as their widest box, so
 * adding or clearing one tile re-widths the whole grid: the new tile can land past the window's
 * edge with nothing going to it, and a cleared one can drag the entire grid sideways by a tile's
 * width — or, when the content narrows past where the view was scrolled to, make the scroller
 * clamp and snap the view across in one frame.
 *
 * So a shape change is treated as a TRAVEL, exactly like entering edit mode: hold the view
 * precisely where it was — past the new clamp if need be, which simply means the space the tile
 * vacated stays on screen for the length of the animation — and interpolate from there to the
 * scroll that frames the whole changed group, empty edge tiles included ([editScrollTarget], the
 * same function that frames a group being opened).
 *
 * The change is detected in the LAYOUT, by comparing this pass's box widths against the last
 * one's, because an effect would only notice a frame later — and that frame is the jump.
 */
private class ReframePlan {
    /** Every group's box width, and where the controller's column sat, last layout. */
    private var widths: IntArray? = null
    private var anchor: Int? = null
    private var from = 0f
    private var to = 0f
    private var live = false
    private var pending: Int? = null
    private var landing: Int? = null
    private var armedFor = 0

    /** Bumped when a shape change needs animating; the stage arms the travel in answer. */
    val request = mutableIntStateOf(0)

    /**
     * **The group to frame, said out loud** (Dylan, 2026-09-25).
     *
     * Inferring it from which box changed shape is not good enough, and fails in both directions
     * that matter. A MOVE changes two boxes — the row a command left and the row it joined — and
     * the first of them is as likely as not the one already on screen, so the travel had nothing
     * to do and the minimal scroll that focus does on its own was all that happened. An ADD is
     * worse: it leaves for the full-screen output picker, so the whole screen is torn down and
     * rebuilt, there is no previous layout to compare against, and no shape change is ever seen.
     *
     * So the stage names the group when a command lands in it, which it knows by binding id
     * (see `seatCommand`) — and a claim survives the picker trip because that id does.
     */
    var frameGroup: RemapSimpleGroup? by mutableStateOf(null)
    var frameTick: Int by mutableIntStateOf(0)
    private var servedTick = 0

    /** The travel is running only once the animation has actually been reset for THIS request —
     *  until then the progress on hand still reads 1 from the last one, which would land the
     *  view at the destination on the very frame that is supposed to hold it still. */
    fun arm(id: Int) { armedFor = id }

    fun shiftAt(
        active: Boolean,
        /** Where the controller's column starts — the anchor the hold is measured against. */
        centreX: Int,
        widths: IntArray,
        scroll: Int,
        max: Int,
        /** Where the view sits right now, whatever else is displacing it. */
        virtual: Int,
        progress: Float,
        groupAt: (index: Int) -> RemapSimpleGroup,
        /** The scroll that frames a whole group, "+" tiles included, from a given position. */
        targetFor: (group: RemapSimpleGroup, from: Int) -> Int,
    ): Int {
        if (!active) {
            this.widths = null
            this.anchor = null
            live = false
            pending = null
            return 0
        }
        val wasW = this.widths
        val wasAnchor = this.anchor
        val claimed = frameGroup.takeIf { frameTick != servedTick }
        // A shape change nobody claimed — a tile cleared or pasted — frames the box it happened
        // to, which is the best guess available and the right one for a single-row change.
        val changed = if (wasW != null && !wasW.contentEquals(widths)) {
            widths.indices.first { widths[it] != wasW[it] }
        } else null
        // A claim PRE-EMPTS a travel already under way, rather than queueing behind it. A move
        // changes TWO boxes, so the shape diff tends to start a travel toward the row a command
        // left a frame or two before the claim naming the row it joined arrives — and waiting
        // one out meant two journeys, the first of them to the wrong place.
        if (claimed != null || (!live && changed != null)) {
            servedTick = frameTick
            // **The CONTROLLER is the anchor** for the hold, not the box that changed — the box
            // that changed is, more often than not, the one thing that did NOT move. A left-flank
            // box is right-aligned in a column it is itself sizing, so losing a tile leaves its
            // outer edge where it was and pulls its inner edge — and the controller, and the
            // entire right half of the grid — a whole tile's width across. Holding the picture
            // still is what makes the change read as one tile appearing or leaving rather than
            // the view lurching (Dylan proposed this anchor for the morph, 2026-09-24, for the
            // same reason; the way out of edit mode has used it since). With no previous layout
            // to measure against — the picker trip rebuilds the screen — there is nothing to
            // hold and the travel simply starts from where the view is.
            // Where the view visually IS: mid-travel that is the position the travel last
            // placed it at, not the scroller's value, which the travel has been overriding.
            val here = (if (live) landing ?: from.roundToInt() else virtual) +
                if (wasAnchor != null) centreX - wasAnchor else 0
            from = here.toFloat()
            to = targetFor(claimed ?: groupAt(changed!!), here).toFloat()
            live = true
            request.intValue++
        }
        this.widths = widths
        this.anchor = centreX
        if (!live) {
            pending = null
            return 0
        }
        val at = if (armedFor == request.intValue) progress else 0f
        // NOT clamped to the scrollable range: the hold is allowed to sit past the end while the
        // content is narrower than the view was, which is the whole point of animating out of it.
        val wanted = from + (to - from) * at
        landing = wanted.roundToInt()
        return (wanted - scroll.coerceIn(0, max)).roundToInt().also { pending = it }
    }

    /** The scroll the scroller should take over once the travel has landed. */
    fun handOff(): Int? {
        if (!live) return null
        val landed = landing ?: return null
        live = false
        landing = null
        pending = null
        return landed
    }
}

/** A placed rectangle in stage (viewport) space, in pixels. */
internal data class StageRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2
}

/** The rect [p] of the way from [a] to [b]. Exact at both ends, so a settled stage measures
 *  identically to one that never animated. */
internal fun lerpRect(a: StageRect, b: StageRect, p: Float): StageRect = when {
    p <= 0f -> a
    p >= 1f -> b
    else -> StageRect(
        left = lerpInt(a.left, b.left, p),
        top = lerpInt(a.top, b.top, p),
        width = lerpInt(a.width, b.width, p),
        height = lerpInt(a.height, b.height, p),
    )
}

private fun lerpInt(a: Int, b: Int, p: Float): Int = a + ((b - a) * p).roundToInt()

/** One band of the rest grid: the flank group on each side of the controller. */
internal class GridBand(val left: RemapSimpleGroup, val right: RemapSimpleGroup)

/** The rest grid's three bands, top to bottom. The utility group joins the last one, in the
 *  centre column — which is what seats it between the two sticks. */
internal val GridBands = listOf(
    GridBand(RemapSimpleGroup.LEFT_SHOULDER, RemapSimpleGroup.RIGHT_SHOULDER),
    GridBand(RemapSimpleGroup.DPAD, RemapSimpleGroup.FACE),
    GridBand(RemapSimpleGroup.LEFT_STICK, RemapSimpleGroup.RIGHT_STICK),
)

/** Which band the controller image occupies, and which one the utility box joins. */
private const val ControllerBand = 1
private const val UtilityBand = 2

/** Where the contents of a card start and finish trading places, as a fraction of the travel.
 *  See [crossfadeAt]. */
private const val CrossfadeStart = 0.18f
private const val CrossfadeSpan = 0.46f

/**
 * The group outline that replaced the basic view's cards (Dylan, 2026-09-22) — how far inside
 * the group's bounds it sits, how thick it draws, and how strongly it reads.
 *
 * Faint on purpose: it is there to say where one group ends and the next begins, not to rebuild
 * the card it replaced out of line work.
 */
private val GroupOutlineInset = 3.dp
private val GroupOutlineWidth = 1.dp

/** How far the group's line stops short of each end of its edge. */
private val GroupOutlineEndTrim = 4.dp
private const val GroupOutlineAlpha = 0.55f

/** The same line where the group holds the controller cursor — the focus affordance the card
 *  used to carry. Drawn in the accent, so it reads as "you are here" rather than "heavier". */
private const val GroupOutlineFocusAlpha = 0.9f

/** How far the body scroller's chevrons sit out past the grid, into the plate's own inset. */
private val BodyChevronOutset = 2.dp

/** Fallback shape for the controller artwork, if its intrinsic size is ever unavailable. */
private const val DefaultControllerAspect = 0.62f

/** Gutter between the rest grid's columns — wide enough to keep the flank boxes off the
 *  controller image between them. */
private val GridColumnGap = 18.dp

/** Gutter between the rest grid's bands. */
private val GridRowGap = 12.dp

/**
 * The controller column's width at rest, as a fraction of the grid's HEIGHT.
 *
 * **It is sized from the height on purpose.** The height is the one dimension that does NOT
 * change between the 1:1 screen and the expanded one (or with the layouts drawer open), so
 * taking the width from it pins the controller at the size it has in 1:1 — Dylan's ask — on any
 * device, and hands every extra pixel of a wider screen to the flanking columns instead. A
 * hardcoded dp would have done the "doesn't scale" half and got the size wrong on anything but
 * one device.
 *
 * 0.385 reproduced the weight share it replaced; Dylan widened it to 0.40 on 2026-09-16. **This
 * is the knob for the controller's resting COLUMN** — the picture inside it is
 * [ControllerImageFraction] of this.
 */
private const val ControllerColumnHeightRatio = 0.40f

/**
 * How much of its column the resting controller image fills (Dylan, 2026-09-21).
 *
 * Separate from [ControllerColumnHeightRatio] because the two do different jobs: the column's
 * width also sizes the utility box beneath the image and the gutters the flanks sit against, so
 * giving the picture breathing room by narrowing the column would have narrowed the box with it.
 * **This is the knob for the controller's resting size**; the column is the knob for the grid's
 * proportions.
 */
private const val ControllerImageFraction = 0.85f
