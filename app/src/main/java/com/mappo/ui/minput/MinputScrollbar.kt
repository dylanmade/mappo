package com.mappo.ui.minput

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/**
 * A thin scroll indicator for a [ScrollState]-backed scroller.
 *
 * Compose ships no scrollbar at all (the platform's own overlay bars don't apply to a
 * `Modifier.horizontalScroll`/`verticalScroll` container), so this is a hand-drawn primitive
 * rather than a restyled M3 component — one of the few places the library has nothing to wrap.
 * Kept to a `Canvas` for that reason: two rounded rects, no layout of its own beyond the
 * thickness it's given.
 *
 * **It is an INDICATOR, not a control** — not draggable. On the controller-navigated surfaces
 * this exists for, scrolling happens by moving focus; the bar's whole job is answering "is
 * there more, and where am I". Making it draggable would add a touch-only affordance at a
 * sub-touch-target thickness, which is worse than not having one.
 *
 * [reverse] mirrors the thumb for a scroller whose resting position is its FAR end — the
 * basic view's mirrored rows, which read outward from a glyph pinned to the right edge and so
 * run `horizontalScroll(reverseScrolling = true)`. There, value 0 means "showing the right
 * end", and a thumb drawn from the left would sit at the wrong end of the track.
 *
 * Self-hiding: when the content fits, there is nothing to indicate and the bar renders
 * nothing at all (it still occupies its thickness, so a container's layout doesn't jump the
 * moment content grows past the viewport). The track is always faint; the thumb brightens
 * while a scroll is actually in progress and settles back afterwards.
 */
@Composable
fun MinputScrollbar(
    state: ScrollState,
    orientation: Orientation,
    modifier: Modifier = Modifier,
    thickness: androidx.compose.ui.unit.Dp = MinputScrollbarThickness,
    reverse: Boolean = false,
) {
    val scrollable = state.maxValue > 0 && state.maxValue != Int.MAX_VALUE
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = MinputScrollbarTrackAlpha)
    val thumbBase = MaterialTheme.colorScheme.onSurface

    val thumbAlpha by animateFloatAsState(
        targetValue = when {
            !scrollable -> 0f
            state.isScrollInProgress -> MinputScrollbarThumbActiveAlpha
            else -> MinputScrollbarThumbRestAlpha
        },
        label = "minput-scrollbar-thumb",
    )

    Canvas(
        modifier = modifier.then(
            if (orientation == Orientation.Horizontal) {
                Modifier.fillMaxWidth().height(thickness)
            } else {
                Modifier.fillMaxHeight().width(thickness)
            },
        ),
    ) {
        if (!scrollable) return@Canvas

        val lengthPx = if (orientation == Orientation.Horizontal) size.width else size.height
        val thicknessPx = if (orientation == Orientation.Horizontal) size.height else size.width
        val radius = CornerRadius(thicknessPx / 2, thicknessPx / 2)

        // Viewport vs. total content. `viewportSize` can still be 0 on the very first frame,
        // before the scroller has measured — fall back to a proportion that keeps the thumb a
        // sane size rather than dividing by zero.
        val viewport = state.viewportSize.toFloat()
        val content = viewport + state.maxValue
        val thumbFraction = when {
            viewport <= 0f || content <= 0f -> MinputScrollbarMinThumbFraction
            else -> (viewport / content).coerceIn(MinputScrollbarMinThumbFraction, 1f)
        }
        val thumbLength = lengthPx * thumbFraction
        val travel = lengthPx - thumbLength
        val progress = if (state.maxValue > 0) state.value.toFloat() / state.maxValue else 0f
        val travelled = progress.coerceIn(0f, 1f).let { if (reverse) 1f - it else it }
        val thumbStart = travel * travelled

        drawRoundRect(color = trackColor, cornerRadius = radius)
        drawRoundRect(
            color = thumbBase.copy(alpha = thumbAlpha),
            topLeft = if (orientation == Orientation.Horizontal) {
                Offset(thumbStart, 0f)
            } else {
                Offset(0f, thumbStart)
            },
            size = if (orientation == Orientation.Horizontal) {
                Size(thumbLength, thicknessPx)
            } else {
                Size(thicknessPx, thumbLength)
            },
            cornerRadius = radius,
        )
    }
}

/** Bar thickness. Hairline by intent — it reports state, it isn't a grab target. */
val MinputScrollbarThickness = 3.dp

private const val MinputScrollbarTrackAlpha = 0.08f
private const val MinputScrollbarThumbRestAlpha = 0.28f
private const val MinputScrollbarThumbActiveAlpha = 0.55f

/** Floor on the thumb's share of the track, so a very long scroller still shows a grabbable-
 *  looking marker instead of a speck. */
private const val MinputScrollbarMinThumbFraction = 0.08f
