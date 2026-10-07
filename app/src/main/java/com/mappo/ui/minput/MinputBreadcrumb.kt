package com.mappo.ui.minput

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.asin
import kotlin.math.sqrt
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.Layout

/**
 * **A breadcrumb row: a pill cut into chevron-joined segments** (Dylan, 2026-10-07) — the
 * hierarchy picker behind the remap editors' bar (application › layout › set › buttons).
 *
 * The silhouette is an M3 button group's — separate buttons with a gap between them, the whole
 * row reading as one pill — but the joins LEAD: every segment except the last bulges toward the
 * next one, and every segment except the first carries the matching recess, so the gap between
 * two segments follows both. The outer ends stay fully round. The point is the hierarchy reading
 * at a glance: each crumb visibly leads into the one after it.
 *
 * The join's shape is a [MinputBreadcrumbJoin]: a pointed [MinputBreadcrumbJoin.Chevron] (the
 * first pass) or a circular [MinputBreadcrumbJoin.Arc] whose curvature runs from straight to a
 * full pill end. The default is [MinputBreadcrumbJoinDefault] — tune it there.
 *
 * **The segments OVERLAP their layout boxes by the recess depth.** A recess is cut out of its
 * segment's box, so boxes placed a plain [MinputSegmentGap] apart leave the gap PLUS the recess
 * between bulge and recess (the first pass shipped exactly that: ~9dp where 4 was asked for). The
 * row instead places each segment so its recess sits [MinputSegmentGap] off the previous bulge —
 * for an arc, the recess is the bulge's circle grown by the gap and the two are CONCENTRIC, so
 * the gap is the same width all the way round the curve, as nested pill ends would be.
 *
 * Place [MinputBreadcrumbSegment]s (each optionally wrapped in a Box with its menu) as the row's
 * direct children, passing each one's [MinputBreadcrumbPosition] (see
 * [MinputBreadcrumbPosition.of]). The row provides [join] to them.
 */
@Composable
fun MinputBreadcrumbRow(
    modifier: Modifier = Modifier,
    size: MinputSize = MinputBreadcrumbSize,
    join: MinputBreadcrumbJoin = MinputBreadcrumbJoinDefault,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMinputBreadcrumbJoin provides join) {
        Layout(content = content, modifier = modifier.height(size.height)) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0)
            val placeables = measurables.map { it.measure(loose) }
            val step = (MinputSegmentGap - join.recessDepth(size.height)).roundToPx()
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

/** The join a [MinputBreadcrumbRow] gives its segments. */
val LocalMinputBreadcrumbJoin = staticCompositionLocalOf<MinputBreadcrumbJoin> { MinputBreadcrumbJoinDefault }

/** Where a segment sits in its [MinputBreadcrumbRow] — which of its ends are round (the row's
 *  outer ends) and which are chevron joins. */
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
 * One crumb: an overline over a title (the bars' identity-stack anatomy), with an optional
 * [leading] glyph, as a button. Wears surface 1 (it sits on the background or over a game);
 * [selected] puts it on the highlight plane — the anchor-of-an-open-menu marking, as every
 * minput control that summons a menu does.
 *
 * Content is held clear of the joins: a recess or bulge end insets by the join's depth plus
 * [MinputBreadcrumbJoinPadding]; a round end takes the variant's padding plus the library's
 * round-end bias ([minputRoundEndBias]), so a segment with one round end and one join end
 * centres optically rather than geometrically.
 */
@Composable
fun MinputBreadcrumbSegment(
    position: MinputBreadcrumbPosition,
    overline: String,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    /** Cap for the title before it ellipsizes — titles are user-typed names. */
    titleMaxWidth: Dp = MinputBreadcrumbTitleMaxWidth,
    onClickLabel: String? = null,
    container: Color = minputBoxContainer(),
    size: MinputSize = MinputBreadcrumbSize,
    join: MinputBreadcrumbJoin = LocalMinputBreadcrumbJoin.current,
) {
    val shape = remember(position, join) { MinputBreadcrumbShape(position, join) }
    val bulgeDepth = join.depth(size.height)
    val recessDepth = join.recessDepth(size.height)
    val fill by animateColorAsState(
        targetValue = if (selected) minputHighlightContainer() else container,
        label = "minputBreadcrumbFill",
    )
    val titleColor by animateColorAsState(
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
    Surface(
        shape = shape,
        color = fill,
        border = minputBevelBorder(fill, cornerRadius = startCorner, endCornerRadius = endCorner),
        modifier = modifier
            .minputInteractiveMotion(interaction)
            .fillMaxHeight()
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = minputIndication(),
                role = Role.DropdownList,
                onClickLabel = onClickLabel,
                onClick = onClick,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                // The stroke rule: padding starts inside the bevel ring.
                .minputStrokeInset()
                .padding(
                    start = if (position.roundStart) {
                        size.contentPadding + minputRoundEndBias(size.corner, size.height)
                    } else recessDepth + MinputBreadcrumbJoinPadding,
                    end = if (position.roundEnd) {
                        size.contentPadding + minputRoundEndBias(size.corner, size.height)
                    } else bulgeDepth + MinputBreadcrumbJoinPadding,
                ),
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(MinputBarIconTextGap))
            }
            Column(verticalArrangement = Arrangement.spacedBy(MinputBarStackGap)) {
                Text(
                    text = overline.uppercase(),
                    style = minputOverlineTextStyle(),
                    color = overlineColor,
                    maxLines = 1,
                )
                Text(
                    text = title,
                    style = minputMiniTextStyle(),
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = titleMaxWidth),
                )
            }
        }
    }
}

/**
 * How two breadcrumb segments meet: the earlier one's end BULGES toward the later one, whose
 * start carries the matching RECESS.
 */
sealed interface MinputBreadcrumbJoin {
    /** A straight-sided point, [depth] deep, apex at mid-height. */
    data class Chevron(val depth: Dp = MinputBreadcrumbChevronDepth) : MinputBreadcrumbJoin

    /**
     * A circular arc through the segment's two corners and a mid-height apex. [curvature] runs
     * 0..1: 0 is a straight, squared edge; 1 is a semicircle — exactly a pill's round end. In
     * between, the apex sits `curvature × height / 2` beyond the corners.
     */
    data class Arc(val curvature: Float) : MinputBreadcrumbJoin {
        init {
            require(curvature in 0f..1f) { "curvature must be in 0..1, was $curvature" }
        }
    }
}

/** How far a join's BULGE apex sits beyond its corners, for a segment [height] tall. */
fun MinputBreadcrumbJoin.depth(height: Dp): Dp = when (this) {
    is MinputBreadcrumbJoin.Chevron -> depth
    is MinputBreadcrumbJoin.Arc -> height / 2 * curvature
}

/**
 * How deep the matching RECESS is cut. A chevron's is its bulge's depth (the two edges run
 * parallel, [gap] apart horizontally). An arc's recess is the bulge's circle grown by [gap], so
 * the gap is uniform round the curve — and a bigger circle through the same corners is shallower.
 */
fun MinputBreadcrumbJoin.recessDepth(height: Dp, gap: Dp = MinputSegmentGap): Dp = when (this) {
    is MinputBreadcrumbJoin.Chevron -> depth
    is MinputBreadcrumbJoin.Arc -> {
        val half = height.value / 2
        val radius = arcRadius(depth(height).value, half) ?: return 0.dp
        val grown = radius + gap.value
        (grown - sqrt(grown * grown - half * half)).dp
    }
}

/** Radius of the circle through two corners [half] above and below the apex, with the apex
 *  [depth] beyond them (chord + sagitta). Null for a flat join. */
private fun arcRadius(depth: Float, half: Float): Float? =
    if (depth <= ArcFlatEpsilon) null else (depth * depth + half * half) / (2 * depth)

/** Below this sagitta (in whatever unit the caller works in) a join is drawn as a straight edge. */
private const val ArcFlatEpsilon = 0.01f

/** The corner radius the bevel treats a join as — see [MinputBreadcrumbSegment]. */
private fun MinputBreadcrumbJoin.bevelCorner(pillCorner: Dp): Dp = when (this) {
    is MinputBreadcrumbJoin.Chevron -> 0.dp
    is MinputBreadcrumbJoin.Arc -> pillCorner * curvature
}

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
        val d = with(density) { join.depth(h.toDp()).toPx() }
        // An arc join: the circle through both corners and the apex — radius from the chord (h)
        // and the sagitta (d). The recess is that circle grown by the gap (see
        // [MinputBreadcrumbRow]), so it is shallower: depth dr, radius rr.
        val radius = if (join is MinputBreadcrumbJoin.Arc) arcRadius(d, r) else null
        val arc = radius != null
        val rr = (radius ?: 0f) + with(density) { MinputSegmentGap.toPx() }
        val dr = if (arc) rr - sqrt(rr * rr - r * r) else d
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
                arc -> {
                    lineTo(0f, h)
                    // The grown circle, apex at x = dr: centre at (dr − rr, r), run back up
                    // anticlockwise from the bottom corner through the apex.
                    val cx = dr - rr
                    val sweep = halfSweep(rr)
                    arcTo(Rect(cx - rr, r - rr, cx + rr, r + rr), sweep, -2 * sweep, false)
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

/** The breadcrumb's control variant: [MinputSize.Large] — the shortest frame that holds a
 *  two-line overline + title stack with air above and below it. */
val MinputBreadcrumbSize = MinputSize.Large

/**
 * **The join every breadcrumb wears unless told otherwise — the tuning knob.** An arc: change
 * the curvature (0 = straight edge … 1 = a full pill end), or swap in
 * `MinputBreadcrumbJoin.Chevron()` for the pointed first pass.
 */
val MinputBreadcrumbJoinDefault: MinputBreadcrumbJoin = MinputBreadcrumbJoin.Arc(curvature = 1f)

/** How far a [MinputBreadcrumbJoin.Chevron] protrudes by default — "slight": enough to read as
 *  an arrow, not a tab. */
val MinputBreadcrumbChevronDepth = 5.dp

/** Air between a join and the segment's content, on top of the join's own depth. */
val MinputBreadcrumbJoinPadding = 6.dp

/** Title cap — four crumbs and the bar's trailing buttons share one row. */
val MinputBreadcrumbTitleMaxWidth = 120.dp

/** The overline on the highlight plane: onPrimary softened, as the drawer rows' secondary
 *  text is (the scheme has no secondary-on-primary role). */
private const val MinputBreadcrumbOverlineOnSelectedAlpha = 0.8f
