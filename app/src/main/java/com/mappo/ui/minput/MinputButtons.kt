package com.mappo.ui.minput

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow

/**
 * Mappo's hand-rolled miniature button — the library's ONE button component (2026-08-24:
 * the former standalone icon button folded in so every variant/color option lives here).
 *
 * [text] is optional: with a [leadingIcon] and no text the button renders as a perfectly
 * CIRCULAR icon button ([MinputPillHeight] diameter) wearing the same container variants
 * as the pill form — pass a [contentDescription] since no label carries the semantics.
 *
 * Container variants, lowest plane to highest:
 *  - [bare] — no container chrome at all (transparent, borderless): the utility-glyph
 *    look for header cogs, kebabs, and steppers ([MinputIconButton] delegates here).
 *  - default — the surface-1 box treatment, for buttons sitting on the background plane.
 *    (A transparent-fill "outlined" tier was trialed between bare and this on 2026-08-27
 *    and rejected same-day — a chromed button carries the fill AND the bevel ring.)
 *  - [elevated] — the topmost button plane, for buttons sitting on a box/card background.
 *  - [highlighted] — the highlight plane. RESERVED in the design language for marking
 *    SELECTED/ACTIVE state (the open drawer's summon, the active member of a set) — never
 *    idle emphasis; an idle button wants [elevated] or [filled] instead.
 *
 * [filled] marks a primary/commit action (Save, Done). NB it is currently a NO-OP on a
 * chromed button: its only ever effect was the stronger onSurface text, which every chromed
 * button now carries (2026-08-30) — it still lifts a `bare` button's label out of the
 * recessive utility grey, and it remains the semantic marker for the commit button in a
 * dialog/panel pair, so call sites keep passing it. Give it a real treatment (its own fill?)
 * when the family next needs a louder CTA. Disabled = dimmed + inert.
 *
 * [leadingIcon] renders a small glyph before the label (or alone, icon-only mode).
 * [leadingIconTint] defaults to Unspecified because the primary use is hardware button
 * prompts (Kenney glyphs carry fixed colors that must not re-tint); pass a theme role for
 * tintable concept icons. Icon-only mode treats Unspecified as "follow the button's
 * content color" instead — an icon-only button has no text to carry the variant's color,
 * so the glyph must (fixed-color art in icon-only form isn't a real case yet).
 *
 * [trailingIcon] renders a glyph at the button's END — dropdown arrows and their kin. It
 * always follows the button's content color (trailing glyphs are concept icons; there is
 * no fixed-color-art case on that side). [alignStart] (2026-08-27) packs the icon+label
 * to the START and pushes the trailing glyph to the far end — for full-width buttons
 * (the layouts drawer's Applications button) whose centered content would float away
 * from its edges; wrap-width buttons keep the centered default.
 */
@Composable
fun MinputPillButton(
    text: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    elevated: Boolean = false,
    highlighted: Boolean = false,
    bare: Boolean = false,
    leadingIcon: Painter? = null,
    leadingIconTint: Color = Color.Unspecified,
    trailingIcon: Painter? = null,
    alignStart: Boolean = false,
    contentDescription: String? = null,
) {
    val iconOnly = text == null && leadingIcon != null
    // Label strength (2026-08-30, Dylan): a chromed button's label reads at onSurface — the
    // same weight as the controls view's input legend and [MinputGroupButton]'s unselected
    // segments, which were already onSurface while these were the dimmer onSurfaceVariant.
    // The two are the SAME typeface, size, and weight; the gap was pure contrast, and at
    // 12sp on a small screen the dimmer grey read as a lighter, mushier face. `bare` keeps
    // onSurfaceVariant: a chrome-less utility glyph (a header cog, a kebab) is deliberately
    // recessive, and it has no label to carry.
    val content = when {
        highlighted -> MaterialTheme.colorScheme.onPrimary
        bare && !filled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    // Highlighted beats bare (2026-08-26): the highlight plane marks SELECTED/ACTIVE
    // state, which must read even on a chrome-less button (a bare drawer summon while
    // its drawer is open). At rest, bare stays transparent and borderless.
    val container = when {
        highlighted -> minputHighlightContainer()
        bare -> Color.Transparent
        elevated -> MinputElevatedContainer
        else -> minputBoxContainer()
    }
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    Surface(
        shape = shape,
        color = container,
        border = if (bare && !highlighted) null else minputBevelBorder(container, MinputPillHeight / 2),
        modifier = modifier
            .minputInteractiveMotion(interaction)
            .height(MinputPillHeight)
            // Icon-only = a perfect circle: width pinned to the height.
            .then(if (iconOnly) Modifier.width(MinputPillHeight) else Modifier)
            .then(
                if (enabled) {
                    Modifier.clip(shape).clickable(
                        interactionSource = interaction,
                        indication = minputIndication(),
                        onClick = onClick,
                    )
                } else Modifier.alpha(0.55f),
            ),
    ) {
        if (iconOnly) {
            Box(
                modifier = Modifier.size(MinputPillHeight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    leadingIcon!!,
                    contentDescription = contentDescription,
                    // Chromed circles are PILLS and use the pill family's icon scale; only
                    // the bare (chrome-less) form keeps the utility-glyph scale — a 16dp
                    // glyph floating in an invisible target reads right, but fills a
                    // visible 24dp circle to bursting.
                    modifier = Modifier.size(
                        if (bare) MinputIconButtonIconSize else MinputPillIconSize,
                    ),
                    tint = if (leadingIconTint == Color.Unspecified) content else leadingIconTint,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (alignStart) Arrangement.Start else Arrangement.Center,
                modifier = Modifier
                    .height(MinputPillHeight)
                    // A leading glyph inks less than its box (Material live area, Lucide
                    // stroke inset), so with symmetric padding the icon flank reads wider
                    // than the text flank — pull the start inset in by the family's
                    // icon-side bias to cancel it (the wrap-width sibling of
                    // MinputPillIconSideBias's fixed-width treatment; mirrored on the
                    // trailing side).
                    .padding(
                        start = if (leadingIcon != null) {
                            MinputPillContentPadding - MinputPillIconSideBias
                        } else MinputPillContentPadding,
                        end = if (trailingIcon != null) {
                            MinputPillContentPadding - MinputPillIconSideBias
                        } else MinputPillContentPadding,
                    ),
            ) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(MinputPillIconSize),
                        tint = leadingIconTint,
                    )
                    Spacer(Modifier.width(MinputGlyphLabelGap))
                }
                Text(
                    text = text.orEmpty(),
                    style = minputMiniTextStyle(),
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Start-aligned: the label claims the slack so a trailing glyph is
                    // pushed to the far end of a stretched button.
                    modifier = if (alignStart) Modifier.weight(1f) else Modifier,
                )
                if (trailingIcon != null) {
                    Spacer(Modifier.width(MinputGlyphLabelGap))
                    Icon(
                        trailingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(MinputPillIconSize),
                        tint = content,
                    )
                }
            }
        }
    }
}

/**
 * Convenience form of the chrome-less utility icon button (header cogs, kebabs, slider
 * steppers): delegates to [MinputPillButton]'s icon-only `bare` mode, so
 * there is exactly ONE button implementation to maintain (2026-08-24 fold).
 */
@Composable
fun MinputIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = MinputPillButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    bare = true,
    leadingIcon = rememberVectorPainter(icon),
    contentDescription = contentDescription,
)
