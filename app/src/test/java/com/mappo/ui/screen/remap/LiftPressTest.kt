package com.mappo.ui.screen.remap

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.NativeKeyEvent
import com.mappo.data.settings.MoveCommitGesture
import com.mappo.ui.component.MoveModeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **How a controller move is confirmed**, as seen by [moveModeKeyEvent] — which owns the question
 * because a held button auto-repeats while focus moves, so no single tile sees both ends of the
 * gesture.
 *
 * There are three ways to pick a tile up: hold the activate button until it lifts and keep
 * holding; hold until it lifts and let go; or start steering before the hold has ripened, which
 * lifts the tile under you (coyote time). For a day each implied its own way out. Dylan settled
 * that 2026-09-26 — the way IN no longer decides, one setting does — so what these pin is the
 * setting, not the engager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LiftPressTest {

    private val from = cell(RemapSimpleGroup.FACE, 0, 0)
    private val to = cell(RemapSimpleGroup.FACE, 0, 1)

    private fun cell(group: RemapSimpleGroup, row: Int, slot: Int) =
        CellKey(group, group.rows[row], slot)

    /** A tiny harness standing in for the host: it holds the lift state and records commits. */
    private class Host(val gesture: MoveCommitGesture) {
        val moveState = MoveModeState<CellKey>()
        var liftPress = LiftPress.None
        var commits = 0
        var committed: Pair<CellKey, CellKey>? = null

        fun send(key: Key, down: Boolean): Boolean = moveModeKeyEvent(
            event = keyEvent(key, down),
            moveState = moveState,
            owns = { true },
            liftPress = liftPress,
            onLiftPress = { liftPress = it },
            gesture = gesture,
            onStep = { _, _ -> },
            onCommit = { pair -> commits++; committed = pair },
        )
    }

    /** A tile already lifted, with the lifting press still down — every engager's end state. */
    private fun lifted(gesture: MoveCommitGesture): Host {
        val host = Host(gesture)
        host.moveState.pickUp(from, byPointer = false)
        host.liftPress = LiftPress.Held
        return host
    }

    // ── Release to place (the default — it is what the touchscreen already does) ─────────────

    @Test
    fun onRelease_lettingGoOverANewSlot_places() {
        val host = lifted(MoveCommitGesture.ON_RELEASE)
        host.moveState.moveTargetTo(to)

        host.send(Key.ButtonA, down = false)

        assertEquals(1, host.commits)
        assertEquals(from to to, host.committed)
    }

    /**
     * **Released onto its own slot still places it** — back where it started, move over (Dylan,
     * 2026-09-26). An earlier version left this case live, reading it as picking a tile up to
     * look around with; but that is what the other setting is for, and a mode that quietly
     * behaves like the other one is the inconsistency the setting exists to remove.
     *
     * Put down where it already was, so there is nothing to relocate: the commit reports null and
     * the tile flies home rather than teleporting.
     */
    @Test
    fun onRelease_lettingGoOnItsOwnSlot_putsItBackAndEndsTheMove() {
        val host = lifted(MoveCommitGesture.ON_RELEASE)

        host.send(Key.ButtonA, down = false)

        assertEquals("the release must be acted on", 1, host.commits)
        assertNull("nothing moved, so there is nothing to report", host.committed)
        assertFalse("the move is over", host.moveState.active)
        assertEquals(from to from, host.moveState.returning)
        assertEquals(LiftPress.None, host.liftPress)
    }

    // ── Press again to place ─────────────────────────────────────────────────────────────────

    /** The lifting press's release never places anything here, however far the target has
     *  travelled — which is what lets the carry survive any amount of looking around. */
    @Test
    fun onPress_lettingGoOfTheLiftingPress_placesNothing() {
        val host = lifted(MoveCommitGesture.ON_PRESS)
        host.moveState.moveTargetTo(to)

        host.send(Key.ButtonA, down = false)

        assertEquals(0, host.commits)
        assertTrue(host.moveState.active)
    }

    @Test
    fun onPress_theNextPress_places() {
        val host = lifted(MoveCommitGesture.ON_PRESS)
        host.moveState.moveTargetTo(to)
        host.send(Key.ButtonA, down = false)

        host.send(Key.ButtonA, down = false)

        assertEquals(1, host.commits)
        assertEquals(from to to, host.committed)
    }

    // ── Shared ───────────────────────────────────────────────────────────────────────────────

    /** B / Escape calls the whole thing off and clears the lift state with it, either way. */
    @Test
    fun cancelling_clearsTheLiftState() {
        for (gesture in MoveCommitGesture.entries) {
            val host = lifted(gesture)
            host.moveState.moveTargetTo(to)

            assertTrue(host.send(Key.ButtonB, down = true))

            assertFalse("$gesture", host.moveState.active)
            assertEquals("$gesture", LiftPress.None, host.liftPress)
            assertEquals("$gesture", 0, host.commits)
            assertEquals(
                "the tile flies home rather than teleporting ($gesture)",
                from to to,
                host.moveState.returning,
            )
        }
    }

    /**
     * Auto-repeat of a still-held activate button must do nothing at all. Under "press again" in
     * particular, a repeat that counted as a press would place the tile the instant a coyote lift
     * happened — the button is already down when the lift occurs.
     */
    @Test
    fun repeatsOfTheHeldButton_doNothing() {
        for (gesture in MoveCommitGesture.entries) {
            val host = lifted(gesture)
            host.moveState.moveTargetTo(to)

            repeat(5) { assertTrue("must be consumed ($gesture)", host.send(Key.ButtonA, down = true)) }

            assertEquals("$gesture", 0, host.commits)
            assertTrue("$gesture", host.moveState.active)
        }
    }

    /** Nothing lifted: the handler is not in the conversation and everything passes through. */
    @Test
    fun withNoMoveInFlight_nothingIsConsumed() {
        val host = Host(MoveCommitGesture.ON_RELEASE)
        assertFalse(host.send(Key.ButtonA, down = false))
        assertFalse(host.send(Key.DirectionRight, down = true))
        assertNull(host.committed)
    }
}

private fun keyEvent(key: Key, down: Boolean): KeyEvent = KeyEvent(
    NativeKeyEvent(
        /* downTime = */ 0L,
        /* eventTime = */ 0L,
        if (down) android.view.KeyEvent.ACTION_DOWN else android.view.KeyEvent.ACTION_UP,
        // Key packs its Android key code into the high word of its value.
        (key.keyCode shr 32).toInt(),
        /* repeat = */ 0,
    ),
)
