package com.mappo.ui.screen.remap

/**
 * **The map a carried command navigates by** — which cell lies one d-pad step from which, and
 * which group lies one step from which.
 *
 * Ordinary focus navigation never needs any of this: the whole stage is ONE focus surface and
 * Compose's spatial search reads actual screen positions. A MOVE is different. It walks the drop
 * target by INDEX, and Dylan specified the route in hardware terms (2026-09-17): up and down walk
 * one flank of the controller, left and right cross it to the group opposite. A route you can
 * learn beats a route derived from wherever the boxes happen to have been laid out.
 *
 * ```
 *   ┌ left column ─┐┌ centre ─┐┌ right column ─┐
 *   │ L-shoulder   ││         ││ R-shoulder    │
 *   │ D-pad        ││ [image] ││ Face buttons  │
 *   │ Left stick   ││         ││ Right stick   │
 *   │ Left utility ││         ││ Right utility │
 *   └──────────────┘└─────────┘└───────────────┘
 * ```
 *
 * **Four bands per side since 2026-09-26** (Dylan): the utility buttons used to be one card in
 * the CENTRE column, seated between the two sticks where the hardware puts Select and Start; they
 * are now a utility group per side, at the bottom of their own column, and the centre column holds
 * nothing but the picture.
 *
 * This file was RemapZoomScene.kt until 2026-09-27, where it also laid out the advanced editor's
 * zoomed scene and parked its camera. That editor is gone (see [RemapStage]); the map it needed
 * survives it, because edit mode carries commands across exactly the same grid.
 */

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
