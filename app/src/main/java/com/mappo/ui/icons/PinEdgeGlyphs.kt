package com.mappo.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * **Which screen edge something is pinned to, as a glyph** (Dylan, 2026-10-14; replaced the
 * dot-grid PinSpot family when the bar's six spots became four edges) — Lucide's
 * align-horizontal-justify-end with ONE square instead of two: a rounded square standing off a
 * full-height line, the line on the pinned edge. Same 24-unit viewport, 2-unit stroke and round
 * caps / joins as Lucide, so it sits among the Lucide glyphs as one of them. START / END mirror
 * under RTL, as the bar's own placement does.
 */
enum class PinEdge(private val mirrors: Boolean) {
    Top(mirrors = false),
    Start(mirrors = true),
    End(mirrors = true),
    Bottom(mirrors = false);

    /** The glyph — built once per edge. */
    val icon: ImageVector by lazy { build() }

    /** Maps a point of the END glyph (line on the right) onto this edge's glyph. */
    private fun place(x: Float, y: Float): Pair<Float, Float> = when (this) {
        End -> x to y
        Start -> (Viewport - x) to y
        Top -> y to (Viewport - x)
        Bottom -> y to x
    }

    private fun build(): ImageVector {
        val (lineStartX, lineStartY) = place(LineX, LineFrom)
        val (lineEndX, lineEndY) = place(LineX, LineTo)
        val (ax, ay) = place(SquareStart, SquareTop)
        val (bx, by) = place(SquareStart + SquareSide, SquareTop + SquareSide)
        val left = minOf(ax, bx)
        val top = minOf(ay, by)
        val right = maxOf(ax, bx)
        val bottom = maxOf(ay, by)
        val r = CornerRadius
        return ImageVector.Builder(
            name = "PinEdge.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = Viewport,
            viewportHeight = Viewport,
            autoMirror = mirrors,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = StrokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // The square — Lucide's rect with rx = 2.
                moveTo(left + r, top)
                lineTo(right - r, top)
                arcTo(r, r, 0f, false, true, right, top + r)
                lineTo(right, bottom - r)
                arcTo(r, r, 0f, false, true, right - r, bottom)
                lineTo(left + r, bottom)
                arcTo(r, r, 0f, false, true, left, bottom - r)
                lineTo(left, top + r)
                arcTo(r, r, 0f, false, true, left + r, top)
                close()
                // The edge.
                moveTo(lineStartX, lineStartY)
                lineTo(lineEndX, lineEndY)
            }
        }.build()
    }

    private companion object {
        const val Viewport = 24f
        const val StrokeWidth = 2f

        /** The END glyph's geometry: Lucide's line at x = 22, y 2 → 22 … */
        const val LineX = 22f
        const val LineFrom = 2f
        const val LineTo = 22f

        /** Line to square, centre to centre of the strokes: a hair more than Lucide's 4, so the
         *  one square reads as standing off its edge (Dylan, 2026-10-15). */
        const val SquareGap = 5f

        /** … and its taller rect's height (14) as the square's side, vertically centred,
         *  [SquareGap] off the line. */
        const val SquareSide = 14f
        const val SquareStart = LineX - SquareGap - SquareSide
        const val SquareTop = (Viewport - SquareSide) / 2f
        const val CornerRadius = 2f
    }
}
