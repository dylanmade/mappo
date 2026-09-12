package com.mappo.ui.minput

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

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
 * **This is a thin layer over M3's `DropdownMenu`, deliberately.** How the menu measures,
 * animates, positions and dismisses is all M3's; minput contributes exactly two things — an
 * optional side placement expressed through M3's own `offset` parameter, and an optional
 * caret expressed as the surface's `shape`.
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
@Composable
internal fun BoxScope.MinputMenuSurface(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    placement: MinputMenuPlacement,
    caret: Boolean,
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
    // menu's own fade-in. Re-keyed on `expanded` so a re-opened menu re-measures rather than
    // trusting stale bounds from wherever it sat last time.
    //
    // Screen space is load-bearing: a popup is its OWN WINDOW, so `boundsInWindow()` inside it
    // is relative to the popup, and comparing that against the anchor's app-window bounds is
    // meaningless — which is what put the caret on the wrong side and at the wrong height.
    var menuScreen by remember(expanded) { mutableStateOf<Rect?>(null) }

    val anchorWidth = with(density) { (anchorRect?.width ?: 0f).toDp() }
    val anchorHeight = with(density) { (anchorRect?.height ?: 0f).toDp() }
    val anchorLeft = with(density) { (anchorRect?.left ?: 0f).toDp() }
    val windowWidth = with(density) { windowWidthPx.toDp() }
    val measuredMenuWidth = with(density) { (menuScreen?.width ?: 0f).toDp() }

    // Width for the fit test and the start-side offset: measured once we have it, a
    // conservative stand-in before.
    val assumedWidth = if (measuredMenuWidth > 0.dp) measuredMenuWidth else MinputMenuAssumedWidth

    // Only second-guess the caller's requested side when the window and the anchor are both
    // known. Without them, honor the request and let M3 clamp — a slightly clipped menu on the
    // requested side beats a confidently mirrored one nowhere near the control.
    val canFitTest = windowKnown && anchorRect != null
    val side = when {
        placement == MinputMenuPlacement.Below -> MinputMenuPlacement.Below
        !canFitTest -> placement
        placement == MinputMenuPlacement.End ->
            if (anchorLeft + anchorWidth + assumedWidth + MinputMenuCaretDepth <= windowWidth) {
                MinputMenuPlacement.End
            } else MinputMenuPlacement.Start
        else ->
            if (anchorLeft - assumedWidth - MinputMenuCaretDepth >= 0.dp) {
                MinputMenuPlacement.Start
            } else MinputMenuPlacement.End
    }

    // M3's `offset` is anchor-relative and behaves exactly as documented: x shifts the menu's
    // start from the anchor's start, y shifts its top from the anchor's bottom. So a side
    // placement is "push clear of the anchor horizontally, then lift back up by the anchor's
    // height to line the tops up". [Below] passes zero and is pure M3.
    val offset = when (side) {
        MinputMenuPlacement.Below -> DpOffset.Zero
        MinputMenuPlacement.End -> DpOffset(anchorWidth + MinputMenuCaretDepth, -anchorHeight)
        MinputMenuPlacement.Start -> DpOffset(-(assumedWidth + MinputMenuCaretDepth), -anchorHeight)
    }

    val wantsCaret = caret && side != MinputMenuPlacement.Below
    // Derived from where the menu LANDED, not from where it was asked to go.
    val caretGeometry = if (wantsCaret) caretGeometryFor(anchorScreen, menuScreen) else null
    // ONE source of truth for which edge the caret is on: the measurement when we have it, the
    // predicted side until then. The shape and the content inset below BOTH read this — when
    // they disagreed (shape measured, inset predicted) the result was a band of menu-colored
    // surface on the opposite edge that no row could ever fill.
    val caretOnStart = caretGeometry?.first ?: (side == MinputMenuPlacement.End)
    val shape = if (caretGeometry != null) {
        val (onStart, centerFromTopPx) = caretGeometry
        remember(onStart, centerFromTopPx) {
            MinputCaretMenuShape(
                corner = MinputMenuCorner,
                caretOnStart = onStart,
                caretCenterFromTopPx = centerFromTopPx,
                caretHalfWidth = MinputMenuCaretWidth / 2,
                caretDepth = MinputMenuCaretDepth,
            )
        }
    } else {
        MenuDefaults.shape
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        shape = shape,
        // The caret is carved OUT of the surface, so the body is [MinputMenuCaretDepth]
        // narrower on the caret side and content has to be inset by the same amount — without
        // it, rows sit flush against the body edge and read as missing their left padding.
        // Inset and shape must agree on WHICH side (see [caretOnStart]); a mismatch is what
        // produced the dead stripe.
        modifier = modifier
            .onGloballyPositioned { menuScreen = it.screenRectOrNull() }
            .then(
                when {
                    !wantsCaret -> Modifier
                    caretOnStart -> Modifier.padding(start = MinputMenuCaretDepth)
                    else -> Modifier.padding(end = MinputMenuCaretDepth)
                },
            ),
        content = content,
    )
}

/**
 * Which edge the caret belongs on, and how far down that edge it points, from the menu's and
 * the anchor's measured positions. Null until both are known.
 *
 * `true` = the caret sits on the menu's START edge (i.e. the menu is to the END of the anchor).
 */
private fun caretGeometryFor(anchor: Rect?, menu: Rect?): Pair<Boolean, Float>? {
    if (anchor == null || menu == null || menu.width <= 0f) return null
    val onStart = anchor.center.x <= menu.center.x
    return onStart to (anchor.center.y - menu.top)
}

/** This node's bounds in SCREEN coordinates, or null when they aren't resolvable yet.
 *  Screen space is the only frame shared by a popup and the app window behind it. */
private fun LayoutCoordinates.screenRectOrNull(): Rect? {
    val origin = positionOnScreen()
    if (origin.isUnspecified) return null
    return Rect(origin, Size(size.width.toFloat(), size.height.toFloat()))
}

/**
 * One row. A pass-through to M3's `DropdownMenuItem` — its type scale, its metrics, its
 * enabled/disabled color resolution.
 *
 * **Disabled state is M3's to handle, and it only works if nothing overrides it.** Passing an
 * explicit color to the `Text`/`Icon` inside the slots defeats `MenuDefaults.itemColors()` and
 * a disabled row renders identically to an enabled one — which shipped, as a greyed "Paste"
 * indistinguishable from "New". A color variation therefore goes through [colors], never
 * through a slot.
 */
@Composable
internal fun MinputMenuRow(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    colors: MenuItemColors = MenuDefaults.itemColors(),
) {
    DropdownMenuItem(
        enabled = enabled,
        colors = colors,
        // Two metrics minput overrides on a menu row: the height (M3's 48dp is a
        // touch-target container; these are dense, controller-navigated surfaces, the trade
        // the library takes everywhere) and a UNIFORM content padding.
        modifier = Modifier.height(MinputMenuItemHeight),
        contentPadding = PaddingValues(horizontal = MinputMenuItemPadding),
        // Icon and check ride INSIDE the text slot rather than M3's leading/trailing slots.
        // Those slots carry a `defaultMinSize` of the full 24dp list-icon width, so a smaller
        // glyph sits at the start of an oversized box and leaves dead space on one side — the
        // gap between icon and label ended up wider than the padding on either end of the row,
        // and no combination of paddings could even them up from outside.
        //
        // The text slot is wrapped by M3 in the resolved text color, so glyphs placed here
        // still pick up enabled/disabled and the destructive variant for free — the disabled
        // treatment stays M3's, which is the part that matters.
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(MinputMenuItemPadding))
                }
                Text(
                    label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (trailingIcon != null) {
                    Spacer(Modifier.width(MinputMenuItemPadding))
                    trailingIcon()
                }
            }
        },
        onClick = onClick,
    )
}

/**
 * The menu surface's outline with a caret pointing back at the anchor: a rounded rect inset by
 * [caretDepth] on the anchor side, plus a triangle filling that inset.
 *
 * A [Shape] rather than a drawn overlay so the caret is part of the surface — it inherits the
 * menu's fill, border and shadow for free, and no second layer can drift out of alignment.
 */
private class MinputCaretMenuShape(
    private val corner: Dp,
    private val caretOnStart: Boolean,
    private val caretCenterFromTopPx: Float,
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
        // Keep the caret's base inside the straight run between the corners so it never grows
        // out of an arc — and so a menu shorter than its anchor still resolves sanely.
        val lo = r + half
        val hi = (size.height - r - half).coerceAtLeast(lo)
        val center = caretCenterFromTopPx.coerceIn(lo, hi)

        val left = if (caretOnStart) depth else 0f
        val right = if (caretOnStart) size.width else size.width - depth

        val path = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = left,
                    top = 0f,
                    right = right,
                    bottom = size.height,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                )
            )
            // Triangle, apex pointing at the anchor.
            moveTo(if (caretOnStart) left else right, center - half)
            lineTo(if (caretOnStart) left - depth else right + depth, center)
            lineTo(if (caretOnStart) left else right, center + half)
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
 */
@Composable
fun BoxScope.MinputActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<MinputAction>,
    modifier: Modifier = Modifier,
    placement: MinputMenuPlacement = MinputMenuPlacement.Below,
    caret: Boolean = false,
) {
    MinputMenuSurface(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        placement = placement,
        caret = caret,
        modifier = modifier,
    ) {
        actions.forEach { action ->
            val colors = if (action.destructive) {
                MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                )
            } else {
                MenuDefaults.itemColors()
            }
            MinputMenuRow(
                label = action.label,
                enabled = action.enabled,
                colors = colors,
                leadingIcon = {
                    Icon(
                        action.icon,
                        contentDescription = null,
                        modifier = Modifier.size(MinputMenuIconSize),
                    )
                },
                onClick = { onDismissRequest(); action.onClick() },
            )
        }
    }
}

/**
 * The minput single-CHOICE menu: options with a check on the current one. The menu half of
 * [MinputPillDropdown], detached from the label-pill anchor so any control can summon it (an
 * icon-only sort button, a card's overflow).
 *
 * Place it in a `Box` alongside the anchor. Picking dismisses first, then commits only on an
 * actual change.
 */
@Composable
fun <T> BoxScope.MinputDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    current: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onPick: (T) -> Unit,
    optionIcon: (@Composable (T) -> Painter?)? = null,
    modifier: Modifier = Modifier,
    placement: MinputMenuPlacement = MinputMenuPlacement.Below,
    caret: Boolean = false,
) {
    MinputMenuSurface(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        placement = placement,
        caret = caret,
        modifier = modifier,
    ) {
        options.forEach { option ->
            val menuIcon = optionIcon?.invoke(option)
            MinputMenuRow(
                label = optionLabel(option),
                enabled = true,
                leadingIcon = menuIcon?.let {
                    {
                        Icon(
                            it,
                            contentDescription = null,
                            modifier = Modifier.size(MinputMenuIconSize),
                        )
                    }
                },
                trailingIcon = if (option == current) {
                    {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(MinputMenuIconSize),
                        )
                    }
                } else null,
                onClick = { onDismissRequest(); if (option != current) onPick(option) },
            )
        }
    }
}

// ── Shared menu metrics ──────────────────────────────────────────────────────────────────────

/** Stand-in width for the side-placement fit test on the first frame, before the menu has been
 *  measured. M3's own `DropdownMenuItemDefaultMaxWidth` is 280dp; this sits mid-range so the
 *  first guess is rarely wrong, and it's replaced by the real width as soon as there is one. */
private val MinputMenuAssumedWidth = 180.dp

/** Glyph edge in a menu row. M3 defaults to a 24dp icon, which shouts next to this app's
 *  menu type scale; the SLOT stays M3's width (so labels still align down the column) and only
 *  the glyph inside it is scaled back. Sizing the glyph is chrome — minput's job; changing the
 *  slot or the row metrics is M3's, and left alone. */
private val MinputMenuIconSize = 16.dp

/** Row height. Below M3's 48dp touch-target container — deliberately; see [MinputMenuRow]. */
private val MinputMenuItemHeight = 34.dp

/** THE menu-row spacing value: row start → glyph, glyph → label, label → check, check → row
 *  end. One number for all four so the row reads as evenly set; changing it moves them
 *  together. */
private val MinputMenuItemPadding = 12.dp

private val MinputMenuCorner = 8.dp

/** How far the caret protrudes from the menu body, and how wide its base is. */
private val MinputMenuCaretDepth = 6.dp
private val MinputMenuCaretWidth = 12.dp
