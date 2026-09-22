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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.paint
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
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
import com.mappo.ui.component.LocalStickScroll
import com.mappo.ui.component.rememberMoveModeState
import com.mappo.ui.minput.MinputBarEdgePadding
import com.mappo.ui.minput.MinputPod
import com.mappo.ui.minput.MinputPodGap
import com.mappo.ui.minput.MinputPodPlateCorner
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputBoxContainer
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.screen.softDropShadow
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
    /** Bumped when the view loses focus in edit mode, to re-seat the cursor on a tile. */
    editFocusTick: Int = 0,
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
    var liftHeld by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val commitMove: (Pair<CellKey, CellKey>?) -> Unit = { pair ->
        liftHeld = false
        pair?.let { (from, to) -> onMoveCommitted(from, to) }
    }
    val editHost = editGroup?.let {
        RowEditHost(
            moveState = moveState,
            focusHandle = focusHandle,
            onCommitMove = commitMove,
            onControllerLift = { liftHeld = true },
            callbacks = callbacks,
            editable = viewingLayer == null,
            onLabel = { labelTarget = it },
            onType = { typeTarget = it },
        )
    }
    // Seat the cursor on the group the user selected — without it, entering edit mode leaves
    // focus on the box that is no longer a focus target. Re-run on [editFocusTick] too: a tap
    // clears Compose focus wholesale, and in edit mode there is no box left to recover onto.
    LaunchedEffect(editGroup, editFocusTick) {
        val group = editGroup ?: return@LaunchedEffect
        // The tiles compose on this frame; their requesters attach with them.
        withFrameNanos { }
        runCatching { focusHandle(CellKey(group, group.rows.first(), 0)).requestFocus() }
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
                    Modifier.fillMaxSize().paint(
                        painter = painter,
                        sizeToIntrinsics = false,
                        contentScale = ContentScale.Fit,
                    ),
                )
            }
            groups.forEach { group -> add { StageCardChrome(interactions.getValue(group), settled) } }
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
                        interactive = !zoomed && !editing,
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
                        liftHeld = liftHeld,
                        onLiftHeld = { liftHeld = it },
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
        // The cards know the stage draws their tiles in flight for them, and hide the ones the
        // overlay is standing in for rather than drawing each twice.
        CompositionLocalProvider(LocalMoveOverlay provides true) {
            Layout(contents = slots) { measurables, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
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
                val gridW = (width - edgeX * 2).coerceAtLeast(0)
                val gridH = (height - edgeY * 2).coerceAtLeast(0)
                val columnGap = GridColumnGap.roundToPx()
                val rowGap = GridRowGap.roundToPx()
                val centreW = (gridH * ControllerColumnHeightRatio).roundToInt().coerceAtMost(gridW / 2)
                val sideW = ((gridW - centreW - columnGap * 2) / 2).coerceAtLeast(0)

                val restBasic = arrayOfNulls<androidx.compose.ui.layout.Placeable>(count)
                fun measureBasic(group: RemapSimpleGroup, minWidth: Int, maxWidth: Int) {
                    val index = groups.indexOf(group)
                    restBasic[index] = basicM[index].measure(
                        Constraints(
                            minWidth = minWidth.coerceIn(0, maxWidth.coerceAtLeast(0)),
                            maxWidth = maxWidth.coerceAtLeast(0),
                            maxHeight = gridH,
                        ),
                    )
                }
                // A FLANK box wraps its content: the column has the whole side of the plate to
                // spend, and a box that fits its assignments is the point of the grid.
                val flanks = GridBands.flatMap { listOf(it.left, it.right) }
                flanks.forEach { measureBasic(it, 0, sideW) }
                fun restOf(group: RemapSimpleGroup) = restBasic[groups.indexOf(group)]!!
                // A CENTRE-COLUMN box does NOT (Dylan, 2026-09-18). It is boxed in by the two flanks
                // and by the controller above it, so growing to fit its content spills it across
                // the columns either side — which is exactly what the utility box did, being two
                // mirrored halves that each claimed a full assignment run. It is pinned to the
                // centre column's width instead, and its rows scroll inside it, cueing the overflow
                // with the same fades + chevrons every other box uses. The compromise the middle
                // column costs.
                measureBasic(RemapSimpleGroup.UTILITY, centreW, centreW)

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
                val centreX = edgeX + sideW + columnGap
                val rightX = centreX + centreW + columnGap

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
                        left = edgeX + sideW - left.width,
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
                val plateRest = StageRect(edgeX, edgeY, gridW, gridH)

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
                val fade = ((p - CrossfadeStart) / CrossfadeSpan).coerceIn(0f, 1f)

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
            look = if (editing) RowTileLook else TableTileLook,
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

/** A card's surface: fill, bevel and (once the zoom has settled) its shadow. Drawn at the card's
 *  live size rather than scaled with its contents, so the corner radius and the bevel stay the
 *  width they were designed at through the whole travel. */
@Composable
private fun StageCardChrome(interaction: MutableInteractionSource, settled: Boolean) {
    val container = minputBoxContainer()
    val shape = RoundedCornerShape(GroupCorner)
    Box(
        Modifier
            .fillMaxSize()
            .minputInteractiveMotion(interaction)
            // The blurred shadow re-rasterizes whenever the rect changes, which during a zoom is
            // every frame for every card. It comes back the moment the travel ends.
            .then(if (settled) Modifier.softDropShadow(cornerRadius = GroupCorner, offsetY = 0.dp) else Modifier)
            .clip(shape)
            .background(container)
            .border(minputBevelBorder(container, GroupCorner), shape),
    )
}

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
    /** Non-null in edit mode: this box's rows are command tiles. */
    edit: RowEditHost?,
) {
    val focusRequester = remember { FocusRequester() }
    // The box is one focus target, so "this box has focus" is exactly "the stick should scroll
    // this box's rows". In edit mode the box itself can't be focused — its tiles can — so the
    // question becomes whether the cursor is anywhere INSIDE it, which is the same question.
    var focused by remember { mutableStateOf(false) }
    if (seatFocus && interactive) {
        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
            onFocusSeated()
        }
    }
    Box(
        modifier = Modifier
            .minputInteractiveMotion(interaction)
            // Clipped so the tap ripple takes the card's shape: the fill and the bevel belong to
            // the chrome sibling, but the indication is drawn here.
            .clip(RoundedCornerShape(GroupCorner))
            .onFocusChanged { focused = if (edit != null) it.hasFocus else it.isFocused }
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
                            onClick = { onOpenGroup(group) },
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
        // A CENTRE-COLUMN box is wider than its rows (it is sized by the column, not by its
        // content), and a Box hands its children a zero minimum — so without this its cluster
        // sat against the left edge of a box it is supposed to be centred in (Dylan,
        // 2026-09-19). A flank box IS its content's size, so centring costs it nothing.
        contentAlignment = Alignment.Center,
    ) {
        // The box is one focus target, so "this box has focus" is exactly "the right stick
        // should scroll this box's rows" — which the scrollers inside can't see for themselves,
        // sitting as they do INSIDE the focusable. Published, not passed: every scroller in the
        // subtree reads it, however deeply the rows get rearranged.
        CompositionLocalProvider(LocalStickScroll provides focused) {
            GroupRows(group, viewingSet, viewingLayer, config, edit = edit)
        }
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
    // Does the CONTROLLER cursor sit in this card? That — not which group the camera is parked
    // on — is what the right stick should scroll (Dylan, 2026-09-21): the stick is a reach of the
    // same hand that moved the cursor here, so the card holding the cursor is the one it means.
    // Under a finger nothing holds focus and no card answers the stick, which is correct.
    var holdsCursor by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Focus LEADS the camera: stepping the d-pad into this card's table pans to it.
            .onFocusChanged {
                holdsCursor = it.hasFocus
                if (it.hasFocus) onLookAt()
            }
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
        Box(Modifier.fillMaxSize().testTag(zoomCardTestTag(group))) {
            CompositionLocalProvider(LocalStickScroll provides holdsCursor) {
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
 *  Kept to the middle: a table scaled down to box size is illegible, and summary rows blown up
 *  to card size are a blur, so neither wants to be the thing on screen at its own extreme. */
private const val CrossfadeStart = 0.18f
private const val CrossfadeSpan = 0.46f

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
