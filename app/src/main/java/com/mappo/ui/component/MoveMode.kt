package com.mappo.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import com.mappo.ui.MappoGesture
import kotlin.math.hypot
import kotlinx.coroutines.withTimeout

/**
 * Shared "pick a thing up and put it somewhere else" state machine.
 *
 * Generalized 2026-09-11 from the keyboard layout editor's button drag (the inline gesture in
 * `MainScreen.kt`, which still owns its own copy until it migrates here). The remap advanced
 * table is the second caller, and it needs something the keyboard version never did: a
 * **controller-driven** path, where a gamepad user holds the activate button to lift a tile and
 * then d-pads the drop target around. Both paths drive the same state, so the visuals (lifted
 * cell translates and rises, drop target highlights) are written once.
 *
 * [K] is whatever identifies a cell to the caller — a button id, a `(row, column)` pair. The
 * state stores keys only; it never interprets them.
 *
 * **Target resolution is split deliberately.** Pointer drags resolve by hit-testing the bounds
 * registered through [Modifier.moveModeCell], because a finger's position is the only signal
 * available. Directional (d-pad) moves are resolved by the CALLER via [moveTargetTo] — a grid
 * knows its own neighbors, and a generic nearest-rect-in-direction search would re-derive that
 * badly. Don't "unify" these; they're different questions.
 */
@Stable
class MoveModeState<K : Any> {

    /** The cell that was picked up, or null when no move is in flight. */
    var origin by mutableStateOf<K?>(null)
        private set

    /** The cell the move would currently land on — starts equal to [origin]. */
    var target by mutableStateOf<K?>(null)
        private set

    /** Live pointer translation for the lifted cell, in pixels. Zero on the controller path. */
    var dragOffset by mutableStateOf(Offset.Zero)
        private set

    /** True while a POINTER is driving the move (finger down). False for a controller lift,
     *  which persists after the button is released and ends on an explicit confirm/cancel. */
    var pointerDriven by mutableStateOf(false)
        private set

    /** Last pointer position in WINDOW space, for callers that need to know where the finger
     *  is rather than just which cell it's over — edge-scrolling a container, for instance. */
    var pointerWindow by mutableStateOf(Offset.Zero)
        private set

    /** Where inside the lifted cell the finger came down, in that cell's local space. Fixed for
     *  the life of the gesture — it is the part of the tile the user is holding. */
    var grabPoint by mutableStateOf(Offset.Zero)
        private set

    /** The lifted cell's own size, so the thing being hit-tested can be the TILE rather than
     *  the fingertip. Fixed for the gesture; zero on the controller path, which never hit-tests. */
    var carriedSize by mutableStateOf(IntSize.Zero)
        private set

    /**
     * **Would releasing right now call the move off?** True while the carried tile is further
     * from every cell than [hitTolerancePx].
     *
     * A pointer move needs a way to say "never mind", and once every scrap of screen resolves
     * to some nearest cell there is no dead space left to drop into (Dylan, 2026-09-25). So the
     * far field becomes the cancel: carry the tile well away from the grid and it reads as
     * abandoned — which the drawer is expected to SHOW, since a cancel the user can't see coming
     * is worse than no cancel at all. [target] stays on [origin] meanwhile, so the release is a
     * no-op through the ordinary path.
     */
    var outOfRange by mutableStateOf(false)
        private set

    val active: Boolean get() = origin != null

    /**
     * A move that ended WITHOUT relocating anything — cancelled outright, or released back where
     * it started — as `origin to target`.
     *
     * It exists so the visuals can play the move BACKWARDS instead of teleporting: a tile that
     * was carried somewhere and then called off should fly home the way it came (Dylan,
     * 2026-09-21). The state machine itself is already finished by then — [active] is false — so
     * this is purely a note for whoever draws the tile, and it is that caller's job to say when
     * the tile has arrived, with [settled]. A caller that doesn't animate can ignore it; the next
     * [pickUp] clears it either way.
     */
    var returning by mutableStateOf<Pair<K, K>?>(null)
        private set

    /** The tiles of a [returning] move have arrived home. */
    fun settled() { returning = null }

    /**
     * Window-space bounds per cell, kept current by [Modifier.moveModeCell]. Only the pointer
     * path reads these, and only during a gesture.
     *
     * A PLAIN map, not snapshot state (2026-09-17): nothing reads it during composition, so the
     * snapshot machinery bought nothing and cost per write — and every cell rewrites its entry
     * whenever anything re-places it. The remap zoom scene made that matter: its camera is a
     * layout-phase offset, so a pan re-places every cell of every table on every frame.
     */
    private val bounds = LinkedHashMap<K, Rect>()

    /**
     * How far the carried tile may sit from a cell and still resolve to it, in pixels — and so
     * also the distance beyond which a release CANCELS ([outOfRange]).
     *
     * Zero means strict overlap, which is wrong for any grid with gaps: a tile crossing the
     * gutter between two rows resolves to nothing, the target collapses back to the origin, and
     * the drop indicator flickers. Sizing it to the gutter turned out to be equally wrong once
     * the cells were spread over a whole screen — then the "widest gutter" is not a gutter at
     * all, it is the empty space between one input group and the next, and a tile carried across
     * it forgot where it was going (Dylan, 2026-09-25). Set it generously: it is the radius
     * within which the grid still claims a tile, not the size of the cracks in it.
     */
    var hitTolerancePx: Float = 0f

    internal fun registerBounds(key: K, rect: Rect) { bounds[key] = rect }
    internal fun unregisterBounds(key: K) { bounds.remove(key) }

    /** Window-space rect of [key], or null if it hasn't been placed yet. Used to convert a
     *  gesture's node-local pointer position into the window space the registry is keyed on. */
    internal fun boundsOf(key: K): Rect? = bounds[key]

    /** Lift [key]. [byPointer] distinguishes a finger drag from a controller lift. [grab] is
     *  where in the cell the finger landed and [size] how big the cell is; see [grabPoint] and
     *  [carriedSize]. */
    fun pickUp(
        key: K,
        byPointer: Boolean,
        grab: Offset = Offset.Zero,
        size: IntSize = IntSize.Zero,
    ) {
        origin = key
        target = key
        dragOffset = Offset.Zero
        pointerDriven = byPointer
        pointerWindow = Offset.Zero
        grabPoint = grab
        carriedSize = size
        outOfRange = false
        // A new lift supersedes any tile still drifting home from the last one.
        returning = null
    }

    /**
     * Pointer path: translate the lifted cell by [offset] (relative to the press point) and
     * re-resolve [target] by hit-testing [bounds] at [pointerWindowPos]. A pointer outside every
     * registered cell leaves the target on the origin, so releasing into dead space is a no-op
     * rather than a surprise drop.
     */
    fun dragTo(offset: Offset, pointerWindowPos: Offset) {
        if (!active) return
        dragOffset = offset
        pointerWindow = pointerWindowPos
        resolveTargetAtPointer()
    }

    /**
     * **Where a tile carried by a FINGER should be drawn**, in the coordinate space whose origin
     * in window space is [spaceOrigin].
     *
     * The finger is the authority, not the slot the tile was lifted from. Drawing it as
     * "the lifted slot's current position, plus how far the finger has travelled" holds only
     * while that slot stays put: an edge-scrolling container slides it out from underneath, so
     * the tile drifts away from the finger by however far the content moved, and once the slot
     * leaves the viewport entirely its registered rect goes empty and the tile is stranded
     * somewhere off screen (Dylan, 2026-09-25: "can flit around the screen or otherwise become
     * offset to the point where it is no longer visible"). [pointerWindow] and [grabPoint] are
     * both independent of what the content is doing, so this simply doesn't have that failure.
     */
    fun carriedTopLeft(spaceOrigin: Offset): Offset = pointerWindow - grabPoint - spaceOrigin

    /** The carried tile's rect in WINDOW space — what the drop target is resolved against. */
    private fun carriedRect(): Rect {
        val topLeft = carriedTopLeft(Offset.Zero)
        return Rect(topLeft, Size(carriedSize.width.toFloat(), carriedSize.height.toFloat()))
    }

    /**
     * Re-run the hit test against the LAST known pointer position. Needed when the cells move
     * under a stationary finger — an edge-scrolling container slides new cells beneath it, and
     * without this the drop target would stay stuck on whatever was there when the finger last
     * moved.
     */
    fun refreshTargetAtPointer() {
        if (active && pointerDriven) resolveTargetAtPointer()
    }

    private fun resolveTargetAtPointer() {
        val nearest = cellAtPointer()
        outOfRange = nearest == null
        target = nearest ?: origin
    }

    /**
     * The cell the carried TILE is on, or nearest to, within [hitTolerancePx]. Null when it is
     * beyond that — which is the cancel ([outOfRange]).
     *
     * **Measured from the tile, not the fingertip** (Dylan, 2026-09-25). Distance from the
     * pointer made the answer depend on WHERE IN THE TILE the user had grabbed it: the same tile
     * in the same place resolved to different neighbours depending on whether it had been picked
     * up by its left edge or its right. The tile is what the user is aiming; the finger is just
     * how they hold it.
     *
     * Gap distance between the two rects, so an overlap is zero and ties — a tile straddling
     * several cells, which is most of the time — break on centre-to-centre. Cells whose
     * registered rect is EMPTY are skipped: a cell scrolled out of its viewport clips to nothing
     * and isn't somewhere a tile can be put.
     */
    private fun cellAtPointer(): K? {
        val tile = carriedRect()
        var best: K? = null
        var bestGap = Float.MAX_VALUE
        var bestCentre = Float.MAX_VALUE
        bounds.forEach { (key, rect) ->
            if (rect.isEmpty) return@forEach
            val gap = hypot(
                maxOf(rect.left - tile.right, tile.left - rect.right, 0f),
                maxOf(rect.top - tile.bottom, tile.top - rect.bottom, 0f),
            )
            val centre = (rect.center - tile.center).getDistance()
            if (gap < bestGap || (gap == bestGap && centre < bestCentre)) {
                bestGap = gap
                bestCentre = centre
                best = key
            }
        }
        return best.takeIf { bestGap <= hitTolerancePx }
    }

    /** Controller path: the caller resolved a directional step to [key]. */
    fun moveTargetTo(key: K) {
        if (active) target = key
    }

    /**
     * End the move, returning `origin to target` when it's a real relocation, or null when the
     * move was a no-op (dropped on itself / nothing valid under the pointer). Clears state
     * either way, so callers can `commit()?.let { (from, to) -> … }` unconditionally.
     */
    fun commit(): Pair<K, K>? {
        val from = origin
        val to = target
        val relocated = from != null && to != null && from != to
        // A no-op drop is a cancellation by another name: nothing moves, so the tile flies home.
        end(settle = !relocated)
        return if (from != null && to != null && relocated) from to to else null
    }

    fun cancel() = end(settle = true)


    private fun end(settle: Boolean) {
        val from = origin
        returning = if (settle && from != null) from to (target ?: from) else null
        origin = null
        target = null
        dragOffset = Offset.Zero
        pointerDriven = false
        outOfRange = false
    }
}

@Composable
fun <K : Any> rememberMoveModeState(): MoveModeState<K> = remember { MoveModeState() }

/**
 * Register this node as a droppable cell keyed by [key] — required on every cell a pointer drag
 * may land on. Bounds are read in WINDOW space so cells in different scroll containers (the
 * table's frozen glyph column vs. its scrolling body) hit-test against one another correctly.
 *
 * **Put this on the cell's UNTRANSFORMED layout node — the same node as
 * [moveModeLongPressSource], never the one carrying the drag/animation `graphicsLayer`.** A
 * drop target is a RESTING SLOT: "which cell is under the finger" must not change as tiles
 * animate around, and the dragged cell's own rect must stay put or the pointer position
 * derived from it feeds back into itself. (This is the one case that inverts the usual
 * `feedback_compose_modifier_order_position_observers` advice about placing observers after
 * placement-shifting modifiers — that rule is for reporting where something ENDED UP; this
 * wants where it BELONGS.)
 */
fun <K : Any> Modifier.moveModeCell(state: MoveModeState<K>, key: K): Modifier = composed {
    // Drop the rect when the cell leaves composition, so a state shared across a changing set
    // of cells can't hit-test against a rect nothing occupies any more.
    DisposableEffect(key) { onDispose { state.unregisterBounds(key) } }
    this.onGloballyPositioned { state.registerBounds(key, it.boundsInWindow()) }
}

/**
 * Touch entry point: long-press this cell to lift it, then drag to choose a drop target and
 * release to commit. Mirrors the keyboard editor's phasing exactly — the feel is load-bearing,
 * and two drag affordances in one app that trip at different thresholds read as broken.
 *
 * **This must sit on a node with NO `graphicsLayer` transform of its own.** `pointerInput`
 * reports positions in post-transform local coordinates, so translating the same node that
 * detects the drag creates a feedback loop: the cell chases the finger while the finger looks
 * stationary to it, producing lag, flicker and drops nowhere near where you let go. Split the
 * cell into an outer layout/gesture node and an inner visual node that carries the transform,
 * exactly as `ReorderableTabBar` and the keyboard button grid do.
 *
 *  - **Phase 1** races the long-press timer against release and against movement past
 *    `touchSlop`. Nothing is consumed here, so a plain tap still reaches the cell's own
 *    `clickable` (that's what keeps tap-to-open-menu working on the same node).
 *  - **Phase 2**, once lifted: haptic, then drag beyond [MappoGesture.reorderSlopPx] before the
 *    move actually starts. The post-lift slop is deliberately coarser than `touchSlop` — the
 *    finger is already planted, so tremor must not read as intent.
 *
 * [onCommit] receives the resolved `from to to` pair; a no-op drop reports null.
 */
fun <K : Any> Modifier.moveModeLongPressSource(
    state: MoveModeState<K>,
    key: K,
    enabled: Boolean = true,
    onCommit: (Pair<K, K>?) -> Unit,
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    if (!enabled) return@composed this
    // **A LIVE handle to this node, not a remembered rect.** The window-space pointer position
    // used to be reconstructed as "the cell's registered top-left, plus the pointer's node-local
    // position", and those two are not the same age: the rect is a snapshot taken whenever
    // `onGloballyPositioned` last fired, while the local position is measured against wherever
    // the node is NOW. Scroll the container under the finger and they disagree by however far
    // the node moved in between — the faster the scroll, the bigger the lie (Dylan, 2026-09-25:
    // "the offset seems to occur at the exact moment the scroll speed increases").
    //
    // The registered rect is also `boundsInWindow()`, which is CLIPPED: as a cell slides past
    // the viewport's edge its clipped top-left stops moving and pins to that edge, while the
    // local position keeps growing — so the reconstruction drifts by exactly the amount clipped
    // away, precisely when the tile is at the edge and the carry scroll is running hardest.
    //
    // `localToWindow` walks the current layout tree at call time, so it has neither problem.
    var coordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    this.onGloballyPositioned { coordinates = it }.pointerInput(key, state) {
        fun windowPositionOf(local: Offset): Offset =
            coordinates?.takeIf { it.isAttached }?.localToWindow(local) ?: local
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val touchSlop = viewConfiguration.touchSlop
            val reorderSlop = MappoGesture.reorderSlopPx(viewConfiguration)
            val longPressMs = viewConfiguration.longPressTimeoutMillis
            val downPos = down.position

            var releasedOrMoved = false
            val longPressed: Boolean = try {
                withTimeout(longPressMs) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                        if (!change.pressed) { releasedOrMoved = true; break }
                        if ((change.position - downPos).getDistance() > touchSlop) {
                            releasedOrMoved = true; break
                        }
                    }
                }
                !releasedOrMoved
            } catch (_: PointerEventTimeoutCancellationException) {
                true
            }
            if (!longPressed) return@awaitEachGesture

            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

            var lifted = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                change.consume()
                if (!change.pressed) {
                    if (lifted) onCommit(state.commit())
                    break
                }
                if (!lifted && (change.position - downPos).getDistance() > reorderSlop) {
                    lifted = true
                    state.pickUp(key, byPointer = true, grab = downPos, size = size)
                }
                if (lifted) {
                    // The registry is keyed in WINDOW space but pointer changes arrive in this
                    // node's LOCAL space. Converted through the node's live coordinates — see
                    // [windowPositionOf] for why the cell's registered rect can't do this job.
                    state.dragTo(
                        offset = change.position - downPos,
                        pointerWindowPos = windowPositionOf(change.position),
                    )
                }
            }
        }
    }
}
