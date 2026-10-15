package com.mappo.ui.screen.virtualset

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mappo.ui.minput.MinputIcon
import com.mappo.ui.minput.MinputIconSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The Positioner: a grab-anywhere-draggable d-pad for nudging the selection — a plus of two
 * fully-rounded bars (the menu container colour) carrying bare arrows, a small (1px) and a large
 * (10px) per arm. Tap to nudge once, hold to repeat; a press that travels past the slop drags the
 * whole d-pad instead. The centre is bare drag space; Options ▸ Positioner toggles it.
 *
 * Ported from the retired overlay editor's own window; the press → ripple wiring stays geometric
 * (one gesture detector over the whole pad drives each zone's ripple), because the pad must stay
 * draggable from any point, buttons included.
 */
@Composable
internal fun Positioner(
    vm: VirtualSetEditorViewModel,
    canvas: EditCanvas,
    bounds: Rect,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val sources = remember { POS_KEYS.associateWith { MutableInteractionSource() } }
    // Top-left (shadow margin included), editor-local px; NaN = the default spot.
    var x by remember { mutableFloatStateOf(Float.NaN) }
    var y by remember { mutableFloatStateOf(Float.NaN) }
    // The pad lives beside the stage (editor px); its nudges move buttons in display px.
    val b = bounds
    val half = with(density) { POSITIONER_DP.dp.toPx() } / 2f
    val centre = with(density) { MENU_SHADOW_MARGIN.dp.toPx() } + half

    fun hit(p: Offset): String? {
        val rx = (p.x - centre) / half
        val ry = (p.y - centre) / half
        return positionerRects().firstOrNull { rx in it.l..it.r && ry in it.t..it.b }?.key
    }

    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            Box(
                Modifier
                    .pointerInput(canvas, bounds) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val key = hit(down.position)
                            var press: PressInteraction.Press? = null
                            var nudging = false
                            var dragging = false
                            var repeat: Job? = null
                            if (key != null) {
                                val zone = positionerRects().first { it.key == key }
                                press = PressInteraction.Press(
                                    Offset(down.position.x - (centre + zone.l * half), down.position.y - (centre + zone.t * half)),
                                ).also { sources.getValue(key).tryEmit(it) }
                                val (dx, dy) = nudgeDelta(key)
                                // Hold → after a delay, nudge and repeat (a quick tap fires on UP).
                                repeat = scope.launch {
                                    delay(NUDGE_HOLD_DELAY_MS)
                                    vm.beginNudge(); nudging = true
                                    vm.stepNudge(dx, dy, canvas.width, canvas.height)
                                    while (isActive) {
                                        delay(NUDGE_HOLD_REPEAT_MS)
                                        vm.stepNudge(dx, dy, canvas.width, canvas.height)
                                    }
                                }
                            }
                            var travelled = Offset.Zero
                            var released = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.changedToUpIgnoreConsumed()) {
                                    released = true
                                    break
                                }
                                val delta = change.positionChange()
                                travelled += delta
                                if (!dragging && travelled.getDistance() > viewConfiguration.touchSlop) {
                                    dragging = true
                                    repeat?.cancel()
                                    if (nudging) { vm.endNudge(); nudging = false }
                                    press?.let { sources.getValue(key!!).tryEmit(PressInteraction.Cancel(it)) }
                                    press = null
                                    if (x.isNaN()) { x = defaultX(b); y = defaultY(b) }
                                }
                                if (dragging) {
                                    change.consume()
                                    x += delta.x
                                    y += delta.y
                                }
                            }
                            repeat?.cancel()
                            press?.let {
                                sources.getValue(key!!).tryEmit(if (released) PressInteraction.Release(it) else PressInteraction.Cancel(it))
                            }
                            if (released && !dragging && key != null && !nudging) {
                                val (dx, dy) = nudgeDelta(key)
                                vm.beginNudge(); vm.stepNudge(dx, dy, canvas.width, canvas.height); vm.endNudge()
                            } else if (nudging) {
                                vm.endNudge()
                            }
                        }
                    }
                    .padding(MENU_SHADOW_MARGIN.dp),
            ) {
                ProvideMenuRipple { PositionerPad(sources) }
            }
        },
    ) { measurables, constraints ->
        val p = measurables.first().measure(Constraints())
        val w = p.width.toFloat()
        val h = p.height.toFloat()
        val px = EditCanvas.clamp(if (x.isNaN()) defaultX(b) else x, w, b.left, b.right)
        val py = EditCanvas.clamp(if (y.isNaN()) defaultY(b) else y, h, b.top, b.bottom)
        layout(constraints.maxWidth, constraints.maxHeight) { p.place(px.roundToInt(), py.roundToInt()) }
    }
}

private fun defaultX(b: Rect) = b.left + b.width * 0.72f
private fun defaultY(b: Rect) = b.top + b.height * 0.40f

@Composable
private fun PositionerPad(sources: Map<String, MutableInteractionSource>) {
    // Background = the menu container colour (matches the core menu).
    val bgFill = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
    val iconTint = MaterialTheme.colorScheme.onSurface
    val halfDp = POSITIONER_DP / 2f
    val smallOff = ((2f * POS_BTN_HALF_F + POS_GAP_F) * halfDp).dp
    val largeOff = ((4f * POS_BTN_HALF_F + 2f * POS_GAP_F) * halfDp).dp
    Box(
        Modifier
            .requiredSize(POSITIONER_DP.dp)
            .drawBehind {
                val d = size.width / 2f
                fun rectPx(l: Float, t: Float, r: Float, b: Float) =
                    android.graphics.RectF(d + l * d, d + t * d, d + r * d, d + b * d)
                // Plus = a vertical pill ∪ a horizontal pill (semicircular prong ends), one fill.
                val barHalf = POS_BG_HALF_F * d
                val plus = android.graphics.Path().apply {
                    addRoundRect(rectPx(-POS_BG_HALF_F, -1f, POS_BG_HALF_F, 1f), barHalf, barHalf, android.graphics.Path.Direction.CW)
                    addRoundRect(rectPx(-1f, -POS_BG_HALF_F, 1f, POS_BG_HALF_F), barHalf, barHalf, android.graphics.Path.Direction.CW)
                }
                drawIntoCanvas { canvas ->
                    val nc = canvas.nativeCanvas
                    val shadow = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(72, 0, 0, 0)
                        maskFilter = android.graphics.BlurMaskFilter(8.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
                        isAntiAlias = true
                    }
                    nc.save(); nc.translate(0f, 2.dp.toPx()); nc.drawPath(plus, shadow); nc.restore()
                    nc.drawPath(plus, android.graphics.Paint().apply { color = bgFill; isAntiAlias = true })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Single filled triangles on the small zones; doubles (fast-forward) on the large.
        PosZone(sources, "up", offsetY = -smallOff) { PosIcon(Icons.Default.PlayArrow, -90f, POS_SMALL_ICON, iconTint, "Up 1") }
        PosZone(sources, "down", offsetY = smallOff) { PosIcon(Icons.Default.PlayArrow, 90f, POS_SMALL_ICON, iconTint, "Down 1") }
        PosZone(sources, "left", offsetX = -smallOff) { PosIcon(Icons.Default.PlayArrow, 180f, POS_SMALL_ICON, iconTint, "Left 1") }
        PosZone(sources, "right", offsetX = smallOff) { PosIcon(Icons.Default.PlayArrow, 0f, POS_SMALL_ICON, iconTint, "Right 1") }
        PosZone(sources, "up_big", offsetY = -largeOff) { PosIcon(Icons.Default.FastForward, -90f, POS_LARGE_ICON, iconTint, "Up 10") }
        PosZone(sources, "down_big", offsetY = largeOff) { PosIcon(Icons.Default.FastForward, 90f, POS_LARGE_ICON, iconTint, "Down 10") }
        PosZone(sources, "left_big", offsetX = -largeOff) { PosIcon(Icons.Default.FastRewind, 0f, POS_LARGE_ICON, iconTint, "Left 10") }
        PosZone(sources, "right_big", offsetX = largeOff) { PosIcon(Icons.Default.FastForward, 0f, POS_LARGE_ICON, iconTint, "Right 10") }
    }
}

/** One bare icon zone, sized to its [positionerRects] square, with a circular ripple driven by
 *  the pad's gesture detector through [key]'s source. */
@Composable
private fun BoxScope.PosZone(
    sources: Map<String, MutableInteractionSource>,
    key: String,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val zoneDp = (POS_BTN_HALF_F * POSITIONER_DP).dp // = 2·half · (DP/2)
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .offset(offsetX, offsetY)
            .requiredSize(zoneDp)
            .clip(CircleShape)
            .indication(sources.getValue(key), ripple(bounded = true)),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun PosIcon(icon: ImageVector, rotation: Float, size: MinputIconSize, tint: Color, desc: String) {
    MinputIcon(icon, contentDescription = desc, size = size, tint = tint, modifier = Modifier.rotate(rotation))
}

/** A positioner zone's rect, normalized to the half-side (coords in [-1, 1] out from centre). */
private data class PosRect(val key: String, val l: Float, val t: Float, val r: Float, val b: Float)

/**
 * The eight nudge zones. Renderer and hit-test both derive from this, so the visible button and
 * its touch region coincide. Every zone is a [POS_BTN_HALF_F] square; along each axis out from
 * centre: bare centre, gap, SMALL, gap, LARGE (half-extent 5·bh + 2·gap, kept under 1).
 */
private fun positionerRects(): List<PosRect> {
    val bh = POS_BTN_HALF_F; val g = POS_GAP_F
    val sNear = bh + g; val sFar = 3f * bh + g
    val lNear = 3f * bh + 2f * g; val lFar = 5f * bh + 2f * g
    return listOf(
        PosRect("up", -bh, -sFar, bh, -sNear),
        PosRect("up_big", -bh, -lFar, bh, -lNear),
        PosRect("down", -bh, sNear, bh, sFar),
        PosRect("down_big", -bh, lNear, bh, lFar),
        PosRect("left", -sFar, -bh, -sNear, bh),
        PosRect("left_big", -lFar, -bh, -lNear, bh),
        PosRect("right", sNear, -bh, sFar, bh),
        PosRect("right_big", lNear, -bh, lFar, bh),
    )
}

private fun nudgeDelta(key: String): Pair<Float, Float> = when (key) {
    "up" -> 0f to -NUDGE_SMALL_PX
    "down" -> 0f to NUDGE_SMALL_PX
    "left" -> -NUDGE_SMALL_PX to 0f
    "right" -> NUDGE_SMALL_PX to 0f
    "up_big" -> 0f to -NUDGE_LARGE_PX
    "down_big" -> 0f to NUDGE_LARGE_PX
    "left_big" -> -NUDGE_LARGE_PX to 0f
    "right_big" -> NUDGE_LARGE_PX to 0f
    else -> 0f to 0f
}

private val POS_KEYS = listOf("up", "up_big", "down", "down_big", "left", "left_big", "right", "right_big")
// Nudge magnitudes (screen px per step): small = fine, large = coarse. Hold to auto-repeat.
private const val NUDGE_SMALL_PX = 1f
private const val NUDGE_LARGE_PX = 10f
private const val NUDGE_HOLD_DELAY_MS = 350L
private const val NUDGE_HOLD_REPEAT_MS = 45L
// The pad is a POSITIONER_DP square; zone fractions are of its half-side.
private const val POSITIONER_DP = 196
private const val POS_BTN_HALF_F = 0.176f   // each zone's square is 2·this
private const val POS_GAP_F = 0.04f         // gap between adjacent zones
private const val POS_BG_HALF_F = 0.21f     // the plus's bar half-width
private val POS_SMALL_ICON = MinputIconSize.Xs
private val POS_LARGE_ICON = MinputIconSize.L
