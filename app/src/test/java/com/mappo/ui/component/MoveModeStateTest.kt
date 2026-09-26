package com.mappo.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
}
