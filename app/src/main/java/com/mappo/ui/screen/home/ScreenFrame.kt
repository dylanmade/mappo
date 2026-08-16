package com.mappo.ui.screen.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mappo.ui.component.AppIconImage
import com.mappo.ui.component.rememberAppIconPainter
import com.mappo.ui.minput.MinputBarEdgePadding
import com.mappo.ui.minput.MinputBarHeight
import com.mappo.ui.minput.MinputBarIconTextGap
import com.mappo.ui.minput.MinputBarStackGap
import com.mappo.ui.minput.MinputBarWidgetIconSize
import com.mappo.ui.minput.MinputGlyphLabelGap
import com.mappo.ui.minput.MinputIconButton
import com.mappo.ui.minput.MinputModalEnterMillis
import com.mappo.ui.minput.MinputModalEnterScale
import com.mappo.ui.minput.MinputModalExitMillis
import com.mappo.ui.minput.minputIndication
import com.mappo.ui.minput.minputInteractiveMotion
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.minput.minputOverlineTextStyle
import com.mappo.ui.screen.softDropShadow

/** Duration of the frame's fade-out exit (MainScreen delays moveTaskToBack by this so the
 *  exit is visible). The frame deliberately rides the minput modal motion spec. */
const val ScreenFrameFadeMillis = MinputModalExitMillis

/** Duration of the resize between the compact 1:1 screen and the full display. */
private const val ExpandMillis = 320

/**
 * The Mappo screen frame: the canvas every route renders into, plus the bottom bar carrying
 * the wordmark and the screen-size toggle. Replaces the skeuomorphic [HandheldFrame] as the
 * mounted home chrome (2026-08-14 — the handheld shell is TABLED, not deleted: real estate
 * won over form; its code stays for a possible return).
 *
 * Two screen states, toggled by the bottom bar's trailing button: **compact** (default) pins
 * the canvas to a 1:1 aspect — the standing preview of how the UI reads on 1:1 target
 * screens — and **expanded** fills the entire display. The resize is animated.
 *
 * Enter/exit is the quick modal fade (alpha + settle-scale on the same spec as
 * [com.mappo.ui.minput.MinputModal]) instead of the old slide; a tap outside the compact
 * screen still dismisses on MAIN.
 */
@Composable
fun ScreenFrame(
    shown: Boolean,
    // Only the MAIN route treats a tap outside the screen as "leave Mappo".
    dismissEnabled: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    // ── The bottom bar's active-context shortcut: the detected foreground application and
    // the runtime-active layout; tapping opens that layout's controls view. ──
    activeAppLabel: String? = null,
    activeAppPackage: String? = null,
    activeLayoutLabel: String? = null,
    onOpenActiveLayout: () -> Unit = {},
    screenContent: @Composable () -> Unit,
) {
    // Starts hidden (0f) so the first composition fades the screen into view.
    val fade = remember { Animatable(0f) }
    LaunchedEffect(shown) {
        if (shown) {
            fade.animateTo(1f, tween(MinputModalEnterMillis, easing = LinearOutSlowInEasing))
        } else {
            fade.animateTo(0f, tween(MinputModalExitMillis, easing = FastOutLinearInEasing))
        }
    }

    // Expanded = the screen fills the display; compact = the resting 1:1 preview square.
    var expanded by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val availW = maxWidth
        val availH = maxHeight

        // Invisible tap-catcher (deliberately NOT a dimming scrim): a tap outside the compact
        // screen dismisses the home on MAIN. (The frozen-blur backdrop that used to fill this
        // area died with the drawer-over-the-game concept, 2026-08-16 — the activity is opaque
        // now and paints its own background.)
        if (fade.value > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(dismissEnabled) {
                        detectTapGestures { if (dismissEnabled) onDismissRequest() }
                    },
            )
        }

        // Screen bounds: the 1:1 side in compact, everything the display offers expanded.
        // Both states animate through the same pair so the toggle resizes smoothly.
        val square = minOf(availW, availH)
        val screenW by animateDpAsState(
            targetValue = if (expanded) availW else square,
            animationSpec = tween(ExpandMillis, easing = FastOutSlowInEasing),
            label = "screenWidth",
        )
        val screenH by animateDpAsState(
            targetValue = if (expanded) availH else square,
            animationSpec = tween(ExpandMillis, easing = FastOutSlowInEasing),
            label = "screenHeight",
        )

        // surfaceContainerLowest — the screen canvas every Mappo route renders into.
        Box(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = fade.value
                    val scale = MinputModalEnterScale + (1f - MinputModalEnterScale) * fade.value
                    scaleX = scale
                    scaleY = scale
                }
                .softDropShadow(cornerRadius = 0.dp, blurRadius = 24.dp, offsetY = 6.dp)
                // Consume taps on the screen so they don't reach the scrim's dismiss catcher.
                .pointerInput(Unit) { detectTapGestures { } }
                .size(width = screenW, height = screenH)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    screenContent()
                }
                FrameBottomBar(
                    expanded = expanded,
                    onToggleExpanded = { expanded = !expanded },
                    activeAppLabel = activeAppLabel,
                    activeAppPackage = activeAppPackage,
                    activeLayoutLabel = activeLayoutLabel,
                    onOpenActiveLayout = onOpenActiveLayout,
                )
            }
        }
    }
}

/**
 * The frame's bottom bar — wordmark at the start, the active-context shortcut centered, the
 * compact ↔ expanded toggle at the end — as a standard bar mirroring the remap top bar's
 * anatomy (shared [MinputBarHeight]/[MinputBarEdgePadding], divider on the content side).
 */
@Composable
private fun FrameBottomBar(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    activeAppLabel: String?,
    activeAppPackage: String?,
    activeLayoutLabel: String?,
    onOpenActiveLayout: () -> Unit,
) {
    Column {
        HorizontalDivider()
        // surfaceContainer — app-bar plane, matching the top bar.
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MinputBarHeight)
                    .padding(horizontal = MinputBarEdgePadding),
            ) {
                Text(
                    text = "Mappo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = MinputGlyphLabelGap),
                )
                ActiveContextButton(
                    appLabel = activeAppLabel,
                    appPackage = activeAppPackage,
                    layoutLabel = activeLayoutLabel,
                    onClick = onOpenActiveLayout,
                    modifier = Modifier.align(Alignment.Center),
                )
                MinputIconButton(
                    icon = if (expanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (expanded) "Compact screen" else "Expand screen",
                    onClick = onToggleExpanded,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

/**
 * The active-context shortcut (rehomed from the controls top bar 2026-08-14): the detected
 * profile (application) and the runtime-active layout at a glance — profile block
 * right-aligned toward the centered icon, layout block left-aligned away from it, each
 * under its own overline. Icon size and stack metrics are the shared bar-widget values, so
 * this and the top bar's viewing widget read as siblings. Tapping opens the active
 * layout's controls view.
 *
 * INVISIBLE variant of the pill family: no fill, no bevel — bare content over the bar,
 * keeping only the clip (ripple bound), indication, and press motion. The fixed footprint
 * plus the equal-weight text flanks pin the icon to the exact center regardless of how long
 * either name runs (both truncate with ellipses).
 */
@Composable
private fun ActiveContextButton(
    appLabel: String?,
    appPackage: String?,
    layoutLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .minputInteractiveMotion(interaction)
            .width(ContextButtonWidth)
            .height(ContextButtonHeight)
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = interaction,
                indication = minputIndication(),
                role = Role.Button,
                onClickLabel = "Open active layout",
                onClick = onClick,
            )
            .padding(horizontal = ContextButtonContentPadding),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(MinputBarStackGap),
        ) {
            Text(
                text = "Active profile".uppercase(),
                style = minputOverlineTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = appLabel ?: "None detected",
                style = minputMiniTextStyle(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(MinputBarIconTextGap))
        val icon = rememberAppIconPainter(appPackage)
        if (icon != null) {
            AppIconImage(icon, size = MinputBarWidgetIconSize)
        } else {
            Icon(
                Icons.Filled.Apps,
                contentDescription = null,
                modifier = Modifier.size(MinputBarWidgetIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(MinputBarIconTextGap))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(MinputBarStackGap),
        ) {
            Text(
                text = "Active layout".uppercase(),
                style = minputOverlineTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = layoutLabel ?: "None",
                style = minputMiniTextStyle(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Fixed footprint of the context button — load-bearing for the icon's exact centering. */
private val ContextButtonWidth = 280.dp
private val ContextButtonHeight = 32.dp

/** Content inset inside the context button (wider than a mini pill — it's a bigger surface). */
private val ContextButtonContentPadding = 14.dp
