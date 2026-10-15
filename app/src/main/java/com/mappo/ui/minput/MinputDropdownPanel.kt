package com.mappo.ui.minput

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * **A dropdown that holds arbitrary content** (2026-10-07) — the minput menu used as a styled
 * container: same fill, corner, elevation and M3 menu motion as [MinputDropdownMenu], but its
 * body is the caller's (a search row over a list, a list of rich rows) rather than one-line
 * options. Born for the remap bar's hierarchy crumbs, whose dropdowns carry the application and
 * layout pickers the layouts drawer used to.
 *
 * **Why a [Popup] and not M3's `DropdownMenu`** — the one place this family steps off the stock
 * menu, and only because the stock menu cannot hold the content: `DropdownMenuContent` measures
 * its column at `IntrinsicSize.Max` inside a `verticalScroll`, and a `LazyColumn` supports
 * neither (intrinsics throw; an unbounded scroll parent gives it infinite height). Everything
 * else is reproduced from M3 rather than reinvented: the popup is focusable (back / outside tap /
 * gamepad B dismiss, d-pad focus is trapped inside it), it opens with the menu's scale-from-the-
 * anchor + fade (120ms in, 75ms out, the M3 values) and wears `MenuDefaults`' container colour
 * and elevation.
 *
 * Placement ([placement]): [MinputMenuPlacement.Below] — below the anchor, start-aligned to it,
 * flipped ABOVE when there is no room below (a bar pinned to the screen's bottom edge); or a SIDE
 * placement (2026-10-14, for a bar pinned down a screen side) — beside the anchor, top-aligned to
 * it, mirrored to the other side when there is no room. Clamped into the window either way.
 * [caret] carves the minput menus' caret into the edge facing the anchor, aimed at its centre
 * from wherever the panel actually LANDED (after flips and clamping). Height is capped at
 * [maxHeight], so a list inside it scrolls — give a lazy list `Modifier.weight(1f, fill = false)`.
 *
 * Place it in a `Box` alongside its anchor, like the other minput menus.
 */
@Composable
fun BoxScope.MinputDropdownPanel(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = MinputDropdownPanelWidth,
    maxHeight: Dp = rememberMinputDropdownPanelMaxHeight(),
    placement: MinputMenuPlacement = MinputMenuPlacement.Below,
    caret: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded
    if (!expandedState.currentState && !expandedState.targetState) return

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    // Where the panel landed — seeded with the requested side, so the caret's inset doesn't
    // change the panel's size once the real placement arrives.
    var placed by remember(placement, layoutDirection) {
        mutableStateOf(MinputPanelPlaced.predicted(placement, layoutDirection))
    }
    val positionProvider = remember(density, placement) {
        MinputPanelPositionProvider(
            placement = placement,
            gapPx = with(density) { MinputDropdownPanelGap.roundToPx() },
            onPlaced = { if (it != placed) placed = it },
        )
    }
    val caretPx = placed.caretCenterPx
    val shape = if (caret) {
        remember(placed.edge, caretPx) {
            MinputCaretMenuShape(
                corner = MinputMenuCorner,
                edge = placed.edge,
                caretCenterPx = caretPx,
                caretHalfWidth = MinputMenuCaretWidth / 2,
                caretDepth = MinputMenuCaretDepth,
            )
        }
    } else RoundedCornerShape(MinputMenuCorner)
    // The caret is carved out of the surface: the body — and the content — sit inset by its depth
    // on that edge. Physical sides: the caret shape's Start / End are its left / right.
    val caretInset = if (!caret) Modifier else Modifier.absolutePadding(
        left = if (placed.edge == MinputCaretEdge.Start) MinputMenuCaretDepth else 0.dp,
        right = if (placed.edge == MinputCaretEdge.End) MinputMenuCaretDepth else 0.dp,
        top = if (placed.edge == MinputCaretEdge.Top) MinputMenuCaretDepth else 0.dp,
        bottom = if (placed.edge == MinputCaretEdge.Bottom) MinputMenuCaretDepth else 0.dp,
    )
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        val transition = rememberTransition(expandedState, label = "minputDropdownPanel")
        val scale by transition.animateFloat(
            transitionSpec = {
                if (targetState) tween(MinputPanelInMillis, easing = LinearOutSlowInEasing)
                // Exit is a pure fade, as M3's: hold the scale until the fade has finished.
                else tween(1, delayMillis = MinputPanelOutMillis - 1)
            },
            label = "minputDropdownPanelScale",
        ) { open -> if (open) 1f else MinputPanelClosedScale }
        val alpha by transition.animateFloat(
            transitionSpec = {
                if (targetState) tween(MinputPanelFadeInMillis) else tween(MinputPanelOutMillis)
            },
            label = "minputDropdownPanelAlpha",
        ) { open -> if (open) 1f else 0f }
        Surface(
            shape = shape,
            color = MenuDefaults.containerColor,
            tonalElevation = MenuDefaults.TonalElevation,
            shadowElevation = MenuDefaults.ShadowElevation,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                this.transformOrigin = placed.transformOrigin
            },
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Column(
                    modifier = Modifier
                        .then(caretInset)
                        .then(modifier)
                        .width(width)
                        .heightIn(max = maxHeight)
                        .padding(MinputDropdownPanelPadding),
                    content = content,
                )
            }
        }
    }
}

/** The default height cap: the window less room for the bar the panel hangs off and a margin,
 *  never taller than [MinputDropdownPanelMaxHeight]. */
@Composable
fun rememberMinputDropdownPanelMaxHeight(): Dp {
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    return if (windowHeight <= 0.dp) MinputDropdownPanelMaxHeight
    else (windowHeight - MinputDropdownPanelWindowMargin).coerceIn(120.dp, MinputDropdownPanelMaxHeight)
}

/** Where a panel landed: the edge its caret sits on (PHYSICAL — [MinputCaretMenuShape]'s Start is
 *  the left), how far along that edge the anchor's centre is, and the transform origin its open
 *  motion grows from. */
private data class MinputPanelPlaced(
    val edge: MinputCaretEdge,
    val caretCenterPx: Float,
    val transformOrigin: TransformOrigin,
) {
    companion object {
        /** Before the first placement: the requested side, aimed nowhere in particular. */
        fun predicted(placement: MinputMenuPlacement, direction: LayoutDirection): MinputPanelPlaced {
            val endIsRight = direction == LayoutDirection.Ltr
            val edge = when (placement) {
                MinputMenuPlacement.Below -> MinputCaretEdge.Top
                // A panel to the anchor's END has its caret on the edge facing back at it.
                MinputMenuPlacement.End -> if (endIsRight) MinputCaretEdge.Start else MinputCaretEdge.End
                MinputMenuPlacement.Start -> if (endIsRight) MinputCaretEdge.End else MinputCaretEdge.Start
            }
            return MinputPanelPlaced(edge, 0f, TransformOrigin.Center)
        }
    }
}

/**
 * [MinputMenuPlacement.Below]: below the anchor, start-aligned; above it when below doesn't fit.
 * A side placement: beside the anchor on the requested side (in reading order), top-aligned to it;
 * the other side when the requested one doesn't fit. Clamped into the window either way. Reports
 * where it landed ([MinputPanelPlaced]) so the caret aims at the anchor and the open motion grows
 * out of it, as M3's menus do.
 */
private class MinputPanelPositionProvider(
    private val placement: MinputMenuPlacement,
    private val gapPx: Int,
    private val onPlaced: (MinputPanelPlaced) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height
        fun clampX(x: Int) = x.coerceIn(0, (windowSize.width - w).coerceAtLeast(0))
        fun clampY(y: Int) = y.coerceIn(0, (windowSize.height - h).coerceAtLeast(0))
        if (placement == MinputMenuPlacement.Below) {
            val wantX = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right - w
            val x = clampX(wantX)
            val below = anchorBounds.bottom + gapPx
            val above = anchorBounds.top - gapPx - h
            val placeBelow = below + h <= windowSize.height || above < 0
            val y = clampY(if (placeBelow) below else above)
            val originX = if (w > 0) {
                val anchorStart = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right
                ((anchorStart - x).toFloat() / w).coerceIn(0f, 1f)
            } else 0f
            onPlaced(
                MinputPanelPlaced(
                    edge = if (placeBelow) MinputCaretEdge.Top else MinputCaretEdge.Bottom,
                    caretCenterPx = (anchorBounds.center.x - x).toFloat(),
                    transformOrigin = TransformOrigin(originX, if (placeBelow) 0f else 1f),
                ),
            )
            return IntOffset(x, y)
        }
        // A side: which PHYSICAL side was asked for, then whether it fits.
        val endIsRight = layoutDirection == LayoutDirection.Ltr
        val wantRight = (placement == MinputMenuPlacement.End) == endIsRight
        val right = anchorBounds.right + gapPx
        val left = anchorBounds.left - gapPx - w
        val fitsRight = right + w <= windowSize.width
        val fitsLeft = left >= 0
        val placeRight = if (wantRight) fitsRight || !fitsLeft else !(fitsLeft || !fitsRight)
        val x = clampX(if (placeRight) right else left)
        val y = clampY(anchorBounds.top)
        val originY = if (h > 0) ((anchorBounds.center.y - y).toFloat() / h).coerceIn(0f, 1f) else 0.5f
        onPlaced(
            MinputPanelPlaced(
                // On the right of the anchor, the caret is on the panel's LEFT edge.
                edge = if (placeRight) MinputCaretEdge.Start else MinputCaretEdge.End,
                caretCenterPx = (anchorBounds.center.y - y).toFloat(),
                transformOrigin = TransformOrigin(if (placeRight) 0f else 1f, originY),
            ),
        )
        return IntOffset(x, y)
    }
}

/** Default panel width — wide enough for a search row over a list of names. */
val MinputDropdownPanelWidth = 220.dp

/** Height ceiling for a panel, whatever the window. */
val MinputDropdownPanelMaxHeight = 360.dp

/** Window height a panel leaves free (the bar it hangs off, plus air) when capping itself. */
private val MinputDropdownPanelWindowMargin = 96.dp

/** Air between the anchor and the panel. */
val MinputDropdownPanelGap = 4.dp

/** Inset between the panel's edge and its content — rows run inside it, so their highlight
 *  keeps the panel's corners clear. */
val MinputDropdownPanelPadding = 6.dp

/** M3's menu motion (DropdownMenu's InTransitionDuration / OutTransitionDuration, the closed
 *  scale, and the 30ms fade-in), reproduced because the panel can't be that composable. */
private const val MinputPanelInMillis = 120
private const val MinputPanelOutMillis = 75
private const val MinputPanelFadeInMillis = 30
private const val MinputPanelClosedScale = 0.8f
