package com.mappo.ui.screen.remap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.paint
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mappo.R
import com.mappo.data.model.steam.ActionLayerGraph
import com.mappo.data.model.steam.ActionSetGraph
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.ui.component.MoveModeState
import com.mappo.ui.component.rememberMoveModeState
import com.mappo.ui.minput.minputBevelBorder
import com.mappo.ui.minput.minputBoxContainer
import com.mappo.ui.screen.softDropShadow
import kotlin.math.roundToInt

/**
 * The advanced editor as a ZOOMED-IN camera over the whole controller, not a modal over it.
 *
 * Replaces the full-screen editor overlay (Dylan, 2026-09-17). That overlay solved "edit this
 * group" by hiding everything else, which read as isolating — the controller vanished, and
 * carrying a command to another group meant closing, finding, reopening. Here every group's
 * table exists at once, laid out around an enlarged controller image in one scene, and the
 * VIEWPORT moves over it:
 *
 * ```
 *   ┌ left column ─┐┌ centre ─┐┌ right column ─┐
 *   │ L-shoulder   ││         ││ R-shoulder    │
 *   │ D-pad        ││ [image] ││ Face buttons  │
 *   │ Left stick   ││ Utility ││ Right stick   │
 *   └──────────────┘└─────────┘└───────────────┘
 * ```
 *
 * Each column is [TableColumnFraction] of the viewport wide, so the group you are on fills
 * about two thirds of the screen and the centre column's remaining third shows the part of the
 * controller that group belongs to. Walking the d-pad off a table's edge lands on the
 * neighbouring group by ordinary spatial focus search — the scene is ONE focus surface — and
 * the camera FOLLOWS focus rather than leading it, so there is no separate "navigate" mode to
 * learn. Up and down travel a side; left and right cross the controller, via the utility group
 * seated between the two sticks (which is also where the basic view now puts it).
 *
 * **The camera is a layout-phase offset**, not a graphics-layer translation: move-mode
 * hit-testing compares registered window bounds against the pointer, and a draw-phase
 * transform would leave those bounds lying about where the cells are.
 */
@Composable
internal fun RemapZoomScene(
    group: RemapSimpleGroup,
    onGroupChange: (RemapSimpleGroup) -> Unit,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    callbacks: RemapGroupEditorCallbacks,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    // Seats controller focus on the group the user opened — the scene is placed over a view
    // whose boxes are still focusable, so without an explicit seat the cursor stays behind.
    entryGroup: RemapSimpleGroup? = null,
    entryFocus: FocusRequester? = null,
) {
    // ONE move state for the whole scene: a command lifted in any table can be carried to any
    // other. The pointer path needs nothing else — every cell registers its window bounds with
    // this state, so a finger crossing from one card to the next resolves targets as it goes.
    val moveState = rememberMoveModeState<CellKey>()
    val cellFocus = remember { mutableStateMapOf<CellKey, FocusRequester>() }
    val focusHandle: (CellKey) -> FocusRequester = { key ->
        cellFocus.getOrPut(key) { FocusRequester() }
    }
    // Resolve the binding group that owns a cell's row. Rows resolve INDIVIDUALLY: a group can
    // span two sources (the shoulder is a trigger plus a bumper), and they are separate binding
    // groups even when they share a card.
    fun bindingGroupIdOf(key: CellKey): Long? {
        val spec = key.group.rows.firstOrNull { it.subInputKey == key.inputKey } ?: return null
        return viewingSet?.presetFor(spec.source)?.group?.group?.id
    }
    val onMoveCommitted: (CellKey, CellKey) -> Unit = { from, to ->
        val fromId = bindingGroupIdOf(from)
        val toId = bindingGroupIdOf(to)
        if (fromId != null && toId != null) {
            if (fromId == toId) {
                callbacks.onMoveCell(fromId, from.inputKey, from.type, to.inputKey, to.type)
            } else {
                // Across binding groups — which is both "carried to another card" and "carried
                // between the two sources sharing one card" (trigger ↔ bumper).
                callbacks.onMoveCellAcross(
                    fromId, from.inputKey, from.type,
                    toId, to.inputKey, to.type,
                )
            }
        }
    }

    BoxWithConstraints(modifier.clipToBounds()) {
        val viewportW = maxWidth
        val viewportH = maxHeight
        val geometry = remember(viewportW, viewportH) { sceneGeometry(viewportW, viewportH) }
        val density = LocalDensity.current
        val camera = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        val target = remember(geometry, group) {
            with(density) {
                val rect = geometry.cards.getValue(group)
                Offset(
                    x = cameraAxis(rect.x.toPx(), rect.width.toPx(), viewportW.toPx(), geometry.width.toPx()),
                    y = cameraAxis(rect.y.toPx(), rect.height.toPx(), viewportH.toPx(), geometry.height.toPx()),
                )
            }
        }
        // The first target is where the scene OPENS; every later one is a pan. (The zoom-in
        // itself is the host's — see RemapSimpleView.)
        val seated = remember(geometry) { mutableStateOf(false) }
        LaunchedEffect(target) {
            if (!seated.value) {
                seated.value = true
                camera.snapTo(target)
            } else {
                camera.animateTo(target, tween(CameraMillis, easing = FastOutSlowInEasing))
            }
        }

        Box(
            // Measure the scene at its full size and place it under the camera, in ONE layout
            // modifier. (`requiredSize` + `offset` was the obvious spelling and the wrong one:
            // an oversized child is CENTRED in the size modifier's own bounds, so every camera
            // position came out shifted by half the overflow.)
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints.fixed(geometry.width.roundToPx(), geometry.height.roundToPx()),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    // Read in the PLACEMENT block: a pan re-places the scene without
                    // recomposing a single card.
                    placeable.place(
                        -camera.value.x.roundToInt(),
                        -camera.value.y.roundToInt(),
                    )
                }
            },
        ) {
            // The controller, blown up and cropped by the columns either side of it: the camera
            // is over its left flank while a left group is open, its right while a right one is.
            Box(
                Modifier
                    .offset(geometry.controller.x, geometry.controller.y)
                    .width(geometry.controller.width)
                    .height(geometry.controller.height)
                    .paint(
                        painter = painterResource(R.drawable.controller_placeholder),
                        sizeToIntrinsics = false,
                        contentScale = ContentScale.Crop,
                    ),
            )
            RemapSimpleGroup.values().forEach { sceneGroup ->
                GroupCard(
                    group = sceneGroup,
                    rect = geometry.cards.getValue(sceneGroup),
                    focused = sceneGroup == group,
                    moveActive = moveState.active,
                    onLookAt = { if (sceneGroup != group) onGroupChange(sceneGroup) },
                    viewingSet = viewingSet,
                    viewingLayer = viewingLayer,
                    config = config,
                    callbacks = callbacks,
                    onClose = onClose,
                    focusRequester = entryFocus.takeIf { sceneGroup == entryGroup },
                    moveState = moveState,
                    onMoveCommitted = onMoveCommitted,
                    focusHandle = focusHandle,
                )
            }
        }
    }
}

/** One group's advanced editor, on its own card at its place in the scene. */
@Composable
private fun GroupCard(
    group: RemapSimpleGroup,
    rect: SceneRect,
    focused: Boolean,
    moveActive: Boolean,
    onLookAt: () -> Unit,
    viewingSet: ActionSetGraph?,
    viewingLayer: ActionLayerGraph?,
    config: ControllerConfig?,
    callbacks: RemapGroupEditorCallbacks,
    onClose: () -> Unit,
    focusRequester: FocusRequester?,
    moveState: MoveModeState<CellKey>,
    onMoveCommitted: (CellKey, CellKey) -> Unit,
    focusHandle: (CellKey) -> FocusRequester,
) {
    val container = minputBoxContainer()
    val shape = RoundedCornerShape(GroupCorner)
    // The group being edited is at full strength; the ones the camera is only showing the edge
    // of are dimmed, so "where am I" survives a table that runs off both sides of the screen.
    // While a command is being CARRIED, every card comes up: they are all drop targets, and a
    // dimmed one reads as somewhere you cannot put it.
    val dim by animateFloatAsState(
        targetValue = if (focused || moveActive) 1f else RestingCardAlpha,
        animationSpec = tween(CameraMillis),
        label = "zoom-card-alpha",
    )
    Box(
        modifier = Modifier
            .offset(rect.x, rect.y)
            .width(rect.width)
            .height(rect.height)
            // Focus LEADS the camera: stepping the d-pad into this card's table pans to it.
            .onFocusChanged { if (it.hasFocus) onLookAt() }
            // The touch equivalent, observed in the Initial pass and never consumed: a finger
            // that reaches into a half-visible card brings it over without taking the press
            // away from whatever tile it landed on.
            .pointerInput(group) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) onLookAt()
                    }
                }
            }
            .graphicsLayer { alpha = dim }
            .softDropShadow(cornerRadius = GroupCorner, offsetY = 0.dp)
            .clip(shape)
            .background(container)
            .border(minputBevelBorder(container, GroupCorner), shape)
            // The focused card carries the editor's identity, so a test (or anything else)
            // asking for "the open editor" gets the one the camera is on.
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

internal fun zoomCardTestTag(group: RemapSimpleGroup): String = "zoom-card:${group.name}"

/**
 * The cell one grid step from [from], CROSSING into the neighbouring group when the step leaves
 * its table — the scene's stepper for a controller-driven move.
 *
 * Entering a neighbour keeps the sense of the travel: arriving from above lands on its top row,
 * from below its bottom row, and from the side keeps the row you were on and starts at the
 * column you are walking toward. Columns are the same six everywhere, so only rows need
 * clamping.
 */
internal fun stepCellAcrossGroups(from: CellKey, dRow: Int, dCol: Int): CellKey? {
    val rows = from.group.rows
    val row = rows.indexOfFirst { it.subInputKey == from.inputKey }.takeIf { it >= 0 } ?: return null
    val column = pressTypeColumns.indexOf(from.type).takeIf { it >= 0 } ?: return null
    val nextRow = row + dRow
    val nextColumn = column + dCol
    if (nextRow in rows.indices && nextColumn in pressTypeColumns.indices) {
        return CellKey(from.group, rows[nextRow].subInputKey, pressTypeColumns[nextColumn])
    }
    val neighbour = from.group.neighbour(dRow, dCol) ?: return null
    val neighbourRows = neighbour.rows
    val landingRow = when {
        dRow > 0 -> 0
        dRow < 0 -> neighbourRows.lastIndex
        else -> row.coerceAtMost(neighbourRows.lastIndex)
    }
    val landingColumn = when {
        dCol > 0 -> 0
        dCol < 0 -> pressTypeColumns.lastIndex
        else -> column
    }
    return CellKey(neighbour, neighbourRows[landingRow].subInputKey, pressTypeColumns[landingColumn])
}

/**
 * Which group lies one step [dRow] / [dCol] away — the scene's map, in hardware terms.
 *
 * Up and down walk a flank; left and right cross the controller, through the utility group
 * sitting between the two sticks. It is deliberately NOT derived from the laid-out rectangles:
 * spatial focus search does that well enough for a free-roaming cursor, but a command being
 * CARRIED should travel a route the user can predict and learn.
 */
internal fun RemapSimpleGroup.neighbour(dRow: Int, dCol: Int): RemapSimpleGroup? = when {
    dRow > 0 -> when (this) {
        RemapSimpleGroup.LEFT_SHOULDER -> RemapSimpleGroup.DPAD
        RemapSimpleGroup.DPAD -> RemapSimpleGroup.LEFT_STICK
        RemapSimpleGroup.RIGHT_SHOULDER -> RemapSimpleGroup.FACE
        RemapSimpleGroup.FACE -> RemapSimpleGroup.RIGHT_STICK
        else -> null
    }
    dRow < 0 -> when (this) {
        RemapSimpleGroup.DPAD -> RemapSimpleGroup.LEFT_SHOULDER
        RemapSimpleGroup.LEFT_STICK -> RemapSimpleGroup.DPAD
        RemapSimpleGroup.FACE -> RemapSimpleGroup.RIGHT_SHOULDER
        RemapSimpleGroup.RIGHT_STICK -> RemapSimpleGroup.FACE
        else -> null
    }
    dCol > 0 -> when (this) {
        RemapSimpleGroup.LEFT_SHOULDER -> RemapSimpleGroup.RIGHT_SHOULDER
        RemapSimpleGroup.DPAD -> RemapSimpleGroup.FACE
        RemapSimpleGroup.LEFT_STICK -> RemapSimpleGroup.UTILITY
        RemapSimpleGroup.UTILITY -> RemapSimpleGroup.RIGHT_STICK
        else -> null
    }
    dCol < 0 -> when (this) {
        RemapSimpleGroup.RIGHT_SHOULDER -> RemapSimpleGroup.LEFT_SHOULDER
        RemapSimpleGroup.FACE -> RemapSimpleGroup.DPAD
        RemapSimpleGroup.RIGHT_STICK -> RemapSimpleGroup.UTILITY
        RemapSimpleGroup.UTILITY -> RemapSimpleGroup.LEFT_STICK
        else -> null
    }
    else -> null
}

/** A placed rectangle in scene space. */
private data class SceneRect(val x: Dp, val y: Dp, val width: Dp, val height: Dp)

private class SceneGeometry(
    val width: Dp,
    val height: Dp,
    val cards: Map<RemapSimpleGroup, SceneRect>,
    val controller: SceneRect,
)

/**
 * Lay the scene out for a viewport of [viewportW] × [viewportH].
 *
 * Three columns of equal width, each [TableColumnFraction] of the viewport: the side columns
 * hold three group cards apiece, the centre one the controller image with the utility card
 * beneath it. Rows are BANDS shared by the two flanks — a left card and its opposite number sit
 * in the same band, whatever their heights — so crossing left or right lands on the group
 * physically opposite, and the camera doesn't lurch vertically on the way.
 *
 * Every card is the height its table wants ([advancedEditorHeight]), capped at the viewport:
 * past that the table scrolls inside the card, as it always has.
 */
private fun sceneGeometry(viewportW: Dp, viewportH: Dp): SceneGeometry {
    val columnW = viewportW * TableColumnFraction
    val maxCardH = (viewportH - SceneMargin * 2).coerceAtLeast(0.dp)
    fun cardHeight(group: RemapSimpleGroup): Dp = advancedEditorHeight(group).coerceAtMost(maxCardH)

    val left = listOf(RemapSimpleGroup.LEFT_SHOULDER, RemapSimpleGroup.DPAD, RemapSimpleGroup.LEFT_STICK)
    val right = listOf(RemapSimpleGroup.RIGHT_SHOULDER, RemapSimpleGroup.FACE, RemapSimpleGroup.RIGHT_STICK)
    val bandHeights = left.indices.map { maxOf(cardHeight(left[it]), cardHeight(right[it])) }
    val bandTops = mutableListOf<Dp>()
    var y = SceneMargin
    bandHeights.forEach { h ->
        bandTops += y
        y += h + BandGap
    }
    val sceneH = y - BandGap + SceneMargin

    val sideCardW = (columnW - SceneMargin - CentreGutter).coerceAtLeast(0.dp)
    val cards = buildMap {
        left.forEachIndexed { band, group ->
            val h = cardHeight(group)
            put(group, SceneRect(SceneMargin, bandTops[band] + (bandHeights[band] - h) / 2, sideCardW, h))
        }
        right.forEachIndexed { band, group ->
            val h = cardHeight(group)
            put(
                group,
                SceneRect(
                    x = columnW * 2 + CentreGutter,
                    y = bandTops[band] + (bandHeights[band] - h) / 2,
                    width = sideCardW,
                    height = h,
                ),
            )
        }
        // The utility card sits in the centre column, in the STICK band: the thumb cluster is
        // where Start and Select live on the hardware, and it puts the group one step right of
        // the left stick and one step left of the right one.
        val utilityH = cardHeight(RemapSimpleGroup.UTILITY)
        put(
            RemapSimpleGroup.UTILITY,
            SceneRect(
                x = columnW + CentreGutter,
                y = bandTops[2] + (bandHeights[2] - utilityH) / 2,
                width = (columnW - CentreGutter * 2).coerceAtLeast(0.dp),
                height = utilityH,
            ),
        )
    }

    // The image fills the centre column above the utility card — the two bands the shoulders,
    // d-pad and face buttons occupy.
    val controller = SceneRect(
        x = columnW,
        y = 0.dp,
        width = columnW,
        height = (bandTops[2] - BandGap).coerceAtLeast(0.dp),
    )
    return SceneGeometry(width = columnW * 3, height = sceneH, cards = cards, controller = controller)
}

/**
 * Where the camera sits on one axis so a card of [cardExtent] at [cardStart] is centred in a
 * [viewportExtent] window onto a scene of [sceneExtent].
 *
 * Centring is all the stops need: a side column's card centres past the scene's edge and clamps
 * flush to it, which is exactly the "table on its own side, controller beside it" framing, while
 * the utility card — the only one in the middle — genuinely centres.
 */
private fun cameraAxis(cardStart: Float, cardExtent: Float, viewportExtent: Float, sceneExtent: Float): Float {
    val slack = sceneExtent - viewportExtent
    val centred = cardStart + cardExtent / 2f - viewportExtent / 2f
    // Scene smaller than the window: centre the scene itself rather than pinning it to a corner.
    return if (slack <= 0f) slack / 2f else centred.coerceIn(0f, slack)
}

/** How much of the viewport one column takes — the group's table, leaving the rest for the
 *  controller beside it (Dylan, 2026-09-17: roughly two thirds / one third). */
private const val TableColumnFraction = 0.68f

/** Inset from the scene's outer edges, so a flush-clamped card doesn't touch the screen. */
private val SceneMargin = 10.dp

/** Air between a side card and the centre column's image. */
private val CentreGutter = 12.dp

/** Vertical space between bands. */
private val BandGap = 14.dp

/** A pan between groups. Matches the editor's own open/close motion. */
private const val CameraMillis = 260

/** Opacity of the cards the camera is not on. */
private const val RestingCardAlpha = 0.45f
