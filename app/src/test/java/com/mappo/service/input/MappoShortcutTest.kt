package com.mappo.service.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Mappo's own universal shortcut, Select + A** (Dylan, 2026-09-25: one of the two hotkeys that
 * must work everywhere).
 *
 * The chord used to be detected inline in `InputAccessibilityService.onKeyEvent`, which cannot
 * see it whenever the Shizuku UserService holds `EVIOCGRAB`: the physical pad is gone from the
 * OS, and that method's own feedback-loop guard deliberately skips the virtual gamepad's echo of
 * it. A Mappo-managed layout almost always configures a gamepad output, which is itself a reason
 * to grab — so the shortcut was unreachable in practice ("does not seem to work anywhere").
 *
 * Living on the dispatcher, the chord can be fed by the accessibility filter OR by the Shizuku
 * raw reader, whichever can currently see the buttons.
 */
class MappoShortcutTest {

    private fun dispatcherWithCounter(): Pair<InputDispatcher, () -> Int> {
        val dispatcher = InputDispatcher()
        var fired = 0
        dispatcher.setShortcutListener { fired++ }
        return dispatcher to { fired }
    }

    @Test
    fun selectThenA_firesOnce() {
        val (dispatcher, fired) = dispatcherWithCounter()

        assertFalse("Select alone is not the shortcut", dispatcher.noteShortcutButton(ShortcutButton.SELECT, true))
        assertTrue(dispatcher.noteShortcutButton(ShortcutButton.A, true))

        assertEquals(1, fired())
    }

    /** A repeat is the same press still held down, not a second invocation. */
    @Test
    fun holdingA_doesNotFireAgain() {
        val (dispatcher, fired) = dispatcherWithCounter()
        dispatcher.noteShortcutButton(ShortcutButton.SELECT, true)
        dispatcher.noteShortcutButton(ShortcutButton.A, true)

        assertFalse(dispatcher.noteShortcutButton(ShortcutButton.A, true, repeat = true))
        assertFalse("a release is not an invocation", dispatcher.noteShortcutButton(ShortcutButton.A, false))

        assertEquals(1, fired())
    }

    @Test
    fun aWithoutSelect_doesNothing() {
        val (dispatcher, fired) = dispatcherWithCounter()

        assertFalse(dispatcher.noteShortcutButton(ShortcutButton.A, true))
        assertEquals(0, fired())
    }

    /** Select released, then A: the chord is over. This is the case the old inline version had
     *  to be careful about — a Select-UP swallowed by another branch left it stuck armed. */
    @Test
    fun aAfterSelectIsReleased_doesNothing() {
        val (dispatcher, fired) = dispatcherWithCounter()
        dispatcher.noteShortcutButton(ShortcutButton.SELECT, true)
        dispatcher.noteShortcutButton(ShortcutButton.SELECT, false)

        assertFalse(dispatcher.noteShortcutButton(ShortcutButton.A, true))
        assertEquals(0, fired())
    }

    /**
     * The state is shared, so a Select held while the grab flips — handing the buttons from the
     * accessibility filter to the raw reader or back — still completes the chord on the other
     * path. Either path may report either half.
     */
    @Test
    fun theChordSurvivesChangingHands() {
        val (dispatcher, fired) = dispatcherWithCounter()

        // Select seen by one path...
        dispatcher.noteShortcutButton(ShortcutButton.SELECT, true)
        // ...A by the other.
        assertTrue(dispatcher.noteShortcutButton(ShortcutButton.A, true))
        assertEquals(1, fired())
    }

    /**
     * **Firing disarms the chord** (Dylan, 2026-09-27): "once the mappo virtual gamepad connects,
     * the Select + A shortcut starts getting activated on every A press after the hotkey is
     * activated once".
     *
     * The Select RELEASE cannot be relied on. Performing the chord brings Mappo to the front,
     * which releases the EVIOCGRAB — and the press was seen by the raw reader while the pad was
     * grabbed, so the OS InputReader never saw it go down and drops the unmatched key-up when it
     * comes, while the raw reader has stood down by then. Nothing cleared the flag, so every
     * subsequent A completed the chord again. One activation per Select press.
     */
    @Test
    fun firing_disarmsTheChord_soTheNextOneWantsAFreshSelect() {
        val (dispatcher, fired) = dispatcherWithCounter()
        dispatcher.noteShortcutButton(ShortcutButton.SELECT, true)
        assertTrue(dispatcher.noteShortcutButton(ShortcutButton.A, true))

        // The same Select still physically held — or its release lost entirely, which is the case
        // that made this bite. Either way, A on its own is nobody's shortcut.
        dispatcher.noteShortcutButton(ShortcutButton.A, false)
        assertFalse(dispatcher.noteShortcutButton(ShortcutButton.A, true))
        assertFalse(dispatcher.noteShortcutButton(ShortcutButton.A, true))
        assertEquals(1, fired())

        // A fresh Select arms it again.
        dispatcher.noteShortcutButton(ShortcutButton.SELECT, false)
        dispatcher.noteShortcutButton(ShortcutButton.SELECT, true)
        assertTrue(dispatcher.noteShortcutButton(ShortcutButton.A, true))
        assertEquals(2, fired())
    }

    /** The grab changing hands loses an edge in either direction, so it invalidates whatever was
     *  held rather than trusting a release to turn up. */
    @Test
    fun aGrabTransition_disarmsTheChord() {
        val (dispatcher, fired) = dispatcherWithCounter()
        dispatcher.noteShortcutButton(ShortcutButton.SELECT, true)

        dispatcher.clearShortcutChord()

        assertFalse(dispatcher.noteShortcutButton(ShortcutButton.A, true))
        assertEquals(0, fired())
    }

    /** With nothing installed to perform it there is no shortcut, and nothing is consumed —
     *  the press has to stay available to whatever else would have had it. */
    @Test
    fun withNoListener_nothingIsConsumed() {
        val dispatcher = InputDispatcher()
        dispatcher.noteShortcutButton(ShortcutButton.SELECT, true)

        assertFalse(dispatcher.noteShortcutButton(ShortcutButton.A, true))
    }
}
