package com.mappo.ui.minput

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.AnimationVector3D
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * **The ONE open / close motion of every minput menu** (2026-10-15 audit) — the popup menus
 * ([MinputDropdownMenu], [MinputActionMenu], and so [MinputPillDropdown]), the arbitrary-content
 * [MinputDropdownPanel], and the in-composition [MinputMenuPanel] all read it, so no menu can
 * open differently from its neighbour.
 *
 * OPEN is M3's classic menu entrance, the one the hierarchy bar's crumb panels always had: a scale
 * up from [ClosedScale] over [InMillis] (linear-out-slow-in) while fading in over [FadeInMillis].
 * CLOSE mirrors it (2026-10-15): back down to [ClosedScale], toward the control that summoned it,
 * while fading out, both over [OutMillis] with the accelerating fast-out-linear-in — M3's own
 * close, a 75ms pure fade, read as the menu blipping out of existence. Never a spring.
 *
 * **Why the menus need their own** — the audit's finding: M3 1.5's `DropdownMenu` takes its motion
 * from the theme's [MotionScheme] (fast spatial for the scale, fast effects for the fade), and
 * Mappo's theme is EXPRESSIVE, whose fast spatial spring overshoots — the pin menu's bounce-back,
 * next to the crumb panels' clean tween. The theme stays expressive (Dylan's call, 2026-06-19);
 * menus override it locally ([MinputMenuMotionScope]), as the motion-scheme doctrine says to.
 */
internal object MinputMenuMotion {
    const val InMillis = 120
    const val OutMillis = 75
    const val FadeInMillis = 30
    const val ClosedScale = 0.8f

    fun <T> scaleEnter(): FiniteAnimationSpec<T> = tween(InMillis, easing = LinearOutSlowInEasing)

    fun <T> scaleExit(): FiniteAnimationSpec<T> = tween(OutMillis, easing = FastOutLinearInEasing)

    fun <T> fadeEnter(): FiniteAnimationSpec<T> = tween(FadeInMillis)

    fun <T> fadeExit(): FiniteAnimationSpec<T> = tween(OutMillis, easing = FastOutLinearInEasing)

    /** The scale, either way: [scaleEnter] while it grows, [scaleExit] while it shrinks. */
    fun <T> scale(): FiniteAnimationSpec<T> = DirectionalSpec(scaleEnter(), scaleExit())

    /** The fade, either way: [fadeEnter] while it rises, [fadeExit] while it falls. */
    fun <T> fade(): FiniteAnimationSpec<T> = DirectionalSpec(fadeEnter(), fadeExit())
}

/**
 * Hosts an M3 menu under [MinputMenuMotion]: the ambient theme with its fast spatial / fast
 * effects specs (the two `DropdownMenu` reads) swapped for the menu motion. `MaterialTheme`
 * re-provides the ripple and resets the text style to bodyLarge for its subtree, so both are
 * handed back exactly as the caller had them — only the motion changes.
 */
@Composable
internal fun MinputMenuMotionScope(content: @Composable () -> Unit) {
    val base = MaterialTheme.motionScheme
    val scheme = remember(base) { MinputMenuMotionScheme(base) }
    val textStyle = LocalTextStyle.current
    val indication = LocalIndication.current
    MaterialTheme(motionScheme = scheme) {
        CompositionLocalProvider(
            LocalTextStyle provides textStyle,
            LocalIndication provides indication,
            content = content,
        )
    }
}

private class MinputMenuMotionScheme(base: MotionScheme) : MotionScheme by base {
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = MinputMenuMotion.scale()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = MinputMenuMotion.fade()
}

/**
 * One spec per direction, for an animation API that takes a single spec for both — `DropdownMenu`
 * animates open and close with the same one. Picks by whether the value is RISING (open: the
 * menu's scale and alpha both grow) or falling, from the first component of the vector.
 */
private class DirectionalSpec<T>(
    private val rising: FiniteAnimationSpec<T>,
    private val falling: FiniteAnimationSpec<T>,
) : FiniteAnimationSpec<T> {
    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<T, V>): VectorizedFiniteAnimationSpec<V> {
        val up = rising.vectorize(converter)
        val down = falling.vectorize(converter)
        return object : VectorizedFiniteAnimationSpec<V> {
            fun pick(initial: V, target: V) = if (first(target) >= first(initial)) up else down

            override fun getValueFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V =
                pick(initialValue, targetValue).getValueFromNanos(playTimeNanos, initialValue, targetValue, initialVelocity)

            override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V =
                pick(initialValue, targetValue).getVelocityFromNanos(playTimeNanos, initialValue, targetValue, initialVelocity)

            override fun getDurationNanos(initialValue: V, targetValue: V, initialVelocity: V): Long =
                pick(initialValue, targetValue).getDurationNanos(initialValue, targetValue, initialVelocity)
        }
    }

    /** The vector's first component (the indexer is internal to compose-animation). */
    private fun first(v: AnimationVector): Float = when (v) {
        is AnimationVector1D -> v.value
        is AnimationVector2D -> v.v1
        is AnimationVector3D -> v.v1
        is AnimationVector4D -> v.v1
        else -> 0f
    }

    override fun equals(other: Any?): Boolean =
        other is DirectionalSpec<*> && other.rising == rising && other.falling == falling

    override fun hashCode(): Int = 31 * rising.hashCode() + falling.hashCode()
}
