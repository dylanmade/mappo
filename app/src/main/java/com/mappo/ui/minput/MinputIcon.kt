package com.mappo.ui.minput

import android.graphics.Matrix
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * **The library's icon: every glyph, from every icon set, measured by its INK** (2026-10-05).
 *
 * Icon sets pad their glyphs differently inside their viewports — Material inks roughly 20 of its
 * 24 units, Lucide 22 plus its stroke, and an individual glyph can ink far less (Material's
 * Download is 14 × 17 of 24, so in a 12dp box it drew 7dp wide with 2.5dp of empty space on its
 * end — the "nudged left" download marker). Sizing the BOX therefore never sized the glyph, and
 * every call site compensated by hand.
 *
 * This primitive crops the vector's viewport to the bounds of what it actually draws, then sizes
 * that: **[size] is the glyph's longer inked side**, and the layout box is exactly the ink — no
 * padding at all. Spacing is the caller's, chosen by picking a size variant and a gap, never by
 * hoping a glyph's built-in margin happens to be right. An end-aligned icon lands flush on the
 * end; two icons at one [MinputIconSize] read as one size whatever set they came from.
 *
 * **It stays vector all the way down.** The crop rebuilds the [ImageVector] itself — same paths,
 * a smaller viewport and a translating root group — and Compose's vector painter renders it at
 * the destination size like any other icon. Nothing is drawn at one size and scaled to another
 * (the `graphicsLayer`-scale road is what rasterizes and goes fuzzy).
 *
 * Ink bounds come from flattening each path ([android.graphics.Path.approximate]) through its
 * group transforms, widened by half the stroke for stroked paths. Computed once per vector and
 * cached. If the measurement fails (a malformed path, or a test runtime without real graphics)
 * the icon falls back to its original viewport rather than failing to draw.
 *
 * Launcher icons are bitmaps: the [ImageBitmap] overload measures their opaque pixels instead.
 */
@Composable
fun MinputIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: MinputIconSize = MinputIconSize.S,
    /** [Color.Unspecified] draws the vector's own colours (multi-colour artwork). */
    tint: Color = LocalContentColor.current,
    /**
     * Reserve a SQUARE of [size] and centre the glyph in it. Only for glyphs that form a
     * COLUMN — menu rows, list rows, a glyph gutter — where labels beside differently-shaped
     * glyphs must still line up. Everywhere else the box is the ink and nothing more.
     */
    slot: Boolean = false,
    /** Size by AREA rather than the longer side — see [minputOpticalScale]. For glyphs read as
     *  PEERS (crumbs side by side, a list of rows), where a wide glyph must not read a step small. */
    optical: Boolean = false,
) = MinputIcon(icon, contentDescription, inkSize = size.dp, modifier = modifier, tint = tint, slot = slot, optical = optical)

/**
 * **The escape hatch: an ink size that isn't on the scale.** Only for sizes that are COMPUTED
 * rather than chosen — a glyph sized from its control's own geometry (a checkbox's tick, a
 * switch thumb's mark) or from user data (a keyboard key's configured text size, an overlay
 * element's size). A designed size always picks a [MinputIconSize].
 */
@Composable
fun MinputIcon(
    icon: ImageVector,
    contentDescription: String?,
    inkSize: Dp,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    slot: Boolean = false,
    optical: Boolean = false,
) {
    val inked = remember(icon) { icon.croppedToInk() }
    val longer = max(inked.viewportWidth, inked.viewportHeight)
    val scale = if (optical) minputOpticalScale(inked.viewportWidth, inked.viewportHeight) else 1f
    val width = inkSize * (inked.viewportWidth / longer) * scale
    val height = inkSize * (inked.viewportHeight / longer) * scale
    if (slot) {
        Box(modifier.size(inkSize), contentAlignment = Alignment.Center) {
            Icon(
                painter = rememberVectorPainter(inked),
                contentDescription = contentDescription,
                tint = tint,
                // An optical glyph may run past its slot on its longer side; the slot still
                // spaces the column, and the overflow stays centred on it.
                modifier = Modifier.requiredSize(width, height),
            )
        }
    } else {
        Icon(
            painter = rememberVectorPainter(inked),
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier.size(width, height),
        )
    }
}

/**
 * **Optical sizing: match glyphs by AREA, not by their longer side** (2026-10-14). Sizing the
 * longer side makes every glyph the same WIDTH or HEIGHT, but a wide glyph then carries far less
 * ink than a square one beside it — Lucide's Gamepad2 inks 22 × 16, so at one size it covered
 * ~73% of the square Layout glyph next to it in the hierarchy bar and read a step smaller.
 *
 * The scale grows the glyph until its ink box has the square's area (the geometric mean of its
 * sides equals the size), capped so the longer side never exceeds [MinputOpticalMaxOvershoot] ×
 * the size — a very elongated glyph gets closer, never dominant. Square glyphs are untouched.
 * Returns the factor to apply on top of the longer-side size.
 */
fun minputOpticalScale(width: Float, height: Float): Float {
    val longer = max(width, height)
    val shorter = min(width, height)
    if (longer <= 0f || shorter <= 0f) return 1f
    return sqrt(longer / shorter).coerceAtMost(MinputOpticalMaxOvershoot)
}

/** How far past its size an [optical][minputOpticalScale] glyph's longer side may run. */
const val MinputOpticalMaxOvershoot = 1.2f

/** A vector drawable resource, through the same ink measurement. (PNG resources aren't
 *  glyphs — they can't be measured this way and don't belong here.) */
@Composable
fun MinputIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: MinputIconSize = MinputIconSize.S,
    tint: Color = LocalContentColor.current,
    slot: Boolean = false,
) = MinputIcon(ImageVector.vectorResource(id), contentDescription, modifier, size, tint, slot)

/**
 * **A BITMAP glyph — a launcher icon — through the same ink rule** (2026-10-12). A launcher icon
 * carries its own transparent margin (legacy icons pad themselves; adaptive ones draw their mask
 * inside the bounds), so drawn at a size it inked smaller than the vector glyphs beside it — the
 * hierarchy bar's Application crumb read a step under its neighbours. The opaque bounds are
 * measured once per bitmap ([MinputBitmapInkAlpha] counts as ink) and only they are drawn, the
 * longer side at [size]. Drawn in its own colours: a launcher icon is artwork, not a tintable
 * glyph.
 */
@Composable
fun MinputIcon(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: MinputIconSize = MinputIconSize.S,
    slot: Boolean = false,
) {
    val ink = remember(bitmap) { bitmap.inkBounds() }
    val painter = remember(bitmap, ink) {
        BitmapPainter(bitmap, srcOffset = IntOffset(ink.left, ink.top), srcSize = IntSize(ink.width, ink.height))
    }
    val longer = max(ink.width, ink.height).toFloat()
    val width = size.dp * (ink.width / longer)
    val height = size.dp * (ink.height / longer)
    val image = @Composable { m: Modifier ->
        Image(painter = painter, contentDescription = contentDescription, modifier = m.size(width, height))
    }
    if (slot) {
        Box(modifier.size(size.dp), contentAlignment = Alignment.Center) { image(Modifier) }
    } else {
        image(modifier)
    }
}

/** The opaque bounds of a bitmap — the whole bitmap when nothing clears [MinputBitmapInkAlpha]. */
private fun ImageBitmap.inkBounds(): IntRect = synchronized(bitmapInkCache) {
    bitmapInkCache.getOrPut(this) {
        val source = asAndroidBitmap()
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((pixels[row + x] ushr 24) > MinputBitmapInkAlpha) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < 0) IntRect(0, 0, w, h) else IntRect(left, top, right + 1, bottom + 1)
    }
}

private val bitmapInkCache = WeakHashMap<ImageBitmap, IntRect>()

/** Alpha (0..255) a pixel must exceed to count as ink — clears a launcher icon's faint shadow
 *  and anti-aliased fringe, which would otherwise widen the bounds by a pixel or two. */
private const val MinputBitmapInkAlpha = 24

/**
 * **The icon scale** — the glyph's longer inked side. One scale for every icon in the app, so
 * "the same size" means the same thing whichever set a glyph came from.
 *
 * Two-dp steps through the dense range, then the larger utility/identity sizes. Because these
 * are INK sizes they read larger than the same number did as a box: the old 13dp pill-icon box
 * inked about 11, the old 16dp icon-button box about 14.
 */
enum class MinputIconSize(val dp: Dp) {
    /** Trailing markers riding a line of mini text (a layout row's like heart); dropdown arrows. */
    Xxs(8.dp),
    /** Markers that need a little more room to read (the download tray + arrow); Small controls. */
    Xs(10.dp),
    /** A Standard control's glyph; text-line companions; tile glyphs. */
    S(12.dp),
    /** Utility glyphs: bare icon buttons, menu rows, panel rows. */
    M(14.dp),
    /** Toolbar and dense-list glyphs. */
    L(16.dp),
    /** App-bar actions and navigation (M3's 24dp icon box). */
    Xl(20.dp),
    Xxl(24.dp),
}

/**
 * **The dropdown triangle's size** — the standard one for every exposed-dropdown arrow
 * (Dylan, 2026-10-06). The triangle is twice as wide as it is tall, and [MinputIcon] sizes the
 * LONGER side, so the number is its width: 8dp across, 4 tall — about the cap height of the
 * overline text it usually follows. At [MinputIconSize.Xs] it measured 10 × 5 and read far
 * larger than the text beside it.
 */
val MinputDropdownArrowSize = MinputIconSize.Xxs

// ── Ink measurement ────────────────────────────────────────────────────────────────────────

private val inkCache = WeakHashMap<ImageVector, ImageVector>()

/** The same vector with its viewport cropped to its ink (cached; the original on failure). */
internal fun ImageVector.croppedToInk(): ImageVector = synchronized(inkCache) {
    inkCache.getOrPut(this) { runCatching { cropToInk(this) }.getOrNull() ?: this }
}

private fun cropToInk(source: ImageVector): ImageVector? {
    val ink = inkBounds(source.root, Matrix()) ?: return null
    if (ink.width <= 0f || ink.height <= 0f) return null
    // Already flush (an icon authored without margins): nothing to rebuild.
    if (ink.left <= 0f && ink.top <= 0f &&
        ink.right >= source.viewportWidth && ink.bottom >= source.viewportHeight
    ) return source
    val builder = ImageVector.Builder(
        name = source.name + ":ink",
        // Proportional to the ink; MinputIcon sizes it explicitly anyway.
        defaultWidth = source.defaultWidth * (ink.width / source.viewportWidth),
        defaultHeight = source.defaultHeight * (ink.height / source.viewportHeight),
        viewportWidth = ink.width,
        viewportHeight = ink.height,
        tintColor = source.tintColor,
        tintBlendMode = source.tintBlendMode,
        autoMirror = source.autoMirror,
    )
    // The crop is a translation of the whole drawing: the ink's corner becomes the origin.
    builder.addGroup(name = "ink", translationX = -ink.left, translationY = -ink.top)
    copyGroup(builder, source.root)
    builder.clearGroup()
    return builder.build()
}

private val mirrorCache = HashMap<Triple<ImageVector, Boolean, Boolean>, ImageVector>()

/**
 * The same vector mirrored [horizontal]ly and/or [vertical]ly about its viewport's centre — for
 * a glyph whose orientation carries meaning (the bar-position mark, one corner glyph standing in
 * for all four). Still a pure vector: a scale group around the original drawing, which
 * [MinputIcon]'s ink measurement flattens like any other group transform. Cached, so a mirrored
 * glyph keeps one identity across recompositions (and one entry in the ink cache).
 */
fun ImageVector.minputMirrored(horizontal: Boolean, vertical: Boolean): ImageVector {
    if (!horizontal && !vertical) return this
    return synchronized(mirrorCache) {
        mirrorCache.getOrPut(Triple(this, horizontal, vertical)) {
            val builder = ImageVector.Builder(
                name = name + ":mirror" + (if (horizontal) "H" else "") + (if (vertical) "V" else ""),
                defaultWidth = defaultWidth,
                defaultHeight = defaultHeight,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                tintColor = tintColor,
                tintBlendMode = tintBlendMode,
                autoMirror = autoMirror,
            )
            builder.addGroup(
                name = "mirror",
                pivotX = viewportWidth / 2f,
                pivotY = viewportHeight / 2f,
                scaleX = if (horizontal) -1f else 1f,
                scaleY = if (vertical) -1f else 1f,
            )
            copyGroup(builder, root)
            builder.clearGroup()
            builder.build()
        }
    }
}

/** Copy [group] (its own transform included) into [builder] — the paths stay exactly as authored. */
private fun copyGroup(builder: ImageVector.Builder, group: VectorGroup) {
    builder.addGroup(
        name = group.name,
        rotate = group.rotation,
        pivotX = group.pivotX,
        pivotY = group.pivotY,
        scaleX = group.scaleX,
        scaleY = group.scaleY,
        translationX = group.translationX,
        translationY = group.translationY,
        clipPathData = group.clipPathData,
    )
    for (node in group) copyNode(builder, node)
    builder.clearGroup()
}

private fun copyNode(builder: ImageVector.Builder, node: VectorNode) {
    when (node) {
        is VectorGroup -> copyGroup(builder, node)
        is VectorPath -> builder.addPath(
            pathData = node.pathData,
            pathFillType = node.pathFillType,
            name = node.name,
            fill = node.fill,
            fillAlpha = node.fillAlpha,
            stroke = node.stroke,
            strokeAlpha = node.strokeAlpha,
            strokeLineWidth = node.strokeLineWidth,
            strokeLineCap = node.strokeLineCap,
            strokeLineJoin = node.strokeLineJoin,
            strokeLineMiter = node.strokeLineMiter,
            trimPathStart = node.trimPathStart,
            trimPathEnd = node.trimPathEnd,
            trimPathOffset = node.trimPathOffset,
        )
    }
}

/** Union of everything [group] draws, in viewport units, under the [parent] transform. */
private fun inkBounds(group: VectorGroup, parent: Matrix): Rect? {
    // The VectorDrawable group transform: about the pivot, scale then rotate, then translate.
    val matrix = Matrix().apply {
        postTranslate(-group.pivotX, -group.pivotY)
        postScale(group.scaleX, group.scaleY)
        postRotate(group.rotation)
        postTranslate(group.translationX + group.pivotX, group.translationY + group.pivotY)
        postConcat(parent)
    }
    var union: Rect? = null
    for (node in group) {
        val bounds = when (node) {
            is VectorGroup -> inkBounds(node, matrix)
            is VectorPath -> pathInk(node, matrix)
        } ?: continue
        union = union?.let {
            Rect(min(it.left, bounds.left), min(it.top, bounds.top),
                max(it.right, bounds.right), max(it.bottom, bounds.bottom))
        } ?: bounds
    }
    return union
}

/** Tight bounds of one path's ink: the flattened outline, plus half the stroke if stroked. */
private fun pathInk(path: VectorPath, matrix: Matrix): Rect? {
    val stroked = path.stroke != null && path.strokeLineWidth > 0f && path.strokeAlpha > 0f
    val filled = path.fill != null && path.fillAlpha > 0f
    if (!stroked && !filled) return null
    val android = PathParser().addPathNodes(path.pathData).toPath().asAndroidPath()
    android.transform(matrix)
    // [fraction, x, y] triples along the outline, curves flattened to within 0.05 units —
    // control points never count, so a curve's bounds are where it actually runs.
    val points = android.approximate(0.05f)
    if (points.size < 3) return null
    var l = Float.POSITIVE_INFINITY
    var t = Float.POSITIVE_INFINITY
    var r = Float.NEGATIVE_INFINITY
    var b = Float.NEGATIVE_INFINITY
    var i = 0
    while (i + 2 < points.size) {
        val x = points[i + 1]
        val y = points[i + 2]
        if (x < l) l = x
        if (x > r) r = x
        if (y < t) t = y
        if (y > b) b = y
        i += 3
    }
    if (stroked) {
        // The stroke scales with the transform (uniformly, for any glyph worth measuring).
        val values = FloatArray(9).also(matrix::getValues)
        val scale = sqrt(abs(values[Matrix.MSCALE_X] * values[Matrix.MSCALE_Y] -
            values[Matrix.MSKEW_X] * values[Matrix.MSKEW_Y]))
        val half = path.strokeLineWidth * scale / 2f
        l -= half; t -= half; r += half; b += half
    }
    return Rect(l, t, r, b)
}
