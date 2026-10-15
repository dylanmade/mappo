package com.mappo.ui.screen.virtualset

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mappo.ui.compact.CompactCheckbox
import com.mappo.ui.minput.MinputIcon
import com.mappo.ui.minput.MinputIconSize
import com.mappo.ui.minput.MinputMenuMotion
import kotlin.math.roundToInt

// The virtual editor's menu (2026-10-15, ported from the retired OverlayLiveEditController): ONE
// always-visible core panel — a vertical list or a horizontal icon bar, grab-anywhere draggable —
// whose rows open CASCADING fly-outs beside them, deeper rows cascading further. It used to be one
// overlay window per level; it is all one composition now, so the levels are placed by a Layout
// off their parent's measured bounds, and an outside press is seen at the editor root's Initial
// pass (it still reaches whatever is underneath — a button, another row).

/** A trailing affordance on a menu row. (A submenu's arrow is implied by [MenuEntry.Item.submenu].) */
internal sealed interface MenuTrailing {
    data object None : MenuTrailing
    data class Check(val checked: Boolean, val onToggle: (Boolean) -> Unit) : MenuTrailing
    data class Value(val text: String) : MenuTrailing
}

/** One menu entry: a divider, an item (leaf action, submenu opener, or a split select+submenu
 *  row), or a custom-composable row (embedded controls — sliders, pill pairs, pickers). */
internal sealed interface MenuEntry {
    data object Divider : MenuEntry
    data class Item(
        val label: String,
        val enabled: Boolean = true,
        // Selected rows also show a trailing check (in addition to the theme-colour text).
        val selected: Boolean = false,
        val indent: Boolean = false,
        val leadingIcon: ImageVector? = null,
        val trailing: MenuTrailing = MenuTrailing.None,
        // A leaf action closes only THIS level (keeps its parent open) — in-menu pickers.
        val closeToParentOnly: Boolean = false,
        // A leaf action leaves every level open (in-place mutations like "+ Fill").
        val keepOpen: Boolean = false,
        // Non-null → a cascading fly-out. @Composable + lazy so its contents recompose live.
        val submenu: (@Composable () -> List<MenuEntry>)? = null,
        // Width of the fly-out [submenu] opens (dp); null = [MENU_WIDTH_DP].
        val submenuWidthDp: Int? = null,
        // Leaf action. With a [submenu] too, the row is SPLIT: body = onClick, arrow = open.
        val onClick: (() -> Unit)? = null,
    ) : MenuEntry

    /** A row rendered by [content] itself; `openSubmenu(subKey, builder)` opens a standard-width
     *  fly-out anchored at this row (how embedded pills spawn their pickers). */
    data class Custom(
        val key: String,
        val content: @Composable ((subKey: String, builder: @Composable () -> List<MenuEntry>) -> Unit) -> Unit,
    ) : MenuEntry
}

/** Where a fly-out sits. */
internal sealed interface FlyoutAnchor {
    /** Beside a parent whose visible edges are [parentLeft]…[parentRight], its top at [top]. */
    data class Side(val parentLeft: Float, val parentRight: Float, val top: Float) : FlyoutAnchor

    /** Below [edgeY] (above it when [growUp]), its left at [left] — the horizontal bar's levels. */
    data class Vertical(val left: Float, val edgeY: Float, val growUp: Boolean) : FlyoutAnchor
}

internal enum class MenuSide { LEFT, RIGHT }

internal class MenuLevel(
    val id: Int,
    val depth: Int,
    // The row that opened this level, so re-tapping it toggles it closed.
    val sourceKey: String,
    val anchor: FlyoutAnchor,
    val widthDp: Int,
    val builder: @Composable () -> List<MenuEntry>,
) {
    val visible = MutableTransitionState(false).apply { targetState = true }

    // Where the level was last placed (visible surface, editor-local px). Plain fields: written in
    // placement, read by press handlers — never by composition.
    var bounds: Rect = Rect.Zero
    var side: MenuSide? = null
}

/**
 * The menu's state: the open fly-out levels (index = depth − 1; the core panel is depth 0 and never
 * in the list), the levels still playing their close, and the core panel's position.
 */
@Stable
internal class CascadeMenuState {
    val levels = mutableStateListOf<MenuLevel>()
    val closing = mutableStateListOf<MenuLevel>()
    private var nextId = 0

    // Which way the current cascade opens — decided by its FIRST side-placed level, inherited by
    // deeper ones so they don't fold back over their parents. Reset by a fresh root submenu.
    var cascadeSide: MenuSide? = null

    /** The editor root's coordinates, so rows can report their bounds editor-local. */
    var editorCoords: LayoutCoordinates? = null

    // ── The core panel ──
    // Position: NaN until first moved (placed at the default — the left edge, vertically centred).
    // The panel pins its TOP edge at [panelEdgeY] when [panelPinTop], else its BOTTOM, so a rotate
    // morphs into the empty space (top half → grows down, bottom half → grows up).
    var panelX by mutableFloatStateOf(Float.NaN)
    var panelEdgeY by mutableFloatStateOf(Float.NaN)
    var panelPinTop by mutableStateOf(true)

    /** The panel's visible surface as last placed (editor-local px). */
    var panelBounds: Rect = Rect.Zero
    var canvasHeight: Float = 0f

    // Root rows' bounds by label — a press on one is the ROW's (it toggles its own fly-out), so the
    // outside-press sweep leaves it alone.
    val rootRowBounds = mutableMapOf<String, Rect>()

    fun boundsOf(coords: LayoutCoordinates): Rect? =
        editorCoords?.takeIf { it.isAttached && coords.isAttached }?.localBoundingBoxOf(coords, clipBounds = false)

    /** Close levels until only [keep] remain (0 = all; the core panel stays). */
    fun truncate(keep: Int) {
        while (levels.size > keep) {
            val level = levels.removeAt(levels.lastIndex)
            level.visible.targetState = false
            closing.add(level)
        }
    }

    fun dismissAll() = truncate(0)

    /** Re-tapping the row that opened the level at this depth closes it; else (re)open. */
    fun toggle(
        parentDepth: Int,
        sourceKey: String,
        anchor: FlyoutAnchor,
        widthDp: Int,
        builder: @Composable () -> List<MenuEntry>,
    ) {
        val openHere = levels.getOrNull(parentDepth)
        truncate(parentDepth)
        if (openHere != null && openHere.sourceKey == sourceKey) return
        if (parentDepth == 0) cascadeSide = null
        levels.add(MenuLevel(nextId++, parentDepth + 1, sourceKey, anchor, widthDp, builder))
    }

    /** A root row's fly-out: beside the vertical panel, or below / above the horizontal bar. */
    fun toggleRoot(
        sourceKey: String,
        itemBounds: Rect,
        horizontal: Boolean,
        widthDp: Int,
        builder: @Composable () -> List<MenuEntry>,
    ) {
        val p = panelBounds
        val anchor = if (!horizontal) {
            FlyoutAnchor.Side(p.left, p.right, itemBounds.top)
        } else if (p.center.y < canvasHeight / 2f) {
            FlyoutAnchor.Vertical(itemBounds.left, p.bottom, growUp = false)
        } else {
            FlyoutAnchor.Vertical(itemBounds.left, p.top, growUp = true)
        }
        toggle(0, sourceKey, anchor, widthDp, builder)
    }

    /**
     * A press anywhere in the editor (seen BEFORE its target): close every level deeper than the
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
 * Make ripples in the editor menus / Positioner clearly visible: a primary-tinted ripple with a
 * stronger-than-default pressed alpha (the stock onSurface ripple over the menu surface reads too
 * faint).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProvideMenuRipple(content: @Composable () -> Unit) {
    val cfg = RippleConfiguration(
        color = MaterialTheme.colorScheme.primary,
        rippleAlpha = RippleAlpha(draggedAlpha = 0.20f, focusedAlpha = 0.26f, hoveredAlpha = 0.16f, pressedAlpha = 0.32f),
    )
    CompositionLocalProvider(LocalRippleConfiguration provides cfg, content = content)
}

/**
 * A soft, blurred drop shadow for a menu surface, drawn with [android.graphics.BlurMaskFilter]
 * rather than `Modifier.shadow` (the doctrine — see `MainScreen.softDropShadow`). Draws into the
 * surrounding [MENU_SHADOW_MARGIN] inset.
 */
internal fun Modifier.menuDropShadow(cornerRadius: Dp): Modifier = drawBehind {
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
internal fun CoreMenuPanel(
    state: CascadeMenuState,
    bounds: Rect,
    horizontal: Boolean,
    entries: @Composable () -> List<MenuEntry>,
) {
    val marginPx = with(LocalDensity.current) { MENU_SHADOW_MARGIN.dp.toPx() }
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
                    .padding(MENU_SHADOW_MARGIN.dp),
            ) {
                val shape = if (horizontal) CircleShape else MaterialTheme.shapes.large
                val shadowCorner = if (horizontal) 100.dp else 16.dp
                Box(Modifier.menuDropShadow(shadowCorner)) {
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
        val px = EditCanvas.clamp(x, w, bounds.left, bounds.right)
        val py = EditCanvas.clamp(top, h, bounds.top, bounds.bottom)
        state.panelBounds = Rect(px + marginPx, py + marginPx, px + w - marginPx, py + h - marginPx)
        layout(constraints.maxWidth, constraints.maxHeight) {
            panel.place(px.roundToInt(), py.roundToInt())
        }
    }
}

@Composable
private fun VerticalPanel(state: CascadeMenuState, entries: @Composable () -> List<MenuEntry>) {
    Column(Modifier.width(MENU_WIDTH_DP.dp).padding(vertical = 6.dp)) {
        entries().forEach { entry ->
            when (entry) {
                is MenuEntry.Divider -> HorizontalDivider(Modifier.padding(vertical = 2.dp))
                is MenuEntry.Item -> RootRow(state, entry)
                is MenuEntry.Custom -> {} // custom rows are fly-out-only
            }
        }
    }
}

@Composable
private fun HorizontalBar(state: CascadeMenuState, entries: @Composable () -> List<MenuEntry>) {
    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        entries().forEach { entry ->
            when (entry) {
                is MenuEntry.Divider -> VerticalDivider(Modifier.height(26.dp).padding(horizontal = 2.dp))
                is MenuEntry.Item -> RootIcon(state, entry)
                is MenuEntry.Custom -> {}
            }
        }
    }
}

/** A root item's tap: open (toggle) its fly-out, or act and close the fly-outs. */
private fun rootTap(state: CascadeMenuState, item: MenuEntry.Item, bounds: Rect, horizontal: Boolean) {
    if (!item.enabled) return
    val sub = item.submenu
    if (sub != null) {
        state.toggleRoot(item.label, bounds, horizontal, item.submenuWidthDp ?: MENU_WIDTH_DP, sub)
    } else {
        item.onClick?.invoke()
        state.dismissAll()
    }
}

/** Track a root item's editor-local bounds (for its fly-out anchor and the outside-press sweep). */
private fun Modifier.rootBounds(state: CascadeMenuState, label: String, onBounds: (Rect) -> Unit): Modifier =
    onGloballyPositioned { c ->
        state.boundsOf(c)?.let {
            state.rootRowBounds[label] = it
            onBounds(it)
        }
    }

@Composable
private fun RootRow(state: CascadeMenuState, item: MenuEntry.Item) {
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
            MinputIcon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = null, size = MinputIconSize.Xxs, tint = contentColor)
        }
    }
}

@Composable
private fun RootIcon(state: CascadeMenuState, item: MenuEntry.Item) {
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
internal fun MenuFlyouts(state: CascadeMenuState, bounds: Rect) {
    (state.levels + state.closing).forEach { level ->
        key(level.id) { FlyoutLevel(state, level, bounds) }
    }
}

@Composable
private fun FlyoutLevel(state: CascadeMenuState, level: MenuLevel, bounds: Rect) {
    val density = LocalDensity.current
    val marginPx = with(density) { MENU_SHADOW_MARGIN.dp.toPx() }
    // A closing level leaves the list once its close has played.
    LaunchedEffect(level) {
        snapshotFlow { level.visible.isIdle && !level.visible.currentState && !level.visible.targetState }
            .collect { gone -> if (gone) state.closing.remove(level) }
    }
    val growUp = (level.anchor as? FlyoutAnchor.Vertical)?.growUp == true
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
                Box(Modifier.padding(MENU_SHADOW_MARGIN.dp)) {
                    Box(Modifier.menuDropShadow(12.dp)) {
                        Surface(
                            // surfaceContainerHighest = the canonical M3 menu surface.
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 6.dp,
                        ) {
                            // Fly-outs may scroll up to the editable height — anchored to their
                            // parent, a scrolling surface carries no drag expectation.
                            val maxH = with(density) { bounds.height.toDp() } - (2 * MENU_SHADOW_MARGIN).dp
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
    state: CascadeMenuState,
    level: MenuLevel,
    w: Float,
    h: Float,
    margin: Float,
    b: Rect,
): Pair<Float, Float> {
    val visW = w - 2 * margin
    val visH = h - 2 * margin
    val (visLeft, baseTop) = when (val a = level.anchor) {
        is FlyoutAnchor.Side -> placeSide(state, level, a, visW, b) to a.top
        is FlyoutAnchor.Vertical ->
            EditCanvas.clamp(a.left, visW, b.left, b.right) to (if (a.growUp) a.edgeY - visH else a.edgeY)
    }
    val visTop = EditCanvas.clamp(baseTop, visH, b.top, b.bottom)
    if (w > 0f) level.bounds = Rect(visLeft, visTop, visLeft + visW, visTop + visH)
    return (visLeft - margin) to (visTop - margin)
}

/**
 * x for a level beside its parent. The cascade's first level picks a side — RIGHT if it fits, else
 * LEFT (else RIGHT and clamp); deeper levels inherit it, flipping fully only when it doesn't fit.
 */
private fun placeSide(state: CascadeMenuState, level: MenuLevel, a: FlyoutAnchor.Side, width: Float, b: Rect): Float {
    val side = level.side ?: run {
        val rightFits = a.parentRight + width <= b.right
        val leftFits = a.parentLeft - width >= b.left
        when (state.cascadeSide) {
            MenuSide.RIGHT -> if (rightFits || !leftFits) MenuSide.RIGHT else MenuSide.LEFT
            MenuSide.LEFT -> if (leftFits || !rightFits) MenuSide.LEFT else MenuSide.RIGHT
            null -> if (rightFits || !leftFits) MenuSide.RIGHT else MenuSide.LEFT
        }.also {
            level.side = it
            if (state.cascadeSide == null) state.cascadeSide = it
        }
    }
    val x = if (side == MenuSide.RIGHT) a.parentRight else a.parentLeft - width
    return EditCanvas.clamp(x, width, b.left, b.right)
}

@Composable
private fun MenuList(state: CascadeMenuState, level: MenuLevel, modifier: Modifier) {
    Column(
        modifier = modifier
            .width(level.widthDp.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
    ) {
        level.builder().forEach { entry ->
            when (entry) {
                is MenuEntry.Divider -> HorizontalDivider(Modifier.padding(vertical = 2.dp))
                is MenuEntry.Item -> MenuRow(state, level, entry)
                is MenuEntry.Custom -> MenuCustomRow(state, level, entry)
            }
        }
    }
}

/** Open (toggle) a fly-out of a row at [rowTop] inside [level]. */
private fun openBeside(
    state: CascadeMenuState,
    level: MenuLevel,
    rowTop: Float,
    key: String,
    widthDp: Int,
    builder: @Composable () -> List<MenuEntry>,
) {
    val b = level.bounds
    state.toggle(level.depth, key, FlyoutAnchor.Side(b.left, b.right, rowTop), widthDp, builder)
}

@Composable
private fun MenuCustomRow(state: CascadeMenuState, level: MenuLevel, entry: MenuEntry.Custom) {
    var rowTop by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { c -> state.boundsOf(c)?.let { rowTop = it.top } }
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        entry.content { subKey, builder -> openBeside(state, level, rowTop, subKey, MENU_WIDTH_DP, builder) }
    }
}

@Composable
private fun MenuRow(state: CascadeMenuState, level: MenuLevel, item: MenuEntry.Item) {
    var rowTop by remember { mutableFloatStateOf(0f) }
    val hasSub = item.submenu != null
    val trailing = item.trailing
    val openSub = { openBeside(state, level, rowTop, item.label, item.submenuWidthDp ?: MENU_WIDTH_DP, item.submenu!!) }
    // Check rows toggle in place; a leaf runs + closes the fly-outs (or just this level when
    // closeToParentOnly); a pure submenu row toggles its fly-out. (A split row selects on the
    // body and exposes the arrow as its own target.)
    val leafClose: () -> Unit =
        if (item.closeToParentOnly) ({ state.truncate(level.depth - 1) }) else ({ state.dismissAll() })
    val rowClick: (() -> Unit)? = when {
        !item.enabled -> null
        trailing is MenuTrailing.Check -> ({ trailing.onToggle(!trailing.checked) })
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
            is MenuTrailing.Check -> CompactCheckbox(checked = trailing.checked, onCheckedChange = null, enabled = item.enabled)
            is MenuTrailing.Value -> Text(
                trailing.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MenuTrailing.None -> {}
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
                MinputIcon(
                    arrow,
                    contentDescription = null,
                    size = MinputIconSize.Xxs,
                    tint = if (item.enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
}

// Editor menu sizing: a narrow fixed width; levels scroll past the editable height.
internal const val MENU_WIDTH_DP = 156
// Fly-out levels hosting embedded controls (Assign rows, sliders, the gradient editor).
internal const val WIDE_MENU_WIDTH_DP = 240
// Transparent inset around each menu surface, giving the drop shadow room.
internal const val MENU_SHADOW_MARGIN = 10
// The vertical ↔ horizontal morph.
private const val ROTATE_MS = 200
