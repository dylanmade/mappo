package com.mappo.ui.minput

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minput ("mini input") — Mappo's component library (`com.mappo.ui.minput`), formalized
 * 2026-08-07 from the control chrome born on the Remap Controls screen: beveled pill
 * buttons, mini icon buttons, pill dropdowns, text-field wells, the stepper slider, the
 * morph modal, and the shared metrics/colors/motion they speak.
 *
 * Philosophy (full doctrine in the `minput` skill):
 * - Styling over reinvented functionality — wrap M3/foundation equivalents for behavior.
 * - Surface system: background → surface 1 ([minputBoxContainer]) → surface 2
 *   ([MinputElevatedContainer]) → highlight ([minputHighlightContainer], selection only);
 *   controls wear the styling of the plane ABOVE the one they sit on.
 * - Coloration derives from theme tokens, overrideable per-view; child surfaces inherit
 *   their parent surface's coloration.
 * - In Mappo: minput first; a missing primitive is surfaced to Dylan before falling back
 *   to stock M3.
 *
 * All metrics live here — never re-derive a private size constant per call site.
 */

/** Compressed body text for mini rows, pills, and control labels. */
@Composable
fun minputMiniTextStyle(): TextStyle =
    MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 14.sp)

/** Overline treatment (uppercase callers + tracked-out small caps look) for headers. */
@Composable
fun minputOverlineTextStyle(): TextStyle =
    MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.9.sp,
    )

/**
 * Container fill shared by grouped boxes and every pill control (dropdowns, label fields,
 * buttons): the accent tint composited over the low container plane. One family, one
 * treatment — pills deliberately match their boxes' attributes.
 */
@Composable
fun minputBoxContainer(): Color =
    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)

/**
 * The topmost button plane: controls sitting ON an elevated box/card background use this
 * fill instead of [minputBoxContainer], which would vanish against its own plane.
 */
val MinputElevatedContainer = Color(0xFF434A5B)

/**
 * The highlight plane — the lightest fill in the family, sitting above even
 * [MinputElevatedContainer]. Reserved for indicating SELECTION (e.g. the raised segment of
 * [MinputGroupButton]); matches the M3 filled-button coloration (the "Map" CTA). Content on
 * it uses `colorScheme.onPrimary`.
 */
@Composable
fun minputHighlightContainer(): Color = MaterialTheme.colorScheme.primary

/**
 * Fill for text-input fields sitting on a box/card plane: a darker "well" than the card it
 * sits on. Deliberately FLAT — no bevel border — because an input is not a button. Same
 * formula shape as [minputBoxContainer] (accent tint over a surface plane), one plane lower
 * and fainter — lands at Dylan's tuned ≈#12141A under the current dark scheme while
 * tracking theme edits.
 */
@Composable
fun minputInputFieldContainer(): Color =
    MaterialTheme.colorScheme.primary.copy(alpha = 0.03f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerLowest)

/**
 * The one hover/press/focus state-layer treatment for every minput interactive: an
 * onSurface-colored ripple. Explicit (not `LocalIndication.current`) because the default
 * ripple derives its color from [androidx.compose.material3.LocalContentColor] — a minput
 * control hosted outside a Surface-managed subtree (overlay panels, frame chrome) would
 * silently inherit whatever content color the host left behind and hover/focus much darker
 * than its siblings. Library components pass this to their clickable/selectable.
 */
@Composable
fun minputIndication(): IndicationNodeFactory =
    ripple(color = MaterialTheme.colorScheme.onSurface)

/** Bevel stroke width for boxes + pill controls (slightly under the original 1dp). */
val MinputBoxStroke = 0.75.dp

/** How far the bevel's highlights deviate from the base fill — "ever so slightly". */
private const val BevelTopHighlightStrength = 0.10f
private const val BevelBottomHighlightStrength = 0.05f

/** Luminance floor below which the bevel strengths apply untouched — every dark plane
 *  (well, surface 1, surface 2) sits under it, so their tuned look never shifts. */
private const val BevelBoostLumFloor = 0.10f

/** Strength multiplier gained per unit of luminance above the floor. */
private const val BevelBoostPerLum = 6f

/** Nudging a fill toward white produces a shrinking delta as the fill lightens (the white
 *  headroom collapses), and light colors need a LARGER absolute delta to read at all — so
 *  on a light fill (the highlight plane) the stock strengths render an invisible bevel.
 *  Scale strength up with luminance to keep the stroke equally legible on every plane. */
private fun bevelStrengthBoost(base: Color): Float =
    1f + BevelBoostPerLum * (base.luminance() - BevelBoostLumFloor).coerceAtLeast(0f)

/** Where along the corner arc the bevel finishes fading: 1−cos(45°) of the radius — the
 *  point where the outline's tangent passes 45° and "top" geometrically becomes "side". */
private const val BevelFadeOfRadius = 0.9f

/**
 * The bevel border on buttons + cards (replaced the old solid accent outline): a very faint
 * thin top and bottom highlight, each the base fill nudged toward white, fading
 * to transparent (same hue, zero alpha — not transparent-black, which muddies the fade).
 * The fade completes WITHIN the corner rounding — by the arc's 45° point — so the highlight
 * ends just before the top border becomes the side border; that needs the real component
 * size, hence a [ShaderBrush] with per-size stops rather than fraction-based gradient stops
 * (which overshot the corners on anything taller than a pill).
 */
@Composable
fun minputBevelBorder(base: Color, cornerRadius: Dp): BorderStroke {
    val fadePx = with(LocalDensity.current) { (cornerRadius * BevelFadeOfRadius).toPx() }
    val boost = bevelStrengthBoost(base)
    return BorderStroke(
        MinputBoxStroke,
        BevelBrush(
            topHighlight = lerp(base, Color.White, (BevelTopHighlightStrength * boost).coerceAtMost(1f)),
            bottomHighlight = lerp(base, Color.White, (BevelBottomHighlightStrength * boost).coerceAtMost(1f)),
            fadePx = fadePx,
        ),
    )
}

// NB: strokes are INNER (Surface `border=` / `Modifier.border`) by deliberate reversion
// (2026-07-13). An outer-stroke experiment (CSS-outline semantics via drawBehind) was tried
// and backed out: everything stateful in Compose — hover/press/focus layers, disabled alpha,
// Surface clipping — operates WITHIN bounds, so outside chrome needed custom parallel handling
// for every state and made borderless fields read smaller than their bordered siblings.

private class BevelBrush(
    private val topHighlight: Color,
    private val bottomHighlight: Color,
    private val fadePx: Float,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val fade = (fadePx / size.height).coerceIn(0.01f, 0.49f)
        return LinearGradientShader(
            from = Offset.Zero,
            to = Offset(0f, size.height),
            colors = listOf(topHighlight, topHighlight.copy(alpha = 0f), bottomHighlight.copy(alpha = 0f), bottomHighlight),
            colorStops = listOf(0f, fade, 1f - fade, 1f),
        )
    }

    override fun equals(other: Any?): Boolean = other is BevelBrush &&
        other.topHighlight == topHighlight && other.bottomHighlight == bottomHighlight && other.fadePx == fadePx

    override fun hashCode(): Int =
        31 * (31 * topHighlight.hashCode() + bottomHighlight.hashCode()) + fadePx.hashCode()
}

/**
 * Canonical timing/corner values for Mappo's rect-lerp morph family — the group editor's
 * expand-from-box and any future in-place morphs speak these same values so the surfaces
 * read as one system. (The remap package aliases these as its internal
 * `ExpandMillis`/`CollapseMillis`/`GroupCorner`; [MinputModal] shares the corner but opens
 * with its own fade + settle, not a morph.)
 */
const val MinputMorphExpandMillis = 300
const val MinputMorphCollapseMillis = 240
val MinputMorphCorner = 8.dp

/** Height of the pill controls (buttons, dropdowns). */
val MinputPillHeight = 24.dp

/** Width floor for pill dropdowns so short values ("None") don't collapse into a tiny chip. */
val MinputPillMinWidth = 62.dp

/** Icon edge inside the pills. */
val MinputPillIconSize = 13.dp

/** Horizontal content inset shared by every pill control (buttons, dropdowns, label fields). */
val MinputPillContentPadding = 10.dp

/** Gap between a leading glyph and its label (pills, headers, captions). */
val MinputGlyphLabelGap = 5.dp

/** Width cap for a pill dropdown's label before it ellipsizes. */
val MinputPillLabelMaxWidth = 156.dp

/** Optical-centering bias for FIXED-WIDTH, center-arranged pills with a leading icon: total
 *  extra END padding vs START, shifting the icon+label block bias/2 toward the icon. Cancels
 *  the leading icon's built-in live-area padding — Material/Lucide glyphs only ink ~10-11dp
 *  of their 13dp box, so with symmetric padding the left flank measures ~2dp wider than the
 *  right (device screenshot audit, 2026-07-13). Wrap-width pills don't need this: their
 *  flanks are pure padding with no centering slack to compare. M3 precedent for biasing
 *  padding toward the icon side: ButtonDefaults.ButtonWithIconContentPadding (16dp icon side
 *  vs 24dp text side). NOT glyph scaling — layout-only, tune freely. */
val MinputPillIconSideBias = 2.dp

/** Outer tap-target edge of [MinputIconButton] (also its footprint spacer in editor rows). */
val MinputIconButtonSize = 24.dp

/** Icon edge inside [MinputIconButton]. */
val MinputIconButtonIconSize = 16.dp

// ── Panel anatomy: header + divider + content ────────────────────────────────────────────
// The shared skeleton of the full-screen panel surfaces (the remap profile/options panels,
// the group editor): a fixed-height header row, a horizontal divider, then content. Every
// surface with this anatomy pulls these values so the family stays in lockstep.

/** Header-row height of a panel surface (title + utilities). */
val MinputPanelHeaderHeight = 42.dp

/** Horizontal inset of the header/content divider. */
val MinputPanelDividerInset = 8.dp

/** Vertical gap between the divider and the first content row below it. */
val MinputPanelDividerContentGap = 10.dp

/** Start inset for a NON-interactive header title (leading icon + overline). The header's
 *  trailing [MinputIconButton]s read this much inward of their edge (their glyph sits
 *  (button − glyph)/2 inside an invisible circular tap target), so a bare title at the same
 *  padding looks flush-left by comparison — this nudge optically matches the two sides. */
val MinputPanelTitleInset = (MinputIconButtonSize - MinputIconButtonIconSize) / 2
