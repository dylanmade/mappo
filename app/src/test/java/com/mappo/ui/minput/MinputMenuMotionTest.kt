package com.mappo.ui.minput

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **Every minput menu opens and closes the same way** (2026-10-15 audit) — the popup menus run on
 * M3's `DropdownMenu`, which animates with the theme's motion scheme; Mappo's theme is
 * EXPRESSIVE, whose fast spatial spring overshoots (the pin menu's bounce-back). The menus swap in
 * [MinputMenuMotion] instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MinputMenuMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun durationMillis(spec: FiniteAnimationSpec<Float>, from: Float, to: Float): Long =
        spec.vectorize(Float.VectorConverter)
            .getDurationNanos(AnimationVector1D(from), AnimationVector1D(to), AnimationVector1D(0f)) / 1_000_000

    @Test
    fun opening_isTheClassicTween_andClosing_mirrorsIt() {
        // Open: the scale grows over InMillis, the fade over FadeInMillis.
        assert(durationMillis(MinputMenuMotion.scale(), MinputMenuMotion.ClosedScale, 1f) == MinputMenuMotion.InMillis.toLong())
        assert(durationMillis(MinputMenuMotion.fade(), 0f, 1f) == MinputMenuMotion.FadeInMillis.toLong())
        // Close: scale and fade run together over OutMillis — the menu visibly shrinks away.
        assert(durationMillis(MinputMenuMotion.fade(), 1f, 0f) == MinputMenuMotion.OutMillis.toLong())
        assert(durationMillis(MinputMenuMotion.scale(), 1f, MinputMenuMotion.ClosedScale) == MinputMenuMotion.OutMillis.toLong())
    }

    @Test
    fun theOpeningScale_neverOvershoots() {
        val spec = MinputMenuMotion.scale<Float>().vectorize(Float.VectorConverter)
        val from = AnimationVector1D(MinputMenuMotion.ClosedScale)
        val to = AnimationVector1D(1f)
        val zero = AnimationVector1D(0f)
        val end = spec.getDurationNanos(from, to, zero)
        val peak = (0..100).maxOf { spec.getValueFromNanos(end * it / 100, from, to, zero).value }
        assert(peak <= 1f) { "the menu scale overshot to $peak" }
    }

    @Test
    fun aMenuScope_replacesTheExpressiveSpring_andKeepsTheCallersTextStyle() {
        var themeSpatial: FiniteAnimationSpec<Float>? = null
        var menuSpatial: FiniteAnimationSpec<Float>? = null
        var menuEffects: FiniteAnimationSpec<Float>? = null
        var menuTextStyle: TextStyle? = null
        val callerStyle = TextStyle(fontSize = 31.sp)
        composeRule.setContent {
            MaterialExpressiveTheme(motionScheme = MotionScheme.expressive()) {
                themeSpatial = MaterialTheme.motionScheme.fastSpatialSpec()
                androidx.compose.runtime.CompositionLocalProvider(LocalTextStyle provides callerStyle) {
                    MinputMenuMotionScope {
                        menuSpatial = MaterialTheme.motionScheme.fastSpatialSpec()
                        menuEffects = MaterialTheme.motionScheme.fastEffectsSpec()
                        menuTextStyle = LocalTextStyle.current
                        Text("menu")
                    }
                }
            }
        }
        composeRule.waitForIdle()
        // The theme's own fast spatial spec is the bouncy spring the audit caught …
        val spring = themeSpatial as SpringSpec<Float>
        assert(spring.dampingRatio < Spring.DampingRatioNoBouncy) { "expected an underdamped spring: ${spring.dampingRatio}" }
        // … and inside a menu it is the menu motion: same durations, no spring.
        assert(menuSpatial !is SpringSpec<*>)
        assert(durationMillis(menuSpatial!!, MinputMenuMotion.ClosedScale, 1f) == MinputMenuMotion.InMillis.toLong())
        assert(durationMillis(menuEffects!!, 1f, 0f) == MinputMenuMotion.OutMillis.toLong())
        assert(menuTextStyle == callerStyle) { "the scope must not reset the caller's text style: $menuTextStyle" }
    }
}
