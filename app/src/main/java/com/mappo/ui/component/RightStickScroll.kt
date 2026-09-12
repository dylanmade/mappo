package com.mappo.ui.component

import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
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
 * Scroll [state] from the right stick's horizontal deflection while [enabled].
 *
 * A stand-in control, at Dylan's request (2026-09-11), for the basic view's horizontally
 * overflowing input rows: the stick scrolls whichever container holds controller focus, until
 * the view gets real scroll affordances. Deliberately NOT a general "stick scrolls the focused
 * scroller" behavior — a call site opts in.
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
fun rightStickHorizontalScroll(state: ScrollState, enabled: Boolean, invert: Boolean = false) {
    val stick = LocalRightStick.current
    val engaged by remember(stick) {
        derivedStateOf { abs(stick.value.x) > RightStickDeadzone }
    }
    val speedPx = with(LocalDensity.current) { RightStickScrollSpeed.toPx() }
    LaunchedEffect(state, enabled, engaged, invert, speedPx) {
        if (!enabled || !engaged) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val seconds = (now - previous) / 1_000_000_000f
            previous = now
            val x = stick.value.x
            // Re-read rather than trusting `engaged`: the effect is keyed on the threshold
            // crossing, and recomposition lands a frame later than the stick returning to rest.
            if (abs(x) <= RightStickDeadzone) break
            val ramp = (abs(x) - RightStickDeadzone) / (1f - RightStickDeadzone)
            val direction = if (invert) -sign(x) else sign(x)
            state.scrollBy(direction * ramp * speedPx * seconds)
        }
    }
}

/** Below this deflection the stick is at rest — wide enough to swallow a worn stick's drift,
 *  since engaging it scrolls content out from under the user. */
private const val RightStickDeadzone = 0.25f

/** Travel at full deflection, per second. */
private val RightStickScrollSpeed = 900.dp
