package com.mappo.ui.component

import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.sign

/**
 * The right stick's current deflection, published by the window root that sees the raw motion
 * events, for UI that wants analog scrolling.
 *
 * **Why a CompositionLocal rather than a Modifier:** a gamepad stick is not a pointer. Compose
 * routes pointer and rotary input through the modifier chain, but joystick axes arrive as
 * generic `MotionEvent`s on the window — `pointerInput` never sees them — so the only place
 * that can observe them is the activity, and the only sane way down to a composable deep in a
 * screen is a local. [com.mappo.MainActivity] provides it; anything outside that window reads
 * the resting default and the effects below simply never engage.
 *
 * Values are normalized deflection in [-1, 1], y DOWN-positive (Android's convention, not
 * flipped to match the gyro's — see `reference_thor_gyro_axis_convention` for the one place
 * that does flip).
 */
val LocalRightStick: androidx.compose.runtime.ProvidableCompositionLocal<State<Offset>> =
    staticCompositionLocalOf { mutableStateOf(Offset.Zero) }

/**
 * Read the right stick out of a generic [MotionEvent], or null when the event isn't one.
 *
 * **Axes Z / RZ, not RX / RY.** That's the Android-side convention Mappo's target handhelds
 * use — verified on the Thor, whose firmware emits the right stick on `ABS_Z`/`ABS_RZ` and its
 * triggers on `ABS_BRAKE`/`ABS_GAS` (`reference_thor_axis_convention`). A PC-style XInput pad
 * plugged into Android inverts that pair, so its triggers would read here as right-stick
 * deflection; per-device disambiguation is the same follow-up the raw `/dev/input` reader is
 * waiting on, and this is a prototyping affordance, so it isn't worth pre-solving.
 */
fun rightStickFrom(event: MotionEvent): Offset? {
    if (event.actionMasked != MotionEvent.ACTION_MOVE) return null
    if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return null
    return Offset(event.getAxisValue(MotionEvent.AXIS_Z), event.getAxisValue(MotionEvent.AXIS_RZ))
}

/**
 * **Which scroller the right stick drives.** One of Mappo's own universal controls (Dylan,
 * 2026-09-25: "right stick = scrolls any container with a scrollbar, both horizontal and
 * vertical"), so it is arbitrated here, at the core, rather than each screen wiring up its own
 * answer.
 *
 * Every scroller that CAN currently scroll registers a claim; the arbiter picks one per axis:
 *
 *  - **Focus decides when there is focus.** The claimant holding controller focus wins, and the
 *    DEEPEST such claimant wins outright — only ancestors of the focused node can claim, so
 *    they form a chain and the deepest is the innermost scroller around the cursor. That is the
 *    rule the remap editor used to implement by hand: the card holding the cursor answers the
 *    stick, not the one the camera is parked on.
 *  - **With no focus anywhere, the BIGGEST scrollable region answers.** This is the case the
 *    old focus-only gate got wrong: a tap focuses nothing, so a view the user has been
 *    touching has no cursor at all, and the stick was dead over a view that plainly had more
 *    to show. "The only scroller on screen" is not enough of a rule — a screen's content area
 *    almost always shares the window with a scrolling bar or tab strip — but the content area
 *    is the one the stick obviously means, and it is also much the larger of the two. Ties are
 *    left unresolved rather than picked at random: seven identical cards with no cursor in any
 *    of them have no answer, and moving one would be a guess.
 *
 * A single arbiter is shared by default rather than requiring a provider, so a scroller is
 * governed whether or not its window remembered to install one. [com.mappo.MainActivity]
 * provides its own for the app window all the same — overlay windows compose separately and
 * should not arbitrate against the activity's scrollers.
 */
@Stable
class StickScrollArbiter {
    private data class Claim(
        val orientation: Orientation,
        val depth: Int,
        val focused: Boolean,
        /** The container's area on screen, in square pixels — how "the biggest" is judged. */
        val area: Long,
    )

    private val claims = mutableStateMapOf<Any, Claim>()

    /** Announce that [token] can scroll along [orientation] right now. Idempotent — it writes
     *  only on an actual change, or registering would invalidate its own readers every pass. */
    fun register(token: Any, orientation: Orientation, depth: Int, focused: Boolean, area: Long) {
        val claim = Claim(orientation, depth, focused, area)
        if (claims[token] != claim) claims[token] = claim
    }

    fun release(token: Any) {
        if (claims.containsKey(token)) claims.remove(token)
    }

    /** Snapshot-backed, so a caller reading it in composition recomposes when the answer moves. */
    fun holds(token: Any): Boolean {
        val mine = claims[token] ?: return false
        val peers = claims.values.filter { it.orientation == mine.orientation }
        val focused = peers.filter { it.focused }
        if (focused.isNotEmpty()) {
            val deepest = focused.maxOf { it.depth }
            return mine.focused &&
                mine.depth == deepest &&
                focused.count { it.depth == deepest } == 1
        }
        val biggest = peers.maxOf { it.area }
        return mine.area == biggest && peers.count { it.area == biggest } == 1
    }
}

val LocalStickScrollArbiter: androidx.compose.runtime.ProvidableCompositionLocal<StickScrollArbiter> =
    staticCompositionLocalOf { StickScrollArbiter() }

/** How many stick-scrollable containers enclose this point in the tree — the depth a claim is
 *  ranked by. Each scroller provides its own depth plus one to its content. */
val LocalStickScrollDepth: androidx.compose.runtime.ProvidableCompositionLocal<Int> =
    staticCompositionLocalOf { 0 }

/**
 * Join the right stick's arbitration ([StickScrollArbiter]) and scroll [state] when it wins.
 *
 * Returns the modifier the CONTAINER must wear: it observes whether controller focus lies
 * anywhere inside — how the scroller knows the cursor is in it — and how big the container is,
 * which is how it is ranked when no cursor exists anywhere. Put it on a node that
 * encloses the content — a focus event modifier reports the aggregate state of the focus
 * targets beneath it, so an ancestor of the scrolled content sees the cursor arrive in any of
 * them.
 *
 * Wrap the content in `LocalStickScrollDepth provides depth + 1` (as
 * [com.mappo.ui.minput.MinputOverflowScroll] does) so a scroller nested inside this one
 * outranks it.
 */
@Composable
fun stickScrollable(
    state: ScrollState,
    orientation: Orientation,
    enabled: Boolean = true,
    invert: Boolean = false,
): Modifier {
    val arbiter = LocalStickScrollArbiter.current
    val depth = LocalStickScrollDepth.current
    val token = remember { Any() }
    var focusWithin by remember { mutableStateOf(false) }
    var area by remember { mutableLongStateOf(0L) }
    // A scroller with nothing to scroll is not a candidate — that is what keeps the "only one
    // on screen" fallback honest, and what stands the remap stage's body scroller down while
    // it is zoomed to exactly one viewport.
    val claiming = enabled && state.maxValue > 0 && state.maxValue != Int.MAX_VALUE
    SideEffect {
        if (claiming) {
            arbiter.register(token, orientation, depth, focusWithin, area)
        } else {
            arbiter.release(token)
        }
    }
    DisposableEffect(arbiter, token) { onDispose { arbiter.release(token) } }
    rightStickScroll(
        state = state,
        orientation = orientation,
        enabled = claiming && arbiter.holds(token),
        invert = invert,
    )
    return Modifier
        .onFocusChanged { focusWithin = it.hasFocus }
        .onSizeChanged { area = it.width.toLong() * it.height.toLong() }
}

/**
 * Scroll [state] from the right stick's deflection along [orientation] while [enabled].
 *
 * A stand-in control, at Dylan's request (2026-09-11), for containers that overflow: the stick
 * scrolls whichever one holds controller focus, until the view gets real scroll affordances.
 * It is NOT tied to a visible scrollbar — the cue and the control are independent, and the
 * stick kept working when the bars became fade + chevron cues (2026-09-19).
 *
 * Ramped from the deadzone edge so a light push creeps and a full push travels, and driven off
 * the frame clock because analog scrolling IS continuous motion — unlike Mappo's input
 * pipeline, which is event-driven end to end ([[feedback_latency_phrasing]]). The loop only
 * runs while the stick is actually deflected: [derivedStateOf] means the raw axis samples
 * (which land on every motion event) invalidate nothing until the deadzone is crossed.
 *
 * [invert] flips the direction for a mirrored container — one whose content extends to the
 * LEFT of its anchor — so the stick always reveals content in the direction it's pushed.
 */
@Composable
fun rightStickScroll(
    state: ScrollState,
    orientation: Orientation,
    enabled: Boolean,
    invert: Boolean = false,
) {
    val stick = LocalRightStick.current
    fun axis(offset: Offset) = if (orientation == Orientation.Horizontal) offset.x else offset.y
    val engaged by remember(stick, orientation) {
        derivedStateOf { abs(axis(stick.value)) > RightStickDeadzone }
    }
    val speedPx = with(LocalDensity.current) { RightStickScrollSpeed.toPx() }
    LaunchedEffect(state, orientation, enabled, engaged, invert, speedPx) {
        if (!enabled || !engaged) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val seconds = (now - previous) / 1_000_000_000f
            previous = now
            val value = axis(stick.value)
            // Re-read rather than trusting `engaged`: the effect is keyed on the threshold
            // crossing, and recomposition lands a frame later than the stick returning to rest.
            if (abs(value) <= RightStickDeadzone) break
            val ramp = (abs(value) - RightStickDeadzone) / (1f - RightStickDeadzone)
            val direction = if (invert) -sign(value) else sign(value)
            state.scrollBy(direction * ramp * speedPx * seconds)
        }
    }
}

/** Below this deflection the stick is at rest — shared with anything that asks "is the stick
 *  scrolling" (the remap stage's pan). */
internal const val RightStickDeadzone = 0.25f

/** Travel at full deflection, per second. */
private val RightStickScrollSpeed = 900.dp
