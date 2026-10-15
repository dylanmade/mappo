package com.mappo.ui.screen.virtualset

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mappo.data.model.OverlayElement
import com.mappo.ui.screen.overlay.OverlayElementConfigContent
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Per-button config as a conventional **M3 modal drawer**: a scrim + a [ModalDrawerSheet] docked
 * to the edge OPPOSITE the button (so it stays partly visible), both driven by one open fraction
 * (0 = off-screen, 1 = open) — M3's own drawer model. Dismiss like a real modal drawer: back, tap
 * the scrim, or swipe the sheet away. Edits commit live; [onClosed] fires once the close has played.
 *
 * Hand-rolled around the real sheet because the editor's drawer must dock to EITHER side and live
 * inside [stage]. With "1:1 screen" on that is the square, so the whole drawer (scrim, sheet, docking
 * edge, slide) lives inside it, exactly as on a 1:1 display; taps beyond it dismiss.
 */
@Composable
internal fun VirtualEditorConfigDrawer(
    element: OverlayElement,
    /** The drawer's stage (editor px): the whole editor, or the "1:1 screen" square. */
    stage: Rect,
    onChange: (OverlayElement) -> Unit,
    onDelete: () -> Unit,
    onClosed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val openFraction = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var closing by remember { mutableStateOf(false) }
    // Dock opposite the button (its centre on the left half → drawer on the right).
    val drawerOnStart = remember(element.id) { (element.x + element.width / 2f) >= 0.5f }
    val dir = if (drawerOnStart) -1f else 1f

    // Enter once measured — the width is needed to start fully off-screen.
    LaunchedEffect(widthPx) {
        if (widthPx > 0f && !closing) openFraction.animateTo(1f, tween(DRAWER_ANIM_MS))
    }
    fun requestClose() {
        if (closing) return
        closing = true
        scope.launch {
            openFraction.animateTo(0f, tween(DRAWER_ANIM_MS))
            onClosed()
        }
    }
    BackHandler { requestClose() }

    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        // Outside-the-stage dismiss catcher (the dead area beyond the 1:1 square).
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { requestClose() } })
        Box(
            Modifier
                .offset { IntOffset(stage.left.roundToInt(), stage.top.roundToInt()) }
                .size(with(density) { stage.width.toDp() }, with(density) { stage.height.toDp() })
                .clipToBounds(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = openFraction.value }
                    .background(DrawerDefaults.scrimColor)
                    .pointerInput(Unit) { detectTapGestures { requestClose() } },
            )
            // DrawerDefaults.shape rounds the trailing corners of a start-docked sheet; mirror it
            // for an end-docked one so the rounded corners face inward.
            val sheetShape = if (drawerOnStart) {
                DrawerDefaults.shape
            } else {
                RoundedCornerShape(topStart = DRAWER_CORNER_DP.dp, bottomStart = DRAWER_CORNER_DP.dp)
            }
            ModalDrawerSheet(
                drawerShape = sheetShape,
                // surfaceContainerHigh — the canonical Mappo drawer container.
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .align(if (drawerOnStart) Alignment.CenterStart else Alignment.CenterEnd)
                    .onSizeChanged { widthPx = it.width.toFloat() }
                    .graphicsLayer {
                        // Hidden until measured so the width-unknown first frame doesn't flash open.
                        alpha = if (widthPx == 0f) 0f else 1f
                        translationX = (1f - openFraction.value) * widthPx * dir
                    }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            if (widthPx <= 0f) return@rememberDraggableState
                            val next = (openFraction.value - delta * dir / widthPx).coerceIn(0f, 1f)
                            scope.launch { openFraction.snapTo(next) }
                        },
                        onDragStopped = {
                            // Through the SAME scope as the per-delta snaps so it runs after the
                            // last one and wins the Animatable.
                            scope.launch {
                                if (openFraction.value < 0.5f) {
                                    openFraction.animateTo(0f, tween(DRAWER_ANIM_MS))
                                    if (!closing) { closing = true; onClosed() }
                                } else {
                                    openFraction.animateTo(1f, tween(DRAWER_ANIM_MS))
                                }
                            }
                        },
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 20.dp),
                ) {
                    OverlayElementConfigContent(
                        element = element,
                        onChange = onChange,
                        onDelete = {
                            onDelete()
                            requestClose()
                        },
                        onDone = { requestClose() },
                    )
                }
            }
        }
    }
}

private const val DRAWER_ANIM_MS = 300
// The leading-corner radius of an end-docked sheet (mirrors DrawerDefaults.shape's 16dp).
private const val DRAWER_CORNER_DP = 16
