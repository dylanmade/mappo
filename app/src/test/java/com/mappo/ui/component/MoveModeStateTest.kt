package com.mappo.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The move state machine's ENDINGS, which is where it grew a second outcome (2026-09-21).
 *
 * A move used to end one way: state cleared, caller told whether anything relocated. The tile on
 * screen then teleported home whenever it hadn't. `returning` is the note that says "these two
 * cells are still on their way back", so whoever draws them can play the move backwards — and it
 * is the DRAWER's job to say when they have arrived, because only it knows how long that took.
 */
class MoveModeStateTest {

    private fun state() = MoveModeState<String>()

    @Test
    fun commit_thatRelocates_reportsThePair_andLeavesNothingReturning() {
        val state = state()
        state.pickUp("a", byPointer = false)
        state.moveTargetTo("b")

        assertEquals("a" to "b", state.commit())
        assertNull("a real move has nothing to fly home", state.returning)
        assertFalse(state.active)
    }

    @Test
    fun commit_ontoItsOwnCell_isACancellation_andFliesHome() {
        val state = state()
        state.pickUp("a", byPointer = true)

        assertNull("nothing moved, so there is no move to report", state.commit())
        assertEquals("a" to "a", state.returning)
    }

    @Test
    fun cancel_afterAiming_carriesBothEndsOfTheAbandonedSwap() {
        val state = state()
        state.pickUp("a", byPointer = false)
        state.moveTargetTo("b")

        state.cancel()

        // Both cells: the lifted tile flies back to "a", and the one it displaced back to "b".
        assertEquals("a" to "b", state.returning)
    }

    @Test
    fun settled_clearsTheReturn_andSoDoesTheNextLift() {
        val state = state()
        state.pickUp("a", byPointer = false)
        state.cancel()
        state.settled()
        assertNull(state.returning)

        state.pickUp("a", byPointer = false)
        state.cancel()
        // A new lift supersedes a tile still drifting home from the last one.
        state.pickUp("c", byPointer = false)
        assertNull(state.returning)
    }

    // ── Carrying a tile over a container that scrolls underneath it ──────────

    /**
     * **A carried tile is drawn from the FINGER, not from the slot it was lifted out of**
     * (Dylan, 2026-09-25).
     *
     * The stage edge-scrolls while a tile is held at the window's rim, which slides the lifted
     * slot out from under the carry. Positioning the tile as "that slot, plus how far the finger
     * has travelled" then drifts it away by however far the content moved — and once the slot
     * leaves the viewport its registered rect goes empty, stranding the tile somewhere off
     * screen: "it is no longer visible and able to be placed anywhere".
     *
     * [MoveModeState.carriedTopLeft] depends on nothing the scroll can move.
     */
    @Test
    fun aCarriedTile_staysUnderAStationaryFinger_whileTheContentScrolls() {
        val state = state()
        // A cell at x=100 in window space; the finger comes down 12px into it and stays there.
        state.registerBounds("a", Rect(100f, 0f, 160f, 40f))
        state.pickUp("a", byPointer = true, grab = Offset(12f, 6f))
        state.dragTo(offset = Offset.Zero, pointerWindowPos = Offset(112f, 6f))

        val before = state.carriedTopLeft(Offset.Zero)

        // The body scrolls 80px left under a finger that has not moved: the cell it was lifted
        // from goes with it, far enough to leave the viewport and register empty.
        state.registerBounds("a", Rect.Zero)

        assertEquals(before, state.carriedTopLeft(Offset.Zero))
        assertEquals(Offset(100f, 0f), before)
    }

    /** And the grab point is what keeps the tile held where the user actually grabbed it,
     *  rather than snapping its corner to the fingertip. */
    @Test
    fun theGrabPoint_survivesTheWholeGesture() {
        val state = state()
        state.pickUp("a", byPointer = true, grab = Offset(12f, 6f))
        state.dragTo(offset = Offset(40f, 0f), pointerWindowPos = Offset(300f, 90f))

        assertEquals(Offset(12f, 6f), state.grabPoint)
        assertEquals(Offset(288f, 84f), state.carriedTopLeft(Offset.Zero))
    }

    // ── Dead space between groups ────────────────────────────────────────────

    /**
     * **A carried tile is always heading somewhere — within reach** (Dylan, 2026-09-25).
     *
     * A tolerance sized to the gap between tiles covers the gutters inside one input group and
     * nothing else, so a tile carried across the empty space between two GROUPS resolved to no
     * cell at all and the target collapsed back to its origin: the tile "doesn't know where it
     * should go". The tolerance is now the radius within which the grid claims a tile.
     */
    @Test
    fun theVoidBetweenGroups_stillResolves() {
        val state = twoCellsApart()
        state.hitTolerancePx = 400f
        // Out in the open between them, but nearer the right-hand cell.
        state.dragTo(offset = Offset(300f, 0f), pointerWindowPos = Offset(300f, 20f))

        assertEquals("right", state.target)
        assertFalse(state.outOfRange)
    }

    /**
     * **Which cell is nearest cannot depend on where the tile was GRABBED** (Dylan, 2026-09-25).
     *
     * Measuring from the fingertip made it: the same tile in the same place resolved to different
     * neighbours depending on whether it had been picked up by its left edge or its right. The
     * tile is what the user aims; the finger is only how they hold it. Both lifts here put the
     * tile in exactly the same place, so both must answer the same.
     */
    @Test
    fun whereTheTileWasGrabbed_doesNotChangeWhatItIsOver() {
        fun targetAfterGrabbingAt(grabX: Float): String? {
            val state = twoCellsApart()
            state.hitTolerancePx = 400f
            state.pickUp("left", byPointer = true, grab = Offset(grabX, 20f), size = IntSize(60, 40))
            // The finger is placed so the TILE lands in the same spot either way — left edge at
            // 210, right edge at 270, so a 130px gap to "right" against 150px to "left". A
            // fingertip measured instead sits at 212 or 268 and flips the answer between them.
            state.dragTo(Offset.Zero, pointerWindowPos = Offset(210f + grabX, 20f))
            return state.target
        }

        assertEquals(targetAfterGrabbingAt(2f), targetAfterGrabbingAt(58f))
    }

    /** Beyond the tolerance the move reads as abandoned: the target sits on the origin so the
     *  release is a no-op, and [MoveModeState.outOfRange] says so loudly enough to draw. */
    @Test
    fun farFromEverything_theMoveIsAbandoned() {
        val state = twoCellsApart()
        state.hitTolerancePx = 80f
        state.dragTo(offset = Offset(300f, 0f), pointerWindowPos = Offset(300f, 20f))

        assertTrue(state.outOfRange)
        assertEquals("the release has to be a no-op", "left", state.target)
        assertNull(state.commit())
    }

    /** Coming back into reach clears it again — it is a live readout, not a latch. */
    @Test
    fun comingBackIntoReach_clearsTheAbandonedFlag() {
        val state = twoCellsApart()
        state.hitTolerancePx = 80f
        state.dragTo(offset = Offset(300f, 0f), pointerWindowPos = Offset(300f, 20f))
        assertTrue(state.outOfRange)

        state.dragTo(offset = Offset(400f, 0f), pointerWindowPos = Offset(410f, 20f))

        assertFalse(state.outOfRange)
        assertEquals("right", state.target)
    }

    /** A cell clipped entirely out of its viewport registers an empty rect. It is not a place a
     *  tile can be put, and it must not win on its collapsed geometry either. */
    @Test
    fun aCellScrolledOutOfView_isNotATarget() {
        val state = twoCellsApart()
        state.hitTolerancePx = 400f
        state.registerBounds("right", Rect.Zero)

        state.dragTo(offset = Offset(300f, 0f), pointerWindowPos = Offset(300f, 20f))

        assertEquals("left", state.target)
    }

    private fun twoCellsApart(): MoveModeState<String> {
        val state = state()
        state.registerBounds("left", Rect(0f, 0f, 60f, 40f))
        state.registerBounds("right", Rect(400f, 0f, 460f, 40f))
        state.pickUp("left", byPointer = true)
        return state
    }
}
