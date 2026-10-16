package com.mappo.ui.minput

import org.junit.Assert.assertEquals
import org.junit.Test

/** The shared cascade placement rule (migrated from the virtual editor's fly-outs, 2026-10-16). */
class MinputCascadeTest {
    // A parent menu at 400..600 in a 0..1000 frame.
    private fun side(width: Float, inherited: MinputCascadeSide? = null, parentLeft: Float = 400f, parentRight: Float = 600f) =
        minputCascadeSide(parentLeft, parentRight, width, 0f, 1000f, inherited)

    @Test fun firstLevel_opensRight_whenItFits() = assertEquals(MinputCascadeSide.Right, side(300f))

    @Test fun firstLevel_opensLeft_whenOnlyLeftFits() =
        assertEquals(MinputCascadeSide.Left, side(300f, parentLeft = 700f, parentRight = 900f))

    @Test fun firstLevel_fallsBackRight_whenNeitherFits() = assertEquals(MinputCascadeSide.Right, side(500f))

    @Test fun deeperLevel_keepsInheritedLeft_whenItFits() =
        assertEquals(MinputCascadeSide.Left, side(300f, inherited = MinputCascadeSide.Left))

    @Test fun deeperLevel_flipsOnlyWhenInheritedSideDoesNotFit() =
        assertEquals(MinputCascadeSide.Right, side(300f, inherited = MinputCascadeSide.Left, parentLeft = 100f, parentRight = 300f))

    @Test fun left_isFlushAgainstTheChosenEdge() {
        assertEquals(600f, minputCascadeLeft(MinputCascadeSide.Right, 400f, 600f, 300f))
        assertEquals(100f, minputCascadeLeft(MinputCascadeSide.Left, 400f, 600f, 300f))
    }
}
