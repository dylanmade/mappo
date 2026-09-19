package com.mappo.ui.screen.remap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.paint
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mappo.R
import com.mappo.data.model.steam.ActionLayerGraph
import com.mappo.data.model.steam.ActionSetGraph
import com.mappo.data.model.steam.ControllerConfig
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
    onOpenGroup: (RemapSimpleGroup) -> Unit,
    onLookAt: (RemapSimpleGroup) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    // One-shot: the basic box that should reclaim controller focus (the zoom just collapsed
    // back into it, or the screen is being seated for the first time).
    focusSeatGroup: RemapSimpleGroup? = null,
    onFocusSeated: () -> Unit = {},
    // Landing spot for controller focus inside the opened group's table.
    entryFocus: FocusRequester? = null,
) {
    val groups = remember { RemapSimpleGroup.values().toList() }
    val zoomed = focus != null

    // ONE move state for every table: a command lifted in any of them can be carried to any
    // other. Every cell registers its window bounds here, so a finger crossing from one card to
    // the next resolves targets as it goes.
    val moveState = rememberMoveModeState<CellKey>()
    val cellFocus = remember { mutableStateMapOf<CellKey, FocusRequester>() }
    val focusHandle: (CellKey) -> FocusRequester = { key ->
        cellFocus.getOrPut(key) { FocusRequester() }
    }
    val onMoveCommitted: (CellKey, CellKey) -> Unit = { from, to ->
        // Rows resolve INDIVIDUALLY: one card can span two sources (the shoulder is a trigger
        // plus a bumper), and those are separate binding groups.
        val fromId = viewingSet?.presetFor(from.source)?.group?.group?.id
        val toId = viewingSet?.presetFor(to.source)?.group?.group?.id
        if (fromId != null && toId != null) {
            if (fromId == toId) {
                callbacks.onMoveCell(fromId, from.inputKey, from.type, to.inputKey, to.type)
            } else {
                // Across binding groups — both "carried to another card" and "carried between
                // the two sources sharing one card" (trigger ↔ bumper).
                callbacks.onMoveCellAcross(fromId, from.inputKey, from.type, toId, to.inputKey, to.type)
            }
        }
    }

    val painter = painterResource(R.drawable.controller_placeholder)
    // The image's own proportions, used by BOTH geometries so the picture scales uniformly
    // across the zoom instead of re-fitting inside cells of different shapes.
    val aspect = remember(painter) {
        val size = painter.intrinsicSize
        if (size.isSpecified && size.width > 0f) size.height / size.width else DefaultControllerAspect
    }
    val interactions = remember { groups.associateWith { MutableInteractionSource() } }

    BoxWithConstraints(modifier.clipToBounds()) {
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
        LaunchedEffect(cameraTarget) {
            val target = cameraTarget ?: run { cameraSeated = false; return@LaunchedEffect }
            if (!cameraSeated) {
                cameraSeated = true
                camera.snapTo(target)
            } else {
                camera.animateTo(target, tween(CameraMillis, easing = FastOutSlowInEasing))
            }
        }

        // Which groups' advanced tables exist right now. Composing seven of them costs far more
        // than one frame, so the opened group's arrives immediately and the rest one per frame
        // once the zoom has landed, nearest card first — they only have to be there by the time
        // someone pans to one or carries a command into it.
        val live = remember { mutableStateListOf<RemapSimpleGroup>() }
        LaunchedEffect(focus) {
            val open = focus
            if (open == null) {
                live.clear()
                return@LaunchedEffect
            }
            if (open !in live) live.add(open)
            if (live.size == groups.size) return@LaunchedEffect
            delay(ExpandMillis.toLong())
            groups.filter { it !in live }
                .sortedBy { cardDistance(scene, open, it) }
                .forEach {
                    withFrameNanos { }
                    live.add(it)
                }
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
                        // under its own table, and must not answer to taps or hold focus.
                        interactive = !zoomed,
                        seatFocus = focusSeatGroup == group,
                        onFocusSeated = onFocusSeated,
                        onOpenGroup = onOpenGroup,
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
                            onMoveCommitted = onMoveCommitted,
                            focusHandle = focusHandle,
                            focusRequester = entryFocus.takeIf { group == focus },
                        )
                    }
                }
            }
        }

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

            val controllerRestH = (centreW * aspect).roundToInt()
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
                left = centreX,
                top = restTop(ControllerBand, controllerRestH),
                width = centreW,
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

            // A card the camera is not on recedes as the zoom takes hold — but every card comes
            // back up while a command is being CARRIED, since they are all drop targets.
            val moveActive = moveState.active
            fun dimOf(group: RemapSimpleGroup): Float =
                if (group == focus || moveActive) 1f else 1f - (1f - RestingCardAlpha) * p

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

                // The focused card last, so it sits above its neighbours as they close in.
                val order = groups.sortedBy { if (it == focus) 1 else 0 }
                order.forEach { group ->
                    val index = groups.indexOf(group)
                    val rect = current.getValue(group)
                    val dim = dimOf(group)
                    chromePlaceables[index].place(rect.left, rect.top)

                    val basic = restBasic[index]!!
                    contentPlacement(basic, rect, containScale(basic.width, basic.height, rect), (1f - fade) * dim)

                    val advanced = advancedPlaceables[index] ?: return@forEach
                    val zoom = zoomRects.getValue(group)
                    val advancedScale = containScale(zoom.width, zoom.height, rect)
                    contentPlacement(advanced, rect, advancedScale, fade * dim)
                }
            }
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
) {
    val focusRequester = remember { FocusRequester() }
    // The box is one focus target, so "this box has focus" is exactly "the stick should scroll
    // this box's rows".
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
            .then(
                if (interactive) {
                    Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused }
                        .clickable(
                            interactionSource = interaction,
                            indication = LocalIndication.current,
                        ) { onOpenGroup(group) }
                } else Modifier,
            )
            .testTag("simple-group:${group.name}")
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        GroupRows(group, viewingSet, viewingLayer, config, stickScrollEnabled = focused)
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
            .pointerInput(group) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) onLookAt()
                    }
                }
            }
            // The focused card carries the editor's identity, so anything asking for "the open
            // editor" gets the one the camera is on.
            .then(if (focused) Modifier.testTag("group-editor") else Modifier),
    ) {
        Box(Modifier.fillMaxSize().testTag(zoomCardTestTag(group))) {
            RemapGroupEditor(
                group = group,
                viewingSet = viewingSet,
                viewingLayer = viewingLayer,
                config = config,
                callbacks = callbacks,
                onClose = onClose,
                modifier = Modifier.fillMaxSize(),
                chrome = focused,
                moveState = moveState,
                stepTarget = ::stepCellAcrossGroups,
                onMoveCommitted = onMoveCommitted,
                focusHandle = focusHandle,
                focusRequester = focusRequester,
            )
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
 * is the knob for the controller's resting size.**
 */
private const val ControllerColumnHeightRatio = 0.40f
