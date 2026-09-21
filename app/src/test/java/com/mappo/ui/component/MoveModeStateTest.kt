package com.mappo.ui.component

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
}
