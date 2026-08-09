package com.mappo.ui.minput

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mappo.ui.screen.softDropShadow

/** Default inset of a modal card from its host's edges. */
val MinputModalMargin = 24.dp

/** Modal open/close timing: quick, standard, asymmetric (incoming decelerates, outgoing
 *  accelerates — the M3 enter/exit convention). */
const val MinputModalEnterMillis = 180
const val MinputModalExitMillis = 140

/** Where the settle starts: the card fades in while growing from this fraction of its final
 *  size — the subtle "settle" every platform's modals carry (macOS sheets, M3 dialogs). Set
 *  to 1f for a pure fade. */
const val MinputModalEnterScale = 0.96f

/**
 * The minput modal: a raised surface centered over its host, opening with a quick eased
 * fade + subtle settle-scale under a scrim — the standard modal entrance (think a desktop
 * app's preferences window), NOT the morph family's rect-lerp. (This component previously
 * morphed open from its summoning control's bounds; that read awkwardly for small corner
 * buttons becoming large centered surfaces and was dropped 2026-08-08. The group editor
 * keeps the morph — it genuinely lives inside its screen and collapses back into its home
 * box.)
 *
 * Geometry: [height] fixes a centered card (width = host minus [margin] per side); null
 * fills the host minus [margin] on every edge — the full-screen-panel mode.
 *
 * Host it in a root-level Box with `Modifier.matchParentSize()` so the scrim covers the
 * summoning surface. Content composes only while the modal is on screen, so `remember`ed
 * form state resets per open — deliberate: each summon is a fresh form.
 *
 * Focus contract (the controller-focus doctrine, owned here so every modal inherits it):
 * - Directional focus can never leave an open modal (exit trap + focusGroup), and while
 *   [obscured] is true — another surface is stacked ABOVE this one — the modal instead
 *   refuses focus ENTRY and lifts its exit trap so the surface above can take the seat.
 * - Pass [focusSeat], a [FocusRequester] attached to a real BUTTON in the content (its
 *   Close button by convention — never a container), and the modal seats it on open,
 *   re-seats it when a surface above closes, and carries [minputFocusRecovery] for
 *   tap-clears-focus resilience.
 * - Content color is provided ([LocalContentColor] = onSurface) — the modal paints its
 *   plane with a raw background, so it must supply what a Surface would.
 *
 * Dismissal: scrim tap, back gesture (the [BackHandler] here composes after the summoning
 * surface's, so it wins while open; stack later-composed modals above earlier ones), or
 * the caller flipping [open] false.
 */
@Composable
fun MinputModal(
    open: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp? = null,
    margin: Dp = MinputModalMargin,
    focusSeat: FocusRequester? = null,
    obscured: Boolean = false,
    testTag: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    // Caller intent (open) vs. what's on screen mid-animation (visible) — the same state
    // pair as the morph family.
    var visible by remember { mutableStateOf(open) }
    val progress = remember { Animatable(if (open) 1f else 0f) }
    LaunchedEffect(open) {
        if (open) {
            visible = true
            progress.animateTo(1f, tween(MinputModalEnterMillis, easing = LinearOutSlowInEasing))
        } else if (visible) {
            progress.animateTo(0f, tween(MinputModalExitMillis, easing = FastOutLinearInEasing))
            visible = false
        }
    }

    BackHandler(enabled = open) { onDismiss() }

    // Seat controller focus once the content is composed, and RE-seat when a surface
    // stacked above closes (its focused node leaves composition, which would strand focus).
    if (focusSeat != null) {
        LaunchedEffect(visible, open, obscured) {
            if (visible && open && !obscured) {
                runCatching { focusSeat.requestFocus() }
            }
        }
    }

    if (!visible) return

    val shape = RoundedCornerShape(MinputMorphCorner)
    val container = minputBoxContainer()
    Box(
        modifier.then(
            if (focusSeat != null) {
                Modifier.minputFocusRecovery(enabled = open && !obscured, target = focusSeat)
            } else Modifier,
        ),
    ) {
        // Scrim — fades with the card; tap dismisses. (Raw scrim color is the sanctioned
        // exception to the no-hardcoded-colors rule; alpha matches the M3 scrim convention.)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                // The scrim is tappable, never a d-pad stop — without this, its full-host
                // rect sits in every directional search.
                .focusProperties { canFocus = false }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(margin)
                .then(
                    if (height != null) {
                        // Fixed-height centered card; the padded constraints clamp it on
                        // short hosts.
                        Modifier.fillMaxWidth().height(height)
                    } else {
                        Modifier.fillMaxSize()
                    },
                )
                // Whole-card fade + settle in the DRAW phase (layout never moves) — the
                // shadow, chrome, and content all ride one layer.
                .graphicsLayer {
                    alpha = progress.value
                    val scale = MinputModalEnterScale + (1f - MinputModalEnterScale) * progress.value
                    scaleX = scale
                    scaleY = scale
                }
                .softDropShadow(cornerRadius = MinputMorphCorner)
                .clip(shape)
                .background(container)
                .border(minputBevelBorder(container, MinputMorphCorner), shape)
                // Focus containment, both directions, gated on intent: topmost → nothing
                // leaves; obscured → nothing enters (the surface above owns focus). Both
                // lift during the close fade so focus handoffs can land.
                .focusProperties {
                    onEnter = { if (obscured) cancelFocusChange() }
                    onExit = { if (open && !obscured) cancelFocusChange() }
                }
                .focusGroup()
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        ) {
            // The modal paints its plane with a raw background, so it must provide the
            // content color a Surface would — otherwise text/ripples inherit whatever the
            // host (frame chrome, overlay root) left in LocalContentColor.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Box(Modifier.fillMaxSize(), content = content)
            }
        }
    }
}
