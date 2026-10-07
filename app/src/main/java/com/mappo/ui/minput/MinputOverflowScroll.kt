package com.mappo.ui.minput

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import androidx.compose.runtime.CompositionLocalProvider
import com.mappo.ui.component.LocalStickScrollDepth
import com.mappo.ui.component.stickScrollable

/**
 * A scroll container that signals overflow with edge fades and chevrons instead of a scrollbar.
 *
 * Wraps foundation's `horizontalScroll` / `verticalScroll` — the scrolling itself (gestures,
 * flings, focus-driven bring-into-view, [state]) is stock; this owns only the cue. Each edge
 * with content beyond it fades that content out over [MinputOverflowFadeWidth] and shows a
 * chevron pointing that way; both animate in and out as the scroll position reaches or leaves
 * that end. Born on the remap basic view's group boxes (2026-09-16), where it replaced
 * [MinputScrollbar] — a bar under every row table was a lot of chrome for "there's more".
 *
 * The container WRAPS its content up to the incoming max size and scrolls beyond it, exactly
 * like the modifiers it wraps.
 *
 * **The fade erases, it doesn't paint.** It's a DstIn mask over an offscreen layer, so the
 * content fades to transparent over whatever sits behind it — any plane, the bevel, a focus
 * lift — with no container color to pass in or get wrong. The cue strengths are read in the
 * draw phase, so the animation never recomposes the content.
 *
 * [reverseScrolling] as on the foundation modifiers: value 0 shows the FAR end. The cues follow
 * what's actually on screen either way — an edge fades when there is content past THAT edge.
 *
 * The right stick scrolls it when the arbiter picks it out of the scrollers on screen —
 * nearest the cursor, or the only one there is; see [scrollStick] and
 * [com.mappo.ui.component.StickScrollArbiter].
 *
 * [chevronOutset] pushes each chevron outward past the container's edge, into whatever padding
 * surrounds it, so the glyph can sit nearer the visible rim of an enclosing card than the
 * scroller's own bounds allow. The chevron is decorative — no semantics, no touch target; the
 * enclosing control owns focus and clicks.
 *
 * [contentShift] is for the case where something OTHER than the scroller is displacing the
 * content — see [MinputScrollbar], which takes the same thing for the same reason.
 *
 * Horizontal cues are VISUAL left/right; Mappo is LTR-only today.
 */
@Composable
fun MinputOverflowScroll(
    state: ScrollState,
    modifier: Modifier = Modifier,
    orientation: Orientation = Orientation.Horizontal,
    reverseScrolling: Boolean = false,
    chevronOutset: Dp = 0.dp,
    chevronTint: Color = MaterialTheme.colorScheme.onSurface,
    // Whether this container is a candidate for the right stick at all. On by default: a
    // scroller that overflows should answer the stick, and the cue it shows — bar, fade,
    // chevron, none — has nothing to do with it (Dylan, 2026-09-19). Pass false for a container
    // the stick must never move; WHICH candidate wins is the arbiter's call, not the caller's.
    scrollStick: Boolean = true,
    // Modifiers for the SCROLLING node itself, as opposed to [modifier], which frames the
    // container and its cues. Test tags and anything else that has to sit where the scroll
    // modifier is belong here: scroll-to-node and the accessibility scroll actions both read
    // the semantics of the node that owns the scroll, and the container isn't it.
    scrollModifier: Modifier = Modifier,
    contentShift: (() -> Float)? = null,
    content: @Composable () -> Unit,
) {
    // "Leading" = left / top, "trailing" = right / bottom, whichever way the scroller runs.
    // Snapshot-backed reads inside a derivedStateOf, so these track the position without
    // recomposing on every pixel of travel — only when an edge's answer actually changes.
    fun at(): Float = state.value + (contentShift?.invoke() ?: 0f)
    val moreLeading by remember(state, reverseScrolling, contentShift) {
        derivedStateOf { if (reverseScrolling) at() < state.maxValue else at() > 0f }
    }
    val moreTrailing by remember(state, reverseScrolling, contentShift) {
        derivedStateOf { if (reverseScrolling) at() > 0f else at() < state.maxValue }
    }
    val leadingCue by animateFloatAsState(
        targetValue = if (moreLeading) 1f else 0f,
        animationSpec = tween(MinputOverflowCueMillis),
        label = "minput-overflow-leading",
    )
    val trailingCue by animateFloatAsState(
        targetValue = if (moreTrailing) 1f else 0f,
        animationSpec = tween(MinputOverflowCueMillis),
        label = "minput-overflow-trailing",
    )
    val horizontal = orientation == Orientation.Horizontal
    // The right stick is one of Mappo's universal controls: every scroller that can scroll puts
    // itself forward and the arbiter decides. The modifier this hands back is how it finds out
    // whether the cursor is inside — it has to sit on a node enclosing the content.
    val stick = stickScrollable(
        state = state,
        orientation = orientation,
        enabled = scrollStick,
        invert = reverseScrolling,
    )
    val depth = LocalStickScrollDepth.current
    Box(modifier.then(stick)) {
        Box(
            Modifier
                .overflowFade(horizontal, leading = { leadingCue }, trailing = { trailingCue })
                .then(
                    if (horizontal) {
                        Modifier.horizontalScroll(state, reverseScrolling = reverseScrolling)
                    } else {
                        Modifier.verticalScroll(state, reverseScrolling = reverseScrolling)
                    },
                )
                .then(scrollModifier),
        ) {
            // A scroller nested inside this one is nearer the cursor than this one is, and
            // outranks it when both hold focus.
            CompositionLocalProvider(LocalStickScrollDepth provides depth + 1) {
                content()
            }
        }
        OverflowChevron(
            icon = if (horizontal) Lucide.ChevronLeft else Lucide.ChevronUp,
            tint = chevronTint,
            alpha = { leadingCue },
            modifier = Modifier
                .align(if (horizontal) Alignment.CenterStart else Alignment.TopCenter)
                .offset(
                    x = if (horizontal) -chevronOutset else 0.dp,
                    y = if (horizontal) 0.dp else -chevronOutset,
                ),
        )
        OverflowChevron(
            icon = if (horizontal) Lucide.ChevronRight else Lucide.ChevronDown,
            tint = chevronTint,
            alpha = { trailingCue },
            modifier = Modifier
                .align(if (horizontal) Alignment.CenterEnd else Alignment.BottomCenter)
                .offset(
                    x = if (horizontal) chevronOutset else 0.dp,
                    y = if (horizontal) 0.dp else chevronOutset,
                ),
        )
    }
}

/** Mask the content out toward each edge by that edge's cue strength in [0, 1]: 0 leaves it
 *  untouched, 1 fades it fully transparent at the very edge. See [MinputOverflowScroll]. */
private fun Modifier.overflowFade(
    horizontal: Boolean,
    leading: () -> Float,
    trailing: () -> Float,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val extent = if (horizontal) size.width else size.height
        val fade = MinputOverflowFadeWidth.toPx().coerceAtMost(extent / 2)
        val band = if (horizontal) Size(fade, size.height) else Size(size.width, fade)
        fun gradient(from: Color, to: Color, start: Float, end: Float) = if (horizontal) {
            Brush.horizontalGradient(0f to from, 1f to to, startX = start, endX = end)
        } else {
            Brush.verticalGradient(0f to from, 1f to to, startY = start, endY = end)
        }
        val lead = leading()
        if (lead > 0f) {
            drawRect(
                brush = gradient(Color.Black.copy(alpha = 1f - lead), Color.Black, 0f, fade),
                size = band,
                blendMode = BlendMode.DstIn,
            )
        }
        val trail = trailing()
        if (trail > 0f) {
            drawRect(
                brush = gradient(Color.Black, Color.Black.copy(alpha = 1f - trail), extent - fade, extent),
                topLeft = if (horizontal) Offset(extent - fade, 0f) else Offset(0f, extent - fade),
                size = band,
                blendMode = BlendMode.DstIn,
            )
        }
    }

@Composable
private fun OverflowChevron(icon: ImageVector, tint: Color, alpha: () -> Float, modifier: Modifier) {
    MinputIcon(
        icon,
        contentDescription = null,
        size = MinputOverflowChevronSize,
        tint = tint,
        modifier = modifier .graphicsLayer { this.alpha = alpha() },
    )
}

/** How far into the viewport an overflowing edge fades the content out. */
val MinputOverflowFadeWidth = 28.dp

/** The "more this way" chevron over a faded edge. */
val MinputOverflowChevronSize = MinputIconSize.Xxs

/** Fade + chevron in/out as a scroll end is reached or left. */
private const val MinputOverflowCueMillis = 150
