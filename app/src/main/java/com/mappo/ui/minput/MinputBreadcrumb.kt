package com.mappo.ui.minput

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.asin
import kotlin.math.sqrt

/**
 * **A breadcrumb row: a pill cut into joined segments** (Dylan, 2026-10-07) — the hierarchy
 * picker behind the remap editors' bar (application › layout › set › buttons).
 *
 * The silhouette is an M3 button group's — separate buttons with a gap between them, the whole
 * row reading as one pill — but the joins LEAD: every segment except the last bulges toward the
 * next one, and every segment except the first carries the matching recess, so the gap between
 * two segments follows both. The outer ends stay fully round. The point is the hierarchy reading
 * at a glance: each crumb visibly leads into the one after it.
 *
 * Three knobs, all handed down to the segments ([LocalMinputBreadcrumbStyle]):
 *  - [join] — the join's [MinputBreadcrumbJoin.style] (a circular ARC or a pointed CHEVRON) and
 *    its [MinputBreadcrumbJoin.curvature] (0 = a straight edge … 1 = as deep as a pill end).
 *    App-wide default: [MinputBreadcrumbJoinDefault].
 *  - [size] — the control variant; [MinputSize.ExtraLarge] holds the overline + title stack.
 *  - [mode] — [MinputBreadcrumbMode.Expanded] (glyph + text) or [MinputBreadcrumbMode.Icon]
 *    (glyph alone). Either way every segment is the SAME width — fixed per mode and size, never
 *    the width of its text, so the row doesn't shuffle while a focus-preview changes a title.
 *
 * **The segments OVERLAP their layout boxes.** A recess is cut out of its segment's box, so boxes
 * placed a plain [MinputSegmentGap] apart would leave the gap PLUS the recess between bulge and
 * recess (the first pass shipped exactly that). The row places each segment so the recess sits
 * exactly [MinputSegmentGap] off the previous bulge, measured square to the edge: an arc's recess
 * is the bulge's circle grown by the gap (concentric — a uniform gap round the curve); a
 * chevron's recess is the bulge's edges moved the gap along their normal.
 *
 * Place [MinputBreadcrumbSegment]s (each optionally in a Box with its menu) as direct children,
 * passing each one's [MinputBreadcrumbPosition] ([MinputBreadcrumbPosition.of]).
 */
@Composable
fun MinputBreadcrumbRow(
    modifier: Modifier = Modifier,
    size: MinputSize = MinputBreadcrumbSize,
    join: MinputBreadcrumbJoin = MinputBreadcrumbJoinDefault,
    mode: MinputBreadcrumbMode = MinputBreadcrumbMode.Expanded,
    content: @Composable () -> Unit,
) {
    val style = MinputBreadcrumbStyle(size = size, join = join, mode = mode)
    CompositionLocalProvider(LocalMinputBreadcrumbStyle provides style) {
        Layout(content = content, modifier = modifier.height(size.height)) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0)
            val placeables = measurables.map { it.measure(loose) }
            val step = join.step(size.height).roundToPx()
            val width = placeables.sumOf { it.width } + step * (placeables.size - 1).coerceAtLeast(0)
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(width.coerceAtLeast(0), height) {
                var x = 0
                placeables.forEach {
                    it.placeRelative(x, (height - it.height) / 2)
                    x += it.width + step
                }
            }
        }
    }
}

/** What a [MinputBreadcrumbRow] hands its segments. */
data class MinputBreadcrumbStyle(
    val size: MinputSize,
    val join: MinputBreadcrumbJoin,
    val mode: MinputBreadcrumbMode,
)

val LocalMinputBreadcrumbStyle = staticCompositionLocalOf {
    MinputBreadcrumbStyle(MinputBreadcrumbSize, MinputBreadcrumbJoinDefault, MinputBreadcrumbMode.Expanded)
}

/** How much of a segment shows. */
enum class MinputBreadcrumbMode {
    /** Glyph + text, start-aligned, at [minputBreadcrumbExpandedWidth]. */
    Expanded,

    /** The glyph alone, centred, at [minputBreadcrumbIconWidth]. */
    Icon,
}

/** Where a segment sits in its [MinputBreadcrumbRow] — which of its ends are round (the row's
 *  outer ends) and which are joins. */
enum class MinputBreadcrumbPosition(val roundStart: Boolean, val roundEnd: Boolean) {
    Only(roundStart = true, roundEnd = true),
    First(roundStart = true, roundEnd = false),
    Middle(roundStart = false, roundEnd = false),
    Last(roundStart = false, roundEnd = true);

    companion object {
        fun of(index: Int, count: Int): MinputBreadcrumbPosition = when {
            count <= 1 -> Only
            index == 0 -> First
            index == count - 1 -> Last
            else -> Middle
        }
    }
}

/**
 * One crumb, as a button: a leading glyph, then (expanded) an optional overline over a title.
 * Wears surface 1 (it sits on the background or over a game); [selected] puts it on the highlight
 * plane — the anchor-of-an-open-menu marking, as every minput control that summons a menu does.
 *
 * [icon] draws the glyph at the size it is handed (the variant's [MinputSize.iconSize]) — a
 * [MinputIcon], or a launcher bitmap at the same edge, so every crumb's glyph matches.
 *
 * Expanded content is START-aligned at [MinputBreadcrumbContentInset] from the segment's visible
 * edge at mid-height — the pill arc's leftmost point, or a recess's apex — so every crumb's glyph
 * lines up the same way whatever its start is. The title takes what is left of the fixed width
 * and ellipsizes. Icon mode centres the glyph between the joins; the overline + title become the
 * button's content description.
 */
@Composable
fun MinputBreadcrumbSegment(
    position: MinputBreadcrumbPosition,
    title: String,
    onClick: () -> Unit,
    icon: @Composable (MinputIconSize) -> Unit,
    modifier: Modifier = Modifier,
    /** The small-caps label over the title; null = the title alone. */
    overline: String? = null,
    selected: Boolean = false,
    onClickLabel: String? = null,
    container: Color = minputBoxContainer(),
    style: MinputBreadcrumbStyle = LocalMinputBreadcrumbStyle.current,
) {
    val size = style.size
    val join = style.join
    val shape = remember(position, join) { MinputBreadcrumbShape(position, join) }
    val bulgeDepth = join.depth(size.height)
    val recessDepth = join.recessDepth(size.height)
    val fill by animateColorAsState(
        targetValue = if (selected) minputHighlightContainer() else container,
        label = "minputBreadcrumbFill",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        label = "minputBreadcrumbTitle",
    )
    val overlineColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = MinputBreadcrumbOverlineOnSelectedAlpha)
        } else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "minputBreadcrumbOverline",
    )
    val interaction = remember { MutableInteractionSource() }
    // A join's corner, as far as the bevel's light is concerned: a chevron is squared and sheds
    // the top face's highlight almost at once; an arc carries it round in proportion to its
    // curvature, all the way to a pill end's run at full curvature.
    val joinCorner = join.bevelCorner(size.corner)
    val startCorner = if (position.roundStart) size.corner else joinCorner
    val endCorner = if (position.roundEnd) size.corner else joinCorner
    val iconMode = style.mode == MinputBreadcrumbMode.Icon
    Surface(
        shape = shape,
        color = fill,
        contentColor = contentColor,
        border = minputBevelBorder(fill, cornerRadius = startCorner, endCornerRadius = endCorner),
        modifier = modifier
            .width(
                (if (iconMode) minputBreadcrumbIconWidth(size) else minputBreadcrumbExpandedWidth(size, join)) -
                    // A round START reaches the box's edge where a recess would be cut back, so
                    // the same box reads a recess wider — trim it, so every crumb has the same
                    // width at mid-height and the same room inside.
                    (if (position.roundStart) recessDepth else 0.dp),
            )
            .minputInteractiveMotion(interaction)
            .fillMaxHeight()
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = minputIndication(),
                role = Role.DropdownList,
                onClickLabel = onClickLabel,
                onClick = onClick,
            )
            .then(
                if (iconMode) {
                    val description = listOfNotNull(overline, title).joinToString(": ")
                    Modifier.semantics { contentDescription = description }
                } else Modifier,
            ),
    ) {
        // The stroke rule: padding starts inside the bevel ring.
        val inset = Modifier.fillMaxHeight().minputStrokeInset()
        if (iconMode) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = inset.padding(
                    start = if (position.roundStart) 0.dp else recessDepth,
                    end = if (position.roundEnd) 0.dp else bulgeDepth,
                ),
            ) { icon(size.iconSize) }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = inset.padding(
                    start = (if (position.roundStart) 0.dp else recessDepth) +
                        MinputBreadcrumbContentInset,
                    end = (if (position.roundEnd) 0.dp else bulgeDepth) +
                        MinputBreadcrumbContentInset,
                ),
            ) {
                icon(size.iconSize)
                Spacer(Modifier.width(MinputBarIconTextGap))
                Column(
                    verticalArrangement = Arrangement.spacedBy(MinputBarStackGap),
                    modifier = Modifier.weight(1f),
                ) {
                    if (overline != null) {
                        Text(
                            text = overline.uppercase(),
                            style = minputOverlineTextStyle(),
                            color = overlineColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = title,
                        style = minputMiniTextStyle(),
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * A middle segment's width at [size]: the glyph, the gap, [MinputBreadcrumbTitleWidth] of text,
 * an inset each side, plus its bulge and recess. Every segment reads this wide at mid-height — a
 * round-STARTED segment's box is trimmed by the recess it doesn't have (its arc reaches the box
 * edge where a recess would be cut back); a round END spans the box just as a bulge does.
 */
fun minputBreadcrumbExpandedWidth(size: MinputSize, join: MinputBreadcrumbJoin): Dp =
    size.iconSize.dp + MinputBarIconTextGap + MinputBreadcrumbTitleWidth +
        MinputBreadcrumbContentInset * 2 + join.depth(size.height) + join.recessDepth(size.height)

/** Every icon-mode segment's width at [size]: the height, plus air for the joins either side. */
fun minputBreadcrumbIconWidth(size: MinputSize): Dp = size.height + MinputBreadcrumbIconModeExtra

/**
 * How two breadcrumb segments meet: the earlier one's end BULGES toward the later one, whose
 * start carries the matching RECESS. [curvature] (0..1) sets how far the bulge's apex sits beyond
 * its corners — `curvature × height / 2` — for EITHER style: 0 is a straight, squared edge; 1 is
 * as deep as a pill end (exactly a pill end, for an [MinputBreadcrumbJoinStyle.Arc]; a right-angle
 * point, for a [MinputBreadcrumbJoinStyle.Chevron]).
 */
data class MinputBreadcrumbJoin(
    val style: MinputBreadcrumbJoinStyle,
    val curvature: Float,
) {
    init {
        require(curvature in 0f..1f) { "curvature must be in 0..1, was $curvature" }
    }
}

enum class MinputBreadcrumbJoinStyle {
    /** A circular arc through the segment's corners and the apex. */
    Arc,

    /** Two straight edges meeting at the apex. */
    Chevron,
}

/** How far a join's BULGE apex sits beyond its corners, for a segment [height] tall. */
fun MinputBreadcrumbJoin.depth(height: Dp): Dp = height / 2 * curvature

/**
 * How deep the matching RECESS is cut. A chevron's recess is its bulge moved along the edges'
 * normal, so it is the same depth. An arc's is the bulge's circle grown by [gap]: a bigger circle
 * through the same corners, so a shallower cut.
 */
fun MinputBreadcrumbJoin.recessDepth(height: Dp, gap: Dp = MinputSegmentGap): Dp = when (style) {
    MinputBreadcrumbJoinStyle.Chevron -> depth(height)
    MinputBreadcrumbJoinStyle.Arc -> {
        val half = height.value / 2
        val radius = arcRadius(depth(height).value, half) ?: return 0.dp
        val grown = radius + gap.value
        (grown - sqrt(grown * grown - half * half)).dp
    }
}

/**
 * The advance from one segment's box end to the next one's box start: the horizontal shift that
 * puts the recess [gap] off the bulge, square to the edge, less the recess (which is cut into the
 * next box). An arc's concentric recess is shifted by exactly the gap; a chevron's edges lean, so
 * moving them [gap] along their normal is a longer horizontal shift.
 */
fun MinputBreadcrumbJoin.step(height: Dp, gap: Dp = MinputSegmentGap): Dp {
    val shift = when (style) {
        MinputBreadcrumbJoinStyle.Arc -> gap
        MinputBreadcrumbJoinStyle.Chevron -> {
            val lean = depth(height).value / (height.value / 2)
            gap * sqrt(1f + lean * lean)
        }
    }
    return shift - recessDepth(height, gap)
}

/** The corner radius the bevel treats a join as — see [MinputBreadcrumbSegment]. */
private fun MinputBreadcrumbJoin.bevelCorner(pillCorner: Dp): Dp = when (style) {
    MinputBreadcrumbJoinStyle.Chevron -> 0.dp
    MinputBreadcrumbJoinStyle.Arc -> pillCorner * curvature
}

/** Radius of the circle through two corners [half] above and below the apex, with the apex
 *  [depth] beyond them (chord + sagitta). Null for a flat join. */
private fun arcRadius(depth: Float, half: Float): Float? =
    if (depth <= ArcFlatEpsilon) null else (depth * depth + half * half) / (2 * depth)

/** Below this sagitta (in whatever unit the caller works in) a join is drawn as a straight edge. */
private const val ArcFlatEpsilon = 0.01f

/**
 * A breadcrumb segment's outline: a rectangle whose ends are either fully round (the row's outer
 * ends) or [join]s — a BULGE on the end side, the matching RECESS on the start side, so a recess
 * nests the previous segment's bulge. Mirrored under RTL, so the joins always lead in reading
 * order.
 */
class MinputBreadcrumbShape(
    private val position: MinputBreadcrumbPosition,
    private val join: MinputBreadcrumbJoin,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val r = h / 2f
        val heightDp = with(density) { h.toDp() }
        val d = with(density) { join.depth(heightDp).toPx() }
        val dr = with(density) { join.recessDepth(heightDp).toPx() }
        // An arc join: the circle through both corners and the apex — radius from the chord (h)
        // and the sagitta (d). The recess is that circle grown by the gap, apex dr deep.
        val radius = if (join.style == MinputBreadcrumbJoinStyle.Arc) arcRadius(d, r) else null
        val grown = if (radius != null) radius + with(density) { MinputSegmentGap.toPx() } else null
        fun halfSweep(of: Float) = Math.toDegrees(asin((r / of).coerceAtMost(1f)).toDouble()).toFloat()
        val path = Path().apply {
            moveTo(if (position.roundStart) r else 0f, 0f)
            // Top edge, then the END: a pill arc, or the join's bulge.
            when {
                position.roundEnd -> {
                    lineTo(w - r, 0f)
                    arcTo(Rect(w - 2 * r, 0f, w, h), -90f, 180f, false)
                }
                radius != null -> {
                    lineTo(w - d, 0f)
                    // Centre at (w − R, r): from the top corner, clockwise through the apex.
                    val cx = w - radius
                    val sweep = halfSweep(radius)
                    arcTo(Rect(cx - radius, r - radius, cx + radius, r + radius), -sweep, 2 * sweep, false)
                }
                else -> {
                    lineTo(w - d, 0f)
                    lineTo(w, r)
                    lineTo(w - d, h)
                }
            }
            // Bottom edge, then the START: a pill arc, or the join's recess.
            when {
                position.roundStart -> {
                    lineTo(r, h)
                    arcTo(Rect(0f, 0f, 2 * r, h), 90f, 180f, false)
                }
                grown != null -> {
                    lineTo(0f, h)
                    // The grown circle, apex at x = dr: centre at (dr − R', r), run back up
                    // anticlockwise from the bottom corner through the apex.
                    val cx = dr - grown
                    val sweep = halfSweep(grown)
                    arcTo(Rect(cx - grown, r - grown, cx + grown, r + grown), sweep, -2 * sweep, false)
                }
                else -> {
                    lineTo(0f, h)
                    lineTo(dr, r)
                    lineTo(0f, 0f)
                }
            }
            close()
        }
        if (layoutDirection == LayoutDirection.Rtl) {
            path.transform(Matrix().apply { translate(w, 0f); scale(-1f, 1f) })
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is MinputBreadcrumbShape &&
        other.position == position && other.join == join

    override fun hashCode(): Int = 31 * position.hashCode() + join.hashCode()
}

/** The breadcrumb's default control variant: [MinputSize.ExtraLarge] — a two-line overline +
 *  title stack with air above and below it. */
val MinputBreadcrumbSize = MinputSize.ExtraLarge

// ── THE TUNING KNOBS ─────────────────────────────────────────────────────────────────────────

/** The join's style: [MinputBreadcrumbJoinStyle.Arc] or [MinputBreadcrumbJoinStyle.Chevron]. */
val MinputBreadcrumbJoinStyleDefault = MinputBreadcrumbJoinStyle.Chevron

/** How deep the joins are: 0 = straight edge … 1 = as deep as a pill end. Drives both styles. */
const val MinputBreadcrumbJoinCurvatureDefault = 0.4f

/** The join every breadcrumb row wears unless told otherwise — built from the two knobs above. */
val MinputBreadcrumbJoinDefault = MinputBreadcrumbJoin(
    style = MinputBreadcrumbJoinStyleDefault,
    curvature = MinputBreadcrumbJoinCurvatureDefault,
)

// ─────────────────────────────────────────────────────────────────────────────────────────────

/** Air between a segment's visible edge at mid-height and its content (expanded mode). */
val MinputBreadcrumbContentInset = 8.dp

/** The text room every expanded segment gets — titles longer than this ellipsize. */
val MinputBreadcrumbTitleWidth = 88.dp

/** An icon-mode segment's width over its height — air for the joins either side of the glyph. */
val MinputBreadcrumbIconModeExtra = 12.dp

/** The overline on the highlight plane: onPrimary softened, as the drawer rows' secondary
 *  text is (the scheme has no secondary-on-primary role). */
private const val MinputBreadcrumbOverlineOnSelectedAlpha = 0.8f
