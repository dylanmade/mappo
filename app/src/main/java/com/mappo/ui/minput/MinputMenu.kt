package com.mappo.ui.minput

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Where a minput menu sits relative to the control that summoned it.
 *
 * [Below] is the conventional dropdown (and the default). [Start] / [End] are the SIDE
 * placements — for a control that is itself content rather than a picker, where dropping a
 * menu over the thing you just tapped hides what you're acting on. Side placement is
 * screen-aware: the requested side is used when there's room and silently mirrored when there
 * isn't, and the caret follows the side actually used.
 *
 * Side placement needs the anchor's size (`anchorSize`) — the menu is a popup and cannot
 * measure the control it hangs off.
 */
enum class MinputMenuPlacement { Below, Start, End }

/**
 * Shared chrome for every minput menu.
 *
 * Both of the library's menus — the single-CHOICE [MinputDropdownMenu] and the verb-list
 * [MinputActionMenu] — render through this, so their type scale, row height, disabled
 * treatment, placement and caret cannot drift apart. (They did: the action menu shipped at
 * `bodyLarge` while the picker inherited M3's `labelLarge`, and the two read as different
 * components. 2026-09-11.)
 *
 * Menus are a FIXED width. Short verb lists at varying widths read as ragged, and — more
 * practically — an exact width is what makes side placement and the caret position
 * computable without measuring the popup first.
 */
@Composable
internal fun MinputMenuSurface(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    itemCount: Int,
    placement: MinputMenuPlacement,
    caret: Boolean,
    anchorSize: DpSize,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    // Window extent for the fit test. Read from the host View first: it is populated in every
    // environment, whereas LocalWindowInfo.containerSize can still be zero (notably under
    // Robolectric). A zero here is not treated as "no room" — see [windowKnown]; getting that
    // wrong mirrored the menu off the start edge of the screen, where clicks never landed.
    val view = LocalView.current
    val fallback = LocalWindowInfo.current.containerSize
    val windowWidthPx = if (view.width > 0) view.width else fallback.width
    val windowHeightPx = if (view.height > 0) view.height else fallback.height
    val windowKnown = windowWidthPx > 0 && windowHeightPx > 0

    // The menu takes no layout space of its own, so this zero-size probe reports the
    // ANCHOR's origin — callers place the menu in a Box beside (or around) their control.
    var anchorOrigin by remember { mutableStateOf<Offset?>(null) }
    Box(Modifier.size(0.dp).onGloballyPositioned { anchorOrigin = it.positionInWindow() })

    val windowWidthDp = with(density) { windowWidthPx.toDp() }
    val windowHeightDp = with(density) { windowHeightPx.toDp() }
    val anchorXDp = with(density) { (anchorOrigin?.x ?: 0f).toDp() }
    val anchorYDp = with(density) { (anchorOrigin?.y ?: 0f).toDp() }

    // Estimated, not measured: every row is a fixed height and the width is pinned, so the
    // footprint is known before the popup exists. Only used to choose a side/alignment — if
    // the estimate is off, M3's own position provider still clamps the menu on screen.
    val menuHeight = MinputMenuItemHeight * itemCount + MinputMenuVerticalPadding * 2

    // Only second-guess the caller's requested side when we actually know the window and have
    // placed the probe. Without both, honor the request and let M3's position provider clamp:
    // a slightly-clipped menu on the requested side beats a confidently mirrored one nowhere
    // near the screen.
    val canFitTest = windowKnown && anchorOrigin != null
    val side = when {
        placement == MinputMenuPlacement.Below -> MinputMenuPlacement.Below
        !canFitTest -> placement
        placement == MinputMenuPlacement.End ->
            if (anchorXDp + anchorSize.width + MinputMenuWidth + MinputMenuCaretDepth <= windowWidthDp) {
                MinputMenuPlacement.End
            } else MinputMenuPlacement.Start
        else ->
            if (anchorXDp - MinputMenuWidth - MinputMenuCaretDepth >= 0.dp) {
                MinputMenuPlacement.Start
            } else MinputMenuPlacement.End
    }

    // Vertical alignment for the side placements: normally the menu's top lines up with the
    // anchor's top; near the bottom of the window it flips to bottom-aligned so the menu
    // doesn't need clamping (which would slide the caret off its anchor).
    val bottomAligned = canFitTest && side != MinputMenuPlacement.Below &&
        anchorYDp + menuHeight > windowHeightDp
    val caretFromTop = if (bottomAligned) {
        (menuHeight - anchorSize.height / 2).coerceAtLeast(MinputMenuCaretInset)
    } else {
        anchorSize.height / 2
    }

    // Where the menu's own anchor point goes, relative to the summoning control's TOP-START.
    // Applied by OFFSETTING a zero-size host box rather than via DropdownMenu's `offset`
    // parameter: the popup then sees an anchor already sitting where the menu belongs, and
    // M3's position provider does its ordinary start-aligned placement plus its own
    // on-screen clamping. Feeding a large `offset` instead sent the popup off-window — the
    // provider's candidate list treats that value very differently from a moved anchor.
    val hostOffset = when (side) {
        MinputMenuPlacement.Below -> DpOffset(0.dp, 0.dp)
        MinputMenuPlacement.End -> DpOffset(
            x = anchorSize.width + MinputMenuCaretDepth,
            y = if (bottomAligned) anchorSize.height - menuHeight else 0.dp,
        )
        MinputMenuPlacement.Start -> DpOffset(
            x = -(MinputMenuWidth + MinputMenuCaretDepth),
            y = if (bottomAligned) anchorSize.height - menuHeight else 0.dp,
        )
    }

    val showCaret = caret && side != MinputMenuPlacement.Below && anchorSize.isSpecified
    val shape = if (showCaret) {
        remember(side, caretFromTop, density) {
            MinputCaretMenuShape(
                corner = MinputMenuCorner,
                caretOnStart = side == MinputMenuPlacement.End,
                caretCenterFromTop = caretFromTop,
                caretHalfWidth = MinputMenuCaretWidth / 2,
                caretDepth = MinputMenuCaretDepth,
            )
        }
    } else {
        MenuDefaults.shape
    }

    Box(Modifier.size(0.dp).offset(x = hostOffset.x, y = hostOffset.y)) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = shape,
        // The caret is carved OUT of the surface, so content must be inset away from it or it
        // would render over the notch. (This modifier lands on the menu's inner Column.)
        modifier = modifier
            .width(MinputMenuWidth)
            .then(
                when {
                    !showCaret -> Modifier
                    side == MinputMenuPlacement.End -> Modifier.padding(start = MinputMenuCaretDepth)
                    else -> Modifier.padding(end = MinputMenuCaretDepth)
                },
            ),
        content = content,
    )
    }
}

/**
 * One row, shared by both menus.
 *
 * Disabled rows dim BOTH the label and the glyph. M3's `DropdownMenuItem` does this for you
 * via `MenuDefaults.itemColors()` — but only if you let it: passing an explicit color to the
 * `Text`/`Icon` inside the slots overrides the disabled color and the row reads as fully
 * enabled. That shipped (a greyed-out "Paste" looked identical to "New"), so colors are
 * resolved HERE against [enabled] and never hard-coded at a call site.
 */
@Composable
internal fun MinputMenuRow(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    val resolved = if (enabled) contentColor else contentColor.copy(alpha = MinputMenuDisabledAlpha)
    DropdownMenuItem(
        enabled = enabled,
        modifier = Modifier.height(MinputMenuItemHeight),
        contentPadding = MinputMenuItemPadding,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        text = {
            Text(
                label,
                style = minputMiniTextStyle(),
                color = resolved,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick,
    )
}

/**
 * The menu surface's outline with a caret pointing back at the anchor: a rounded rect inset
 * by [caretDepth] on the anchor side, plus a triangle filling that inset.
 *
 * A [Shape] rather than a drawn overlay so the caret is part of the surface — it inherits the
 * menu's fill, border and shadow for free, and no second layer can drift out of alignment
 * with the first.
 */
private class MinputCaretMenuShape(
    private val corner: Dp,
    private val caretOnStart: Boolean,
    private val caretCenterFromTop: Dp,
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
        // Keep the caret's base inside the straight run between the corners, so it never
        // grows out of an arc.
        val center = with(density) { caretCenterFromTop.toPx() }
            .coerceIn(r + half, (size.height - r - half).coerceAtLeast(r + half))

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
 * @param placement where the menu sits; [MinputMenuPlacement.Start]/[End] need [anchorSize].
 * @param caret draws the pointer back at the summoning control. Side placements only.
 * @param anchorSize the summoning control's size — required for side placement.
 */
@Composable
fun MinputActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<MinputAction>,
    modifier: Modifier = Modifier,
    placement: MinputMenuPlacement = MinputMenuPlacement.Below,
    caret: Boolean = false,
    anchorSize: DpSize = DpSize.Unspecified,
) {
    MinputMenuSurface(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        itemCount = actions.size,
        placement = placement,
        caret = caret,
        anchorSize = anchorSize,
        modifier = modifier,
    ) {
        actions.forEach { action ->
            val base = if (action.destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            val iconTint = if (action.destructive) base else MaterialTheme.colorScheme.onSurfaceVariant
            MinputMenuRow(
                label = action.label,
                enabled = action.enabled,
                contentColor = base,
                leadingIcon = {
                    Icon(
                        action.icon,
                        contentDescription = null,
                        modifier = Modifier.size(MinputMenuIconSize),
                        tint = if (action.enabled) iconTint
                        else iconTint.copy(alpha = MinputMenuDisabledAlpha),
                    )
                },
                onClick = { onDismissRequest(); action.onClick() },
            )
        }
    }
}

/**
 * The minput single-CHOICE menu: options with a check on the current one. [MinputDropdownMenu]
 * is [MinputPillDropdown]'s menu half, detached from the label-pill anchor so any control can
 * summon it (an icon-only sort button, a card's overflow). Place it in a `Box` beside the
 * anchor, exactly like M3's `DropdownMenu` (which it wraps).
 *
 * Picking dismisses first, then commits only on an actual change.
 */
@Composable
fun <T> MinputDropdownMenu(
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
    anchorSize: DpSize = DpSize.Unspecified,
) {
    MinputMenuSurface(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        itemCount = options.size,
        placement = placement,
        caret = caret,
        anchorSize = anchorSize,
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailingIcon = if (option == current) {
                    {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(MinputMenuIconSize),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else null,
                onClick = { onDismissRequest(); if (option != current) onPick(option) },
            )
        }
    }
}

private val DpSize.isSpecified: Boolean get() = this != DpSize.Unspecified

// ── Shared menu metrics ──────────────────────────────────────────────────────────────────────

/** Fixed menu width. See [MinputMenuSurface] for why it's fixed rather than intrinsic. */
val MinputMenuWidth = 168.dp

/** Row height — the family's dense scale, well under M3's 48dp menu item. Consistent with the
 *  sub-touch-target trade the whole library makes for controller-navigated surfaces. */
val MinputMenuItemHeight = 32.dp

/** M3's own `DropdownMenuVerticalPadding`, which isn't public. Mirrored here for the height
 *  estimate; keep in step if the M3 value ever changes. */
private val MinputMenuVerticalPadding = 8.dp

private val MinputMenuItemPadding =
    androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)

/** Leading/trailing glyph edge — the pill family's leading-icon scale. */
private val MinputMenuIconSize = 14.dp

private val MinputMenuCorner = 8.dp

/** How far the caret protrudes from the menu body, and how wide its base is. */
private val MinputMenuCaretDepth = 6.dp
private val MinputMenuCaretWidth = 12.dp

/** Floor for the caret's distance from the menu's top edge. */
private val MinputMenuCaretInset = 14.dp

/** Disabled rows keep their place in the menu and dim instead of disappearing, so a menu's
 *  shape doesn't shift between the cells that summon it. */
private const val MinputMenuDisabledAlpha = 0.38f
