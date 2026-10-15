package com.mappo.ui.minput

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.unit.lerp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * **A breadcrumb row: a pill cut into joined segments** (Dylan, 2026-10-07) — the hierarchy
 * picker behind the remap editors' bar (application › layout › buttons).
 *
 * The silhouette is an M3 button group's — separate buttons with a gap between them, the whole
 * row reading as one pill — but the joins LEAD: every segment except the last bulges toward the
 * next one, and every segment except the first carries the matching recess, so the gap between
 * two segments follows both. The outer ends stay fully round. The point is the hierarchy reading
 * at a glance: each crumb visibly leads into the one after it.
 *
 * Three knobs, all handed down to the segments ([LocalMinputBreadcrumbStyle]):
 *  - [join] — the CHEVRON join's [MinputBreadcrumbJoin.curvature] (0 = a straight edge … 1 = as
 *    deep as a pill end). Chevrons only since 2026-10-14 — the arc style was retired.
 *    App-wide default: [MinputBreadcrumbJoinDefault].
 *  - [size] — the control variant; [MinputSize.ExtraLarge] holds the overline + title stack.
 *  - [mode] — [MinputBreadcrumbMode.Expanded] (glyph + text) or [MinputBreadcrumbMode.Collapsed]
 *    (glyph alone) — the row's default; a segment can override it for itself (2026-10-14), and
 *    animates between the two. Either way every segment in a mode is the SAME width — fixed per
 *    mode and size, never the width of its text, so the row doesn't shuffle while a
 *    focus-preview changes a title.
 *
 * **The segments OVERLAP their layout boxes.** A recess is cut out of its segment's box, so boxes
 * placed a plain [MinputSegmentGap] apart would leave the gap PLUS the recess between bulge and
 * recess (the first pass shipped exactly that). The row places each segment so the recess sits
 * exactly [MinputSegmentGap] off the previous bulge, measured square to the edge: the recess is
 * the bulge's edges moved the gap along their normal.
 *
 * Place [MinputBreadcrumbSegment]s (each optionally in a Box with its menu) as direct children,
 * passing each one's [MinputBreadcrumbPosition] ([MinputBreadcrumbPosition.of]).
 *
 * [orientation] (2026-10-14): [MinputBreadcrumbOrientation.Vertical] stacks the segments top to
 * bottom — the same outline transposed, so each bulge leads DOWN into the next — for a row pinned
 * down a screen side. Vertical segments are always [MinputBreadcrumbMode.Collapsed]: a glyph
 * stack, there being no room across it for text.
 */
@Composable
fun MinputBreadcrumbRow(
    modifier: Modifier = Modifier,
    size: MinputSize = MinputBreadcrumbSize,
    join: MinputBreadcrumbJoin = MinputBreadcrumbJoinDefault,
    mode: MinputBreadcrumbMode = MinputBreadcrumbMode.Expanded,
    orientation: MinputBreadcrumbOrientation = MinputBreadcrumbOrientation.Horizontal,
    content: @Composable () -> Unit,
) {
    val style = MinputBreadcrumbStyle(size = size, join = join, mode = mode, orientation = orientation)
    CompositionLocalProvider(LocalMinputBreadcrumbStyle provides style) {
        if (orientation == MinputBreadcrumbOrientation.Vertical) {
            Layout(content = content, modifier = modifier.width(size.height)) { measurables, constraints ->
                val loose = constraints.copy(minHeight = 0)
                val placeables = measurables.map { it.measure(loose) }
                val step = join.step(size.height).roundToPx()
                val height = placeables.sumOf { it.height } + step * (placeables.size - 1).coerceAtLeast(0)
                val width = placeables.maxOfOrNull { it.width } ?: 0
                layout(width, height.coerceAtLeast(0)) {
                    var y = 0
                    placeables.forEach {
                        it.placeRelative((width - it.width) / 2, y)
                        y += it.height + step
                    }
                }
            }
            return@CompositionLocalProvider
        }
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
    val orientation: MinputBreadcrumbOrientation = MinputBreadcrumbOrientation.Horizontal,
)

/** Which way a [MinputBreadcrumbRow] runs. */
enum class MinputBreadcrumbOrientation {
    /** Start to end — the joins lead in reading order. */
    Horizontal,

    /** Top to bottom — collapsed segments only; the joins lead downward. */
    Vertical,
}

val LocalMinputBreadcrumbStyle = staticCompositionLocalOf {
    MinputBreadcrumbStyle(MinputBreadcrumbSize, MinputBreadcrumbJoinDefault, MinputBreadcrumbMode.Expanded)
}

/** How much of a segment shows. */
enum class MinputBreadcrumbMode {
    /** Glyph + text, start-aligned, at [minputBreadcrumbExpandedWidth]. */
    Expanded,

    /** The glyph alone, centred, at [minputBreadcrumbIconWidth] — functionally an icon button,
     *  still wearing its chevron joins. */
    Collapsed,
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
 * and ellipsizes. Collapsed, the glyph centres between the joins and the overline + title become
 * the button's content description. [mode] defaults to the row's; a change between the two
 * animates the width, the glyph's alignment and the text's fade together.
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
    mode: MinputBreadcrumbMode = style.mode,
) {
    val size = style.size
    val join = style.join
    val vertical = style.orientation == MinputBreadcrumbOrientation.Vertical
    val shape = remember(position, join, vertical) { MinputBreadcrumbShape(position, join, vertical) }
    val joinDepth = join.depth(size.height)
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
    // the top face's highlight almost at once. Vertical, the joins are the top and bottom ends and
    // both sides run straight — the bevel's side fades see a pill's sides either way.
    val startCorner = if (position.roundStart || vertical) size.corner else 0.dp
    val endCorner = if (position.roundEnd || vertical) size.corner else 0.dp
    val collapsed = vertical || mode == MinputBreadcrumbMode.Collapsed
    // 1 = expanded, 0 = collapsed. Width, glyph position and text alpha all ride it, so the
    // crumb folds as one motion; the text leaves the composition only once it has fully faded.
    val expansion by animateFloatAsState(
        targetValue = if (collapsed) 0f else 1f,
        animationSpec = tween(MinputBreadcrumbCollapseMillis, easing = FastOutSlowInEasing),
        label = "minputBreadcrumbExpansion",
    )
    val showText = expansion > 0f
    val endAdjust = minputBreadcrumbEndAdjust(position, join, size.height)
    val expandedWidth = minputBreadcrumbExpandedWidth(size, join) + endAdjust
    val collapsedWidth = minputBreadcrumbIconWidth(size) + endAdjust
    // Collapsed, the glyph sits on the shape's CENTROID, not midway between its end edges: a pill
    // end carries less of the button than a join, so the midpoint reads off-centre on the row's
    // outer crumbs.
    val collapsedGlyphCentre = remember(position, join, size, collapsedWidth) {
        minputBreadcrumbCentroidX(position, join, size.height, collapsedWidth)
    }
    // Where expanded content starts: the inset off the visible edge at mid-height.
    val startEdge = if (position.roundStart) 0.dp else joinDepth
    val endEdge = if (position.roundEnd) 0.dp else joinDepth
    Surface(
        shape = shape,
        color = fill,
        contentColor = contentColor,
        border = minputBevelBorder(fill, cornerRadius = startCorner, endCornerRadius = endCorner),
        modifier = modifier
            .then(
                // A round end gives up more of its box than a join does, so an outer crumb's box
                // is sized to read as long as a middle one (endAdjust). Vertical,
                // that length runs down the stack.
                if (vertical) Modifier.height(collapsedWidth).fillMaxWidth()
                else Modifier.width(lerp(collapsedWidth, expandedWidth, expansion)).fillMaxHeight(),
            )
            .minputInteractiveMotion(interaction)
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = minputIndication(),
                role = Role.DropdownList,
                onClickLabel = onClickLabel,
                onClick = onClick,
            )
            .then(
                if (collapsed) {
                    val description = listOfNotNull(overline, title).joinToString(": ")
                    Modifier.semantics { contentDescription = description }
                } else Modifier,
            ),
    ) {
        // Placed by hand rather than aligned: the glyph slides from its expanded spot to the
        // collapsed centroid along the same curve as the width, and the text trails it at its
        // EXPANDED width (clipped by the shape as the crumb narrows). Alignment-based layout let
        // the fading text fill the row and shove the glyph to the start edge at both ends of the
        // motion. The stroke rule: coordinates are inside the bevel ring.
        Layout(
            content = {
                Box { icon(size.iconSize) }
                if (showText) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(MinputBarStackGap),
                        modifier = Modifier.alpha(expansion),
                    ) {
                        if (overline != null) {
                            Text(
                                text = overline.uppercase(),
                                style = minputOverlineTextStyle(),
                                color = overlineColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                            )
                        }
                        Text(
                            text = title,
                            style = minputMiniTextStyle(),
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                        )
                    }
                }
            },
            modifier = (if (vertical) Modifier.fillMaxSize() else Modifier.fillMaxHeight()).minputStrokeInset(),
        ) { measurables, constraints ->
            val stroke = MinputBoxStroke.toPx()
            if (vertical) {
                // The glyph alone, on the centroid down the stack, centred across it.
                val glyph = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
                val glyphY = collapsedGlyphCentre.toPx() - stroke - glyph.height / 2f
                return@Layout layout(constraints.maxWidth, constraints.maxHeight) {
                    glyph.placeRelative((constraints.maxWidth - glyph.width) / 2, glyphY.roundToInt())
                }
            }
            val inset = MinputBreadcrumbContentInset.toPx()
            val gap = MinputBarIconTextGap.toPx()
            val glyph = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
            val expandedGlyphX = startEdge.toPx() + inset
            val collapsedGlyphX = collapsedGlyphCentre.toPx() - stroke - glyph.width / 2f
            val glyphX = androidx.compose.ui.util.lerp(collapsedGlyphX, expandedGlyphX, expansion)
            // The text's room in the EXPANDED crumb, held through the motion so it never reflows.
            val textRoom = (expandedWidth.toPx() - 2 * stroke - expandedGlyphX - glyph.width - gap -
                endEdge.toPx() - inset).toInt().coerceAtLeast(0)
            val text = measurables.getOrNull(1)
                ?.measure(constraints.copy(minWidth = 0, maxWidth = textRoom, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val h = constraints.maxHeight
                glyph.placeRelative(glyphX.roundToInt(), (h - glyph.height) / 2)
                text?.placeRelative((glyphX + glyph.width + gap).roundToInt(), (h - text.height) / 2)
            }
        }
    }
}

/**
 * A middle segment's width at [size]: the glyph, the gap, [MinputBreadcrumbTitleWidth] of text,
 * an inset each side, plus its bulge and recess. The row's outer segments differ from it by
 * [minputBreadcrumbEndAdjust].
 */
fun minputBreadcrumbExpandedWidth(size: MinputSize, join: MinputBreadcrumbJoin): Dp =
    size.iconSize.dp + MinputBarIconTextGap + MinputBreadcrumbTitleWidth +
        MinputBreadcrumbContentInset * 2 + join.depth(size.height) * 2

/** A collapsed middle segment's width at [size]: the height, plus air for the joins either side.
 *  The row's outer segments differ from it by [minputBreadcrumbEndAdjust]. */
fun minputBreadcrumbIconWidth(size: MinputSize): Dp = size.height + MinputBreadcrumbIconModeExtra

/**
 * What a segment at [position] adds to (or takes off) a middle segment's box so every crumb is the
 * same length (Dylan, 2026-10-14/15: "consistent in length across the modes and pin locations").
 *
 * **Equal PITCH**: the row is one pill divided into equal parts at its gaps. A gap is a chevron
 * band; the eye places the divide at its MIDDLE — half the join depth in from the corners, half
 * the gap's own width across — and an outer crumb's far end at its pill TIP. So a middle crumb's
 * part is its box less the depth plus a whole gap (half each side), and an outer crumb's is its
 * box less half the depth plus half a gap: each round end differs from a join end by half of
 * (gap width − depth), which is exactly half the [step] between boxes. A fixed rule in dp, so it
 * holds for collapsed and expanded crumbs, either orientation, every pin — measured on a render,
 * all three parts come out equal to the pixel.
 *
 * What it replaced, for the record — each read wrong on the device: equal AREA (the first crumb
 * long), equal centre line (the first short), and a 0..1 blend of the two (wrong somewhere at
 * every value: the blend only ever moved the first crumb, while the last read long too).
 */
fun minputBreadcrumbEndAdjust(position: MinputBreadcrumbPosition, join: MinputBreadcrumbJoin, height: Dp): Dp {
    val half = join.step(height) / 2
    return (if (position.roundStart) half else 0.dp) + (if (position.roundEnd) half else 0.dp)
}

/**
 * The x of a segment's area centroid — where a lone glyph reads as centred — in a box [width]
 * wide, LTR (the row mirrors under RTL). Integrated over the outline [MinputBreadcrumbShape]
 * draws, sampled across the height.
 */
fun minputBreadcrumbCentroidX(
    position: MinputBreadcrumbPosition,
    join: MinputBreadcrumbJoin,
    height: Dp,
    width: Dp,
): Dp {
    val h = height.value
    val r = h / 2
    val w = width.value
    val d = join.depth(height).value
    fun bow(dy: Float) = sqrt((r * r - dy * dy).coerceAtLeast(0f))
    var area = 0f
    var moment = 0f
    val samples = CentroidSamples
    for (i in 0 until samples) {
        val dy = kotlin.math.abs((i + 0.5f) / samples * h - r)
        val left = if (position.roundStart) r - bow(dy) else d * (1 - dy / r)
        val right = if (position.roundEnd) w - r + bow(dy) else w - d * dy / r
        area += right - left
        moment += (right * right - left * left) / 2
    }
    return if (area > 0f) (moment / area).dp else (w / 2).dp
}

/** Rows sampled by [minputBreadcrumbCentroidX] — far finer than a pixel matters. */
private const val CentroidSamples = 64

/**
 * How two breadcrumb segments meet: a CHEVRON — the earlier one's end BULGES to a point toward
 * the later one, whose start carries the matching RECESS. [curvature] (0..1) sets how far the
 * apex sits beyond the corners — `curvature × height / 2`: 0 is a straight, squared edge; 1 a
 * right-angle point, as deep as a pill end.
 */
data class MinputBreadcrumbJoin(
    val curvature: Float,
) {
    init {
        require(curvature in 0f..1f) { "curvature must be in 0..1, was $curvature" }
    }
}

/** How far a join's apex sits beyond its corners, for a segment [height] tall — the bulge's
 *  depth and the recess's alike (the recess is the bulge's edges moved along their normal). */
fun MinputBreadcrumbJoin.depth(height: Dp): Dp = height / 2 * curvature

/**
 * The advance from one segment's box end to the next one's box start: the horizontal shift that
 * puts the recess [gap] off the bulge, square to the edges — which lean, so it is longer than the
 * gap — less the recess (which is cut into the next box).
 */
fun MinputBreadcrumbJoin.step(height: Dp, gap: Dp = MinputSegmentGap): Dp {
    val lean = depth(height).value / (height.value / 2)
    return gap * sqrt(1f + lean * lean) - depth(height)
}

/**
 * A breadcrumb segment's outline: a rectangle whose ends are either fully round (the row's outer
 * ends) or chevron [join]s — a BULGE on the end side, the matching RECESS on the start side, so a
 * recess nests the previous segment's bulge. Mirrored under RTL, so the joins always lead in reading
 * order. [vertical]: the same outline TRANSPOSED — drawn along the box's height, the joins on
 * its top (recess) and bottom (bulge) ends; never mirrored.
 */
class MinputBreadcrumbShape(
    private val position: MinputBreadcrumbPosition,
    private val join: MinputBreadcrumbJoin,
    private val vertical: Boolean = false,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // Built in the horizontal frame: the run along w, the thickness h.
        val w = if (vertical) size.height else size.width
        val h = if (vertical) size.width else size.height
        val r = h / 2f
        val d = with(density) { join.depth(h.toDp()).toPx() }
        val path = Path().apply {
            moveTo(if (position.roundStart) r else 0f, 0f)
            // Top edge, then the END: a pill arc, or the chevron's bulge.
            if (position.roundEnd) {
                lineTo(w - r, 0f)
                arcTo(Rect(w - 2 * r, 0f, w, h), -90f, 180f, false)
            } else {
                lineTo(w - d, 0f)
                lineTo(w, r)
                lineTo(w - d, h)
            }
            // Bottom edge, then the START: a pill arc, or the chevron's recess.
            if (position.roundStart) {
                lineTo(r, h)
                arcTo(Rect(0f, 0f, 2 * r, h), 90f, 180f, false)
            } else {
                lineTo(0f, h)
                lineTo(d, r)
                lineTo(0f, 0f)
            }
            close()
        }
        if (vertical) {
            // Transpose: (x, y) → (y, x).
            path.transform(
                Matrix().apply {
                    this[0, 0] = 0f; this[1, 1] = 0f
                    this[0, 1] = 1f; this[1, 0] = 1f
                },
            )
        } else if (layoutDirection == LayoutDirection.Rtl) {
            path.transform(Matrix().apply { translate(w, 0f); scale(-1f, 1f) })
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is MinputBreadcrumbShape &&
        other.position == position && other.join == join && other.vertical == vertical

    override fun hashCode(): Int = (31 * position.hashCode() + join.hashCode()) * 31 + vertical.hashCode()
}

/** The breadcrumb's default control variant: [MinputSize.ExtraLarge] — a two-line overline +
 *  title stack with air above and below it. */
val MinputBreadcrumbSize = MinputSize.ExtraLarge

// ── THE TUNING KNOBS ─────────────────────────────────────────────────────────────────────────

/** How deep the chevron joins are: 0 = straight edge … 1 = a right-angle point. */
const val MinputBreadcrumbJoinCurvatureDefault = 0.4f

/** The join every breadcrumb row wears unless told otherwise. */
val MinputBreadcrumbJoinDefault = MinputBreadcrumbJoin(curvature = MinputBreadcrumbJoinCurvatureDefault)

// ─────────────────────────────────────────────────────────────────────────────────────────────

/** Air between a segment's visible edge at mid-height and its content (expanded mode). */
val MinputBreadcrumbContentInset = 8.dp

/** The text room every expanded segment gets — titles longer than this ellipsize. */
val MinputBreadcrumbTitleWidth = 88.dp

/** A collapsed segment's width over its height — air for the joins either side of the glyph. */
val MinputBreadcrumbIconModeExtra = 12.dp

/** How long a segment takes to fold between expanded and collapsed. */
const val MinputBreadcrumbCollapseMillis = 250

/** The overline on the highlight plane: onPrimary softened, as the drawer rows' secondary
 *  text is (the scheme has no secondary-on-primary role). */
private const val MinputBreadcrumbOverlineOnSelectedAlpha = 0.8f
