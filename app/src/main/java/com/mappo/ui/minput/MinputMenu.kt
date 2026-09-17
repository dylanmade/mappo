package com.mappo.ui.minput

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.rememberTextMeasurer
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
    val offset = when (side) {
        MinputMenuPlacement.Below -> DpOffset.Zero
        MinputMenuPlacement.End -> DpOffset(anchorWidth + MinputMenuCaretDepth, -anchorHeight)
        MinputMenuPlacement.Start -> DpOffset(-(menuWidth + MinputMenuCaretDepth), -anchorHeight)
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
                edge = if (onStart) MinputCaretEdge.Start else MinputCaretEdge.End,
                caretCenterPx = centerFromTopPx,
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
            // OUTERMOST of minput's additions, so it reports the menu's FINAL bounds — the
            // caret's vertical aim is measured from this, and reading the pre-collapse box
            // would skew it by the padding being removed.
            .onGloballyPositioned { menuScreen = it.screenRectOrNull() }
            // Frame inset, VERTICAL only. M3's own 8dp band is collapsed away first so this is
            // the single value in play rather than a stack of two.
            //
            // Horizontal frame inset is deliberately absent: the rows own their horizontal
            // padding, and insetting the frame instead would leave a margin down each side
            // that no row's highlight reaches — the same dead-stripe problem the caret inset
            // caused. Rows run the full width; the frame only holds them off the top and
            // bottom edges.
            .padding(vertical = framePadding)
            .collapseMenuVerticalPadding(collapse = true)
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
): Dp {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    return remember(labels, hasLeadingIcon, hasTrailingIcon, style, density) {
        val widestLabelPx = labels.maxOfOrNull { measurer.measure(it, style).size.width } ?: 0
        val decor = MinputMenuItemPadding * 2 +
            (if (hasLeadingIcon) MinputMenuIconSize + MinputMenuItemPadding else 0.dp) +
            (if (hasTrailingIcon) MinputMenuIconSize + MinputMenuItemPadding else 0.dp)
        val label = with(density) { widestLabelPx.toDp() }
        (decor + label + MinputMenuWidthSlack)
            .coerceIn(MinputMenuMinWidth, MinputMenuMaxWidth)
    }
}

/**
 * Remove the 8dp band `DropdownMenuContent` pads above the first row and below the last.
 *
 * M3 applies it to the menu's inner Column, INSIDE the slot the `modifier` parameter reaches,
 * so it can't simply be left out — but every minput modifier does wrap it, and a measure pass
 * can give the space back: measure the padded child, report a box two insets shorter, and
 * place the child lifted by one inset. The padding then falls outside the Surface (which
 * sizes to what this reports) and is clipped away, leaving the rows flush with the menu's
 * rounded ends.
 *
 * Always applied: minput supplies its own frame inset instead (`framePadding`), so leaving
 * M3's band in place would stack two values that no call site can tell apart.
 */
private fun Modifier.collapseMenuVerticalPadding(collapse: Boolean = true): Modifier =
    if (!collapse) this else layout { measurable, constraints ->
        val inset = M3MenuVerticalPadding.roundToPx()
        val placeable = measurable.measure(constraints)
        val height = (placeable.height - inset * 2).coerceAtLeast(0)
        layout(placeable.width, height) { placeable.place(0, -inset) }
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
) {
    val contentColor = if (enabled) colors.textColor else colors.disabledTextColor
    val interaction = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MinputMenuItemHeight)
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
 * @param caret draws the pointer back at the summoning control. Side placements only.
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
            hasTrailingIcon = false,
        ),
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
 *
 * @param placement where the menu sits relative to its anchor.
 * @param caret draws the pointer back at the summoning control. Side placements only.
 * @param framePadding inset above the first row and below the last. Defaults to
 *   [MinputMenuFramePadding]; pass `0.dp` for rows flush with the menu's ends.
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
    framePadding: Dp = MinputMenuFramePadding,
) {
    MinputMenuSurface(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        placement = placement,
        caret = caret,
        framePadding = framePadding,
        menuWidth = rememberMinputMenuWidth(
            labels = options.map(optionLabel),
            hasLeadingIcon = optionIcon != null,
            // The check on the current option.
            hasTrailingIcon = true,
        ),
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
internal val MinputMenuIconSize = 16.dp

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

/** M3's own `DropdownMenuVerticalPadding`, which isn't public. Mirrored so
 *  [collapseMenuVerticalPadding] can give the space back; keep in step if M3 ever changes it. */
private val M3MenuVerticalPadding = 8.dp

internal val MinputMenuCorner = 8.dp

/** How far the caret protrudes from the menu body, and how wide its base is. */
internal val MinputMenuCaretDepth = 6.dp
internal val MinputMenuCaretWidth = 12.dp
