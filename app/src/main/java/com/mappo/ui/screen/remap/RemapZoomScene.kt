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
 *   ┌ left column ──┐┌ centre ─┐┌ right column ─┐
 *   │ L-shoulder    ││         ││ R-shoulder    │
 *   │ D-pad         ││ [image] ││ Face buttons  │
 *   │ Left stick    ││         ││ Right stick   │
 *   │ Left utility  ││         ││ Right utility │
 *   └──────────────┘└─────────┘└───────────────┘
 * ```
 *
 * Each column is [TableColumnFraction] of the viewport wide, so the group you are on fills about
 * two thirds of the screen and the centre column's remaining third shows the part of the
 * controller that group belongs to. Walking the d-pad off a table's edge lands on the
 * neighbouring group by ordinary spatial focus search — the whole stage is ONE focus surface —
 * and the camera FOLLOWS focus rather than leading it, so there is no separate "navigate" mode
 * to learn. Up and down travel a side; left and right cross the controller to the group opposite.
 *
 * **Four bands per side since 2026-09-26** (Dylan): the utility buttons used to be one card in
 * the CENTRE column, seated between the two sticks where the hardware puts Select and Start;
 * they are now a utility group per side, at the bottom of their own column, and the centre
 * column holds nothing but the picture.
 */

internal fun zoomCardTestTag(group: RemapSimpleGroup): String = "zoom-card:${group.name}"

/**
 * The cell one grid step from [from], CROSSING into the neighbouring group when the step leaves
 * its table — the scene's stepper for a controller-driven move.
 *
 * Entering a neighbour keeps the sense of the travel: arriving from above lands on its top row,
 * from below its bottom row, and from the side keeps the row you were on and starts at the slot
 * you are walking toward.
 *
 * [slots] reports a row's tile count, its trailing "+" included. Rows became variable-length
 * stacks on 2026-09-20, so both the "did this step leave the table" test and the landing slot
 * have to ask rather than assume — a row of one command has two stops, its neighbour may have
 * six, and stepping between them clamps to what each actually holds.
 */
internal fun stepCellAcrossGroups(
    from: CellKey,
    dRow: Int,
    dCol: Int,
    slots: (RemapSimpleGroup, SimpleRowSpec) -> Int,
): CellKey? {
    val rows = from.group.rows
    val row = rows.indexOf(from.row).takeIf { it >= 0 } ?: return null
    val nextRow = row + dRow
    // [dCol] is a SCREEN direction; a slot index counts outward from its row's glyph. On a
    // MIRRORED row those run opposite ways (see [slotsRunLeftward]), so the two have to be
    // converted into one another rather than used interchangeably.
    val nextSlot = from.slot + dCol * from.group.slotStepFor(from.row)
    if (nextRow in rows.indices) {
        val spec = rows[nextRow]
        val count = slots(from.group, spec)
        // A row step keeps the slot, clamped: stepping down from slot 4 onto a two-tile row
        // lands on its last tile rather than nowhere.
        if (dRow != 0) return CellKey(from.group, spec, nextSlot.coerceIn(0, count - 1))
        if (nextSlot in 0 until count) return CellKey(from.group, spec, nextSlot)
    }
    val neighbour = from.group.neighbour(dRow, dCol) ?: return null
    val neighbourRows = neighbour.rows
    val landingRow = when {
        dRow > 0 -> 0
        dRow < 0 -> neighbourRows.lastIndex
        else -> row.coerceAtMost(neighbourRows.lastIndex)
    }
    val spec = neighbourRows[landingRow]
    val lastSlot = (slots(neighbour, spec) - 1).coerceAtLeast(0)
    // Enter the neighbour at the edge you arrive from, in SCREEN terms: travelling right, land
    // on its leftmost tile — which is its LAST slot when its row reads leftward.
    val enteringLeftward = neighbour.slotsRunLeftward(spec)
    val landingSlot = when {
        dCol > 0 -> if (enteringLeftward) lastSlot else 0
        dCol < 0 -> if (enteringLeftward) 0 else lastSlot
        else -> from.slot.coerceAtMost(lastSlot)
    }
    return CellKey(neighbour, spec, landingSlot)
}

/**
 * **Does this row lay its slots out right-to-left?**
 *
 * A slot INDEX counts outward from the row's input glyph, which is the card's outward-facing
 * edge on a mirrored group — so on the left flank, slot 0 is the RIGHTMOST tile and the indices
 * climb leftward. Both views mirror by the same rule ([RemapSimpleGroup.anchorFor]), including
 * the centre group, which mirrors per ROW around its own centre line rather than as a whole.
 *
 * Ordinary focus navigation never needed this — Compose's spatial search reads actual screen
 * positions — but a MOVE walks the drop target by index, so without it pressing right on the
 * left flank carried a tile left and pressing left did nothing at all (Dylan, 2026-09-23).
 */
internal fun RemapSimpleGroup.slotsRunLeftward(spec: SimpleRowSpec): Boolean =
    anchorFor(spec) == RowAnchor.END

/** +1 where a row's slots climb rightward, -1 where they climb leftward: the factor that turns
 *  a screen-space column step into a slot-index step. */
internal fun RemapSimpleGroup.slotStepFor(spec: SimpleRowSpec): Int =
    if (slotsRunLeftward(spec)) -1 else 1

/**
 * Which group lies one step [dRow] / [dCol] away — the scene's map, in hardware terms.
 *
 * Up and down walk a flank, top to bottom; left and right cross the controller to the group in
 * the same band on the other side. It is deliberately NOT derived from the laid-out rectangles:
 * spatial focus search does that well enough for a free-roaming cursor, but a command being
 * CARRIED should travel a route the user can predict and learn.
 *
 * Read off [GridBands], which is the one statement of the grid's shape — the map used to be
 * written out twice per direction and had to be edited in four places when Select and Start
 * became side groups (2026-09-26). A band's two groups are each other's horizontal neighbours;
 * the bands' order is the vertical one.
 */
internal fun RemapSimpleGroup.neighbour(dRow: Int, dCol: Int): RemapSimpleGroup? {
    val band = GridBands.indexOfFirst { it.left == this || it.right == this }
    if (band < 0) return null
    val onLeft = GridBands[band].left == this
    return when {
        dRow != 0 -> GridBands.getOrNull(band + dRow)?.let { if (onLeft) it.left else it.right }
        // Crossing is a SCREEN direction: rightward only leaves a left-column group, and
        // leftward only a right-column one.
        dCol > 0 -> GridBands[band].right.takeIf { onLeft }
        dCol < 0 -> GridBands[band].left.takeIf { !onLeft }
        else -> null
    }
}

/** Which band the controller image occupies — shared with the rest grid, which anchors its
 *  own bands around the same one (see RemapStage's `ControllerBand`). */
private const val ControllerBand = 1

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

    val left = listOf(
        RemapSimpleGroup.LEFT_SHOULDER,
        RemapSimpleGroup.DPAD,
        RemapSimpleGroup.LEFT_STICK,
        RemapSimpleGroup.LEFT_UTILITY,
    )
    val right = listOf(
        RemapSimpleGroup.RIGHT_SHOULDER,
        RemapSimpleGroup.FACE,
        RemapSimpleGroup.RIGHT_STICK,
        RemapSimpleGroup.RIGHT_UTILITY,
    )
    // The image takes its size from the viewport's HEIGHT, exactly as the basic grid's does
    // (Dylan, 2026-09-21) — it used to be the full width of the centre column, so a wider screen
    // (or closing the layouts drawer) grew the controller while the basic view's stayed put, and
    // the same object was two different sizes depending on how much room was going spare. The
    // COLUMN is still width-derived: it is the scene's layout grid, and it should stretch.
    val controllerW = (viewportH * ZoomControllerHeightRatio).coerceAtMost(columnW)
    val controllerH = controllerW * controllerAspect
    // A band is as tall as the tallest thing IN it — including, in band 1, the controller image
    // in the centre column. That last part matters: the rest grid sizes its middle band the same
    // way (see RemapStage), and the two geometries must agree about where the controller sits
    // relative to the cards (Dylan, 2026-09-18) — it used to be centred over bands 0 AND 1
    // together, which put it most of a band above where the basic view has it, and zooming into
    // the face buttons carried them off the top of the screen.
    val bandHeights = left.indices.map { band ->
        val flanks = maxOf(cardHeight(left[band]), cardHeight(right[band]))
        if (band == ControllerBand) maxOf(flanks, controllerH) else flanks
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
    }

    // The image sits in the centre column, CENTRED ON THE MIDDLE BAND — the d-pad and the face
    // buttons, the groups it is between — and at its OWN proportions, the same ones the rest
    // grid gives it. Both halves of that matter: the proportions let the picture scale
    // uniformly across the zoom instead of re-fitting into a differently-shaped cell, and the
    // band keeps it in the same place relative to each group as the basic view has it, which
    // is what stops the zoom from sliding the controller out from under the group you opened
    // (see RemapStage).
    val controller = SceneRect(
        x = columnW + ((columnW - controllerW) / 2).coerceAtLeast(0.dp),
        y = bandTops[ControllerBand] +
            ((bandHeights[ControllerBand] - controllerH) / 2).coerceAtLeast(0.dp),
        width = controllerW,
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
internal fun cameraAxis(cardStart: Float, cardExtent: Float, viewportExtent: Float, sceneExtent: Float): Float =
    clampCameraAxis(cardStart + cardExtent / 2f - viewportExtent / 2f, viewportExtent, sceneExtent)

/**
 * Hold one camera axis inside the scene — the only positions the camera may occupy.
 *
 * Shared with the TOUCH PAN (2026-09-21), which moves the camera directly rather than by picking
 * a card: a finger and a d-pad must be able to reach exactly the same set of views, or the two
 * ways of navigating the scene would disagree about where its edges are.
 */
internal fun clampCameraAxis(value: Float, viewportExtent: Float, sceneExtent: Float): Float {
    val slack = sceneExtent - viewportExtent
    // Scene smaller than the window: centre the scene itself rather than pinning it to a corner.
    return if (slack <= 0f) slack / 2f else value.coerceIn(0f, slack)
}

/** How much of the viewport one column takes — the group's table, leaving the rest for the
 *  controller beside it (Dylan, 2026-09-17: roughly two thirds / one third). */
private const val TableColumnFraction = 0.68f

/**
 * The zoomed controller's width, as a fraction of the viewport's HEIGHT.
 *
 * Height, for the same reason the basic grid's `ControllerColumnHeightRatio` is: the height is
 * the dimension that does NOT change when the screen widens or the layouts drawer closes, so
 * taking the size from it pins the picture at one size on any device. 1.09 is what the old
 * `0.68 × width` came to on a 16:10 screen, so nothing moves on the shape this was tuned on; it
 * clamps to the column on anything narrower.
 */
private const val ZoomControllerHeightRatio = 1.09f

/** Inset from the scene's outer edges, so a flush-clamped card doesn't touch the screen. */
private val SceneMargin = 10.dp

/** Air between a side card and the centre column's image. */
private val CentreGutter = 12.dp

/** Vertical space between bands. */
private val BandGap = 14.dp

/** A pan between groups. Matches the editor's own open/close motion. */
internal const val CameraMillis = 260

