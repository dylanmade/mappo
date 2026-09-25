package com.mappo.ui.component

import androidx.compose.foundation.gestures.Orientation
import org.junit.Test

/**
 * **Which scroller the right stick means** — Mappo's one universal control (Dylan, 2026-09-25:
 * "right stick = scrolls any container with a scrollbar, both horizontal and vertical").
 *
 * The rule is a plain class so it can be pinned here rather than through a composition: the
 * scroll loop never goes idle while the stick is held, which makes the Compose test harness a
 * poor place to ask what the arbiter decided.
 *
 * The shape every case is built on is the one every real screen has — a content area sharing
 * the window with a strip of chrome that also scrolls.
 */
class StickScrollArbiterTest {

    private val content = Any()
    private val chrome = Any()
    private val inner = Any()

    private fun StickScrollArbiter.content(focused: Boolean, depth: Int = 0, area: Long = 90_000) =
        register(content, Orientation.Horizontal, depth, focused, area)

    private fun StickScrollArbiter.chrome(focused: Boolean, depth: Int = 0, area: Long = 12_000) =
        register(chrome, Orientation.Horizontal, depth, focused, area)

    /**
     * The case the old gate got wrong. It asked only "does controller focus sit inside me", and
     * a tap focuses nothing — so after any spell on the touchscreen the stick was dead over a
     * view that plainly had more to show (Dylan: the view mode "currently doesn't" scroll).
     *
     * "The only scroller on screen" was the first rule tried in its place and it answers almost
     * never, because of the chrome. The bigger region is the one the stick obviously means.
     */
    @Test
    fun withNoCursor_theBiggerRegionTakesTheStick() {
        val arbiter = StickScrollArbiter()
        arbiter.content(focused = false)
        arbiter.chrome(focused = false)

        assert(arbiter.holds(content)) { "the content area did not take the stick" }
        assert(!arbiter.holds(chrome)) { "the strip of chrome answered the stick as well" }
    }

    /** With a cursor, it decides — even sitting in the smaller region. */
    @Test
    fun theCursorBeatsSize() {
        val arbiter = StickScrollArbiter()
        arbiter.content(focused = false)
        arbiter.chrome(focused = true)

        assert(arbiter.holds(chrome)) { "the region holding the cursor did not take the stick" }
        assert(!arbiter.holds(content)) { "the bigger region took the stick from the cursor" }
    }

    /**
     * Nested scrollers are both ancestors of the cursor, so both hold focus; the INNER one is
     * what the stick means. This is the rule the remap editor used to implement by hand — the
     * card holding the cursor answers, not the camera's group.
     */
    @Test
    fun nested_theInnerScrollerWins() {
        val arbiter = StickScrollArbiter()
        arbiter.content(focused = true, depth = 0)
        arbiter.register(inner, Orientation.Horizontal, 1, focused = true, area = 5_000)

        assert(arbiter.holds(inner)) { "the inner scroller did not take the stick" }
        assert(!arbiter.holds(content)) { "the outer scroller answered over the inner one" }
    }

    /**
     * **A tie is left unresolved rather than broken at random.** Seven identical cards with no
     * cursor in any of them have no answer, and moving one would be a guess — the stick doing
     * nothing is the honest outcome.
     */
    @Test
    fun equallyBigWithNoCursor_nobodyTakesIt() {
        val arbiter = StickScrollArbiter()
        arbiter.content(focused = false, area = 90_000)
        arbiter.chrome(focused = false, area = 90_000)

        assert(!arbiter.holds(content)) { "one of two identical regions took the stick" }
        assert(!arbiter.holds(chrome)) { "one of two identical regions took the stick" }
    }

    /** The two axes are arbitrated separately: the stick has two of them, and a horizontal
     *  content area and a vertical list can answer at the same time without competing. */
    @Test
    fun theAxesAreDecidedSeparately() {
        val arbiter = StickScrollArbiter()
        arbiter.content(focused = false, area = 90_000)
        arbiter.register(chrome, Orientation.Vertical, 0, focused = false, area = 12_000)

        assert(arbiter.holds(content)) { "the horizontal region lost to a vertical one" }
        assert(arbiter.holds(chrome)) { "the vertical region lost to a horizontal one" }
    }

    /** A scroller that stops being able to scroll — or leaves the composition — stops counting,
     *  or it would go on outranking the one the user can actually see. */
    @Test
    fun releasing_handsTheStickOn() {
        val arbiter = StickScrollArbiter()
        arbiter.content(focused = false)
        arbiter.chrome(focused = false)
        assert(!arbiter.holds(chrome))

        arbiter.release(content)

        assert(arbiter.holds(chrome)) { "the chrome never inherited the stick" }
        assert(!arbiter.holds(content)) { "a released scroller still answered" }
    }
}
