package com.mappo.ui.minput

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.foundation.layout.absolutePadding
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.PopupProperties
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.ui.graphics.Color

/**
 * Where a minput menu sits relative to the control that summoned it.
 *
 * [Below] is M3's own behavior, untouched — the conventional dropdown, and the default.
 * [Start] / [End] are SIDE placements, for a control that is itself content rather than a
 * picker, where dropping a menu over the thing you just tapped hides what you're acting on.
 *
 * Side placement is screen-aware: the requested side is used when there's room and mirrored
 * when there isn't. M3's own position provider does the final clamping in every case.
 */
enum class MinputMenuPlacement { Below, Start, End }

/**
 * Shared surface for every minput menu — the single-CHOICE [MinputDropdownMenu] and the
 * verb-list [MinputActionMenu] both render through it, so their placement, caret and item
 * treatment cannot drift apart.
 *
 * **This is a thin layer over M3's `DropdownMenuPopup`, deliberately.** How the menu animates and
 * dismisses is M3's, and its surface is `DropdownMenu`'s own (container color, elevation, shape
 * defaults); minput contributes an optional side placement expressed as an anchor-relative
 * offset, an optional caret expressed as the surface's `shape`, and — since 2026-10-16 — the
 * POSITIONING itself ([MinputMenuPositionProvider]). `DropdownMenu` hard-codes a 48dp margin off
 * the window's top and bottom and won't take a provider: a menu hung off a control nearer the
 * edge than that (the hierarchy bar's menu button, compact) was shoved down to the margin, a
 * visible gap away from its button.
 *
 * In particular the menu gets NO fixed width, NO fixed row height and NO overridden text
 * style. An earlier pass added all three and each one backfired: M3's menu item stretches its
 * label to fill whatever width it is handed, so pinning the width shoved the trailing check to
 * the far edge and opened a chasm beside every label; and the type scale it ships with
 * (`labelLarge`) is already the smaller, denser one. Let M3 size itself and the menu scales
 * with the type scale for free.
 *
 * A menu must be placed inside a `Box` alongside its anchor — hence the [BoxScope] receiver,
 * which is also how the surface measures the anchor ([Modifier.matchParentSize]). That
 * measurement is what makes the caret correct: rather than PREDICTING where M3 will put the
 * menu, the caret is derived from where it ACTUALLY landed, so it stays pointed at the anchor
 * even when M3 flips the menu above the control to fit it on screen. (Predicting it is what
 * left the caret stranded above tall menus, always pointing at the first row.)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BoxScope.MinputMenuSurface(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    placement: MinputMenuPlacement,
    caret: Boolean,
    framePadding: Dp,
    menuWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current

    // Window extent for the fit test. The host View first: it is populated in every
    // environment, whereas LocalWindowInfo.containerSize can still read zero.
    val view = LocalView.current
    val fallback = LocalWindowInfo.current.containerSize
    val windowWidthPx = if (view.width > 0) view.width else fallback.width
    val windowKnown = windowWidthPx > 0

    // The anchor's true bounds. This box takes the parent Box's size without influencing it,
    // so it reports exactly the control the menu hangs off. Captured in BOTH spaces: window
    // for the fit test (which compares against the app window's width), SCREEN for the caret.
    var anchorRect by remember { mutableStateOf<Rect?>(null) }
    var anchorScreen by remember { mutableStateOf<Rect?>(null) }
    Box(
        Modifier.matchParentSize().onGloballyPositioned {
            anchorRect = it.boundsInWindow()
            anchorScreen = it.screenRectOrNull()
        }
    )

    // Where the menu actually ended up, in SCREEN space. Null until the popup has been laid
    // out once, so the caret simply doesn't draw on the first frame — invisible under the
    // menu's own fade-in.
    //
    // Screen space is load-bearing: a popup is its OWN WINDOW, so `boundsInWindow()` inside it
    // is relative to the popup, and comparing that against the anchor's app-window bounds is
    // meaningless — which is what put the caret on the wrong side and at the wrong height.
    //
    // NOT re-keyed on `expanded`: this instance belongs to ONE anchor, so a remembered rect can
    // only be a previous opening of the same menu in the same place. Clearing it on close made
    // the exit animation recompute its own placement from stale defaults and visibly jump the
    // dismissing popup across the screen.
    var menuScreen by remember { mutableStateOf<Rect?>(null) }
    val popupView = remember { PopupViewRef() }
    val layoutDirection = LocalLayoutDirection.current

    // A NESTED menu (opened from a [MinputAction.submenu] row) — placed by the cascade rule below,
    // not by [placement]. Null for a menu summoned by an ordinary control.
    val cascadeParent = LocalMinputCascadeParent.current
    // This menu as a PARENT: where its body landed and which way its cascade runs, for its rows'
    // nested menus.
    val frame = remember { MinputMenuFrame() }
    // How a choice made anywhere in this menu closes the WHOLE cascade: a root menu just closes;
    // a nested one hands it up to its parent, which hands it up in turn — so it reaches the root
    // from any depth, never stopping one level up.
    frame.closeCascade = cascadeParent?.finish ?: onDismissRequest

    val anchorWidth = with(density) { (anchorRect?.width ?: 0f).toDp() }
    val anchorHeight = with(density) { (anchorRect?.height ?: 0f).toDp() }
    val anchorLeft = with(density) { (anchorRect?.left ?: 0f).toDp() }
    val windowWidth = with(density) { windowWidthPx.toDp() }

    // ── Placement: PREDICTED width, never the measured one ────────────────────────────────
    //
    // [menuWidth] is computed from the menu's own labels and metrics before the popup exists
    // (see [rememberMinputMenuWidth]), so it is identical on the first frame and the last.
    // That stability is the requirement, not the avoidance of a width: an earlier pass fed the
    // popup's MEASURED width back into its own offset, so placement changed once it had laid
    // itself out — and changed again when the measurement was dropped on dismissal, which is
    // what lurched a closing menu across the screen. The measurement now feeds the caret SHAPE
    // only, which repositions nothing.
    //
    // With an exact width up front, a cramped side MIRRORS to the opposite side (right-aligning
    // against the anchor is just `-(width + caret)`); [Below] is only the last resort when
    // neither side fits.
    val canFitTest = windowKnown && anchorRect != null
    fun fitsEnd() = anchorLeft + anchorWidth + menuWidth + MinputMenuCaretDepth <= windowWidth
    fun fitsStart() = anchorLeft - menuWidth - MinputMenuCaretDepth >= 0.dp
    val side = when {
        placement == MinputMenuPlacement.Below -> MinputMenuPlacement.Below
        !canFitTest -> placement
        placement == MinputMenuPlacement.End -> when {
            fitsEnd() -> MinputMenuPlacement.End
            fitsStart() -> MinputMenuPlacement.Start
            else -> MinputMenuPlacement.Below
        }
        else -> when {
            fitsStart() -> MinputMenuPlacement.Start
            fitsEnd() -> MinputMenuPlacement.End
            else -> MinputMenuPlacement.Below
        }
    }

    // M3's `offset` is anchor-relative and behaves exactly as documented: x shifts the menu's
    // start from the anchor's start, y shifts its top from the anchor's bottom. So a side
    // placement is "push clear of the anchor horizontally, then lift back up by the anchor's
    // height to line the tops up". [Below] passes zero and is pure M3.
    // A caret on a [Below] menu needs its depth of air between anchor and body; M3 applies the
    // same y offset upward when it flips the menu above the anchor.
    val sideOffset = when (side) {
        MinputMenuPlacement.Below -> if (caret) DpOffset(0.dp, MinputMenuCaretDepth) else DpOffset.Zero
        MinputMenuPlacement.End -> DpOffset(anchorWidth + MinputMenuCaretDepth, -anchorHeight)
        MinputMenuPlacement.Start -> DpOffset(-(menuWidth + MinputMenuCaretDepth), -anchorHeight)
    }

    // ── Cascade placement (2026-10-16) ────────────────────────────────────────────────────────
    //
    // A nested menu opens BESIDE ITS PARENT MENU — flush against the parent's body edge, its first
    // row level with the row that opened it — on the side [minputCascadeSide] picks (the rule the
    // virtual editor's fly-outs use): right if it fits, else left, deeper levels inheriting.
    // Beside the ROW (its anchor) would put it over the parent, and the window-space fit test above
    // is meaningless here: inside the parent's popup, "the window" is that popup. So this works in
    // SCREEN space against the app's visible display frame — which is also the space Compose
    // positions a popup-in-a-popup in, so the offset lands where it is computed.
    val cascadeOffset = cascadeParent?.let { parent ->
        val body = parent.frame.body ?: return@let null
        val row = anchorScreen ?: return@let null
        val display = android.graphics.Rect().also { view.getWindowVisibleDisplayFrame(it) }
        val widthPx = with(density) { menuWidth.toPx() }
        val cascadeSide = minputCascadeSide(
            parentLeft = body.left,
            parentRight = body.right,
            width = widthPx,
            boundsLeft = display.left.toFloat(),
            boundsRight = display.right.toFloat(),
            inherited = parent.frame.cascadeSide,
        )
        frame.cascadeSide = cascadeSide
        val left = minputCascadeLeft(cascadeSide, body.left, body.right, widthPx)
        // M3 places the menu's START at the anchor's start + x (LTR), or its END at the anchor's
        // end − x (RTL); its top at the anchor's bottom + y.
        val xPx = if (layoutDirection == LayoutDirection.Ltr) left - row.left else row.right - (left + widthPx)
        with(density) { DpOffset(xPx.toDp(), -(anchorHeight + framePadding)) }
    }
    val offset = cascadeOffset ?: sideOffset

    // 2026-10-14: [Below] carries one too — on the top edge, or the bottom once M3 flips the menu
    // above the anchor. Never on a cascade: it hangs off its parent, not off a control.
    val wantsCaret = caret && cascadeParent == null
    val vertical = side == MinputMenuPlacement.Below
    // Derived from where the menu LANDED, not from where it was asked to go — and until it has
    // landed once, PREDICTED from the anchor (M3 lines the menu's start up with the anchor's below
    // it, its top with the anchor's beside it), so the caret spawns aimed rather than sliding in.
    val caretGeometry = if (!wantsCaret) null else {
        caretGeometryFor(anchorScreen, menuScreen, vertical)
            ?: predictedCaretGeometry(side, anchorRect, with(density) { menuWidth.toPx() }, layoutDirection, view)
    }
    // ONE source of truth for which edge the caret is on: the measurement when we have it, the
    // predicted side until then. The shape and the content inset below BOTH read this — when
    // they disagreed (shape measured, inset predicted) the result was a band of menu-colored
    // surface on the opposite edge that no row could ever fill.
    val caretEdge = caretGeometry?.first ?: MinputCaretEdge.Top
    val shape = if (caretGeometry != null) {
        val (edge, centerAlongEdgePx) = caretGeometry
        remember(edge, centerAlongEdgePx) {
            MinputCaretMenuShape(
                corner = MinputMenuCorner,
                edge = edge,
                caretCenterPx = centerAlongEdgePx,
                caretHalfWidth = MinputMenuCaretWidth / 2,
                caretDepth = MinputMenuCaretDepth,
            )
        }
    } else {
        MenuDefaults.shape
    }

    // A nested menu: the d-pad pointing back at the parent steps out of it, as Back does — the
    // conventional cascade key. Consumed here so only the innermost menu reacts; its content is
    // handed a null so ITS nested menus re-provide.
    val stepOut = cascadeParent?.stepOut
    val towardParent = if (layoutDirection == LayoutDirection.Ltr) Key.DirectionLeft else Key.DirectionRight

    // A NESTED menu's popup takes every touch while it is up (it is the topmost focusable window),
    // so an outside tap only ever reached it — and closed it alone, leaving its parent open. Where
    // the tap LANDED decides (2026-10-16): on the parent menu, step back out to it (Back does the
    // same); anywhere else, the tap was a dismissal of the whole cascade.
    val outsideTap = remember { OutsideTapRef() }
    val dismiss: () -> Unit = if (cascadeParent == null) onDismissRequest else {
        {
            val tap = outsideTap.take()
            val parentBody = cascadeParent.frame.body
            if (tap == null || (parentBody != null && parentBody.contains(tap))) onDismissRequest()
            else cascadeParent.finish()
        }
    }

    val positionProvider = remember(offset, density) { MinputMenuPositionProvider(offset, density) }

    // The menu motion, not the theme's expressive spring (see MinputMenuMotion).
    MinputMenuMotionScope {
        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = dismiss,
            popupPositionProvider = positionProvider,
            properties = PopupProperties(focusable = true),
        ) {
            // The popup's own window (its root view): where the menu landed, unscaled.
            val popupRoot = LocalView.current
            popupView.view = popupRoot
            // The popup window's root sees the touch that falls outside it, before it dismisses.
            DisposableEffect(popupRoot) {
                val window = popupRoot.parent as? View
                window?.setOnTouchListener { _, event ->
                    outsideTap.record(event)
                    false
                }
                onDispose { window?.setOnTouchListener(null) }
            }
            Surface(
                shape = shape,
                color = MenuDefaults.containerColor,
                tonalElevation = MenuDefaults.TonalElevation,
                shadowElevation = MenuDefaults.ShadowElevation,
            ) {
                Column(
                    // The caret is carved OUT of the surface, so the body is [MinputMenuCaretDepth]
                    // narrower on the caret side and content has to be inset by the same amount — without
                    // it, rows sit flush against the body edge and read as missing their left padding.
                    // Inset and shape must agree on WHICH side (see [caretOnStart]); a mismatch is what
                    // produced the dead stripe.
                    modifier = modifier
                        // The PREDICTED width, enforced (2026-10-16): placement already assumes it, and
                        // every row's trailing glyph (check, cascade arrow) then sits at the end of a width
                        // reserved PAST the longest label — so no row's glyph can sit over another row's
                        // label, whichever row carries it. Measured intrinsically instead, a menu took its
                        // longest ROW's width, and a short row's arrow landed over a long row's text. Plus
                        // the caret's depth when it is carved out of a side.
                        .width(
                            menuWidth + if (wantsCaret && (caretEdge == MinputCaretEdge.Start || caretEdge == MinputCaretEdge.End)) {
                                MinputMenuCaretDepth
                            } else 0.dp,
                        )
                        .then(
                            if (stepOut == null) Modifier else Modifier.onPreviewKeyEvent { event ->
                                if (event.key != towardParent) return@onPreviewKeyEvent false
                                if (event.type == KeyEventType.KeyDown) stepOut()
                                true
                            },
                        )
                        // Where the menu landed, for the caret's aim: the popup WINDOW's own bounds, read
                        // when the menu lays out. Not this node's screen rect — that is drawn through the
                        // menu's open SCALE, so it changed every frame of the animation and the caret slid
                        // across the edge as the menu grew.
                        .onGloballyPositioned { coords ->
                            val landed = popupView.view?.screenRectOrNull() ?: coords.screenRectOrNull()
                            menuScreen = landed
                            // The BODY, for nested menus to sit flush against: the caret's carve excluded.
                            frame.body = landed?.let { r ->
                                val depth = MinputMenuCaretDepth.toPxIn(density)
                                when {
                                    !wantsCaret -> r
                                    caretEdge == MinputCaretEdge.Start -> r.copy(left = r.left + depth)
                                    caretEdge == MinputCaretEdge.End -> r.copy(right = r.right - depth)
                                    else -> r
                                }
                            }
                        }
                        // Frame inset, VERTICAL only — the single value in play (M3's own 8dp band was
                        // collapsed away while this hosted `DropdownMenu`; the popup has none).
                        //
                        // Horizontal frame inset is deliberately absent: the rows own their horizontal
                        // padding, and insetting the frame instead would leave a margin down each side
                        // that no row's highlight reaches — the same dead-stripe problem the caret inset
                        // caused. Rows run the full width; the frame only holds them off the top and
                        // bottom edges.
                        .padding(vertical = framePadding)
                        .then(
                            // Physical sides: the caret shape's Start / End are its left / right.
                            if (!wantsCaret) Modifier else when (caretEdge) {
                                MinputCaretEdge.Start -> Modifier.absolutePadding(left = MinputMenuCaretDepth)
                                MinputCaretEdge.End -> Modifier.absolutePadding(right = MinputMenuCaretDepth)
                                MinputCaretEdge.Top -> Modifier.padding(top = MinputMenuCaretDepth)
                                MinputCaretEdge.Bottom -> Modifier.padding(bottom = MinputMenuCaretDepth)
                            },
                        )
                        .verticalScroll(rememberScrollState()),
                ) {
                    CompositionLocalProvider(
                        LocalMinputMenuFrame provides frame,
                        LocalMinputCascadeParent provides null,
                    ) { content() }
                }
            }
        }
    }
}

/**
 * Where every minput menu goes: [offset] from the anchor exactly as `DropdownMenu` reads one — x
 * from the anchor's START (its end in RTL), y from its BOTTOM — then, when that runs off the
 * window, M3's own fallbacks: the end-aligned x, the flipped-above y (the same offset upward).
 * Clamped into the window with NO margin: `DropdownMenu`'s 48dp off the top and bottom is the
 * reason this exists (see [MinputMenuSurface]). Feeds the open motion its transform origin, as
 * M3's provider does.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class MinputMenuPositionProvider(
    offset: DpOffset,
    density: Density,
) : DropdownMenuPopupPositionProvider {
    override val transformOriginState = mutableStateOf(TransformOrigin.Center)
    private val dx = with(density) { offset.x.roundToPx() }
    private val dy = with(density) { offset.y.roundToPx() }

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height
        fun fitsX(x: Int) = x >= 0 && x + w <= windowSize.width
        val ltr = layoutDirection == LayoutDirection.Ltr
        val startAligned = if (ltr) anchorBounds.left + dx else anchorBounds.right - dx - w
        val endAligned = if (ltr) anchorBounds.right - dx - w else anchorBounds.left + dx
        val x = (if (fitsX(startAligned) || !fitsX(endAligned)) startAligned else endAligned)
            .coerceIn(0, (windowSize.width - w).coerceAtLeast(0))
        val below = anchorBounds.bottom + dy
        val above = anchorBounds.top - dy - h
        val y = (if (below + h <= windowSize.height || above < 0) below else above)
            .coerceIn(0, (windowSize.height - h).coerceAtLeast(0))
        val menu = IntRect(IntOffset(x, y), popupContentSize)
        transformOriginState.value = menuTransformOrigin(anchorBounds, menu)
        return IntOffset(x, y)
    }
}

/** M3's menu transform origin: the middle of where the anchor and menu overlap on each axis, or
 *  the menu edge nearest the anchor when they don't — the menu grows out of its control. */
private fun menuTransformOrigin(anchor: IntRect, menu: IntRect): TransformOrigin {
    fun pivot(anchorStart: Int, anchorEnd: Int, menuStart: Int, menuEnd: Int): Float {
        val size = menuEnd - menuStart
        return when {
            menuStart >= anchorEnd -> 0f
            menuEnd <= anchorStart -> 1f
            size == 0 -> 0f
            else -> {
                val mid = (maxOf(anchorStart, menuStart) + minOf(anchorEnd, menuEnd)) / 2
                (mid - menuStart).toFloat() / size
            }
        }
    }
    return TransformOrigin(
        pivot(anchor.left, anchor.right, menu.left, menu.right),
        pivot(anchor.top, anchor.bottom, menu.top, menu.bottom),
    )
}

/** The last touch the menu popup's window saw (screen space), for a nested menu to tell an
 *  outside tap on its parent from one anywhere else. Good only for the dismissal that touch
 *  causes — Back dismisses with nothing recent, and steps out one level. */
private class OutsideTapRef {
    private var point: Offset? = null
    private var at = 0L

    fun record(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN && event.actionMasked != MotionEvent.ACTION_OUTSIDE) return
        point = Offset(event.rawX, event.rawY)
        at = event.eventTime
    }

    fun take(): Offset? {
        val recent = point?.takeIf { SystemClock.uptimeMillis() - at < OutsideTapWindowMillis }
        point = null
        return recent
    }
}

/** How old a recorded touch may be and still be the one that caused a dismissal — it is
 *  synchronous in practice; this only rules out a stale touch meeting a later Back. */
private const val OutsideTapWindowMillis = 250L

/** Holds the menu popup's root view — written as the popup composes, read as it lays out. */
private class PopupViewRef {
    var view: View? = null
}

private fun View.screenRectOrNull(): Rect? {
    if (width <= 0 || height <= 0) return null
    val location = IntArray(2)
    getLocationOnScreen(location)
    return Rect(location[0].toFloat(), location[1].toFloat(), (location[0] + width).toFloat(), (location[1] + height).toFloat())
}

/**
 * The caret before the menu has landed once: M3 places a [MinputMenuPlacement.Below] menu with
 * its start against the anchor's start — below it, or above it when there isn't room (judged
 * here by the roomier side; the measurement corrects a miss) — and a side menu (our offset) with
 * its top against the anchor's top. So the aim is the anchor's own centre from that corner.
 */
private fun predictedCaretGeometry(
    side: MinputMenuPlacement,
    anchor: Rect?,
    menuWidthPx: Float,
    layoutDirection: LayoutDirection,
    hostView: View,
): Pair<MinputCaretEdge, Float>? {
    if (anchor == null) return null
    val endIsRight = layoutDirection == LayoutDirection.Ltr
    return when (side) {
        MinputMenuPlacement.Below -> {
            val roomBelow = hostView.rootView.height - anchor.bottom
            val edge = if (roomBelow >= anchor.top) MinputCaretEdge.Top else MinputCaretEdge.Bottom
            edge to (if (endIsRight) anchor.width / 2 else menuWidthPx - anchor.width / 2)
        }
        // A menu to the anchor's END faces back at it with its start edge (physical left in LTR).
        MinputMenuPlacement.End -> (if (endIsRight) MinputCaretEdge.Start else MinputCaretEdge.End) to anchor.height / 2
        MinputMenuPlacement.Start -> (if (endIsRight) MinputCaretEdge.End else MinputCaretEdge.Start) to anchor.height / 2
    }
}

/**
 * The width a minput menu will take, worked out from its labels rather than read back from the
 * laid-out popup.
 *
 * Predicting it is the point. Placement needs a width (to mirror a menu onto the start side, it
 * has to be right-aligned against the anchor), and taking that from the popup's own measurement
 * is a feedback loop: the menu lands, reports its size, and moves. Because minput owns the row
 * layout, the width is simply decor + the widest label, and a [TextMeasurer] gives the label
 * part exactly — before anything is shown, and identically on every frame after.
 *
 * Kept within M3's own menu envelope. Labels wider than the cap ellipsize.
 */
@Composable
private fun rememberMinputMenuWidth(
    labels: List<String>,
    hasLeadingIcon: Boolean,
    hasTrailingIcon: Boolean,
    /** The trailing glyph's width: the check's slot by default; a cascade-only menu passes the
     *  (much narrower) [minputSubmenuArrowWidth]. */
    trailingWidth: Dp = MinputMenuIconSize.dp,
): Dp {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    return remember(labels, hasLeadingIcon, hasTrailingIcon, trailingWidth, style, density) {
        val widestLabelPx = labels.maxOfOrNull { measurer.measure(it, style).size.width } ?: 0
        val decor = MinputMenuItemPadding * 2 +
            (if (hasLeadingIcon) MinputMenuIconSize.dp + MinputMenuItemPadding else 0.dp) +
            (if (hasTrailingIcon) trailingWidth + MinputMenuItemPadding else 0.dp)
        val label = with(density) { widestLabelPx.toDp() }
        (decor + label + MinputMenuWidthSlack)
            .coerceIn(MinputMenuMinWidth, MinputMenuMaxWidth)
    }
}

/**
 * Which edge the caret belongs on, and how far along that edge it points, from the menu's and
 * the anchor's measured positions. Null until both are known.
 *
 * A side menu ([vertical] false): Start when the menu is to the END of the anchor, the distance
 * down from its top. A [MinputMenuPlacement.Below] menu: Top when it hangs below the anchor,
 * Bottom when M3 flipped it above, the distance in from its left.
 */
private fun caretGeometryFor(anchor: Rect?, menu: Rect?, vertical: Boolean): Pair<MinputCaretEdge, Float>? {
    if (anchor == null || menu == null || menu.width <= 0f) return null
    if (vertical) {
        val edge = if (anchor.center.y <= menu.center.y) MinputCaretEdge.Top else MinputCaretEdge.Bottom
        return edge to (anchor.center.x - menu.left)
    }
    val edge = if (anchor.center.x <= menu.center.x) MinputCaretEdge.Start else MinputCaretEdge.End
    return edge to (anchor.center.y - menu.top)
}

/** This node's bounds in SCREEN coordinates, or null when they aren't resolvable yet.
 *  Screen space is the only frame shared by a popup and the app window behind it. */
private fun LayoutCoordinates.screenRectOrNull(): Rect? {
    val origin = positionOnScreen()
    if (origin.isUnspecified) return null
    return Rect(origin, Size(size.width.toFloat(), size.height.toFloat()))
}

/**
 * One row.
 *
 * Hand-built rather than wrapped, which is a deliberate exception to the library's
 * styling-over-reinvention rule, and the reason is in M3's own numbers: `DropdownMenuItem`
 * pins `sizeIn(minWidth = 112.dp, minHeight = 48.dp)` on its row. The height is a
 * touch-target floor these dense, controller-navigated menus don't take; the WIDTH floor is
 * worse, because the menu Column sizes itself to `IntrinsicSize.Max` of its rows — so a short
 * verb menu ("New" / "Paste") is forced out to 112dp and every label trails a block of dead
 * space that reads as an oversized right padding. Both floors are set INSIDE the slot the
 * `modifier` parameter reaches, so neither can be relaxed from a call site.
 *
 * What is still M3's, and must stay M3's: the ripple, and the enabled/disabled color
 * resolution — [colors] comes from `MenuDefaults.itemColors()` and is read through its public
 * enabled/disabled pairs. (Hard-coding a color here is what once made a greyed "Paste" look
 * identical to "New"; going through [colors] keeps that automatic.) The resolved color is
 * provided as `LocalContentColor` so caller-supplied glyphs inherit it without every call
 * site re-deriving it.
 *
 * Spacing is ONE value on all four gaps — see [MinputMenuItemPadding].
 */
@Composable
internal fun MinputMenuRow(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    /** Holds the row lit while something it opened is up — a cascade row while its nested menu
     *  shows, so the path through the menus stays visible. M3's hover state-layer strength. */
    active: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (enabled) colors.textColor else colors.disabledTextColor
    val interaction = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(MinputMenuItemHeight)
                .then(
                    if (active) {
                        Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = MinputMenuActiveRowAlpha))
                    } else Modifier,
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = ripple(),
                    onClick = onClick,
                )
                .padding(horizontal = MinputMenuItemPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(MinputMenuItemPadding))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailingIcon != null) {
                Spacer(Modifier.width(MinputMenuItemPadding))
                trailingIcon()
            }
        }
    }
}

/** Which edge of a menu surface its caret protrudes from. Start/End are the side placements;
 *  Top/Bottom exist for [MinputMenuPanel], which can sit above or below what summoned it. */
enum class MinputCaretEdge { Start, End, Top, Bottom }

/**
 * The menu surface's outline with a caret pointing back at the anchor: a rounded rect inset by
 * [caretDepth] on the [edge] side, plus a triangle filling that inset. [caretCenterPx] is the
 * caret's position ALONG that edge — from the top for Start/End, from the left for Top/Bottom.
 *
 * A [Shape] rather than a drawn overlay so the caret is part of the surface — it inherits the
 * menu's fill, border and shadow for free, and no second layer can drift out of alignment.
 */
internal class MinputCaretMenuShape(
    private val corner: Dp,
    private val edge: MinputCaretEdge,
    private val caretCenterPx: Float,
    private val caretHalfWidth: Dp,
    private val caretDepth: Dp,
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = with(density) { corner.toPx() }
        val depth = with(density) { caretDepth.toPx() }
        val half = with(density) { caretHalfWidth.toPx() }
        val vertical = edge == MinputCaretEdge.Start || edge == MinputCaretEdge.End
        // Keep the caret's base inside the straight run between the corners so it never grows
        // out of an arc — and so a menu shorter than its anchor still resolves sanely.
        val run = if (vertical) size.height else size.width
        val lo = r + half
        val hi = (run - r - half).coerceAtLeast(lo)
        val center = caretCenterPx.coerceIn(lo, hi)

        val left = if (edge == MinputCaretEdge.Start) depth else 0f
        val right = if (edge == MinputCaretEdge.End) size.width - depth else size.width
        val top = if (edge == MinputCaretEdge.Top) depth else 0f
        val bottom = if (edge == MinputCaretEdge.Bottom) size.height - depth else size.height

        val path = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                )
            )
            // Triangle, apex pointing at the anchor.
            when (edge) {
                MinputCaretEdge.Start -> {
                    moveTo(left, center - half); lineTo(left - depth, center); lineTo(left, center + half)
                }
                MinputCaretEdge.End -> {
                    moveTo(right, center - half); lineTo(right + depth, center); lineTo(right, center + half)
                }
                MinputCaretEdge.Top -> {
                    moveTo(center - half, top); lineTo(center, top - depth); lineTo(center + half, top)
                }
                MinputCaretEdge.Bottom -> {
                    moveTo(center - half, bottom); lineTo(center, bottom + depth); lineTo(center + half, bottom)
                }
            }
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * The minput ACTION menu: a flat list of one-line commands summoned by a control.
 *
 * Distinct from [MinputDropdownMenu], which is a single-CHOICE picker — it tracks a `current`
 * value and renders a check on it. Nothing here is "current": every row is a verb, so there is
 * no selection state and no trailing check.
 *
 * Deliberately single-line, with NO helper text: tile and row menus name self-evident verbs
 * (Edit / Copy / Paste / Move / Clear) where a second line of prose would be noise. The
 * two-line icon + title + helper form lives on `RichMenuItem` in the remap package — reach for
 * that when an action genuinely needs tutorializing.
 *
 * Picking dismisses FIRST, then invokes, so a handler that opens another surface isn't racing
 * this menu's exit animation.
 *
 * Place it in a `Box` alongside the control that summons it.
 *
 * @param placement where the menu sits relative to its anchor.
 * @param caret draws the pointer back at the summoning control.
 * @param framePadding inset above the first row and below the last. Defaults to
 *   [MinputMenuFramePadding]; pass `0.dp` for rows flush with the menu's ends.
 */
@Composable
fun BoxScope.MinputActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<MinputAction>,
    modifier: Modifier = Modifier,
    placement: MinputMenuPlacement = MinputMenuPlacement.Below,
    caret: Boolean = false,
    framePadding: Dp = MinputMenuFramePadding,
) {
    MinputMenuSurface(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        placement = placement,
        caret = caret,
        framePadding = framePadding,
        menuWidth = rememberMinputMenuWidth(
            labels = actions.map { it.label },
            hasLeadingIcon = true,
            // A cascade row's arrow.
            hasTrailingIcon = actions.any { it.submenu != null },
            trailingWidth = minputSubmenuArrowWidth(),
        ),
        modifier = modifier,
    ) {
        actions.forEach { action -> MinputActionRow(action, menuExpanded = expanded, onDismissMenu = onDismissRequest) }
    }
}

/**
 * One [MinputAction] as a menu row — THE action row, shared by [MinputActionMenu] and
 * [MinputDropdownMenu]'s action groups so a verb (and a cascade) reads the same in both.
 *
 * A plain verb closes the WHOLE cascade first (its own menu and every menu above it, however
 * deep it sits — [MinputMenuFrame.closeCascade]), then invokes; a [MinputAction.checked] toggle
 * invokes and leaves the menus up. A [MinputAction.submenu] row instead opens its nested menu
 * beside itself and leaves its own menu up: the trailing [MinputSubmenuArrow] is drawn HERE (the
 * primitive owns the cascade affordance, never the call site), the row stays lit while the nested
 * menu shows, and the d-pad toward the arrow opens it too. A choice landing in the nested menu —
 * or [MinputSubmenu.onFinished] — closes the whole cascade too; stepping OUT (Back, an outside tap
 * on this menu) closes only the nested one.
 */
@Composable
internal fun MinputActionRow(
    action: MinputAction,
    menuExpanded: Boolean,
    onDismissMenu: () -> Unit,
) {
    val colors = if (action.destructive) {
        MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.error,
            leadingIconColor = MaterialTheme.colorScheme.error,
        )
    } else {
        MenuDefaults.itemColors()
    }
    val leading: @Composable () -> Unit = {
        MinputIcon(action.icon, contentDescription = null, size = MinputMenuIconSize, slot = true)
    }
    // The menu this row sits in. Read when a choice lands, so it is the current one.
    val menuFrame = LocalMinputMenuFrame.current
    val closeCascade: () -> Unit = { menuFrame?.closeCascade?.invoke() ?: onDismissMenu() }
    val checked = action.checked
    if (checked != null) {
        MinputMenuRow(
            label = action.label,
            enabled = action.enabled,
            colors = colors,
            leadingIcon = leading,
            // The row is the target; the box only shows the state.
            trailingIcon = { MinputCheckbox(checked = checked, onCheckedChange = null, enabled = action.enabled) },
            onClick = action.onClick,
            modifier = Modifier.semantics {
                role = Role.Checkbox
                toggleableState = ToggleableState(checked)
            },
        )
        return
    }
    val submenu = action.submenu
    if (submenu == null) {
        MinputMenuRow(
            label = action.label,
            enabled = action.enabled,
            colors = colors,
            leadingIcon = leading,
            onClick = { closeCascade(); action.onClick() },
        )
        return
    }
    var open by remember { mutableStateOf(false) }
    val towardArrow = if (LocalLayoutDirection.current == LayoutDirection.Rtl) Key.DirectionLeft else Key.DirectionRight
    Box {
        MinputMenuRow(
            label = action.label,
            enabled = action.enabled,
            colors = colors,
            leadingIcon = leading,
            trailingIcon = { MinputSubmenuArrow() },
            active = open,
            onClick = { open = true },
            modifier = Modifier.onKeyEvent { event ->
                if (!action.enabled || event.key != towardArrow) return@onKeyEvent false
                if (event.type == KeyEventType.KeyDown) open = true
                true
            },
        )
        val cascadeParent = remember(menuFrame) {
            menuFrame?.let {
                MinputCascadeParent(it, stepOut = { open = false }, finish = { open = false; closeCascade() })
            }
        }
        CompositionLocalProvider(LocalMinputCascadeParent provides cascadeParent) {
            submenu(
                MinputSubmenu(
                    // Never outlives its parent, whatever closed that.
                    expanded = open && menuExpanded,
                    onDismissRequest = { open = false },
                    onFinished = { open = false; closeCascade() },
                ),
            )
        }
    }
}

/**
 * THE cascade affordance: the small SOLID right-pointing triangle a row wears when it opens a
 * nested menu — the conventional desktop/M3 submenu arrow, not a stroked chevron (a chevron at the
 * menu's glyph size shouted next to the labels; Dylan, 2026-10-16). Sized by its ink at
 * [MinputSubmenuArrowSize], with NO slot (it's the row's last element, nothing aligns past it);
 * auto-mirrored, so it points toward the nested menu in RTL too. Tints with the row's content
 * color by default, so a disabled row greys it with its label.
 */
@Composable
fun MinputSubmenuArrow(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    MinputIcon(
        Icons.AutoMirrored.Filled.ArrowRight,
        contentDescription = null,
        size = MinputSubmenuArrowSize,
        tint = tint,
        modifier = modifier,
    )
}

/** The cascade arrow's ink size — the dropdown-triangle scale ([MinputDropdownArrowSize]'s step). */
val MinputSubmenuArrowSize = MinputIconSize.Xxs

/** The width the cascade arrow draws at — what a menu reserves past its longest label for it. */
internal fun minputSubmenuArrowWidth(): Dp =
    Icons.AutoMirrored.Filled.ArrowRight.minputInkWidth(MinputSubmenuArrowSize.dp)

/** A menu as a cascade PARENT: where its body landed on screen and which way its cascade runs.
 *  Written as the menu lays out / places itself; read by its rows' nested menus. */
private class MinputMenuFrame {
    var body by mutableStateOf<Rect?>(null)
    var cascadeSide: MinputCascadeSide? = null
    /** Closes every menu from this one up to the root — set by [MinputMenuSurface] each
     *  composition: the root's own dismissal, or (nested) its parent's [MinputCascadeParent.finish],
     *  which closes this level and calls the parent frame's [closeCascade] in turn. */
    var closeCascade: () -> Unit = {}
}

/** What a nested menu knows of its parent: its [frame], how to step back out to it (the d-pad
 *  key pointing back at the parent, bound by [MinputMenuSurface]), and how to close the whole
 *  cascade from the root down ([finish] — a choice, or an outside tap that missed the parent). */
private class MinputCascadeParent(val frame: MinputMenuFrame, val stepOut: () -> Unit, val finish: () -> Unit)

/** Provided by every [MinputMenuSurface] to its own content: the menu the rows sit in. */
private val LocalMinputMenuFrame = compositionLocalOf<MinputMenuFrame?> { null }

/** Set by a cascade row around its nested menu; null everywhere else. */
private val LocalMinputCascadeParent = compositionLocalOf<MinputCascadeParent?> { null }

private fun Dp.toPxIn(density: Density): Float = with(density) { toPx() }

/**
 * The minput single-CHOICE menu: options with a check on the current one. The menu half of
 * [MinputPillDropdown], detached from the label-pill anchor so any control can summon it (an
 * icon-only sort button, a card's overflow).
 *
 * Place it in a `Box` alongside the anchor. Picking closes the menu first (the whole cascade, when
 * it is nested), then commits only on an actual change.
 *
 * @param placement where the menu sits relative to its anchor.
 * @param caret draws the pointer back at the summoning control.
 * @param framePadding inset above the first row and below the last. Defaults to
 *   [MinputMenuFramePadding]; pass `0.dp` for rows flush with the menu's ends.
 * @param actionGroups verbs that live with the choice (2026-10-14 — the hierarchy bar's pin menu
 *   carries the crumbs' collapse toggles): each group follows the options under a divider, its
 *   rows drawn as [MinputActionMenu]'s. Picking one closes the menu (cascade) first, then invokes.
 */
@Composable
fun <T> BoxScope.MinputDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    current: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onPick: (T) -> Unit,
    optionIcon: (@Composable (T) -> ImageVector?)? = null,
    modifier: Modifier = Modifier,
    placement: MinputMenuPlacement = MinputMenuPlacement.Below,
    caret: Boolean = false,
    framePadding: Dp = MinputMenuFramePadding,
    actionGroups: List<List<MinputAction>> = emptyList(),
) {
    val actions = actionGroups.flatten()
    MinputMenuSurface(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        placement = placement,
        caret = caret,
        framePadding = framePadding,
        menuWidth = rememberMinputMenuWidth(
            labels = options.map(optionLabel) + actions.map { it.label },
            hasLeadingIcon = optionIcon != null || actions.isNotEmpty(),
            // The check on the current option (wider than any cascade row's arrow).
            hasTrailingIcon = true,
        ),
        modifier = modifier,
    ) {
        // A pick closes the whole cascade, as a verb does (see MinputActionRow).
        val menuFrame = LocalMinputMenuFrame.current
        options.forEach { option ->
            val menuIcon = optionIcon?.invoke(option)
            MinputMenuRow(
                label = optionLabel(option),
                enabled = true,
                leadingIcon = menuIcon?.let {
                    {
                        MinputIcon(it, contentDescription = null, size = MinputMenuIconSize, slot = true)
                    }
                },
                trailingIcon = if (option == current) {
                    {
                        MinputIcon(Icons.Filled.Check, contentDescription = null, size = MinputMenuIconSize, slot = true)
                    }
                } else null,
                onClick = {
                    menuFrame?.closeCascade?.invoke() ?: onDismissRequest()
                    if (option != current) onPick(option)
                },
            )
        }
        actionGroups.filter { it.isNotEmpty() }.forEach { group ->
            HorizontalDivider(Modifier.padding(vertical = MinputMenuFramePadding))
            group.forEach { action -> MinputActionRow(action, menuExpanded = expanded, onDismissMenu = onDismissRequest) }
        }
    }
}

// ── Shared menu metrics ──────────────────────────────────────────────────────────────────────

/** A dp or two of headroom on the predicted width, so a label that measures to exactly the
 *  available space doesn't ellipsize on a rounding difference. */
private val MinputMenuWidthSlack = 2.dp

/** Envelope for the predicted width. The cap is M3's own `DropdownMenuItemDefaultMaxWidth`; the
 *  floor keeps a one-word menu from collapsing to a sliver. */
private val MinputMenuMinWidth = 96.dp
private val MinputMenuMaxWidth = 280.dp

/** Glyph edge in a menu row. M3 defaults to a 24dp icon, which shouts next to this app's
 *  menu type scale; the SLOT stays M3's width (so labels still align down the column) and only
 *  the glyph inside it is scaled back. Sizing the glyph is chrome — minput's job; changing the
 *  slot or the row metrics is M3's, and left alone. */
internal val MinputMenuIconSize = MinputIconSize.M

/** Row height. Below M3's 48dp touch-target container — deliberately; see [MinputMenuRow]. */
internal val MinputMenuItemHeight = 34.dp

/** THE menu-row spacing value: row start → glyph, glyph → label, label → check, check → row
 *  end. One number for all four so the row reads as evenly set; changing it moves them
 *  together.
 *
 *  Note on what this LOOKS like: the glyph→label gap reads a little wider than the numbers
 *  say, because Material glyphs ink only ~85% of their viewport (the same optical mismatch
 *  behind the library's move to Lucide). The spacing is even; the icon is what's narrow. Don't
 *  "correct" it by shrinking this gap alone — that just makes the numbers uneven too. */
internal val MinputMenuItemPadding = 12.dp

/**
 * Inset above the first row and below the last — the menu FRAME, as distinct from the row
 * padding. Applied instead of (not on top of) M3's own 8dp band.
 *
 * Deliberately SMALLER than [MinputMenuItemPadding] and tracked as its own value. Matching the
 * two looked wrong in both directions: a row already carries vertical breathing room in its own
 * height, so repeating the horizontal value at the frame reads as a gap at each end, while
 * collapsing the frame to nothing pins the first and last rows against the menu's rounded ends.
 * The horizontal and vertical frame insets are simply not the same quantity.
 */
val MinputMenuFramePadding = 5.dp

internal val MinputMenuCorner = 8.dp

/** A cascade row's lit state while its nested menu is up — M3's hover state-layer opacity. */
private const val MinputMenuActiveRowAlpha = 0.08f

/** How far the caret protrudes from the menu body, and how wide its base is. */
internal val MinputMenuCaretDepth = 6.dp
internal val MinputMenuCaretWidth = 12.dp
