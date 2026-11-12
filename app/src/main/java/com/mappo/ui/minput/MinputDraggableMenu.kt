package com.mappo.ui.minput

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * **The minput DRAGGABLE MENU** — a floating, grab-anywhere tool menu that lives IN the host's
 * composition (no popup window): ONE always-visible core panel, a vertical list or a horizontal
 * icon bar ([MinputDraggableMenu]'s `horizontal`, morphing between the two about its pinned edge),
 * whose rows open CASCADING fly-outs beside them, deeper rows cascading further
 * ([MinputDraggableMenuFlyouts]). Rows are data ([MinputDraggableMenuEntry]): leaf actions, check
 * toggles, value rows, submenu openers, split select+submenu rows, and custom rows hosting embedded
 * controls (sliders, pickers) that can spawn fly-outs of their own.
 *
 * Born as the virtual button editor's core menu (ported 2026-10-15 from the retired
 * `OverlayLiveEditController`, where it was one overlay window per level) and EXTERNALIZED here
 * 2026-11-11 when that editor moved its commands onto the bar, a toolbar row and per-button
 * context menus. The editor still hosts the FLY-OUTS alone — opened beside a button with
 * [MinputDraggableMenuState.openAt] — for its rich per-button editing levels.
 *
 * Unlike the popup menus ([MinputActionMenu] / [MinputDropdownMenu]) nothing here takes focus or
 * swallows outside touches: a press outside a fly-out closes it AND still reaches whatever is
 * underneath. That is why it is in-composition, and what it asks of its host:
 *
 *  1. one [MinputDraggableMenuState], `remember`ed;
 *  2. [Modifier.minputDraggableMenuHost] on the host's root — the box the menu floats in. It gives
 *     rows a coordinate space and watches every press (Initial pass, never consumed) to close the
 *     fly-outs it didn't land in;
 *  3. [MinputDraggableMenu] (optional — the core panel) and [MinputDraggableMenuFlyouts], composed
 *     LAST in that root so they sit above its content, both handed the bounds (host-local px) the
 *     menu may occupy.
 *
 * Fly-outs carry the one minput menu motion ([MinputMenuMotion]) and the shared cascade side rule
 * ([minputCascadeSide]).
 */

/** A trailing affordance on a menu row. (A submenu's arrow is implied by [MinputDraggableMenuEntry.Item.submenu].) */
internal sealed interface MinputDraggableMenuTrailing {
    data object None : MinputDraggableMenuTrailing
    data class Check(val checked: Boolean, val onToggle: (Boolean) -> Unit) : MinputDraggableMenuTrailing
    data class Value(val text: String) : MinputDraggableMenuTrailing
}

/** One menu entry: a divider, an item (leaf action, submenu opener, or a split select+submenu
 *  row), or a custom-composable row (embedded controls — sliders, pill pairs, pickers). */
internal sealed interface MinputDraggableMenuEntry {
    data object Divider : MinputDraggableMenuEntry
    data class Item(
        val label: String,
        val enabled: Boolean = true,
        // Selected rows also show a trailing check (in addition to the theme-colour text).
        val selected: Boolean = false,
        val indent: Boolean = false,
        val leadingIcon: ImageVector? = null,
        val trailing: MinputDraggableMenuTrailing = MinputDraggableMenuTrailing.None,
        // A leaf action closes only THIS level (keeps its parent open) — in-menu pickers.
        val closeToParentOnly: Boolean = false,
        // A leaf action leaves every level open (in-place mutations like "+ Fill").
        val keepOpen: Boolean = false,
        // Non-null → a cascading fly-out. @Composable + lazy so its contents recompose live.
        val submenu: (@Composable () -> List<MinputDraggableMenuEntry>)? = null,
        // Width of the fly-out [submenu] opens (dp); null = [MinputDraggableMenuWidthDp].
        val submenuWidthDp: Int? = null,
        // Leaf action. With a [submenu] too, the row is SPLIT: body = onClick, arrow = open.
        val onClick: (() -> Unit)? = null,
    ) : MinputDraggableMenuEntry

    /** A row rendered by [content] itself; `openSubmenu(subKey, builder)` opens a standard-width
     *  fly-out anchored at this row (how embedded pills spawn their pickers). */
    data class Custom(
        val key: String,
        val content: @Composable ((subKey: String, builder: @Composable () -> List<MinputDraggableMenuEntry>) -> Unit) -> Unit,
    ) : MinputDraggableMenuEntry
}

/** Where a fly-out sits. */
internal sealed interface MinputFlyoutAnchor {
    /** Beside a parent whose visible edges are [parentLeft]…[parentRight], its top at [top]. */
    data class Side(val parentLeft: Float, val parentRight: Float, val top: Float) : MinputFlyoutAnchor

    /** Below [edgeY] (above it when [growUp]), its left at [left] — the horizontal bar's levels. */
    data class Vertical(val left: Float, val edgeY: Float, val growUp: Boolean) : MinputFlyoutAnchor
}

internal class MinputDraggableMenuLevel(
    val id: Int,
    val depth: Int,
    // The row that opened this level, so re-tapping it toggles it closed.
    val sourceKey: String,
    val anchor: MinputFlyoutAnchor,
    val widthDp: Int,
    val builder: @Composable () -> List<MinputDraggableMenuEntry>,
) {
    val visible = MutableTransitionState(false).apply { targetState = true }

    // Where the level was last placed (visible surface, host-local px). Plain fields: written in
    // placement, read by press handlers — never by composition.
    var bounds: Rect = Rect.Zero
    var side: MinputCascadeSide? = null
}

/**
 * The menu's state: the open fly-out levels (index = depth − 1; the core panel is depth 0 and never
 * in the list), the levels still playing their close, and the core panel's position.
 */
@Stable
internal class MinputDraggableMenuState {
    val levels = mutableStateListOf<MinputDraggableMenuLevel>()
    val closing = mutableStateListOf<MinputDraggableMenuLevel>()
    private var nextId = 0

    // Which way the current cascade opens — decided by its FIRST side-placed level, inherited by
    // deeper ones so they don't fold back over their parents. Reset by a fresh root submenu.
    var cascadeSide: MinputCascadeSide? = null

    /** The host root's coordinates, so rows can report their bounds host-local. Set by
     *  [minputDraggableMenuHost]. */
    var hostCoords: LayoutCoordinates? = null

    // ── The core panel ──
    // Position: NaN until first moved (placed at the default — the left edge, vertically centred).
    // The panel pins its TOP edge at [panelEdgeY] when [panelPinTop], else its BOTTOM, so a rotate
    // morphs into the empty space (top half → grows down, bottom half → grows up).
    var panelX by mutableFloatStateOf(Float.NaN)
    var panelEdgeY by mutableFloatStateOf(Float.NaN)
    var panelPinTop by mutableStateOf(true)

    /** The panel's visible surface as last placed (host-local px). */
    var panelBounds: Rect = Rect.Zero
    var canvasHeight: Float = 0f

    // Root rows' bounds by label — a press on one is the ROW's (it toggles its own fly-out), so the
    // outside-press sweep leaves it alone.
    val rootRowBounds = mutableMapOf<String, Rect>()

    fun boundsOf(coords: LayoutCoordinates): Rect? =
        hostCoords?.takeIf { it.isAttached && coords.isAttached }?.localBoundingBoxOf(coords, clipBounds = false)

    /** Close levels until only [keep] remain (0 = all; the core panel stays). */
    fun truncate(keep: Int) {
        while (levels.size > keep) {
            val level = levels.removeAt(levels.lastIndex)
            level.visible.targetState = false
            closing.add(level)
        }
    }

    fun dismissAll() = truncate(0)

    /**
     * Open a fly-out with NO core panel behind it: a first level beside [anchor] (host-local px —
     * the thing being acted on, e.g. a button on a canvas), on whichever side it fits, its top
     * level with the anchor's. Replaces whatever cascade was open.
     */
    fun openAt(
        sourceKey: String,
        anchor: Rect,
        widthDp: Int = MinputDraggableMenuWidthDp,
        builder: @Composable () -> List<MinputDraggableMenuEntry>,
    ) {
        truncate(0)
        cascadeSide = null
        levels.add(
            MinputDraggableMenuLevel(nextId++, 1, sourceKey, MinputFlyoutAnchor.Side(anchor.left, anchor.right, anchor.top), widthDp, builder),
        )
    }

    /** Call just before flipping the core panel's `horizontal`: closes the fly-outs and pins the
     *  panel where it sits, so the morph grows into its own half of the host. */
    fun prepareRotate(shadowMarginPx: Float) {
        dismissAll()
        settlePanel(shadowMarginPx)
    }

    /** Re-tapping the row that opened the level at this depth closes it; else (re)open. */
    fun toggle(
        parentDepth: Int,
        sourceKey: String,
        anchor: MinputFlyoutAnchor,
        widthDp: Int,
        builder: @Composable () -> List<MinputDraggableMenuEntry>,
    ) {
        val openHere = levels.getOrNull(parentDepth)
        truncate(parentDepth)
        if (openHere != null && openHere.sourceKey == sourceKey) return
        if (parentDepth == 0) cascadeSide = null
        levels.add(MinputDraggableMenuLevel(nextId++, parentDepth + 1, sourceKey, anchor, widthDp, builder))
    }

    /** A root row's fly-out: beside the vertical panel, or below / above the horizontal bar. */
    fun toggleRoot(
        sourceKey: String,
        itemBounds: Rect,
        horizontal: Boolean,
        widthDp: Int,
        builder: @Composable () -> List<MinputDraggableMenuEntry>,
    ) {
        val p = panelBounds
        val anchor = if (!horizontal) {
            MinputFlyoutAnchor.Side(p.left, p.right, itemBounds.top)
        } else if (p.center.y < canvasHeight / 2f) {
            MinputFlyoutAnchor.Vertical(itemBounds.left, p.bottom, growUp = false)
        } else {
            MinputFlyoutAnchor.Vertical(itemBounds.left, p.top, growUp = true)
        }
        toggle(0, sourceKey, anchor, widthDp, builder)
    }

    /**
     * A press anywhere in the host (seen BEFORE its target): close every level deeper than the
     * one it landed in — all of them if it landed in none. A press on a root row is the row's.
     */
    fun onPress(position: Offset) {
        if (levels.isEmpty()) return
        if (rootRowBounds.values.any { it.contains(position) }) return
        var keep = 0
        levels.forEachIndexed { i, level -> if (level.bounds.contains(position)) keep = i + 1 }
        truncate(keep)
    }

    /** Pin the panel to the edge on its own half of the screen, from where it now sits. */
    fun settlePanel(marginPx: Float) {
        val p = panelBounds
        if (p == Rect.Zero) return
        panelX = p.left - marginPx
        panelPinTop = p.center.y < canvasHeight / 2f
        panelEdgeY = if (panelPinTop) p.top - marginPx else p.bottom + marginPx
    }
}

/**
 * Make ripples in the draggable menu (and tool pads styled after it) clearly visible: a primary-tinted ripple with a
 * stronger-than-default pressed alpha (the stock onSurface ripple over the menu surface reads too
 * faint).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MinputDraggableMenuRipple(content: @Composable () -> Unit) {
    val cfg = RippleConfiguration(
        color = MaterialTheme.colorScheme.primary,
        rippleAlpha = RippleAlpha(draggedAlpha = 0.20f, focusedAlpha = 0.26f, hoveredAlpha = 0.16f, pressedAlpha = 0.32f),
    )
    CompositionLocalProvider(LocalRippleConfiguration provides cfg, content = content)
}

/**
 * A soft, blurred drop shadow for a menu surface, drawn with [android.graphics.BlurMaskFilter]
 * rather than `Modifier.shadow` (the doctrine — see `MainScreen.softDropShadow`). Draws into the
 * surrounding [MinputDraggableMenuShadowMarginDp] inset.
 */
internal fun Modifier.minputDraggableMenuShadow(cornerRadius: Dp): Modifier = drawBehind {
    val blurPx = 8.dp.toPx()
    val offsetYPx = 2.dp.toPx()
    val cornerPx = cornerRadius.toPx()
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(72, 0, 0, 0)
        // NORMAL (not OUTER) so the shadow hugs the edge — the opaque surface on top covers the
        // inner half, leaving just the soft outer halo.
        maskFilter = android.graphics.BlurMaskFilter(blurPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
        isAntiAlias = true
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRoundRect(0f, offsetYPx, size.width, size.height + offsetYPx, cornerPx, cornerPx, paint)
    }
}

// ── The core panel ──────────────────────────────────────────────────────────────────────────

/**
 * The always-visible core menu, placed inside the editable [bounds]. Grab anywhere to drag it
 * (starting a drag closes the fly-outs, which hang off it); a tap that never moves is the row's.
 * Rotating morphs between the vertical panel and the horizontal bar about the pinned edge.
 */
@Composable
internal fun MinputDraggableMenu(
    state: MinputDraggableMenuState,
    bounds: Rect,
    horizontal: Boolean,
    entries: @Composable () -> List<MinputDraggableMenuEntry>,
) {
    val marginPx = with(LocalDensity.current) { MinputDraggableMenuShadowMarginDp.dp.toPx() }
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            Box(
                Modifier
                    .pointerInput(state) {
                        detectDragGestures(
                            onDragStart = {
                                state.dismissAll()
                                if (state.panelX.isNaN()) state.settlePanel(marginPx)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                state.panelX += amount.x
                                state.panelEdgeY += amount.y
                            },
                            onDragEnd = { state.settlePanel(marginPx) },
                            onDragCancel = { state.settlePanel(marginPx) },
                        )
                    }
                    .padding(MinputDraggableMenuShadowMarginDp.dp),
            ) {
                val shape = if (horizontal) CircleShape else MaterialTheme.shapes.large
                val shadowCorner = if (horizontal) 100.dp else 16.dp
                Box(Modifier.minputDraggableMenuShadow(shadowCorner)) {
                    Surface(
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                    ) {
                        AnimatedContent(
                            targetState = horizontal,
                            transitionSpec = {
                                fadeIn(tween(ROTATE_MS / 2, delayMillis = ROTATE_MS / 2)) togetherWith
                                    fadeOut(tween(ROTATE_MS / 2)) using SizeTransform(clip = true) { _, _ -> tween(ROTATE_MS) }
                            },
                            contentAlignment = if (state.panelPinTop) Alignment.TopStart else Alignment.BottomStart,
                            label = "core-menu-rotate",
                        ) { isHorizontal ->
                            if (isHorizontal) HorizontalBar(state, entries) else VerticalPanel(state, entries)
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val panel = measurables.first().measure(Constraints())
        val w = panel.width.toFloat()
        val h = panel.height.toFloat()
        val defaultTop = bounds.top + (bounds.height - h) / 2f
        val x = if (state.panelX.isNaN()) bounds.left else state.panelX
        val top = when {
            state.panelEdgeY.isNaN() -> defaultTop
            state.panelPinTop -> state.panelEdgeY
            else -> state.panelEdgeY - h
        }
        val px = clampSpan(x, w, bounds.left, bounds.right)
        val py = clampSpan(top, h, bounds.top, bounds.bottom)
        state.panelBounds = Rect(px + marginPx, py + marginPx, px + w - marginPx, py + h - marginPx)
        layout(constraints.maxWidth, constraints.maxHeight) {
            panel.place(px.roundToInt(), py.roundToInt())
        }
    }
}

@Composable
private fun VerticalPanel(state: MinputDraggableMenuState, entries: @Composable () -> List<MinputDraggableMenuEntry>) {
    Column(Modifier.width(MinputDraggableMenuWidthDp.dp).padding(vertical = 6.dp)) {
        entries().forEach { entry ->
            when (entry) {
                is MinputDraggableMenuEntry.Divider -> HorizontalDivider(Modifier.padding(vertical = 2.dp))
                is MinputDraggableMenuEntry.Item -> RootRow(state, entry)
                is MinputDraggableMenuEntry.Custom -> {} // custom rows are fly-out-only
            }
        }
    }
}

@Composable
private fun HorizontalBar(state: MinputDraggableMenuState, entries: @Composable () -> List<MinputDraggableMenuEntry>) {
    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        entries().forEach { entry ->
            when (entry) {
                is MinputDraggableMenuEntry.Divider -> VerticalDivider(Modifier.height(26.dp).padding(horizontal = 2.dp))
                is MinputDraggableMenuEntry.Item -> RootIcon(state, entry)
                is MinputDraggableMenuEntry.Custom -> {}
            }
        }
    }
}

/** A root item's tap: open (toggle) its fly-out, or act and close the fly-outs. */
private fun rootTap(state: MinputDraggableMenuState, item: MinputDraggableMenuEntry.Item, bounds: Rect, horizontal: Boolean) {
    if (!item.enabled) return
    val sub = item.submenu
    if (sub != null) {
        state.toggleRoot(item.label, bounds, horizontal, item.submenuWidthDp ?: MinputDraggableMenuWidthDp, sub)
    } else {
        item.onClick?.invoke()
        state.dismissAll()
    }
}

/** Track a root item's host-local bounds (for its fly-out anchor and the outside-press sweep). */
private fun Modifier.rootBounds(state: MinputDraggableMenuState, label: String, onBounds: (Rect) -> Unit): Modifier =
    onGloballyPositioned { c ->
        state.boundsOf(c)?.let {
            state.rootRowBounds[label] = it
            onBounds(it)
        }
    }

@Composable
private fun RootRow(state: MinputDraggableMenuState, item: MinputDraggableMenuEntry.Item) {
    val contentColor =
        if (item.enabled) MaterialTheme.colorScheme.onSurface
        // 0.38 = M3 disabled-content opacity (no dedicated scheme token).
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .rootBounds(state, item.label) { bounds = it }
            .clickable(enabled = item.enabled) { rootTap(state, item, bounds, horizontal = false) }
            .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item.leadingIcon?.let {
            MinputIcon(it, contentDescription = null, size = MinputIconSize.M, tint = contentColor, slot = true)
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = contentColor,
        )
        if (item.submenu != null) {
            MinputSubmenuArrow(tint = contentColor)
        }
    }
}

@Composable
private fun RootIcon(state: MinputDraggableMenuState, item: MinputDraggableMenuEntry.Item) {
    val tint =
        if (item.enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .rootBounds(state, item.label) { bounds = it }
            .clickable(enabled = item.enabled) { rootTap(state, item, bounds, horizontal = true) },
        contentAlignment = Alignment.Center,
    ) {
        item.leadingIcon?.let { MinputIcon(it, contentDescription = item.label, size = MinputIconSize.L, tint = tint) }
    }
}

// ── Fly-out levels ──────────────────────────────────────────────────────────────────────────

/** Every open (and closing) fly-out level, each placed off its anchor inside [bounds]. */
@Composable
internal fun MinputDraggableMenuFlyouts(state: MinputDraggableMenuState, bounds: Rect) {
    (state.levels + state.closing).forEach { level ->
        key(level.id) { FlyoutLevel(state, level, bounds) }
    }
}

@Composable
private fun FlyoutLevel(state: MinputDraggableMenuState, level: MinputDraggableMenuLevel, bounds: Rect) {
    val density = LocalDensity.current
    val marginPx = with(density) { MinputDraggableMenuShadowMarginDp.dp.toPx() }
    // A closing level leaves the list once its close has played.
    LaunchedEffect(level) {
        snapshotFlow { level.visible.isIdle && !level.visible.currentState && !level.visible.targetState }
            .collect { gone -> if (gone) state.closing.remove(level) }
    }
    val growUp = (level.anchor as? MinputFlyoutAnchor.Vertical)?.growUp == true
    val origin = TransformOrigin(0f, if (growUp) 1f else 0f)
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            AnimatedVisibility(
                visibleState = level.visible,
                // The one minput menu motion: scale + fade, in from and out toward the anchor.
                enter = fadeIn(MinputMenuMotion.fadeEnter()) +
                    scaleIn(MinputMenuMotion.scaleEnter(), initialScale = MinputMenuMotion.ClosedScale, transformOrigin = origin),
                exit = fadeOut(MinputMenuMotion.fadeExit()) +
                    scaleOut(MinputMenuMotion.scaleExit(), targetScale = MinputMenuMotion.ClosedScale, transformOrigin = origin),
            ) {
                Box(Modifier.padding(MinputDraggableMenuShadowMarginDp.dp)) {
                    Box(Modifier.minputDraggableMenuShadow(12.dp)) {
                        Surface(
                            // surfaceContainerHighest = the canonical M3 menu surface.
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 6.dp,
                        ) {
                            // Fly-outs may scroll up to the editable height — anchored to their
                            // parent, a scrolling surface carries no drag expectation.
                            val maxH = with(density) { bounds.height.toDp() } - (2 * MinputDraggableMenuShadowMarginDp).dp
                            MenuList(state, level, Modifier.heightIn(max = maxH))
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        // A closed level's AnimatedVisibility drops its content once the close has played — the
        // frame before the level leaves the list has nothing to place.
        val p = measurables.firstOrNull()?.measure(Constraints())
            ?: return@Layout layout(constraints.maxWidth, constraints.maxHeight) {}
        val (x, y) = placeFlyout(state, level, p.width.toFloat(), p.height.toFloat(), marginPx, bounds)
        layout(constraints.maxWidth, constraints.maxHeight) { p.place(x.roundToInt(), y.roundToInt()) }
    }
}

/** A level's top-left (its shadow margin included), clamped inside [b]; records its bounds. */
private fun placeFlyout(
    state: MinputDraggableMenuState,
    level: MinputDraggableMenuLevel,
    w: Float,
    h: Float,
    margin: Float,
    b: Rect,
): Pair<Float, Float> {
    val visW = w - 2 * margin
    val visH = h - 2 * margin
    val (visLeft, baseTop) = when (val a = level.anchor) {
        is MinputFlyoutAnchor.Side -> placeSide(state, level, a, visW, b) to a.top
        is MinputFlyoutAnchor.Vertical ->
            clampSpan(a.left, visW, b.left, b.right) to (if (a.growUp) a.edgeY - visH else a.edgeY)
    }
    val visTop = clampSpan(baseTop, visH, b.top, b.bottom)
    if (w > 0f) level.bounds = Rect(visLeft, visTop, visLeft + visW, visTop + visH)
    return (visLeft - margin) to (visTop - margin)
}

/**
 * x for a level beside its parent, by the shared minput cascade rule ([minputCascadeSide]): the
 * cascade's first level picks a side, deeper levels inherit it. Decided once per level.
 */
private fun placeSide(state: MinputDraggableMenuState, level: MinputDraggableMenuLevel, a: MinputFlyoutAnchor.Side, width: Float, b: Rect): Float {
    val side = level.side ?: minputCascadeSide(a.parentLeft, a.parentRight, width, b.left, b.right, state.cascadeSide)
        .also {
            level.side = it
            if (state.cascadeSide == null) state.cascadeSide = it
        }
    return clampSpan(minputCascadeLeft(side, a.parentLeft, a.parentRight, width), width, b.left, b.right)
}

@Composable
private fun MenuList(state: MinputDraggableMenuState, level: MinputDraggableMenuLevel, modifier: Modifier) {
    Column(
        modifier = modifier
            .width(level.widthDp.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
    ) {
        level.builder().forEach { entry ->
            when (entry) {
                is MinputDraggableMenuEntry.Divider -> HorizontalDivider(Modifier.padding(vertical = 2.dp))
                is MinputDraggableMenuEntry.Item -> MenuRow(state, level, entry)
                is MinputDraggableMenuEntry.Custom -> MenuCustomRow(state, level, entry)
            }
        }
    }
}

/** Open (toggle) a fly-out of a row at [rowTop] inside [level]. */
private fun openBeside(
    state: MinputDraggableMenuState,
    level: MinputDraggableMenuLevel,
    rowTop: Float,
    key: String,
    widthDp: Int,
    builder: @Composable () -> List<MinputDraggableMenuEntry>,
) {
    val b = level.bounds
    state.toggle(level.depth, key, MinputFlyoutAnchor.Side(b.left, b.right, rowTop), widthDp, builder)
}

@Composable
private fun MenuCustomRow(state: MinputDraggableMenuState, level: MinputDraggableMenuLevel, entry: MinputDraggableMenuEntry.Custom) {
    var rowTop by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { c -> state.boundsOf(c)?.let { rowTop = it.top } }
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        entry.content { subKey, builder -> openBeside(state, level, rowTop, subKey, MinputDraggableMenuWidthDp, builder) }
    }
}

@Composable
private fun MenuRow(state: MinputDraggableMenuState, level: MinputDraggableMenuLevel, item: MinputDraggableMenuEntry.Item) {
    var rowTop by remember { mutableFloatStateOf(0f) }
    val hasSub = item.submenu != null
    val trailing = item.trailing
    val openSub = { openBeside(state, level, rowTop, item.label, item.submenuWidthDp ?: MinputDraggableMenuWidthDp, item.submenu!!) }
    // Check rows toggle in place; a leaf runs + closes the fly-outs (or just this level when
    // closeToParentOnly); a pure submenu row toggles its fly-out. (A split row selects on the
    // body and exposes the arrow as its own target.)
    val leafClose: () -> Unit =
        if (item.closeToParentOnly) ({ state.truncate(level.depth - 1) }) else ({ state.dismissAll() })
    val rowClick: (() -> Unit)? = when {
        !item.enabled -> null
        trailing is MinputDraggableMenuTrailing.Check -> ({ trailing.onToggle(!trailing.checked) })
        item.onClick != null -> ({ item.onClick.invoke(); if (!item.keepOpen) leafClose() })
        hasSub -> openSub
        else -> null
    }
    val contentColor = when {
        !item.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        item.selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { c -> state.boundsOf(c)?.let { rowTop = it.top } }
            .then(if (rowClick != null) Modifier.clickable(onClick = rowClick) else Modifier)
            .padding(start = if (item.indent) 28.dp else 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item.leadingIcon?.let {
            MinputIcon(it, contentDescription = null, size = MinputIconSize.M, tint = contentColor, slot = true)
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = contentColor,
        )
        if (item.selected) {
            MinputIcon(Icons.Default.Check, contentDescription = "Selected", size = MinputIconSize.M, tint = MaterialTheme.colorScheme.primary)
        }
        when (trailing) {
            // onCheckedChange = null so the ROW owns the toggle (the whole row is tappable).
            is MinputDraggableMenuTrailing.Check -> MinputCheckbox(checked = trailing.checked, onCheckedChange = null, enabled = item.enabled)
            is MinputDraggableMenuTrailing.Value -> Text(
                trailing.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MinputDraggableMenuTrailing.None -> {}
        }
        if (hasSub) {
            val arrow = Icons.AutoMirrored.Filled.ArrowRight
            if (item.onClick != null) {
                // Split row: the arrow is its own target so the body keeps its select action.
                MinputIcon(
                    arrow,
                    contentDescription = "Open submenu",
                    size = MinputIconSize.Xs,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = item.enabled, onClick = openSub).padding(2.dp),
                    slot = true,
                )
            } else {
                MinputSubmenuArrow(
                    tint = if (item.enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
}

// Sizing: a narrow fixed width; levels scroll past the bounds' height.
internal const val MinputDraggableMenuWidthDp = 156
// Fly-out levels hosting embedded controls (Assign rows, sliders, the gradient editor).
internal const val MinputDraggableMenuWideWidthDp = 240
// Transparent inset around each menu surface, giving the drop shadow room.
internal const val MinputDraggableMenuShadowMarginDp = 10
// The vertical ↔ horizontal morph.
private const val ROTATE_MS = 200

/**
 * The host side of a draggable menu (see the file header): reports the host's coordinates and
 * height to [state], and watches every press BEFORE its target to close the fly-outs it didn't
 * land in. Observed, never consumed — the press still reaches the row / content under it.
 */
internal fun Modifier.minputDraggableMenuHost(state: MinputDraggableMenuState): Modifier = this
    .onGloballyPositioned {
        state.hostCoords = it
        state.canvasHeight = it.size.height.toFloat()
    }
    .pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.onPress(down.position)
        }
    }

/** [value] clamped so an [extent]-long span stays inside [lo, hi]; a span bigger than the bounds
 *  may overflow past an edge. */
private fun clampSpan(value: Float, extent: Float, lo: Float, hi: Float): Float =
    value.coerceIn(minOf(lo, hi - extent), maxOf(lo, hi - extent))
