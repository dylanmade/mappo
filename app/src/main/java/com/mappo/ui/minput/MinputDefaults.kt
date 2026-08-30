package com.mappo.ui.minput

import android.graphics.ComposeShader
import android.graphics.PorterDuff
import android.os.Build
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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

/** One step below [minputMiniTextStyle] — secondary metadata riding under a mini-text
 *  primary (a layout card's author line, its description). Same family, next size down,
 *  no overline tracking. */
@Composable
fun minputMicroTextStyle(): TextStyle =
    MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp)

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
 * The lighter well variant, for text-input fields sitting directly on the BACKGROUND
 * plane: the standard well ([minputInputFieldContainer]) is built on the lowest surface
 * plane, so against the screen background it all but vanishes — there is no darker plane
 * for a background-seated well to recess into. This variant steps the base one plane UP
 * instead (same formula shape over `surfaceContainerLow`), lifting the field off the
 * background while staying fainter than the surface-1 controls beside it, so it still
 * reads as a well rather than a button.
 */
@Composable
fun minputInputFieldContainerLight(): Color =
    MaterialTheme.colorScheme.primary.copy(alpha = 0.03f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)

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
private const val BevelBottomHighlightStrength = 0.10f

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

/** How far an input field's outline deviates from the well fill it wraps — matched to the
 *  bevel's top highlight so outlined wells and beveled buttons carry one stroke intensity. */
private const val InputFieldOutlineStrength = BevelTopHighlightStrength

/**
 * Outline color for an `outlined` text-input field, derived from the field's own container
 * fill (pass whichever well variant the instance wears): the fill nudged toward white by
 * the bevel family's derivation — same strength as the bevel's top highlight,
 * luminance-adaptive boost included — but applied as a solid uniform ring rather than a
 * fading bevel, because a well is flat, not raised. Rendered like every button outline:
 * an INNER stroke of [MinputBoxStroke] width (see the NB below [minputBevelBorder]), so
 * outlined and plain fields measure identically.
 */
fun minputInputFieldOutline(base: Color): Color =
    lerp(base, Color.White, (InputFieldOutlineStrength * bevelStrengthBoost(base)).coerceAtMost(1f))

/** The bevel's three layers, each with an independently tunable alpha: a uniform
 *  full-perimeter outline underneath (side light on a raised element), and the top/bottom
 *  highlights compositing over it. The highlights keep emphasis at any alpha mix because
 *  they render additively ON TOP of the outline. */
private const val BevelOutlineAlpha = 0.35f
private const val BevelTopHighlightAlpha = 1f
private const val BevelBottomHighlightAlpha = 1f

/** How far the outline deviates from the base fill — same white-nudge family as the
 *  highlights, so all three layers read as one light source. */
private const val BevelOutlineStrength = BevelTopHighlightStrength

/** Where along the corner arc the bevel finishes fading: 1−cos(45°) of the radius — the
 *  point where the outline's tangent passes 45° and "top" geometrically becomes "side". */
private const val BevelFadeOfRadius = 0.6f

/** Fade run for a squared(-ish) corner, applied as a floor on the 45°-point run above: a
 *  physically square corner sheds the top face's light almost immediately, so its side
 *  highlight dies within this short distance instead of a rounded arc's long travel. */
private val BevelSquareCornerFade = 1.dp

/**
 * The bevel border on buttons + cards (replaced the old solid accent outline): a very faint
 * thin top and bottom highlight, each the base fill nudged toward white, fading
 * to transparent (same hue, zero alpha — not transparent-black, which muddies the fade).
 * The fade completes WITHIN the corner rounding — by the arc's 45° point — so the highlight
 * ends just before the top border becomes the side border; that needs the real component
 * size, hence a [ShaderBrush] with per-size stops rather than fraction-based gradient stops
 * (which overshot the corners on anything taller than a pill).
 *
 * Sides may round differently ([cornerRadius] = start side, [endCornerRadius] = end side —
 * a group-button end segment mixes a pill end with squared inner edges): each side edge
 * fades over ITS corner's run, so a squared edge darkens much sooner than a rounded one
 * (floored at [BevelSquareCornerFade] rather than collapsing to zero).
 *
 * [outline] (2026-08-27, default ON): a uniform full-perimeter ring ([BevelOutlineAlpha])
 * rendered UNDERNEATH the highlights, which still fade to nothing and composite over it —
 * a whisper of side reflection completing the lit-physical read on every raised element
 * (buttons, group boxes, cards, modals). Three independently tunable layer alphas
 * (outline, top, bottom) live above. Pass `outline = false` for the rare surface that
 * wants the bare fade-to-nothing bevel.
 */
@Composable
fun minputBevelBorder(
    base: Color,
    cornerRadius: Dp,
    endCornerRadius: Dp = cornerRadius,
    outline: Boolean = true,
): BorderStroke {
    val density = LocalDensity.current
    fun fadePx(radius: Dp): Float = with(density) {
        maxOf((radius * BevelFadeOfRadius).toPx(), BevelSquareCornerFade.toPx())
    }
    val startFadePx = fadePx(cornerRadius)
    val endFadePx = fadePx(endCornerRadius)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val boost = bevelStrengthBoost(base)
    fun layer(strength: Float, alpha: Float): Color =
        lerp(base, Color.White, (strength * boost).coerceAtMost(1f)).copy(alpha = alpha)
    return BorderStroke(
        MinputBoxStroke,
        BevelBrush(
            topHighlight = layer(BevelTopHighlightStrength, BevelTopHighlightAlpha),
            bottomHighlight = layer(BevelBottomHighlightStrength, BevelBottomHighlightAlpha),
            // Alpha 0 (no outline) keeps the highlight hue so the fade stays clean —
            // never transparent-black.
            outline = layer(BevelOutlineStrength, if (outline) BevelOutlineAlpha else 0f),
            fadeLeftPx = if (rtl) endFadePx else startFadePx,
            fadeRightPx = if (rtl) startFadePx else endFadePx,
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
    private val outline: Color,
    private val fadeLeftPx: Float,
    private val fadeRightPx: Float,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val left = verticalShader(size, fadeLeftPx)
        // Nested ComposeShaders need API 28 on a hardware canvas; 26/27 degrade to the
        // uniform single-gradient bevel.
        if (fadeLeftPx == fadeRightPx || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return left
        val right = verticalShader(size, fadeRightPx)
        // Positional pick — left×(1−x/w) + right×(x/w). The two verticals agree wherever
        // the stroke runs horizontally (solid top/bottom rows), so the crossfade is only
        // ever visible at the side strokes, where the ramps sit at ~0/~1. Transparent-black
        // is fine here: DST_IN reads only the mask's alpha.
        return ComposeShader(
            ComposeShader(left, horizontalAlphaRamp(size, leftOpaque = true), PorterDuff.Mode.DST_IN),
            ComposeShader(right, horizontalAlphaRamp(size, leftOpaque = false), PorterDuff.Mode.DST_IN),
            PorterDuff.Mode.ADD,
        )
    }

    private fun verticalShader(size: Size, fadePx: Float): Shader {
        val fade = (fadePx / size.height).coerceIn(0.01f, 0.49f)
        // Layered render, baked into one gradient: the uniform outline ring underneath, the
        // highlights compositing over it at the solid top/bottom rows and fading down to
        // just-the-outline by the corner arcs. The highlight fade over a constant underlay
        // is linear, so per-stop compositing reproduces the true two-layer stack.
        return LinearGradientShader(
            from = Offset.Zero,
            to = Offset(0f, size.height),
            colors = listOf(
                topHighlight.compositeOver(outline),
                outline,
                outline,
                bottomHighlight.compositeOver(outline),
            ),
            colorStops = listOf(0f, fade, 1f - fade, 1f),
        )
    }

    private fun horizontalAlphaRamp(size: Size, leftOpaque: Boolean): Shader =
        LinearGradientShader(
            from = Offset.Zero,
            to = Offset(size.width, 0f),
            colors = if (leftOpaque) listOf(Color.Black, Color.Transparent)
            else listOf(Color.Transparent, Color.Black),
        )

    override fun equals(other: Any?): Boolean = other is BevelBrush &&
        other.topHighlight == topHighlight && other.bottomHighlight == bottomHighlight &&
        other.fadeLeftPx == fadeLeftPx && other.fadeRightPx == fadeRightPx

    override fun hashCode(): Int {
        var h = topHighlight.hashCode()
        h = 31 * h + bottomHighlight.hashCode()
        h = 31 * h + fadeLeftPx.hashCode()
        h = 31 * h + fadeRightPx.hashCode()
        return h
    }
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

/** Horizontal content inset shared by every pill control (buttons, dropdowns, label fields).
 *  Widened 10dp → 12dp (Dylan, 2026-08-29): the family read pinched, labels crowding their
 *  own chrome. Deliberately ONE value across the family — a button and the dropdown beside
 *  it must measure alike. Icon-ONLY buttons never consult it (they pin to a perfect
 *  [MinputPillHeight] circle), so they are unaffected. */
val MinputPillContentPadding = 12.dp

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

/** Track size of [MinputSwitch] — the bar-scale toggle. The height matches the mini text
 *  line ([minputMiniTextStyle]'s 14sp line at the app's 0.85 scale ≈ 12dp), so an
 *  overline + switch stack measures like the bar's overline + value text stacks; the
 *  width runs WIDER than M3's 52×32 proportions (Dylan, 2026-08-21 — a uniform scale
 *  that thin reads stubby). */
val MinputSwitchWidth = 28.dp
val MinputSwitchHeight = 12.dp

/** Inset between the switch track edge and its thumb (the thumb diameter is
 *  [MinputSwitchHeight] minus twice this). */
val MinputSwitchThumbInset = 2.dp

/** Outer tap-target edge of [MinputIconButton] (also its footprint spacer in editor rows). */
val MinputIconButtonSize = 24.dp

/** Icon edge inside [MinputIconButton]. */
val MinputIconButtonIconSize = 16.dp

// ── Panel anatomy: header + divider + content ────────────────────────────────────────────
// The shared skeleton of the full-screen panel surfaces (the remap layout/options panels,
// the group editor): a fixed-height header row, a horizontal divider, then content. Every
// surface with this anatomy pulls these values so the family stays in lockstep.

/** Header-row height of a panel surface (title + utilities). */
val MinputPanelHeaderHeight = 42.dp

/** Horizontal inset of the header/content divider. */
val MinputPanelDividerInset = 8.dp

/** Vertical gap between the divider and the first content row below it. */
val MinputPanelDividerContentGap = 6.dp

/** Start inset for a NON-interactive header title (leading icon + overline). The header's
 *  trailing [MinputIconButton]s read this much inward of their edge (their glyph sits
 *  (button − glyph)/2 inside an invisible circular tap target), so a bare title at the same
 *  padding looks flush-left by comparison — this nudge optically matches the two sides. */
val MinputPanelTitleInset = (MinputIconButtonSize - MinputIconButtonIconSize) / 2

// ── App-bar anatomy ──────────────────────────────────────────────────────────────────────
// The fixed edge bars of the screen chrome (the remap top bar, the home frame's bottom bar):
// a surfaceContainer strip separated from content by a divider. Both bars pull these values.

/** Bar height — the context button's height plus a whisker of air above and below. */
val MinputBarHeight = 38.dp

/** Horizontal inset at a bar's edges. */
val MinputBarEdgePadding = 6.dp

/** The app icon inside a bar's context widget (the top bar's viewing stack, the bottom
 *  bar's active shortcut) — one size so the two widgets read as siblings. */
val MinputBarWidgetIconSize = 20.dp

/** Horizontal gap between a bar widget's app icon and its flanking text stack(s). */
val MinputBarIconTextGap = 6.dp

/** Vertical gap inside a bar widget's text stack, between the overline and the value. */
val MinputBarStackGap = 1.dp

// ── Pod anatomy ──────────────────────────────────────────────────────────────────────────
// [MinputPod]: the pill-shaped plate a cluster of controls rides on, for chrome that floats
// over content instead of banding across it (born with the remap controls view's transparent
// top bar). Metrics live here so every pod in the app measures alike.

/** Inset between a pod's rim and the controls it carries. */
val MinputPodPadding = 4.dp

/** Resting height of a pod: a pill control plus [MinputPodPadding] above and below. A floor,
 *  not a cap — taller content grows the plate. */
val MinputPodHeight = MinputPillHeight + MinputPodPadding * 2

/** Gap between controls riding the SAME pod. */
val MinputPodItemGap = 4.dp

/** Gap between adjacent pods. */
val MinputPodGap = 6.dp
