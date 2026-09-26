package com.mappo.ui.screen.remap

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.NativeKeyEvent
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
 * **The three ways a controller move can be driven** (Dylan, 2026-09-26), as seen by
 * [moveModeKeyEvent] — which owns them because a held button auto-repeats while focus moves, so
 * no single tile sees both ends of the gesture.
 *
 *  1. Hold through the lift, keep holding, navigate, release → commits.
 *  2. Hold through the lift, release without moving, navigate, press → commits.
 *  3. Hold, but start navigating BEFORE the hold ripens → the tile lifts under you, and the
 *     release of that now-spent press must NOT commit; a later press does.
 *
 * The third is the one that needed a third state. It looks exactly like (1) from the handler's
 * seat — a lifting press still down, and a target that has moved — so a boolean "is it held"
 * could not tell them apart, and the coyote lift committed the moment the user let go of a
 * button they had already finished with.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LiftPressTest {

    private val from = cell(RemapSimpleGroup.FACE, 0, 0)
    private val to = cell(RemapSimpleGroup.FACE, 0, 1)

    private fun cell(group: RemapSimpleGroup, row: Int, slot: Int) =
        CellKey(group, group.rows[row], slot)

    /** A tiny harness standing in for the host: it holds the lift state and records commits. */
    private class Host {
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
            onStep = { _, _ -> },
            onCommit = { pair -> commits++; committed = pair },
        )
    }

    @Test
    fun heldThroughTheLift_andMoved_commitsOnRelease() {
        val host = Host()
        host.moveState.pickUp(from, byPointer = false)
        host.liftPress = LiftPress.CommitsOnRelease
        host.moveState.moveTargetTo(to)

        host.send(Key.ButtonA, down = false)

        assertEquals(1, host.commits)
        assertEquals(from to to, host.committed)
    }

    /** Released without having moved: the user picked a tile up to look around with. The move
     *  stays live, and the NEXT press is the confirm. */
    @Test
    fun releasedWithoutMoving_staysLive_untilTheNextPress() {
        val host = Host()
        host.moveState.pickUp(from, byPointer = false)
        host.liftPress = LiftPress.CommitsOnRelease

        host.send(Key.ButtonA, down = false)
        assertEquals(0, host.commits)
        assertTrue("the tile must still be in hand", host.moveState.active)
        assertEquals(LiftPress.None, host.liftPress)

        host.moveState.moveTargetTo(to)
        host.send(Key.ButtonA, down = false)

        assertEquals(1, host.commits)
        assertEquals(from to to, host.committed)
    }

    /**
     * The coyote lift. Its press is spent — the user was already steering when the tile came up
     * under them — so letting go of it means nothing, however far the target has travelled.
     */
    @Test
    fun aSpentPress_doesNotCommitWhenItIsReleased() {
        val host = Host()
        host.moveState.pickUp(from, byPointer = false)
        host.liftPress = LiftPress.Spent
        host.moveState.moveTargetTo(to)

        host.send(Key.ButtonA, down = false)

        assertEquals("releasing a spent press must not confirm", 0, host.commits)
        assertTrue(host.moveState.active)
        // ...and from there it is the press-again mode.
        host.send(Key.ButtonA, down = false)
        assertEquals(1, host.commits)
        assertEquals(from to to, host.committed)
    }

    /** B / Escape calls the whole thing off and clears the lift state with it, whichever kind of
     *  press is outstanding. */
    @Test
    fun cancelling_clearsTheLiftState() {
        val host = Host()
        host.moveState.pickUp(from, byPointer = false)
        host.liftPress = LiftPress.Spent
        host.moveState.moveTargetTo(to)

        assertTrue(host.send(Key.ButtonB, down = true))

        assertFalse(host.moveState.active)
        assertEquals(LiftPress.None, host.liftPress)
        assertEquals(0, host.commits)
        assertEquals("the tile flies home rather than teleporting", from to to, host.moveState.returning)
    }

    /** Auto-repeat of a still-held activate button must do nothing at all: only the release (or a
     *  fresh press's release) decides. Otherwise a coyote lift would confirm itself instantly. */
    @Test
    fun repeatsOfTheHeldButton_doNothing() {
        val host = Host()
        host.moveState.pickUp(from, byPointer = false)
        host.liftPress = LiftPress.Spent
        host.moveState.moveTargetTo(to)

        repeat(5) { assertTrue("must be consumed", host.send(Key.ButtonA, down = true)) }

        assertEquals(0, host.commits)
        assertTrue(host.moveState.active)
    }

    /** Nothing lifted: the handler is not in the conversation and everything passes through. */
    @Test
    fun withNoMoveInFlight_nothingIsConsumed() {
        val host = Host()
        assertFalse(host.send(Key.ButtonA, down = false))
        assertFalse(host.send(Key.DirectionRight, down = true))
        assertNull(host.committed)
    }
}

/** The five-argument constructor, not the two-argument one: under Robolectric the short form
 *  leaves both the action and the key code at zero. */
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
