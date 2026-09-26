package com.mappo.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **Why a carried tile's window position can't be rebuilt from its cell's registered rect.**
 *
 * `moveModeCell` registers `boundsInWindow()`, which is CLIPPED to the scroll container. The
 * drag gesture used to reconstruct the finger's window position as "that rect's top-left, plus
 * the pointer's node-local position" — and the moment a cell starts sliding past the viewport's
 * edge, the clipped top-left stops moving with it while the local position keeps growing. The
 * reconstruction then drifts by exactly the amount clipped away, which is worst at the edge,
 * which is exactly where the carry edge-scroll runs (Dylan, 2026-09-25: the tile comes off the
 * finger "at the exact moment the scroll speed increases").
 *
 * This pins the divergence itself, so nobody "simplifies" the gesture back onto the rect.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1280dp-h800dp")
class MoveModeCellPositionTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun aHalfScrolledCellsClippedRect_liesAboutWhereTheCellIs() {
        var clipped: Rect? = null
        var live: Offset? = null
        val scroll = ScrollState(0)
        composeRule.setContent {
            Row(Modifier.width(200.dp).horizontalScroll(scroll)) {
                Spacer(Modifier.width(300.dp).height(50.dp))
                Box(
                    Modifier.width(100.dp).height(50.dp).onGloballyPositioned {
                        clipped = it.boundsInWindow()
                        live = it.localToWindow(Offset.Zero)
                    },
                )
                // Content past the cell, so the scroll can carry it off the LEADING edge
                // instead of running out of range with the cell still fully visible.
                Spacer(Modifier.width(300.dp).height(50.dp))
            }
        }
        composeRule.waitForIdle()

        // Scroll until the cell straddles the viewport's leading edge: it starts 300 into the
        // content, so 350 puts its left 50px PAST the edge, with 50px of it still showing.
        composeRule.runOnIdle { scroll.dispatchRawDelta(350f) }
        composeRule.waitForIdle()

        val clippedLeft = clipped!!.left
        val trueLeft = live!!.x
        assertEquals(
            "the clipped rect should pin to the viewport edge while the cell keeps going",
            50f,
            clippedLeft - trueLeft,
            0.5f,
        )

        // What that costs the reconstruction: a finger 60px into the cell is really at
        // trueLeft + 60, but rebuilding it from the registered rect puts it 50px to the right.
        val fingerInCell = Offset(60f, 10f)
        val reconstructed = Offset(clippedLeft, clipped!!.top) + fingerInCell
        val actual = live!! + fingerInCell
        assertEquals(50f, reconstructed.x - actual.x, 0.5f)
    }

    /**
     * And the gesture itself: with the cell half off the leading edge, the finger's reported
     * WINDOW position must be where the finger actually is. Rebuilt from the registered rect it
     * came out 50px to the right — which is what put the carried tile out from under the touch.
     */
    @Test
    fun aDragOnAHalfScrolledCell_reportsTheRealWindowPosition() {
        val state = MoveModeState<String>()
        val scroll = ScrollState(0)
        var trueLeft = 0f
        composeRule.setContent {
            Row(Modifier.width(200.dp).horizontalScroll(scroll)) {
                Spacer(Modifier.width(300.dp).height(50.dp))
                Box(
                    Modifier
                        .width(100.dp)
                        .height(50.dp)
                        .testTag("cell")
                        .onGloballyPositioned { trueLeft = it.localToWindow(Offset.Zero).x }
                        .moveModeCell(state, "cell")
                        .moveModeLongPressSource(state, "cell") { },
                )
                Spacer(Modifier.width(300.dp).height(50.dp))
            }
        }
        composeRule.waitForIdle()
        // A quarter of the cell off the leading edge — enough to clip, little enough that the
        // press lands on it whichever bounds the test harness measures the offset against.
        composeRule.runOnIdle { scroll.dispatchRawDelta(325f) }
        composeRule.waitForIdle()

        // Local x 75 on a cell whose true left is -50 → the finger is at window x 25, inside
        // the 50px of it still showing.
        composeRule.onNodeWithTag("cell").performTouchInput {
            down(Offset(40f, 25f))
            // Hold past the long-press timeout to lift, then travel past the coarser post-lift
            // slop so the move actually starts.
            advanceEventTime(1_000L)
            moveTo(Offset(140f, 25f))
        }
        composeRule.waitForIdle()

        assertEquals("a lift should have happened", "cell", state.origin)
        // Asserted as a RELATIVE claim so it doesn't depend on which bounds the harness
        // measured the injected offsets against: the tile is held at the point it was grabbed,
        // so after travelling 100px it sits 100px right of where the cell really is. Rebuilt
        // from the clipped rect this came out 25px further right — the cell's true left is
        // off-screen at -25 while its clipped left is pinned to 0.
        assertEquals(trueLeft + 100f, state.carriedTopLeft(Offset.Zero).x, 1f)
    }
}
