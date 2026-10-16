package com.mappo.ui.minput

/**
 * Which PHYSICAL side of its parent a nested (cascading) menu opens on.
 *
 * Physical rather than start/end because the decision is about screen room, which is physical.
 */
enum class MinputCascadeSide { Left, Right }

/**
 * **THE cascade placement rule** — which side of its parent a nested menu opens on. Shared by the
 * minput popup menus' [MinputAction.submenu] cascades and the virtual editor's fly-out panel
 * (migrated from there, 2026-10-16), so every cascade in the app makes the same call.
 *
 * Beside the PARENT MENU (its visible edges [parentLeft]…[parentRight]), never beside the row: a
 * nested menu that opens over its own parent hides the path it came from.
 *
 * The cascade's FIRST level ([inherited] null) opens RIGHT when it fits, else LEFT when that fits,
 * else RIGHT (and the caller clamps it into [boundsLeft]…[boundsRight]). Deeper levels INHERIT
 * their parent's side so a cascade keeps marching one way instead of folding back over itself,
 * flipping only when the inherited side doesn't fit and the other does.
 *
 * All values in one coordinate space (px); [width] is the nested menu's visible width.
 */
fun minputCascadeSide(
    parentLeft: Float,
    parentRight: Float,
    width: Float,
    boundsLeft: Float,
    boundsRight: Float,
    inherited: MinputCascadeSide?,
): MinputCascadeSide {
    val rightFits = parentRight + width <= boundsRight
    val leftFits = parentLeft - width >= boundsLeft
    return when (inherited) {
        MinputCascadeSide.Left -> if (leftFits || !rightFits) MinputCascadeSide.Left else MinputCascadeSide.Right
        MinputCascadeSide.Right, null -> if (rightFits || !leftFits) MinputCascadeSide.Right else MinputCascadeSide.Left
    }
}

/** The nested menu's left edge for [side]: flush against the parent's edge on that side. */
fun minputCascadeLeft(side: MinputCascadeSide, parentLeft: Float, parentRight: Float, width: Float): Float =
    if (side == MinputCascadeSide.Right) parentRight else parentLeft - width
