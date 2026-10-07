package com.mappo.ui.minput

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
 * Placement: below the anchor, start-aligned to it; flipped ABOVE when there is no room below
 * (a bar pinned to the screen's bottom edge), clamped into the window either way. Height is capped
 * at [maxHeight], so a list inside it scrolls — give a lazy list `Modifier.weight(1f, fill = false)`.
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded
    if (!expandedState.currentState && !expandedState.targetState) return

    val density = LocalDensity.current
    var transformOrigin by remember { mutableStateOf(TransformOrigin.Center) }
    val positionProvider = remember(density) {
        MinputPanelPositionProvider(
            gapPx = with(density) { MinputDropdownPanelGap.roundToPx() },
            onPlaced = { transformOrigin = it },
        )
    }
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
            shape = RoundedCornerShape(MinputMenuCorner),
            color = MenuDefaults.containerColor,
            tonalElevation = MenuDefaults.TonalElevation,
            shadowElevation = MenuDefaults.ShadowElevation,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                this.transformOrigin = transformOrigin
            },
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Column(
                    modifier = modifier
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

/**
 * Below the anchor, start-aligned; above it when below doesn't fit; clamped into the window.
 * Reports the transform origin (the anchor's start, on the edge nearest it) so the open motion
 * grows out of the control that summoned it, as M3's menus do.
 */
private class MinputPanelPositionProvider(
    private val gapPx: Int,
    private val onPlaced: (TransformOrigin) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val wantX = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left
        else anchorBounds.right - popupContentSize.width
        val x = wantX.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gapPx
        val above = anchorBounds.top - gapPx - popupContentSize.height
        val placeBelow = below + popupContentSize.height <= windowSize.height || above < 0
        val y = (if (placeBelow) below else above)
            .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        val originX = if (popupContentSize.width > 0) {
            val anchorStart = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left
            else anchorBounds.right
            ((anchorStart - x).toFloat() / popupContentSize.width).coerceIn(0f, 1f)
        } else 0f
        onPlaced(TransformOrigin(originX, if (placeBelow) 0f else 1f))
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
