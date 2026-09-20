package com.mappo.ui.theme

import android.os.Build
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import com.themestudio.core.LocalThemeStudioController
import com.themestudio.core.LocalThemeStudioVariantOverride
import com.themestudio.core.applyOverrides
import com.themestudio.core.rememberThemeFontResolver
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicMaterialThemeState

/**
 * MaterialKolor seed — DodgerBlue (#1E90FF). The base color scheme is generated from this seed
 * (SPEC_2025 expressive, TonalSpot) rather than a hand-authored palette, via materialkolor.com.
 * Change the seed to re-tint the whole app.
 */
private val MappoSeedColor = Color(0xFF1E90FF)

/** Map a Theme-Studio palette-style name (see `ColorGenerationOverrides.STYLE_NAMES`) to MaterialKolor's. */
private fun paletteStyleFromName(name: String?): PaletteStyle = when (name) {
    "Neutral" -> PaletteStyle.Neutral
    "Vibrant" -> PaletteStyle.Vibrant
    "Expressive" -> PaletteStyle.Expressive
    "Rainbow" -> PaletteStyle.Rainbow
    "FruitSalad" -> PaletteStyle.FruitSalad
    "Monochrome" -> PaletteStyle.Monochrome
    "Fidelity" -> PaletteStyle.Fidelity
    "Content" -> PaletteStyle.Content
    else -> PaletteStyle.TonalSpot
}

@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun MappoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Default off: Mappo's Material Theme Builder palette is a deliberate brand choice;
    // dynamic color (Android 12+) replaces it with system-derived colors from the
    // user's wallpaper. Caller can opt in by passing dynamicColor = true.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Theme Studio integration: when the editor forces a variant, honor it;
    // when it has overrides, merge them onto the chosen base scheme/typography/shapes.
    val variantOverride = LocalThemeStudioVariantOverride.current
    val effectiveDark = variantOverride ?: darkTheme
    val controller = LocalThemeStudioController.current
    val overrides = controller.overrides
    // MaterialKolor generates the base scheme from a seed (SPEC_2025 expressive). The seed, palette
    // style, and contrast are live-editable from Theme Studio's Colors tab (colorGeneration
    // overrides), falling back to [MappoSeedColor] / TonalSpot / 0. Wallpaper dynamicColor still
    // wins. This is the app's ONLY palette source (2026-08-30): the hand-authored
    // lightScheme/darkScheme pair and the Color.kt dump behind them were deleted — dead since the
    // seed landed, and a stale second palette is worse than none. Re-tint from [MappoSeedColor].
    val gen = overrides.colorGeneration
    val materialKolorState = rememberDynamicMaterialThemeState(
        isDark = effectiveDark,
        style = paletteStyleFromName(gen.style),
        contrastLevel = (gen.contrast ?: 0f).toDouble(),
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
        seedColor = gen.seed ?: MappoSeedColor,
    )
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (effectiveDark) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        else -> materialKolorState.colorScheme
    }
    val variantColors =
        if (effectiveDark) overrides.colors.dark else overrides.colors.light
    val colorScheme = baseScheme.applyOverrides(variantColors)
    val fontResolver = rememberThemeFontResolver()
    val typography = rememberAppTypography().applyOverrides(overrides.typography, fontResolver)
    val shapes = Shapes().applyOverrides(overrides.shapes)

    val extraColors = if (effectiveDark) MappoExtraColors.Dark else MappoExtraColors.Light
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        // Expressive (bouncy) motion — enabled with the MaterialKolor expressive palette. NOTE:
        // expressive overshoot previously made the ModalNavigationDrawer slide past its open anchor
        // and snap back; re-verify the drawer + other overshoot-sensitive components on device.
        motionScheme = MotionScheme.expressive(),
        typography = typography,
        shapes = shapes,
    ) {
        // Force the soft keyboard to NOT go fullscreen ("extract" mode). On landscape
        // handhelds the IME defaults to a fullscreen editor that covers the app, and in
        // that mode Compose text fields don't refresh on delete (backspace stays stale
        // until the next keystroke). Compose has no per-field IME flag, so we intercept
        // the platform text-input request and OR IME_FLAG_NO_FULLSCREEN/NO_EXTRACT_UI
        // into the EditorInfo. Living in MappoTheme means every host (both activities +
        // the overlay windows) and every current/future field inherits it — one place,
        // no per-field or per-screen maintenance.
        val noFullscreenIme = remember {
            PlatformTextInputInterceptor { request, nextHandler ->
                val patched = PlatformTextInputMethodRequest { outAttrs ->
                    request.createInputConnection(outAttrs).also {
                        outAttrs.imeOptions = outAttrs.imeOptions or
                            EditorInfo.IME_FLAG_NO_FULLSCREEN or
                            EditorInfo.IME_FLAG_NO_EXTRACT_UI
                    }
                }
                nextHandler.startInputMethod(patched)
            }
        }
        InterceptPlatformTextInput(interceptor = noFullscreenIme) {
            // Reserve a small margin when a scroll container brings a focused child into
            // view, so a field scrolled up above the docked keyboard rests just above it
            // instead of flush against its top edge. This is a scroll offset, not a layout
            // gap, so there's no visible stripe. It also gives focused items a little
            // breathing room from any scroll-container edge generally.
            val bringIntoViewDensity = LocalDensity.current
            val bringIntoViewSpec = remember(bringIntoViewDensity) {
                val marginPx = with(bringIntoViewDensity) { 16.dp.toPx() }
                object : BringIntoViewSpec {
                    override fun calculateScrollDistance(
                        offset: Float,
                        size: Float,
                        containerSize: Float,
                    ): Float {
                        // Same logic as the default spec, but treats the item as [marginPx]
                        // larger on each edge so the scroll leaves that much margin.
                        val leadingEdge = offset - marginPx
                        val trailingEdge = offset + size + marginPx
                        return when {
                            leadingEdge >= 0 && trailingEdge <= containerSize -> 0f
                            leadingEdge < 0 && trailingEdge > containerSize -> 0f
                            abs(leadingEdge) < abs(trailingEdge - containerSize) -> leadingEdge
                            else -> trailingEdge - containerSize
                        }
                    }
                }
            }
            CompositionLocalProvider(
                LocalMappoExtraColors provides extraColors,
                LocalBringIntoViewSpec provides bringIntoViewSpec,
                content = content,
            )
        }
    }
}

/**
 * Project-specific colors that don't have a clean role in the M3 [ColorScheme]. Currently:
 *  - drag-and-drop zone indicators, where users expect literal green / red regardless of
 *    the active theme palette (theme tertiary/error read as "another accent / destructive
 *    action," not "valid / invalid drop target").
 *  - the in-editor button-selection outline, which is intentionally near-white in both
 *    modes so it reads as a high-contrast "overlay" rather than a color-keyed accent.
 *  - the remap advanced table's PRESS-TYPE column colors, a fixed identity palette: each
 *    press type owns one hue across the whole app so a user learns "cyan = Long" once.
 *    Theme-derived roles can't express six mutually-distinct hues, and letting them re-tint
 *    with the seed would break that learned mapping — same reasoning as the literal green/red
 *    above.
 */
data class MappoExtraColors(
    val dropZoneValid: Color,
    val dropZoneInvalid: Color,
    /** Marks where a dragged thing was PICKED UP FROM, as distinct from where it will land.
     *  Literal blue for the same reason the pair above are literal green/red: users read
     *  "origin vs destination" from hue, and a theme accent would collide with selection. */
    val dropZoneOrigin: Color,
    val selectionOutline: Color,
    val pressTypes: PressTypePalette,
) {
    companion object {
        // One lightness step down from pure white — bright enough to read as luminous,
        // not so pure that it loses anti-aliasing on light surfaces.
        private val SelectionOutlineNearWhite = Color(0xFFF2F2F2)

        val Light = MappoExtraColors(
            dropZoneValid = Color(0xFF2E7D32),    // M-spec green 800 — readable on light fills
            dropZoneInvalid = Color(0xFFC62828),  // M-spec red 800
            dropZoneOrigin = Color(0xFF1565C0),   // blue 800
            selectionOutline = SelectionOutlineNearWhite,
            pressTypes = PressTypePalette.Light,
        )
        val Dark = MappoExtraColors(
            dropZoneValid = Color(0xFF66BB6A),    // green 400 — lifts off dark surface
            dropZoneInvalid = Color(0xFFEF5350),  // red 400
            dropZoneOrigin = Color(0xFF64B5F6),   // blue 300
            selectionOutline = SelectionOutlineNearWhite,
            pressTypes = PressTypePalette.Dark,
        )
    }
}

/**
 * ╔══════════════════════════════════════════════════════════════════════════════════════╗
 * ║  PRESS-TYPE COLORS — the advanced table's column coloration. EDIT THESE FREELY.      ║
 * ╚══════════════════════════════════════════════════════════════════════════════════════╝
 *
 * Four INDEPENDENT roles per press type, so each can be tuned without disturbing the others:
 *
 *  - [header] — overline text naming the press type (the type picker, the old column headers).
 *               Used as an opaque color.
 *  - [tile]   — composited OVER the cell's normal surface, so **the alpha byte is the tint
 *               strength**: `0x38` ≈ 22%. Raise it for a louder column, drop it toward `0x00`
 *               to make a column read as untinted.
 *  - [icon]   — the press-type glyph leading a tile (2026-09-20). Its OWN value rather than
 *               the header's: the glyph sits ON the tile's tint, where the header color was
 *               tuned against the card. **Alpha byte applies.**
 *  - [plus]   — the empty cell's "+" glyph. **Alpha byte again** — `0x4D` ≈ 30%.
 *
 * Every value is a literal ARGB, applied as-is at the call site with no derived alpha or
 * blending on top. What you write here is what renders, so tuning is direct: change the
 * number, rebuild, look.
 *
 * `press` (the standard Press column) is deliberately NEUTRAL — no hue, the ordinary control
 * surface, per Dylan's spec that Press keeps the standard button colors. Its `tile` is fully
 * transparent so the cell shows the plain elevated container.
 */
@Immutable
data class PressTypeColors(
    val header: Color,
    val tile: Color,
    val icon: Color,
    val plus: Color,
)

@Immutable
data class PressTypePalette(
    val press: PressTypeColors,
    val long: PressTypeColors,
    val double: PressTypeColors,
    val chord: PressTypeColors,
    val down: PressTypeColors,
    val up: PressTypeColors,
) {
    companion object {
        /** Dark theme — the handheld target, and the palette actually tuned against. */
        val Dark = PressTypePalette(
            // Neutral: matches onSurfaceVariant / the untinted cell surface.
            press = PressTypeColors(
                header = Color(0xFFBFC6D4),
                tile = Color(0x00000000),
                icon = Color(0xCCBFC6D4),
                plus = Color(0x4DBFC6D4),
            ),
            // Cyan 300
            long = PressTypeColors(
                header = Color(0xFF4DD0E1),
                tile = Color(0x384DD0E1),
                icon = Color(0xE64DD0E1),
                plus = Color(0x4D4DD0E1),
            ),
            // Magenta / pink 300
            double = PressTypeColors(
                header = Color(0xFFF06292),
                tile = Color(0x38F06292),
                icon = Color(0xE6F06292),
                plus = Color(0x4DF06292),
            ),
            // Yellow / amber 300
            chord = PressTypeColors(
                header = Color(0xFFFFD54F),
                tile = Color(0x38FFD54F),
                icon = Color(0xE6FFD54F),
                plus = Color(0x4DFFD54F),
            ),
            // Red 400
            down = PressTypeColors(
                header = Color(0xFFEF5350),
                tile = Color(0x38EF5350),
                icon = Color(0xE6EF5350),
                plus = Color(0x4DEF5350),
            ),
            // Green 300
            up = PressTypeColors(
                header = Color(0xFF81C784),
                tile = Color(0x3881C784),
                icon = Color(0xE681C784),
                plus = Color(0x4D81C784),
            ),
        )

        /** Light theme — the same hues dropped to M-spec 700/800 steps so they stay legible
         *  as text and strokes on light fills. Tile tints run a little stronger because a
         *  faint wash disappears against a light surface. */
        val Light = PressTypePalette(
            press = PressTypeColors(
                header = Color(0xFF4A5160),
                tile = Color(0x00000000),
                icon = Color(0xCC4A5160),
                plus = Color(0x4D4A5160),
            ),
            long = PressTypeColors(
                header = Color(0xFF00838F),
                tile = Color(0x3300838F),
                icon = Color(0xE600838F),
                plus = Color(0x5900838F),
            ),
            double = PressTypeColors(
                header = Color(0xFFAD1457),
                tile = Color(0x33AD1457),
                icon = Color(0xE6AD1457),
                plus = Color(0x59AD1457),
            ),
            chord = PressTypeColors(
                header = Color(0xFF9A6700),
                tile = Color(0x339A6700),
                icon = Color(0xE69A6700),
                plus = Color(0x599A6700),
            ),
            down = PressTypeColors(
                header = Color(0xFFC62828),
                tile = Color(0x33C62828),
                icon = Color(0xE6C62828),
                plus = Color(0x59C62828),
            ),
            up = PressTypeColors(
                header = Color(0xFF2E7D32),
                tile = Color(0x332E7D32),
                icon = Color(0xE62E7D32),
                plus = Color(0x592E7D32),
            ),
        )
    }
}

val LocalMappoExtraColors = compositionLocalOf { MappoExtraColors.Light }
