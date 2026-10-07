package com.mappo.ui.minput

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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

/**
 * **A breadcrumb row: a pill cut into chevron-joined segments** (Dylan, 2026-10-07) — the
 * hierarchy picker behind the remap editors' bar (application › layout › set › buttons).
 *
 * The silhouette is an M3 button group's — separate buttons with a gap between them, the whole
 * row reading as one pill — but the joins are CHEVRONS: every segment except the last ends in a
 * slight point aimed at the next one, and every segment except the first carries the matching
 * notch, so the gap between two segments runs parallel to both. The outer ends stay fully round.
 * The point is the hierarchy reading at a glance: each crumb visibly leads into the one after it.
 *
 * Place [MinputBreadcrumbSegment]s in this row, passing each one's [MinputBreadcrumbPosition]
 * (see [MinputBreadcrumbPosition.of]); the row owns only the spacing and the shared height.
 */
@Composable
fun MinputBreadcrumbRow(
    modifier: Modifier = Modifier,
    size: MinputSize = MinputBreadcrumbSize,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.height(size.height),
        horizontalArrangement = Arrangement.spacedBy(MinputSegmentGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

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
 * Content is held clear of the chevrons: a notch or point end insets by the chevron's depth plus
 * [MinputBreadcrumbChevronPadding]; a round end takes the variant's padding plus the library's
 * round-end bias ([minputRoundEndBias]), so a segment with one round end and one chevron end
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
) {
    val shape = remember(position) { MinputBreadcrumbShape(position, MinputBreadcrumbChevronDepth) }
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
    // A chevron join is a squared corner as far as the bevel's light is concerned: it sheds the
    // top face's highlight almost at once, where a round end carries it round the arc.
    val startCorner = if (position.roundStart) size.corner else 0.dp
    val endCorner = if (position.roundEnd) size.corner else 0.dp
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
                    } else MinputBreadcrumbChevronDepth + MinputBreadcrumbChevronPadding,
                    end = if (position.roundEnd) {
                        size.contentPadding + minputRoundEndBias(size.corner, size.height)
                    } else MinputBreadcrumbChevronDepth + MinputBreadcrumbChevronPadding,
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
 * A breadcrumb segment's outline: a rectangle whose ends are either fully round (the row's outer
 * ends) or chevron joins — a POINT on the end side, the matching NOTCH on the start side, both
 * [chevronDepth] deep and meeting at mid-height, so a notch nests the previous segment's point
 * with a parallel gap. Mirrored under RTL, so the chevrons always lead in reading order.
 */
class MinputBreadcrumbShape(
    private val position: MinputBreadcrumbPosition,
    private val chevronDepth: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val r = h / 2f
        val d = with(density) { chevronDepth.toPx() }
        val path = Path().apply {
            moveTo(if (position.roundStart) r else 0f, 0f)
            // Top edge, then the END: an arc, or a point.
            if (position.roundEnd) {
                lineTo(w - r, 0f)
                arcTo(Rect(w - 2 * r, 0f, w, h), -90f, 180f, false)
            } else {
                lineTo(w - d, 0f)
                lineTo(w, r)
                lineTo(w - d, h)
            }
            // Bottom edge, then the START: an arc, or a notch.
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
        if (layoutDirection == LayoutDirection.Rtl) {
            path.transform(Matrix().apply { translate(w, 0f); scale(-1f, 1f) })
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is MinputBreadcrumbShape &&
        other.position == position && other.chevronDepth == chevronDepth

    override fun hashCode(): Int = 31 * position.hashCode() + chevronDepth.hashCode()
}

/** The breadcrumb's control variant: [MinputSize.Large] — the shortest frame that holds a
 *  two-line overline + title stack with air above and below it. */
val MinputBreadcrumbSize = MinputSize.Large

/** How far a chevron join protrudes — "slight": enough to read as an arrow, not a tab. */
val MinputBreadcrumbChevronDepth = 5.dp

/** Air between a chevron join and the segment's content, on top of the chevron's own depth. */
val MinputBreadcrumbChevronPadding = 6.dp

/** Title cap — four crumbs and the bar's trailing buttons share one row. */
val MinputBreadcrumbTitleMaxWidth = 120.dp

/** The overline on the highlight plane: onPrimary softened, as the drawer rows' secondary
 *  text is (the scheme has no secondary-on-primary role). */
private const val MinputBreadcrumbOverlineOnSelectedAlpha = 0.8f
