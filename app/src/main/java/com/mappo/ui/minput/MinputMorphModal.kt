package com.mappo.ui.minput

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mappo.ui.screen.softDropShadow
import kotlin.math.roundToInt

/**
 * Canonical timing/corner values for Mappo's rect-lerp morph family — the group editor,
 * the profile/options panels, and [MinputMorphModal] all speak these same values so the
 * surfaces read as one system. (The remap package aliases these as its internal
 * `ExpandMillis`/`CollapseMillis`/`GroupCorner`.)
 */
const val MinputMorphExpandMillis = 300
const val MinputMorphCollapseMillis = 240
val MinputMorphCorner = 8.dp

/** Default side inset of a morph modal from its host's edges. */
val MinputMorphModalMargin = 24.dp

/**
 * A modal surface that morphs open from a summoning control's bounds — the modal member of
 * the morph family (same rect-lerp + fade treatment as the remap group editor and the
 * profile/options panels), sized as a centered card rather than full screen, with a scrim.
 *
 * Host it in a root-level Box with `Modifier.matchParentSize()` so the scrim covers the
 * summoning surface. [originBounds] is read lazily each frame and must return the summoning
 * control's bounds in the SAME coordinate space as [rootSize] (the host box). Content
 * composes only while the modal is on screen, so `remember`ed form state resets per open —
 * deliberate: each summon is a fresh form.
 *
 * Dismissal: scrim tap, back gesture (the [BackHandler] here composes after the summoning
 * surface's, so it wins while open), or the caller flipping [open] false.
 */
@Composable
fun MinputMorphModal(
    open: Boolean,
    onDismiss: () -> Unit,
    originBounds: () -> Rect?,
    rootSize: IntSize,
    height: Dp,
    modifier: Modifier = Modifier,
    horizontalMargin: Dp = MinputMorphModalMargin,
    testTag: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    // Caller intent (open) vs. what's on screen mid-animation (visible) — the same state
    // pair as the rest of the morph family.
    var visible by remember { mutableStateOf(open) }
    val progress = remember { Animatable(if (open) 1f else 0f) }
    LaunchedEffect(open) {
        if (open) {
            visible = true
            progress.animateTo(1f, tween(MinputMorphExpandMillis, easing = FastOutSlowInEasing))
        } else if (visible) {
            progress.animateTo(0f, tween(MinputMorphCollapseMillis, easing = FastOutSlowInEasing))
            visible = false
        }
    }

    BackHandler(enabled = open) { onDismiss() }

    if (!visible || rootSize == IntSize.Zero) return
    val origin = originBounds() ?: return

    val density = LocalDensity.current
    val marginPx = with(density) { horizontalMargin.toPx() }
    val heightPx = with(density) { height.toPx() }
        .coerceAtMost(rootSize.height - marginPx * 2)
    val target = Rect(
        offset = Offset(marginPx, (rootSize.height - heightPx) / 2f),
        size = Size(rootSize.width - marginPx * 2, heightPx),
    )
    val shape = RoundedCornerShape(MinputMorphCorner)
    val container = minputBoxContainer()
    Box(modifier) {
        // Scrim — fades with the morph; tap dismisses. (Raw scrim color is the sanctioned
        // exception to the no-hardcoded-colors rule; alpha matches the M3 scrim convention.)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Box(
            modifier = Modifier
                // Per-frame rect is read in the LAYOUT phase; fades in the DRAW phase — the
                // no-recompose-per-frame lesson from the group-editor morph.
                .layout { measurable, constraints ->
                    val rect = lerp(origin, target, progress.value)
                    val placeable = measurable.measure(
                        Constraints.fixed(
                            rect.width.roundToInt().coerceAtLeast(1),
                            rect.height.roundToInt().coerceAtLeast(1),
                        ),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
                    }
                }
                .softDropShadow(cornerRadius = MinputMorphCorner)
                .clip(shape)
                .background(container)
                .border(minputBevelBorder(container, MinputMorphCorner), shape)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value }, content = content)
        }
    }
}
