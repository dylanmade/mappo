package com.mappo.ui.minput

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mappo.ui.screen.softDropShadow

/**
 * A menu surface that lives IN the composition rather than in a popup window: the same fill,
 * corner, caret, row metrics and enter/exit motion as [MinputActionMenu] / [MinputDropdownMenu],
 * placed wherever its caller lays it out.
 *
 * **Why it exists.** M3's `DropdownMenu` is a popup — its own focusable window — so while one is
 * open, controller focus lives inside it and nothing behind it can be navigated. The remap basic
 * view's group menu (2026-09-16) must stay open WHILE the user d-pads through the group's
 * commands beside it, and must be reachable from them by an ordinary directional move. That
 * needs a surface in the same focus tree as its neighbors, which only an in-composition one is.
 * Anything that doesn't need that should keep using the popup menus.
 *
 * Consequences callers own: placement (this draws where it's put; pass [caretEdge] and
 * [caretCenter] so the caret points back at the summoning control — [caretCenter] runs along
 * that edge, from the top for Start/End and from the left for Top/Bottom), dismissal, and
 * z-order. Rows are ordinary focusables, so directional focus reaches them; build them from
 * [MinputMenuRow] to match the popup menus exactly.
 *
 * Motion mirrors M3's menu: a quick fade + scale-in from the caret side, a shorter fade out.
 */
@Composable
fun MinputMenuPanel(
    visible: Boolean,
    caretEdge: MinputCaretEdge,
    caretCenter: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val origin = when (caretEdge) {
        MinputCaretEdge.Start -> TransformOrigin(0f, 0.5f)
        MinputCaretEdge.End -> TransformOrigin(1f, 0.5f)
        MinputCaretEdge.Top -> TransformOrigin(0.5f, 0f)
        MinputCaretEdge.Bottom -> TransformOrigin(0.5f, 1f)
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(MinputMenuPanelEnterMillis)) +
            scaleIn(tween(MinputMenuPanelEnterMillis), initialScale = MinputMenuPanelEnterScale, transformOrigin = origin),
        exit = fadeOut(tween(MinputMenuPanelExitMillis)) +
            scaleOut(tween(MinputMenuPanelExitMillis), targetScale = MinputMenuPanelEnterScale, transformOrigin = origin),
    ) {
        val caretPx = with(LocalDensity.current) { caretCenter.toPx() }
        val shape = remember(caretEdge, caretPx) {
            MinputCaretMenuShape(
                corner = MinputMenuCorner,
                edge = caretEdge,
                caretCenterPx = caretPx,
                caretHalfWidth = MinputMenuCaretWidth / 2,
                caretDepth = MinputMenuCaretDepth,
            )
        }
        // The caret is carved out of the surface, so the BODY is inset by the caret depth on
        // that side — the shadow and the content both follow the body, not the full box.
        val bodyInset = Modifier.padding(
            start = if (caretEdge == MinputCaretEdge.Start) MinputMenuCaretDepth else 0.dp,
            end = if (caretEdge == MinputCaretEdge.End) MinputMenuCaretDepth else 0.dp,
            top = if (caretEdge == MinputCaretEdge.Top) MinputMenuCaretDepth else 0.dp,
            bottom = if (caretEdge == MinputCaretEdge.Bottom) MinputMenuCaretDepth else 0.dp,
        )
        Box {
            Box(Modifier.matchParentSize().then(bodyInset).softDropShadow(cornerRadius = MinputMenuCorner))
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Column(
                    modifier = Modifier
                        .clip(shape)
                        .background(MenuDefaults.containerColor, shape)
                        .then(bodyInset)
                        .padding(vertical = MinputMenuFramePadding)
                        // Rows fill the panel, and the panel is as wide as its widest row.
                        .width(IntrinsicSize.Max),
                    content = content,
                )
            }
        }
    }
}

private const val MinputMenuPanelEnterMillis = 120
private const val MinputMenuPanelExitMillis = 75
private const val MinputMenuPanelEnterScale = 0.8f
