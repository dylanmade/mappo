package com.mappo.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * **Where something is pinned on the screen, as a glyph** (Dylan, 2026-10-12) — a 3 × 3 grid of
 * dots standing for the screen, with the pinned spot drawn as a LINE through three of them:
 *  - an EDGE's middle is that edge's row (or column) of dots — top middle = the three top dots;
 *  - a CORNER is the corner dot and its two neighbours along the edges, an L — top left = the
 *    left-middle dot, the top-left dot, the top-middle dot.
 *
 * All eight spots are here; the hierarchy bar's pin button uses the six top/bottom ones, the
 * side-middle two are for whatever pins to a side next. START / END mirror under RTL, as the
 * bar's own alignment does.
 */
enum class PinSpot(
    /** The three grid cells the line runs through, in order — (column, row), 0..2 each. */
    private val line: List<Pair<Int, Int>>,
    private val mirrors: Boolean,
) {
    TopStart(listOf(0 to 1, 0 to 0, 1 to 0), mirrors = true),
    TopCenter(listOf(0 to 0, 1 to 0, 2 to 0), mirrors = false),
    TopEnd(listOf(1 to 0, 2 to 0, 2 to 1), mirrors = true),
    CenterStart(listOf(0 to 0, 0 to 1, 0 to 2), mirrors = true),
    CenterEnd(listOf(2 to 0, 2 to 1, 2 to 2), mirrors = true),
    BottomStart(listOf(0 to 1, 0 to 2, 1 to 2), mirrors = true),
    BottomCenter(listOf(0 to 2, 1 to 2, 2 to 2), mirrors = false),
    BottomEnd(listOf(1 to 2, 2 to 2, 2 to 1), mirrors = true);

    /** The glyph — built once per spot. */
    val icon: ImageVector by lazy { build() }

    private fun build(): ImageVector = ImageVector.Builder(
        name = "PinSpot.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = mirrors,
    ).apply {
        // The dots — all nine, the pinned three included (the line runs THROUGH them).
        path(fill = SolidColor(Color.Black)) {
            for (column in 0..2) for (row in 0..2) {
                val cx = cell(column)
                val cy = cell(row)
                moveTo(cx - DotRadius, cy)
                arcToRelative(DotRadius, DotRadius, 0f, true, true, DotRadius * 2, 0f)
                arcToRelative(DotRadius, DotRadius, 0f, true, true, -DotRadius * 2, 0f)
                close()
            }
        }
        // The pinned spot.
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = LineWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            line.forEachIndexed { index, (column, row) ->
                if (index == 0) moveTo(cell(column), cell(row)) else lineTo(cell(column), cell(row))
            }
        }
    }.build()

    private companion object {
        /** Dot centres sit at 4 / 12 / 20 of the 24-unit viewport. */
        fun cell(index: Int): Float = 4f + index * 8f

        /** Dots heavier than the line, so the three it joins still read as dots on it. */
        const val DotRadius = 2.5f
        const val LineWidth = 3f
    }
}
