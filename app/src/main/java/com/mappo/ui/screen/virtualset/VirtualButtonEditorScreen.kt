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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.mappo.ui.minput.MinputActionMenu
import com.mappo.ui.minput.MinputDraggableMenuFlyouts
import com.mappo.ui.minput.MinputDraggableMenuState
import com.mappo.ui.minput.MinputDraggableMenuWideWidthDp
import com.mappo.ui.minput.MinputMenuPlacement
import com.mappo.ui.minput.minputDraggableMenuHost
import com.mappo.ui.theme.LocalMappoExtraColors
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mappo.data.model.OverlayElement
import com.mappo.service.overlay.element.VirtualButtonEditor
import com.mappo.ui.screen.overlay.OverlayElementVisual
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * **The virtual button editor** (2026-10-15) — the controls screen's second body, shown under the
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
 *  - ONE gesture layer over the canvas does all the canvas gestures, hit-testing geometrically
 *    (2026-11-11 — moving and resizing are no longer what a plain drag does):
 *      · TAP a button → it is selected and its CONTEXT MENU opens beside it (the physical editor's
 *        tile menu, for virtual buttons: Edit, Copy, Paste, Align, Templatize, Move, Resize, Delete);
 *      · HOLD a button → it LIFTS (with the rest of the selection, when it is part of one) and the
 *        drag carries it. The menu's Move does the same without the hold: the selection is lifted,
 *        and the next drag — from anywhere — carries it. BLUE marks where a lifted button came
 *        from, GREEN where it will land (snapping included), as on the physical editor;
 *      · the menu's Resize shows the corner handles; dragging one scales the selection about the
 *        opposite corner. They stay until the selection changes;
 *      · DRAG from anywhere else — empty space, or a button not yet held long enough — draws a
 *        marquee; releasing it selects what it covers and opens their context menu;
 *      · TAP empty space → deselect.
 *  - Live drags draw from [preview] rects and commit ONCE on release (one undo entry, one batch
 *    write); the preview holds until the write lands, so nothing flashes back to where it was.
 *  - Above the canvas: the context menu (a minput popup menu), Edit's fly-outs (the minput
 *    draggable menu's, hosted here without its core panel), the Positioner, the config drawer.
 *    The canvas-wide verbs live with the hierarchy bar: New and Undo | Redo on the toolbar row
 *    under it ([VirtualButtonEditorToolbar]), Options and Layers in its menu
 *    ([virtualButtonEditorBarActions]).
 *
 * [backdrop] = a frozen shot of the game when the editor was summoned over one; else the plain
 * content plane the physical editor sits on, so the crossfade between the two is just the body.
 */
@Composable
fun VirtualButtonEditorScreen(
    backdrop: Bitmap?,
    modifier: Modifier = Modifier,
) {
    VirtualButtonEditorScreen(backdrop = backdrop, vm = hiltViewModel(), modifier = modifier)
}

@Composable
internal fun VirtualButtonEditorScreen(
    backdrop: Bitmap?,
    vm: VirtualButtonEditorViewModel,
    modifier: Modifier = Modifier,
) {
    val elements by vm.elements.collectAsStateWithLifecycle()
    val selected by vm.selectedIds.collectAsStateWithLifecycle()
    val snapOn by vm.overlaySettings.snapEnabled.collectAsStateWithLifecycle()
    val squareOn by vm.overlaySettings.squareEditArea.collectAsStateWithLifecycle()
    val gridOn by vm.showGrid.collectAsStateWithLifecycle()
    val positionerOpen by vm.positionerOpen.collectAsStateWithLifecycle()
    val configId by vm.configElementId.collectAsStateWithLifecycle()
    // Edit's fly-outs (Assign / Customize) — the draggable menu's levels, with no core panel.
    val flyouts = remember { MinputDraggableMenuState() }
    // The context menu of the selection, hung off [menuAnchor] (the selection's box, editor px,
    // as it was when the menu opened — it must outlive a selection its own Delete just emptied).
    var menuOpen by remember { mutableStateOf(false) }
    var menuAnchor by remember { mutableStateOf(Rect.Zero) }
    // The menu's Resize: the corner handles show (and take drags) only while this is on.
    var resizeMode by remember { mutableStateOf(false) }
    // The menu's Move: the selection is lifted, and the next drag — from anywhere — carries it.
    var moveArmed by remember { mutableStateOf(false) }
    // A move in flight: where each carried button came from, and where it would land.
    val moveOrigin = remember { mutableStateMapOf<Long, Rect>() }
    val moveLanding = remember { mutableStateMapOf<Long, Rect>() }
    // Both modes belong to the selection they were asked for.
    LaunchedEffect(selected) {
        resizeMode = false
        moveArmed = false
        if (selected.isEmpty()) menuOpen = false
    }

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

    BackHandler(enabled = flyouts.levels.isNotEmpty() || moveArmed || resizeMode) {
        when {
            flyouts.levels.isNotEmpty() -> flyouts.dismissAll()
            moveArmed -> moveArmed = false
            else -> resizeMode = false
        }
    }

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
            // Every press, BEFORE its target, closes the Edit fly-outs it didn't land in.
            .minputDraggableMenuHost(flyouts),
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
        // Handles keep their on-screen size at any stage scale.
        val chromeScale = if (fit > 0f) 1f / fit else 1f

        val currentElements by rememberUpdatedState(elements)
        val currentSelected by rememberUpdatedState(selected)
        val currentSnap by rememberUpdatedState(snapOn)
        val currentResize by rememberUpdatedState(resizeMode)
        val currentMoveArmed by rememberUpdatedState(moveArmed)
        val haptic = LocalHapticFeedback.current
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
            flyouts.dismissAll()
        }

        // Open the context menu on [ids] — beside their box as it sits now.
        val openMenu by rememberUpdatedState<(Set<Long>) -> Unit> { ids ->
            unionOf(ids.mapNotNull { id -> currentElements.firstOrNull { it.id == id }?.let(rectOf) })?.let { box ->
                flyouts.dismissAll()
                menuAnchor = toEditor(box)
                menuOpen = true
            }
        }
        // What is lifted, and from where: a move in flight, or a selection the menu's Move armed.
        val lifted: Map<Long, Rect> = when {
            moveOrigin.isNotEmpty() -> moveOrigin
            moveArmed -> elements.filter { it.id in selected }.associate { it.id to canvas.rectOf(it) }
            else -> emptyMap()
        }
        val extras = LocalMappoExtraColors.current

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

            // A move's two ends, under the buttons: a BLUE copy of each lifted button where it came
            // from, a GREEN one where it will land (once that is somewhere else) — the physical
            // editor's origin / destination colours. The carried button itself draws translucent
            // (below), so the end it is standing on shows through it.
            lifted.forEach { (id, origin) ->
                val el = elements.firstOrNull { it.id == id } ?: return@forEach
                key("origin", id) { MoveGhost(el, { origin }, extras.dropZoneOrigin) }
                if (moveLanding[id]?.let { it != origin } == true) {
                    key("landing", id) { MoveGhost(el, { moveLanding[id] ?: origin }, extras.dropZoneValid) }
                }
            }

            // The buttons — the run-mode visual itself (WYSIWYG), plus a selection outline.
            // Geometry is read at layout, so a drag frame relayouts without recomposing.
            elements.sortedBy { it.zIndex }.forEach { el ->
                key(el.id) {
                    val carried = el.id in lifted
                    Box(
                        Modifier
                            .graphicsLayer { alpha = if (carried) CARRIED_ALPHA else 1f }
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
                    if (box != null) drawSelectionChrome(box, multi = selected.size > 1, handles = resizeMode, selectionColor, onPrimary, chromeScale)
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
                            fun rectsOf(ids: Set<Long>): Map<Long, Rect> =
                                ids.mapNotNull { id -> els.firstOrNull { it.id == id }?.let { id to rectOf(it) } }.toMap()
                            val selRects = rectsOf(sel)
                            val box = unionOf(selRects.values)
                            // Handles exist only in resize mode.
                            val corner = if (currentResize && box != null) {
                                Corner.entries.firstOrNull { c ->
                                    val p = c.pointOf(box)
                                    abs(p.x - start.x) <= handleHalf && abs(p.y - start.y) <= handleHalf
                                }
                            } else null
                            val hit = if (corner == null) {
                                els.sortedByDescending { it.zIndex }.firstOrNull { rectOf(it).contains(start) }
                            } else null
                            // The menu's Move armed the selection: this press carries it, wherever it lands.
                            val armed = currentMoveArmed && selRects.isNotEmpty()

                            var press = when {
                                armed -> Press.Move
                                corner != null -> Press.Resize
                                hit != null -> Press.Undecided
                                else -> Press.Marquee
                            }
                            var group: Map<Long, Rect> = if (press == Press.Move || press == Press.Resize) selRects else emptyMap()
                            // The member that snaps (the rest keep their offsets from it).
                            var primaryId = hit?.id?.takeIf { it in group } ?: group.keys.firstOrNull()
                            var dragging = false

                            if (press == Press.Undecided && hit != null) {
                                // A press on a button is decided by what happens first: up → a
                                // tap; travel → a marquee, started on the button; neither, for the
                                // long-press time → the button lifts.
                                when (awaitPressOutcome(down.id, start, slop)) {
                                    PressOutcome.Tap -> {
                                        val ids = if (hit.id in sel) sel else setOf(hit.id)
                                        vm.tapElement(hit.id)
                                        openMenu(ids)
                                        return@awaitEachGesture
                                    }
                                    PressOutcome.Cancelled -> return@awaitEachGesture
                                    PressOutcome.Travelled -> {
                                        press = Press.Marquee
                                        dragging = true
                                    }
                                    PressOutcome.Held -> {
                                        // The whole selection when it's part of it, else just it.
                                        val ids = if (hit.id in sel) sel else setOf(hit.id)
                                        if (hit.id !in sel) vm.select(ids)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        press = Press.Move
                                        group = rectsOf(ids)
                                        primaryId = hit.id
                                    }
                                }
                            }
                            if (press == Press.Move) {
                                moveLanding.clear()
                                moveOrigin.clear()
                                moveOrigin.putAll(group)
                            }

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
                                    when (press) {
                                        Press.Resize -> if (corner != null && box != null) {
                                            preview.putAll(resizeGroup(canvas, corner, box, group, d))
                                        }
                                        Press.Move -> primaryId?.let { primary ->
                                            val moved = moveGroup(canvas, primary, group, d, currentSnap, els, rectOf)
                                            preview.putAll(moved.carried)
                                            moveLanding.putAll(moved.landing)
                                        }
                                        else -> marquee = Rect(
                                            minOf(start.x, change.position.x), minOf(start.y, change.position.y),
                                            maxOf(start.x, change.position.x), maxOf(start.y, change.position.y),
                                        )
                                    }
                                }
                            } finally {
                                when (press) {
                                    Press.Move -> {
                                        // Dropped: the buttons settle on their landing spots.
                                        val landed = if (released && dragging) {
                                            group.keys.mapNotNull { id -> moveLanding[id]?.takeIf { it != group[id] }?.let { id to it } }.toMap()
                                        } else emptyMap()
                                        if (landed.isNotEmpty()) {
                                            preview.putAll(landed)
                                            // Members that landed where they started hold no preview.
                                            group.keys.filter { it !in landed }.forEach { preview.remove(it) }
                                            vm.commitRects(landed.map { (id, r) -> canvas.toElementRect(id, r) })
                                            awaitingCommit = true
                                        } else {
                                            preview.clear()
                                        }
                                        moveOrigin.clear()
                                        moveLanding.clear()
                                        // A drop ends the menu's Move — and so does a tap (a cancel).
                                        moveArmed = false
                                    }
                                    Press.Resize -> {
                                        val rects = group.keys.mapNotNull { id -> preview[id]?.let { canvas.toElementRect(id, it) } }
                                        if (released && dragging && rects.isNotEmpty()) {
                                            vm.commitRects(rects)
                                            awaitingCommit = true
                                        } else {
                                            preview.clear()
                                        }
                                    }
                                    else -> if (!dragging) {
                                        if (released) {
                                            vm.clearSelection()
                                            resizeMode = false
                                        }
                                    } else {
                                        val m = marquee
                                        if (released && m != null) {
                                            // Select what the box covers — and open their menu.
                                            val ids = els.filter { rectOf(it).overlaps(m) }.mapTo(mutableSetOf()) { it.id }
                                            vm.select(ids)
                                            if (ids.isNotEmpty()) openMenu(ids)
                                        }
                                        marquee = null
                                    }
                                }
                            }
                        }
                    },
            )
        }

        // The selection's context menu, hung off the selection's box (as the tile menus hang off
        // their tile): beside it, mirrored to the other side when there's no room.
        Box(
            Modifier
                .offset { IntOffset(menuAnchor.left.roundToInt(), menuAnchor.top.roundToInt()) }
                .size(with(density) { menuAnchor.width.toDp() }, with(density) { menuAnchor.height.toDp() }),
        ) {
            MinputActionMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                actions = virtualButtonContextActions(
                    vm = vm,
                    onEdit = { which, id ->
                        when (which) {
                            EditFlyout.Assign ->
                                flyouts.openAt("edit:assign", menuAnchor, MinputDraggableMenuWideWidthDp) { assignEntries(vm, id) }
                            EditFlyout.Customize ->
                                flyouts.openAt("edit:customize", menuAnchor) { customizeEntries(vm, id) }
                        }
                    },
                    onMove = { moveArmed = true },
                    onResize = { resizeMode = true },
                ),
                placement = MinputMenuPlacement.End,
                caret = true,
            )
        }
        MinputDraggableMenuFlyouts(flyouts, chromeBounds)
        if (positionerOpen) Positioner(vm, canvas, chromeBounds)

        configId?.let { id ->
            // A snapshot at open (the drawer's fields hold their own edits); it closes if the
            // button goes away underneath it.
            val element = remember(id) { elements.firstOrNull { it.id == id } }
            if (element == null) {
                LaunchedEffect(id) { vm.configElementId.value = null }
            } else {
                key(id) {
                    VirtualButtonEditorConfigDrawer(
                        element = element,
                        stage = chromeBounds,
                        onChange = { vm.virtualButtonEditor.update(it) },
                        onDelete = { vm.deleteElement(id) },
                        onClosed = { vm.configElementId.value = null },
                    )
                }
            }
        }
    }
}

/** A move's two readings of the group: where each button is CARRIED (under the finger, bounds
 *  clamped but unsnapped) and where it would LAND if dropped now (snapping applied). */
private class MovedGroup(val carried: Map<Long, Rect>, val landing: Map<Long, Rect>)

/**
 * The group [groupStart] dragged by [d]: only the [primaryId] snaps; its resolved delta moves
 * every member, so the group keeps its shape. Members aren't snap targets.
 */
private fun moveGroup(
    canvas: EditCanvas,
    primaryId: Long,
    groupStart: Map<Long, Rect>,
    d: Offset,
    snap: Boolean,
    elements: List<OverlayElement>,
    rectOf: (OverlayElement) -> Rect,
): MovedGroup {
    val b = canvas.bounds
    val ps = groupStart[primaryId] ?: return MovedGroup(emptyMap(), emptyMap())
    val px = EditCanvas.clamp(ps.left + d.x, ps.width, b.left, b.right)
    val py = EditCanvas.clamp(ps.top + d.y, ps.height, b.top, b.bottom)
    fun shifted(dx: Float, dy: Float) = groupStart.mapValues { (_, r) ->
        val nx = EditCanvas.clamp(r.left + dx, r.width, b.left, b.right)
        val ny = EditCanvas.clamp(r.top + dy, r.height, b.top, b.bottom)
        Rect(nx, ny, nx + r.width, ny + r.height)
    }
    val carried = shifted(px - ps.left, py - ps.top)
    if (!snap) return MovedGroup(carried, carried)
    val others = elements.filter { it.id !in groupStart }.map(rectOf)
    val snapped = canvas.snap(px, py, ps.width, ps.height, others)
    return MovedGroup(carried, shifted(snapped.x - ps.left, snapped.y - ps.top))
}

/** How a press that began on a button resolved (see [awaitPressOutcome]). */
private enum class PressOutcome { Tap, Travelled, Held, Cancelled }

/** What a press on the canvas is doing. */
private enum class Press { Undecided, Move, Resize, Marquee }

/**
 * Wait out a press on a button: [PressOutcome.Tap] if it lifts first, [PressOutcome.Travelled] if
 * it moves past [slop] first, [PressOutcome.Held] once it has stayed put for the long-press time.
 */
private suspend fun AwaitPointerEventScope.awaitPressOutcome(pointer: PointerId, start: Offset, slop: Float): PressOutcome =
    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        var outcome: PressOutcome? = null
        while (outcome == null) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == pointer }
            outcome = when {
                change == null -> PressOutcome.Cancelled
                change.changedToUpIgnoreConsumed() -> PressOutcome.Tap
                (change.position - start).getDistance() > slop -> PressOutcome.Travelled
                else -> null
            }
        }
        outcome
    } ?: PressOutcome.Held

/**
 * One end of a move: a copy of [element] at [rect], washed in [color] through its own silhouette
 * (the wash is drawn SrcAtop on the copy's layer, so it takes the button's shape, whatever that is).
 */
@Composable
private fun MoveGhost(element: OverlayElement, rect: () -> Rect, color: Color) {
    Box(
        Modifier
            .offset {
                val r = rect()
                IntOffset(r.left.roundToInt(), r.top.roundToInt())
            }
            .layout { measurable, _ ->
                val r = rect()
                val w = r.width.roundToInt().coerceAtLeast(1)
                val h = r.height.roundToInt().coerceAtLeast(1)
                val p = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { p.place(0, 0) }
            }
            .graphicsLayer {
                alpha = GHOST_ALPHA
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(color.copy(alpha = GHOST_WASH_ALPHA), blendMode = BlendMode.SrcAtop)
            },
    ) {
        OverlayElementVisual(element = element)
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
    val minW = VirtualButtonEditor.MIN_SIZE * canvas.width
    val minH = VirtualButtonEditor.MIN_SIZE * canvas.height
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

/** The selection chrome: a dashed outline of the box when several are selected, and — in resize
 *  mode ([handles]) — a corner dot on each corner of it. */
private fun DrawScope.drawSelectionChrome(box: Rect, multi: Boolean, handles: Boolean, primary: Color, onPrimary: Color, scale: Float) {
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
    if (!handles) return
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
// A lifted button, drawn translucent so the move end it stands on shows through it.
private const val CARRIED_ALPHA = 0.6f
// A move's ends: the button's copy, and how strongly it is washed in the end's colour.
private const val GHOST_ALPHA = 0.75f
private const val GHOST_WASH_ALPHA = 0.7f
// The frozen game under the editor is dimmed like the old edit scrim (~40% black).
private const val BACKDROP_SCRIM_ALPHA = 0.4f
