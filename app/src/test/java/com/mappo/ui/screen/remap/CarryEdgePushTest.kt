package com.mappo.ui.screen.remap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The edge-scroll ramp for a tile carried to the window's rim** (see [carryEdgePush]).
 *
 * The shape is the whole point of it: the first cut reached full speed only at the very rim,
 * which with a tile held under the finger is somewhere between awkward and impossible to sit on
 * — Dylan, 2026-09-25: "it feels a little difficult to achieve full drag scroll speed where the
 * bands/ramp are currently". A PLATEAU is what makes topping out reachable.
 */
class CarryEdgePushTest {

    private val viewport = 1000f
    private val band = 120f
    private val plateau = 44f

    private fun push(x: Float) = carryEdgePush(x, viewport, band, plateau)

    @Test
    fun theMiddleOfTheWindow_doesNotScroll() {
        assertEquals(0f, push(500f), 0f)
        // Just inside the band's lip is still nothing, so entering it is a smooth start rather
        // than a step.
        assertEquals(0f, push(band), 0f)
    }

    @Test
    fun theSignSaysWhichWay() {
        assertTrue("the leading edge pulls back toward the start", push(10f) < 0f)
        assertTrue("the trailing edge pulls forward", push(viewport - 10f) > 0f)
    }

    /** The reachability claim: full speed arrives at the plateau's edge, not at the glass. */
    @Test
    fun fullSpeed_startsAtThePlateau_notAtTheRim() {
        assertEquals(-1f, push(plateau), 0.001f)
        assertEquals(-1f, push(plateau / 2f), 0.001f)
        assertEquals(-1f, push(0f), 0.001f)
        assertEquals(1f, push(viewport - plateau), 0.001f)
    }

    /** A finger that has run off the edge of the glass keeps the pull, rather than wrapping or
     *  dropping to nothing. */
    @Test
    fun pastTheEdge_staysAtFullSpeed() {
        assertEquals(-1f, push(-60f), 0.001f)
        assertEquals(1f, push(viewport + 60f), 0.001f)
    }

    /** Between the lip and the plateau it ramps, monotonically. */
    @Test
    fun theRampIsMonotonic() {
        var previous = 0f
        var x = band
        while (x >= plateau) {
            val here = -push(x)
            assertTrue("pull should not decrease moving toward the edge (at x=$x)", here >= previous)
            previous = here
            x -= 4f
        }
        assertEquals(1f, previous, 0.001f)
    }

    /** A degenerate configuration — plateau as wide as the band — must not divide by zero. */
    @Test
    fun aPlateauAsWideAsTheBand_isStillFinite() {
        val value = carryEdgePush(x = 10f, viewportW = viewport, band = 120f, plateau = 120f)
        assertEquals(-1f, value, 0.001f)
    }
}
