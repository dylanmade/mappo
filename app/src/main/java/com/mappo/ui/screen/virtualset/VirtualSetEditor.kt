package com.mappo.ui.screen.virtualset

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mappo.data.model.OverlayElement
import com.mappo.service.overlay.element.OverlayEditor
import com.mappo.ui.screen.overlay.OverlayElementVisual
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * **The virtual set editor** (2026-10-15) — the controls screen's second body, shown under the
 * SAME hierarchy bar as the physical editor; switching between them is a crossfade, not a trip
 * to another activity. Buttons are placed free-form on a canvas that IS the screen (the editor
 * is laid out at the DISPLAY's full size and scaled to fit Mappo's screen, so a button reads exactly
 * as the run-mode overlay will draw it).
 *
 * Every piece used to be its own overlay window (`OverlayLiveEditController`, retired): the scrim,
 * one window per button, the resize handles, the menu and each of its fly-outs, the Positioner,
 * the config drawer. While editing, buttons don't need windows — one window per button is a
 * RUN-MODE need (empty space passing through to the game) — so it is one composition now:
 *
 *  - ONE gesture layer over the canvas does all the canvas gestures, hit-testing geometrically:
 *    press a corner handle → resize the selection about the opposite corner; press a button →
 *    tap to select (accumulating; tapping the sole selection opens its config) or drag the
 *    selection, snapping; press empty space → tap to deselect, or drag a marquee.
 *  - Live drags draw from [preview] rects and commit ONCE on release (one undo entry, one batch
 *    write); the preview holds until the write lands, so nothing flashes back to where it was.
 *  - The core menu, its fly-outs, the Positioner and the config drawer sit above.
 *
 * [backdrop] = a frozen shot of the game when the editor was summoned over one; else the plain
 * content plane the physical editor sits on, so the crossfade between the two is just the body.
 */
@Composable
fun VirtualSetEditor(
    backdrop: Bitmap?,
    modifier: Modifier = Modifier,
) {
    VirtualSetEditor(backdrop = backdrop, vm = hiltViewModel(), modifier = modifier)
}

@Composable
internal fun VirtualSetEditor(
    backdrop: Bitmap?,
    vm: VirtualSetEditorViewModel,
    modifier: Modifier = Modifier,
) {
    val elements by vm.elements.collectAsStateWithLifecycle()
    val selected by vm.selectedIds.collectAsStateWithLifecycle()
    val snapOn by vm.overlaySettings.snapEnabled.collectAsStateWithLifecycle()
    val squareOn by vm.overlaySettings.squareEditArea.collectAsStateWithLifecycle()
    val gridOn by vm.showGrid.collectAsStateWithLifecycle()
    val horizontal by vm.menuHorizontal.collectAsStateWithLifecycle()
    val positionerOpen by vm.positionerOpen.collectAsStateWithLifecycle()
    val configId by vm.configElementId.collectAsStateWithLifecycle()
    val menu = remember { CascadeMenuState() }

    // Live geometry of a drag / resize in progress, by element id (stage px).
    val preview = remember { mutableStateMapOf<Long, Rect>() }
    // A committed drag's preview holds until the elements flow carries the write.
    var awaitingCommit by remember { mutableStateOf(false) }
    LaunchedEffect(elements) {
        if (awaitingCommit) {
            preview.clear()
            awaitingCommit = false
        }
    }
    LaunchedEffect(awaitingCommit) {
        if (awaitingCommit) {
            // A write that changed nothing re-emits nothing — don't hold the preview forever.
            delay(COMMIT_FALLBACK_MS)
            preview.clear()
            awaitingCommit = false
        }
    }
    var marquee by remember { mutableStateOf<Rect?>(null) }

    BackHandler(enabled = menu.levels.isNotEmpty()) { menu.dismissAll() }

    // The run-mode overlay hides while the editor is ON SCREEN (its windows would sit over Mappo,
    // and over the buttons being edited) — not merely while it's open: leaving Mappo with the
    // editor open brings the overlay back over the game.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.session.onEditorShown()
                Lifecycle.Event.ON_STOP -> vm.session.onEditorHidden()
                else -> {}
            }
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) vm.session.onEditorShown()
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.session.onEditorHidden()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .testTag(VIRTUAL_EDITOR_TAG)
            // The lowest plane — the same the physical editor's body sits on.
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .onGloballyPositioned { menu.editorCoords = it }
            // Every press, BEFORE its target: close the fly-outs it didn't land in. Observed, not
            // consumed — the press still reaches the button / row / canvas under it.
            .pointerInput(menu) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    menu.onPress(down.position)
                }
            },
    ) {
        val density = LocalDensity.current
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        // THE STAGE: the display, at full size, scaled to fit Mappo's screen (compact 1:1 or
        // expanded, under the frame's bottom bar) and centred — so a button's geometry, label and
        // strokes are exactly what the run-mode overlay draws, just smaller. Pointer input inside
        // the scaled layer reads stage px; the menus, Positioner and drawer stay at full size
        // around it (in editor px).
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        // (An unreported display — 0×0 — falls back to the editor's own box, unscaled.)
        val display = remember(context, configuration, boxW, boxH) {
            displaySizePx(context).takeIf { it.width > 0f && it.height > 0f } ?: Size(boxW, boxH)
        }
        val fit = if (display.width > 0f && display.height > 0f) {
            minOf(boxW / display.width, boxH / display.height)
        } else 1f
        val stageLeft = (boxW - display.width * fit) / 2f
        val stageTop = (boxH - display.height * fit) / 2f
        val canvas = remember(display, density.density, squareOn) {
            EditCanvas(display.width, display.height, density.density, squareOn)
        }
        fun toEditor(r: Rect) = Rect(stageLeft + r.left * fit, stageTop + r.top * fit, stageLeft + r.right * fit, stageTop + r.bottom * fit)
        // Where the menu, Positioner and drawer may go: the whole editor — or with "1:1 screen"
        // on, the square (its serviceability test).
        val chromeBounds = if (squareOn) toEditor(canvas.bounds) else Rect(0f, 0f, boxW, boxH)
        menu.canvasHeight = boxH
        // Handles keep their on-screen size at any stage scale.
        val chromeScale = if (fit > 0f) 1f / fit else 1f

        val currentElements by rememberUpdatedState(elements)
        val currentSelected by rememberUpdatedState(selected)
        val currentSnap by rememberUpdatedState(snapOn)
        val rectOf: (OverlayElement) -> Rect = { el -> preview[el.id] ?: canvas.rectOf(el) }

        // "1:1 screen" on (or already on when the editor opens): pull every button inside.
        LaunchedEffect(squareOn, canvas) {
            if (!squareOn) return@LaunchedEffect
            val b = canvas.bounds
            val rects = currentElements.mapNotNull { el ->
                val r = canvas.rectOf(el)
                val w = minOf(r.width, b.width)
                val h = minOf(r.height, b.height)
                val x = EditCanvas.clamp(r.left, w, b.left, b.right)
                val y = EditCanvas.clamp(r.top, h, b.top, b.bottom)
                val moved = abs(x - r.left) > 0.5f || abs(y - r.top) > 0.5f || abs(w - r.width) > 0.5f || abs(h - r.height) > 0.5f
                if (moved) canvas.toElementRect(el.id, Rect(x, y, x + w, y + h)) else null
            }
            vm.commitRects(rects)
            menu.dismissAll()
        }

        val selectionColor = MaterialTheme.colorScheme.primary
        val onPrimary = MaterialTheme.colorScheme.onPrimary
        val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
        val squareOutline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        Box(
            Modifier
                .align(Alignment.Center)
                .requiredSize(with(density) { display.width.toDp() }, with(density) { display.height.toDp() })
                .graphicsLayer {
                    scaleX = fit
                    scaleY = fit
                }
                .testTag(VIRTUAL_STAGE_TAG)
                // The screen the buttons live on, one plane up from the editor around it.
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            backdrop?.let { bmp ->
                // The game, frozen, dimmed so the edit chrome reads over it.
                Image(
                    bitmap = remember(bmp) { bmp.asImageBitmap() },
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
                // Black is the sanctioned raw colour for scrims.
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = BACKDROP_SCRIM_ALPHA)))
            }

            // The 1:1 mask and the grid guide, behind the buttons.
            Box(Modifier.fillMaxSize().drawBehind { drawCanvasGuides(canvas, squareOn, gridOn, gridColor, squareOutline, chromeScale) })

            // The buttons — the run-mode visual itself (WYSIWYG), plus a selection outline.
            // Geometry is read at layout, so a drag frame relayouts without recomposing.
            elements.sortedBy { it.zIndex }.forEach { el ->
                key(el.id) {
                    Box(
                        Modifier
                            .offset {
                                val r = rectOf(el)
                                IntOffset(r.left.roundToInt(), r.top.roundToInt())
                            }
                            .layout { measurable, _ ->
                                val r = rectOf(el)
                                val w = r.width.roundToInt().coerceAtLeast(1)
                                val h = r.height.roundToInt().coerceAtLeast(1)
                                val p = measurable.measure(Constraints.fixed(w, h))
                                layout(w, h) { p.place(0, 0) }
                            },
                    ) {
                        OverlayElementVisual(element = el, selectionColor = if (el.id in selected) selectionColor else null)
                    }
                }
            }

            // Selection chrome + marquee, drawn (no touch of their own — the gesture layer
            // hit-tests the handles geometrically).
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val box = unionOf(selected.mapNotNull { id -> elements.firstOrNull { it.id == id }?.let(rectOf) })
                    if (box != null) drawSelectionChrome(box, multi = selected.size > 1, selectionColor, onPrimary, chromeScale)
                    marquee?.let { m ->
                        drawRect(selectionColor.copy(alpha = 0.12f), topLeft = m.topLeft, size = m.size)
                        drawRect(selectionColor, topLeft = m.topLeft, size = m.size, style = Stroke(width = 1.5.dp.toPx() * chromeScale))
                    }
                },
            )

            // THE gesture layer.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(canvas, chromeScale) {
                        val handleHalf = HANDLE_TOUCH_DP.dp.toPx() * chromeScale / 2f
                        val slop = viewConfiguration.touchSlop * chromeScale
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val start = down.position
                            val els = currentElements
                            val sel = currentSelected
                            val selRects = sel.mapNotNull { id -> els.firstOrNull { it.id == id }?.let { id to rectOf(it) } }.toMap()
                            val box = unionOf(selRects.values)
                            val corner = box?.let { b ->
                                Corner.entries.firstOrNull { c ->
                                    val p = c.pointOf(b)
                                    abs(p.x - start.x) <= handleHalf && abs(p.y - start.y) <= handleHalf
                                }
                            }
                            val hit = if (corner == null) {
                                els.sortedByDescending { it.zIndex }.firstOrNull { rectOf(it).contains(start) }
                            } else null
                            // What moves with an element drag: the whole selection when it's
                            // part of it, else just it.
                            val groupStart: Map<Long, Rect> = when {
                                corner != null -> selRects
                                hit != null -> {
                                    val ids = if (hit.id in sel) sel else setOf(hit.id)
                                    ids.mapNotNull { id -> els.firstOrNull { it.id == id }?.let { id to rectOf(it) } }.toMap()
                                }
                                else -> emptyMap()
                            }
                            var dragging = false
                            var released = false
                            try {
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.changedToUpIgnoreConsumed()) {
                                        released = true
                                        break
                                    }
                                    val d = change.position - start
                                    if (!dragging && d.getDistance() > slop) dragging = true
                                    if (!dragging) continue
                                    change.consume()
                                    when {
                                        corner != null && box != null ->
                                            preview.putAll(resizeGroup(canvas, corner, box, groupStart, d))
                                        hit != null ->
                                            preview.putAll(moveGroup(canvas, hit.id, groupStart, d, currentSnap, els, rectOf))
                                        else -> marquee = Rect(
                                            minOf(start.x, change.position.x), minOf(start.y, change.position.y),
                                            maxOf(start.x, change.position.x), maxOf(start.y, change.position.y),
                                        )
                                    }
                                }
                            } finally {
                                when {
                                    !dragging -> if (released) {
                                        if (hit != null) vm.tapElement(hit.id)
                                        else if (corner == null) vm.clearSelection()
                                    }
                                    hit != null || corner != null -> {
                                        val rects = groupStart.keys.mapNotNull { id -> preview[id]?.let { canvas.toElementRect(id, it) } }
                                        if (released && rects.isNotEmpty()) {
                                            vm.commitRects(rects)
                                            awaitingCommit = true
                                        } else {
                                            preview.clear()
                                        }
                                    }
                                    else -> {
                                        val m = marquee
                                        if (released && m != null) {
                                            vm.select(els.filter { rectOf(it).overlaps(m) }.mapTo(mutableSetOf()) { it.id })
                                        }
                                        marquee = null
                                    }
                                }
                            }
                        }
                    },
            )
        }

        CoreMenuPanel(menu, chromeBounds, horizontal) {
            rootMenuEntries(vm, onRotate = {
                menu.dismissAll()
                menu.settlePanel(with(density) { MENU_SHADOW_MARGIN.dp.toPx() })
                vm.menuHorizontal.value = !vm.menuHorizontal.value
            })
        }
        MenuFlyouts(menu, chromeBounds)
        if (positionerOpen) Positioner(vm, canvas, chromeBounds)

        configId?.let { id ->
            // A snapshot at open (the drawer's fields hold their own edits); it closes if the
            // button goes away underneath it.
            val element = remember(id) { elements.firstOrNull { it.id == id } }
            if (element == null) {
                LaunchedEffect(id) { vm.configElementId.value = null }
            } else {
                key(id) {
                    VirtualEditorConfigDrawer(
                        element = element,
                        stage = chromeBounds,
                        onChange = { vm.overlayEditor.update(it) },
                        onDelete = { vm.deleteElement(id) },
                        onClosed = { vm.configElementId.value = null },
                    )
                }
            }
        }
    }
}

/**
 * The group [groupStart] dragged by [d]: only the [primaryId] (under the finger) snaps; its
 * resolved delta moves every member, so the group keeps its shape. Members aren't snap targets.
 */
private fun moveGroup(
    canvas: EditCanvas,
    primaryId: Long,
    groupStart: Map<Long, Rect>,
    d: Offset,
    snap: Boolean,
    elements: List<OverlayElement>,
    rectOf: (OverlayElement) -> Rect,
): Map<Long, Rect> {
    val b = canvas.bounds
    val ps = groupStart[primaryId] ?: return emptyMap()
    var px = EditCanvas.clamp(ps.left + d.x, ps.width, b.left, b.right)
    var py = EditCanvas.clamp(ps.top + d.y, ps.height, b.top, b.bottom)
    if (snap) {
        val others = elements.filter { it.id !in groupStart }.map(rectOf)
        val snapped = canvas.snap(px, py, ps.width, ps.height, others)
        px = snapped.x
        py = snapped.y
    }
    val dx = px - ps.left
    val dy = py - ps.top
    return groupStart.mapValues { (_, r) ->
        val nx = EditCanvas.clamp(r.left + dx, r.width, b.left, b.right)
        val ny = EditCanvas.clamp(r.top + dy, r.height, b.top, b.bottom)
        Rect(nx, ny, nx + r.width, ny + r.height)
    }
}

/**
 * A corner drag: derive the new box (min-size + bounds clamped), then scale every member about
 * the FIXED opposite corner, Photoshop-style.
 */
private fun resizeGroup(
    canvas: EditCanvas,
    corner: Corner,
    startBox: Rect,
    startRects: Map<Long, Rect>,
    d: Offset,
): Map<Long, Rect> {
    if (startBox.width <= 0f || startBox.height <= 0f || startRects.isEmpty()) return emptyMap()
    val minW = OverlayEditor.MIN_SIZE * canvas.width
    val minH = OverlayEditor.MIN_SIZE * canvas.height
    // The smallest box that keeps every member at/above MIN_SIZE after scaling.
    val minBoxW = (startBox.width * startRects.values.maxOf { minW / it.width }).coerceIn(1f, startBox.width)
    val minBoxH = (startBox.height * startRects.values.maxOf { minH / it.height }).coerceIn(1f, startBox.height)
    val b = canvas.bounds
    var left = startBox.left; var top = startBox.top; var right = startBox.right; var bottom = startBox.bottom
    val leftMoved = corner == Corner.TopLeft || corner == Corner.BottomLeft
    val topMoved = corner == Corner.TopLeft || corner == Corner.TopRight
    if (leftMoved) left = (startBox.left + d.x).coerceIn(minOf(b.left, right - minBoxW), right - minBoxW)
    else right = (startBox.right + d.x).coerceIn(left + minBoxW, maxOf(b.right, left + minBoxW))
    if (topMoved) top = (startBox.top + d.y).coerceIn(minOf(b.top, bottom - minBoxH), bottom - minBoxH)
    else bottom = (startBox.bottom + d.y).coerceIn(top + minBoxH, maxOf(b.bottom, top + minBoxH))
    val sx = (right - left) / startBox.width
    val sy = (bottom - top) / startBox.height
    val anchor = corner.fixedPointOf(startBox)
    return startRects.mapValues { (_, r) ->
        val nx = anchor.x + (r.left - anchor.x) * sx
        val ny = anchor.y + (r.top - anchor.y) * sy
        Rect(nx, ny, nx + r.width * sx, ny + r.height * sy)
    }
}

/** "1:1 screen" mask + outline, and the grid guide: translucent PLUSES at every lattice corner. */
private fun DrawScope.drawCanvasGuides(
    canvas: EditCanvas,
    squareOn: Boolean,
    gridOn: Boolean,
    gridColor: Color,
    squareOutline: Color,
    // Hairlines + plus arms keep their on-screen size at any stage scale.
    chromeScale: Float,
) {
    val b = canvas.bounds
    if (squareOn) {
        // Black is the sanctioned raw colour for scrims / masks.
        val mask = Color.Black.copy(alpha = 0.55f)
        drawRect(mask, topLeft = Offset.Zero, size = Size(b.left, size.height))
        drawRect(mask, topLeft = Offset(b.right, 0f), size = Size(size.width - b.right, size.height))
        drawRect(mask, topLeft = Offset(b.left, 0f), size = Size(b.width, b.top))
        drawRect(mask, topLeft = Offset(b.left, b.bottom), size = Size(b.width, size.height - b.bottom))
        drawRect(squareOutline, topLeft = b.topLeft, size = b.size, style = Stroke(width = 1.dp.toPx() * chromeScale))
    }
    if (gridOn) {
        val (cellX, cellY) = canvas.gridCell()
        val cols = (b.width / cellX).roundToInt()
        val rows = (b.height / cellY).roundToInt()
        val arm = 4.dp.toPx() * chromeScale
        val line = chromeScale
        for (i in 0..cols) for (j in 0..rows) {
            val px = b.left + i * cellX
            val py = b.top + j * cellY
            drawLine(gridColor, Offset(px - arm, py), Offset(px + arm, py), line)
            drawLine(gridColor, Offset(px, py - arm), Offset(px, py + arm), line)
        }
    }
}

/** The resize chrome: a corner dot on each corner of the selection box (+ a dashed outline of the
 *  box when several are selected). */
private fun DrawScope.drawSelectionChrome(box: Rect, multi: Boolean, primary: Color, onPrimary: Color, scale: Float) {
    if (multi) {
        drawRect(
            primary,
            topLeft = box.topLeft,
            size = box.size,
            style = Stroke(
                width = 1.5.dp.toPx() * scale,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx() * scale, 6.dp.toPx() * scale), 0f),
            ),
        )
    }
    val r = HANDLE_DOT_DP.dp.toPx() * scale / 2f
    Corner.entries.forEach { c ->
        val p = c.pointOf(box)
        drawCircle(primary, radius = r, center = p)
        drawCircle(onPrimary, radius = r, center = p, style = Stroke(width = 1.5.dp.toPx() * scale))
    }
}

/** The four resize corners. */
internal enum class Corner {
    TopLeft, TopRight, BottomLeft, BottomRight;

    /** Where this handle sits on [r]. */
    fun pointOf(r: Rect): Offset = when (this) {
        TopLeft -> r.topLeft
        TopRight -> r.topRight
        BottomLeft -> r.bottomLeft
        BottomRight -> r.bottomRight
    }

    /** The OPPOSITE corner — the anchor a drag from this one scales about. */
    fun fixedPointOf(r: Rect): Offset = when (this) {
        TopLeft -> r.bottomRight
        TopRight -> r.bottomLeft
        BottomLeft -> r.topRight
        BottomRight -> r.topLeft
    }
}

internal const val VIRTUAL_EDITOR_TAG = "virtual-editor"
internal const val VIRTUAL_STAGE_TAG = "virtual-editor:stage"
// Resize handle: the touch area is larger than the visible dot for an easy grab.
private const val HANDLE_TOUCH_DP = 32
private const val HANDLE_DOT_DP = 14
private const val COMMIT_FALLBACK_MS = 600L
// The frozen game under the editor is dimmed like the old edit scrim (~40% black).
private const val BACKDROP_SCRIM_ALPHA = 0.4f
