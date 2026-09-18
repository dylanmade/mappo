package com.mappo.ui.screen.remap

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The ZOOMED geometry of the remap controls view, and the map a carried command navigates by.
 *
 * The advanced editor is not a screen you open: it is this view's own elements, zoomed in on
 * (Dylan, 2026-09-17). [RemapStage] owns the travel between the basic grid and the geometry laid
 * out here; this file owns where everything IS once zoomed, where the camera parks, and which
 * group lies one d-pad step from which.
 *
 * ```
 *   ┌ left column ─┐┌ centre ─┐┌ right column ─┐
 *   │ L-shoulder   ││         ││ R-shoulder    │
 *   │ D-pad        ││ [image] ││ Face buttons  │
 *   │ Left stick   ││ Utility ││ Right stick   │
 *   └─────────────┘└─────────┘└───────────────┘
 * ```
 *
 * Each column is [TableColumnFraction] of the viewport wide, so the group you are on fills about
 * two thirds of the screen and the centre column's remaining third shows the part of the
 * controller that group belongs to. Walking the d-pad off a table's edge lands on the
 * neighbouring group by ordinary spatial focus search — the whole stage is ONE focus surface —
 * and the camera FOLLOWS focus rather than leading it, so there is no separate "navigate" mode
 * to learn. Up and down travel a side; left and right cross the controller, via the utility
 * group seated between the two sticks (which is also where the basic grid puts it).
 */

internal fun zoomCardTestTag(group: RemapSimpleGroup): String = "zoom-card:${group.name}"

/** Distance between two cards' centres — the order neighbouring tables compose in. */
internal fun cardDistance(geometry: SceneGeometry, from: RemapSimpleGroup, to: RemapSimpleGroup): Float {
    val a = geometry.cards.getValue(from)
    val b = geometry.cards.getValue(to)
    val dx = (a.x + a.width / 2) - (b.x + b.width / 2)
    val dy = (a.y + a.height / 2) - (b.y + b.height / 2)
    return dx.value * dx.value + dy.value * dy.value
}

/**
 * The cell one grid step from [from], CROSSING into the neighbouring group when the step leaves
 * its table — the scene's stepper for a controller-driven move.
 *
 * Entering a neighbour keeps the sense of the travel: arriving from above lands on its top row,
 * from below its bottom row, and from the side keeps the row you were on and starts at the
 * column you are walking toward. Columns are the same six everywhere, so only rows need
 * clamping.
 */
internal fun stepCellAcrossGroups(from: CellKey, dRow: Int, dCol: Int): CellKey? {
    val rows = from.group.rows
    val row = rows.indexOf(from.row).takeIf { it >= 0 } ?: return null
    val column = pressTypeColumns.indexOf(from.type).takeIf { it >= 0 } ?: return null
    val nextRow = row + dRow
    val nextColumn = column + dCol
    if (nextRow in rows.indices && nextColumn in pressTypeColumns.indices) {
        return CellKey(from.group, rows[nextRow], pressTypeColumns[nextColumn])
    }
    val neighbour = from.group.neighbour(dRow, dCol) ?: return null
    val neighbourRows = neighbour.rows
    val landingRow = when {
        dRow > 0 -> 0
        dRow < 0 -> neighbourRows.lastIndex
        else -> row.coerceAtMost(neighbourRows.lastIndex)
    }
    val landingColumn = when {
        dCol > 0 -> 0
        dCol < 0 -> pressTypeColumns.lastIndex
        else -> column
    }
    return CellKey(neighbour, neighbourRows[landingRow], pressTypeColumns[landingColumn])
}

/**
 * Which group lies one step [dRow] / [dCol] away — the scene's map, in hardware terms.
 *
 * Up and down walk a flank; left and right cross the controller, through the utility group
 * sitting between the two sticks. It is deliberately NOT derived from the laid-out rectangles:
 * spatial focus search does that well enough for a free-roaming cursor, but a command being
 * CARRIED should travel a route the user can predict and learn.
 */
internal fun RemapSimpleGroup.neighbour(dRow: Int, dCol: Int): RemapSimpleGroup? = when {
    dRow > 0 -> when (this) {
        RemapSimpleGroup.LEFT_SHOULDER -> RemapSimpleGroup.DPAD
        RemapSimpleGroup.DPAD -> RemapSimpleGroup.LEFT_STICK
        RemapSimpleGroup.RIGHT_SHOULDER -> RemapSimpleGroup.FACE
        RemapSimpleGroup.FACE -> RemapSimpleGroup.RIGHT_STICK
        else -> null
    }
    dRow < 0 -> when (this) {
        RemapSimpleGroup.DPAD -> RemapSimpleGroup.LEFT_SHOULDER
        RemapSimpleGroup.LEFT_STICK -> RemapSimpleGroup.DPAD
        RemapSimpleGroup.FACE -> RemapSimpleGroup.RIGHT_SHOULDER
        RemapSimpleGroup.RIGHT_STICK -> RemapSimpleGroup.FACE
        else -> null
    }
    dCol > 0 -> when (this) {
        RemapSimpleGroup.LEFT_SHOULDER -> RemapSimpleGroup.RIGHT_SHOULDER
        RemapSimpleGroup.DPAD -> RemapSimpleGroup.FACE
        RemapSimpleGroup.LEFT_STICK -> RemapSimpleGroup.UTILITY
        RemapSimpleGroup.UTILITY -> RemapSimpleGroup.RIGHT_STICK
        else -> null
    }
    dCol < 0 -> when (this) {
        RemapSimpleGroup.RIGHT_SHOULDER -> RemapSimpleGroup.LEFT_SHOULDER
        RemapSimpleGroup.FACE -> RemapSimpleGroup.DPAD
        RemapSimpleGroup.RIGHT_STICK -> RemapSimpleGroup.UTILITY
        RemapSimpleGroup.UTILITY -> RemapSimpleGroup.LEFT_STICK
        else -> null
    }
    else -> null
}

/** A placed rectangle in scene space. */
internal data class SceneRect(val x: Dp, val y: Dp, val width: Dp, val height: Dp)

internal class SceneGeometry(
    val width: Dp,
    val height: Dp,
    val cards: Map<RemapSimpleGroup, SceneRect>,
    val controller: SceneRect,
)

/**
 * Lay the scene out for a viewport of [viewportW] × [viewportH].
 *
 * Three columns of equal width, each [TableColumnFraction] of the viewport: the side columns
 * hold three group cards apiece, the centre one the controller image with the utility card
 * beneath it. Rows are BANDS shared by the two flanks — a left card and its opposite number sit
 * in the same band, whatever their heights — so crossing left or right lands on the group
 * physically opposite, and the camera doesn't lurch vertically on the way.
 *
 * Every card is the height its table wants ([advancedEditorHeight]), capped at the viewport:
 * past that the table scrolls inside the card, as it always has.
 */
internal fun sceneGeometry(viewportW: Dp, viewportH: Dp, controllerAspect: Float): SceneGeometry {
    val columnW = viewportW * TableColumnFraction
    val maxCardH = (viewportH - SceneMargin * 2).coerceAtLeast(0.dp)
    fun cardHeight(group: RemapSimpleGroup): Dp = advancedEditorHeight(group).coerceAtMost(maxCardH)

    val left = listOf(RemapSimpleGroup.LEFT_SHOULDER, RemapSimpleGroup.DPAD, RemapSimpleGroup.LEFT_STICK)
    val right = listOf(RemapSimpleGroup.RIGHT_SHOULDER, RemapSimpleGroup.FACE, RemapSimpleGroup.RIGHT_STICK)
    // Band 2 also holds the utility card in the centre column, and since the sticks became
    // one-row tables (2026-09-17) that card is the tallest thing in the band — leave it out of
    // the measurement and it overhangs the bottom of the scene, past where the camera can go.
    val bandHeights = left.indices.map { band ->
        val flanks = maxOf(cardHeight(left[band]), cardHeight(right[band]))
        if (band == 2) maxOf(flanks, cardHeight(RemapSimpleGroup.UTILITY)) else flanks
    }
    val bandTops = mutableListOf<Dp>()
    var y = SceneMargin
    bandHeights.forEach { h ->
        bandTops += y
        y += h + BandGap
    }
    val sceneH = y - BandGap + SceneMargin

    val sideCardW = (columnW - SceneMargin - CentreGutter).coerceAtLeast(0.dp)
    val cards = buildMap {
        left.forEachIndexed { band, group ->
            val h = cardHeight(group)
            put(group, SceneRect(SceneMargin, bandTops[band] + (bandHeights[band] - h) / 2, sideCardW, h))
        }
        right.forEachIndexed { band, group ->
            val h = cardHeight(group)
            put(
                group,
                SceneRect(
                    x = columnW * 2 + CentreGutter,
                    y = bandTops[band] + (bandHeights[band] - h) / 2,
                    width = sideCardW,
                    height = h,
                ),
            )
        }
        // The utility card sits in the centre column, in the STICK band: the thumb cluster is
        // where Start and Select live on the hardware, and it puts the group one step right of
        // the left stick and one step left of the right one.
        val utilityH = cardHeight(RemapSimpleGroup.UTILITY)
        put(
            RemapSimpleGroup.UTILITY,
            SceneRect(
                x = columnW + CentreGutter,
                y = bandTops[2] + (bandHeights[2] - utilityH) / 2,
                width = (columnW - CentreGutter * 2).coerceAtLeast(0.dp),
                height = utilityH,
            ),
        )
    }

    // The image sits in the centre column, above the utility card, at its OWN proportions —
    // the same ones the rest grid gives it, so the picture scales uniformly across the zoom
    // instead of re-fitting into a differently-shaped cell (see RemapStage).
    val controllerH = columnW * controllerAspect
    val controllerSpan = (bandTops[2] - BandGap).coerceAtLeast(0.dp)
    val controller = SceneRect(
        x = columnW,
        y = ((controllerSpan - controllerH) / 2).coerceAtLeast(0.dp),
        width = columnW,
        height = controllerH,
    )
    return SceneGeometry(width = columnW * 3, height = sceneH, cards = cards, controller = controller)
}

/**
 * Where the camera sits on one axis so a card of [cardExtent] at [cardStart] is centred in a
 * [viewportExtent] window onto a scene of [sceneExtent].
 *
 * Centring is all the stops need: a side column's card centres past the scene's edge and clamps
 * flush to it, which is exactly the "table on its own side, controller beside it" framing, while
 * the utility card — the only one in the middle — genuinely centres.
 */
internal fun cameraAxis(cardStart: Float, cardExtent: Float, viewportExtent: Float, sceneExtent: Float): Float {
    val slack = sceneExtent - viewportExtent
    val centred = cardStart + cardExtent / 2f - viewportExtent / 2f
    // Scene smaller than the window: centre the scene itself rather than pinning it to a corner.
    return if (slack <= 0f) slack / 2f else centred.coerceIn(0f, slack)
}

/** How much of the viewport one column takes — the group's table, leaving the rest for the
 *  controller beside it (Dylan, 2026-09-17: roughly two thirds / one third). */
private const val TableColumnFraction = 0.68f

/** Inset from the scene's outer edges, so a flush-clamped card doesn't touch the screen. */
private val SceneMargin = 10.dp

/** Air between a side card and the centre column's image. */
private val CentreGutter = 12.dp

/** Vertical space between bands. */
private val BandGap = 14.dp

/** A pan between groups. Matches the editor's own open/close motion. */
internal const val CameraMillis = 260

/** Opacity of the cards the camera is not on. */
internal const val RestingCardAlpha = 0.45f
