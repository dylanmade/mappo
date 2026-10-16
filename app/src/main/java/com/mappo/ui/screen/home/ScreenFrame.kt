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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
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
import com.mappo.ui.minput.MinputBar
import com.mappo.ui.minput.MinputButton
import com.mappo.ui.minput.MinputEdge
import com.mappo.ui.minput.MinputModalEnterMillis
import com.mappo.ui.minput.MinputModalEnterScale
import com.mappo.ui.minput.MinputModalExitMillis
import com.mappo.ui.screen.softDropShadow
import kotlinx.coroutines.launch

/** Duration of the frame's fade-out exit (MainScreen delays moveTaskToBack by this so the
 *  exit is visible). The frame deliberately rides the minput modal motion spec. */
const val ScreenFrameFadeMillis = MinputModalExitMillis

/**
 * **The shape of Mappo's screen canvas**, width over height — 1 in the compact 1:1 preview, the
 * display's own shape expanded. Null outside a [ScreenFrame].
 *
 * The compact screen is a square drawn INSIDE the full display, so the window — and
 * `LocalConfiguration` — never change shape when it is toggled. Anything sized by "is this a 1:1
 * screen" has to ask this instead (the remap stage's pan target does; Dylan, 2026-09-28: "when the
 * screen size is reduced to 1:1, we're still panning to a 3 tile distance"). It is the shape the
 * frame is heading FOR, so it switches once when toggled rather than on every frame of the resize.
 */
val LocalScreenAspect = compositionLocalOf<Float?> { null }

/**
 * Opens the global-options drawer [ScreenFrame] hosts, for a route that summons it from its own
 * chrome (the hierarchy bar's menu → Settings, 2026-10-16) rather than by the edge swipe. A no-op
 * outside a [ScreenFrame].
 */
val LocalOpenAppDrawer = staticCompositionLocalOf<() -> Unit> { {} }

/** Duration of the resize between the compact 1:1 screen and the full display. */
private const val ExpandMillis = 320

/**
 * The Mappo screen frame: the canvas every route renders into. Replaces the skeuomorphic
 * [HandheldFrame] as the mounted home chrome (2026-08-14 — the handheld shell is TABLED, not
 * deleted: real estate won over form; its code stays for a possible return).
 *
 * Two screen states ([expanded]): **fullscreen** (the default since 2026-10-15) fills the entire
 * display, and **compact** pins the canvas to a 1:1 aspect — the standing preview of how the UI
 * reads on 1:1 target screens. The resize is animated.
 *
 * The global-options drawer ([drawerContent]) is hosted here inside the canvas so it slides
 * within Mappo's screen. 2026-10-15: the bottom bar that carried its Mappo button and the
 * screen-size toggle is GONE — it took layout space from the routes (the virtual set editor
 * needs the whole display). The drawer opens by its swipe from the leading edge; the size
 * toggle is one of its rows.
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
    // Fullscreen = the screen fills the display; else the resting 1:1 preview square. Persisted
    // (ScreenSizeSettings), toggled from the drawer since the bottom bar retired.
    expanded: Boolean,
    modifier: Modifier = Modifier,
    // The drawer's sheet content; receives the close action for its rows.
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
            // Mappo's screen and its scrim covers the routes.
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val drawerScope = rememberCoroutineScope()
            val closeDrawer: () -> Unit = { drawerScope.launch { drawerState.close() } }
            // Remembered: a static local's new value recomposes everything under it.
            val openDrawer: () -> Unit = remember(drawerState, drawerScope) {
                { drawerScope.launch { drawerState.open() } }
            }
            val aspect = if (expanded && availH.value > 0f) availW / availH else 1f
            CompositionLocalProvider(LocalScreenAspect provides aspect, LocalOpenAppDrawer provides openDrawer) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = { drawerContent(closeDrawer) },
            ) {
                // No bottom bar any more (2026-10-15): the routes get the whole canvas, and the
                // drawer is reached by its swipe from the leading edge.
                Box(Modifier.fillMaxSize()) {
                    screenContent()
                }
            }
            }
        }
    }
}
