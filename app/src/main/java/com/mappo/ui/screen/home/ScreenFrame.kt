package com.mappo.ui.screen.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.mappo.ui.minput.MinputBarEdgePadding
import com.mappo.ui.minput.MinputButton
import com.mappo.ui.minput.MinputPillTallHeight
import com.mappo.ui.minput.MinputPod
import com.mappo.ui.minput.MinputPodTallHeight
import com.mappo.ui.minput.MinputModalEnterMillis
import com.mappo.ui.minput.MinputModalEnterScale
import com.mappo.ui.minput.MinputModalExitMillis
import com.mappo.ui.screen.softDropShadow
import kotlinx.coroutines.launch

/** Duration of the frame's fade-out exit (MainScreen delays moveTaskToBack by this so the
 *  exit is visible). The frame deliberately rides the minput modal motion spec. */
const val ScreenFrameFadeMillis = MinputModalExitMillis

/** Duration of the resize between the compact 1:1 screen and the full display. */
private const val ExpandMillis = 320

/**
 * The Mappo screen frame: the canvas every route renders into, plus the bottom bar carrying
 * the Mappo button and the screen-size toggle. Replaces the skeuomorphic [HandheldFrame] as the
 * mounted home chrome (2026-08-14 — the handheld shell is TABLED, not deleted: real estate
 * won over form; its code stays for a possible return).
 *
 * Two screen states, toggled by the bottom bar's trailing button: **compact** (default) pins
 * the canvas to a 1:1 aspect — the standing preview of how the UI reads on 1:1 target
 * screens — and **expanded** fills the entire display. The resize is animated.
 *
 * 2026-08-20: the bottom bar's Mappo button opens the global-options drawer ([drawerContent],
 * hosted here inside the canvas so it slides within Mappo's screen); the active-context
 * shortcut it used to carry retired — that context lives in the controls home's top bar now.
 * The rest of the bar is reserved for future contextual button controls.
 *
 * Enter/exit is the quick modal fade (alpha + settle-scale on the same spec as
 * [com.mappo.ui.minput.MinputModal]) instead of the old slide; a tap outside the compact
 * screen still dismisses on MAIN.
 */
@Composable
fun ScreenFrame(
    shown: Boolean,
    // Only the home route treats a tap outside the screen as "leave Mappo".
    dismissEnabled: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    // The wordmark drawer's sheet content; receives the close action for its rows.
    drawerContent: @Composable (closeDrawer: () -> Unit) -> Unit = {},
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
            // The global-options drawer lives INSIDE the canvas: the sheet slides within
            // Mappo's screen and its scrim covers the routes AND the bottom bar.
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val drawerScope = rememberCoroutineScope()
            val closeDrawer: () -> Unit = { drawerScope.launch { drawerState.close() } }
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = { drawerContent(closeDrawer) },
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        screenContent()
                    }
                    FrameBottomBar(
                        expanded = expanded,
                        onToggleExpanded = { expanded = !expanded },
                        onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                    )
                }
            }
        }
    }
}

/**
 * The frame's bottom bar — the Mappo button at the start (opens the global-options drawer),
 * the compact ↔ expanded toggle at the end; the middle is reserved for future contextual
 * button controls. The active-context shortcut that used to sit here retired 2026-08-20 —
 * the controls home's top bar carries that context now.
 *
 * **2026-08-30 (Dylan):** rebuilt on the same anatomy as the remap controls top bar. The
 * strip's `surfaceContainer` fill and its divider are GONE — the bar is transparent, a
 * layout tool rather than a surface, and its contents float as pods over the canvas. That
 * is not cosmetic drift: a [MinputPod] wears a plane BELOW `surfaceContainer`, so a pod on
 * the old filled strip would have read as a dark hole punched in it. The two bars must be
 * changed together for the same reason.
 *
 * The Mappo button rides the TALL pod/button scale — it summons the app-wide drawer and is
 * the one control down here that earns extra presence. The fullscreen toggle is a plain
 * button on the bare bar, deliberately POD-LESS: it is a temporary debug affordance for
 * previewing the UI at 1:1, not permanent chrome, so it should not read as an equal.
 */
@Composable
private fun FrameBottomBar(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenDrawer: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Horizontal inset only, matching the top bar: no vertical air of its own, so
            // the pod sits flush to the canvas edge and all breathing room is the content
            // side's to give.
            .height(MinputPodTallHeight)
            .padding(horizontal = MinputBarEdgePadding),
    ) {
        MinputPod(
            height = MinputPodTallHeight,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            MinputButton(
                text = "Mappo",
                onClick = onOpenDrawer,
                height = MinputPillTallHeight,
                contentDescription = "Open Mappo options",
            )
        }
        MinputButton(
            onClick = onToggleExpanded,
            leadingIcon = rememberVectorPainter(
                if (expanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            ),
            contentDescription = if (expanded) "Compact screen" else "Expand screen",
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}
