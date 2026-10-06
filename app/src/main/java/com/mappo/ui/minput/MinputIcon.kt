package com.mappo.ui.minput

import android.graphics.Matrix
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
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
 * Launcher icons are bitmaps, not glyphs, and stay on `AppIconImage`.
 */
@Composable
fun MinputIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: MinputIconSize = MinputIconSize.S,
    /** [Color.Unspecified] draws the vector's own colours (multi-colour artwork). */
    tint: Color = LocalContentColor.current,
) {
    val inked = remember(icon) { icon.croppedToInk() }
    val longer = max(inked.viewportWidth, inked.viewportHeight)
    val width = size.dp * (inked.viewportWidth / longer)
    val height = size.dp * (inked.viewportHeight / longer)
    Icon(
        painter = rememberVectorPainter(inked),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(width, height),
    )
}

/** A vector drawable resource, through the same ink measurement. (PNG resources aren't
 *  glyphs — they can't be measured this way and don't belong here.) */
@Composable
fun MinputIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: MinputIconSize = MinputIconSize.S,
    tint: Color = LocalContentColor.current,
) = MinputIcon(ImageVector.vectorResource(id), contentDescription, modifier, size, tint)

/**
 * **The icon scale** — the glyph's longer inked side. One scale for every icon in the app, so
 * "the same size" means the same thing whichever set a glyph came from.
 *
 * Two-dp steps through the dense range, then the larger utility/identity sizes. Because these
 * are INK sizes they read larger than the same number did as a box: the old 13dp pill-icon box
 * inked about 11, the old 16dp icon-button box about 14.
 */
enum class MinputIconSize(val dp: Dp) {
    /** Trailing markers riding a line of mini text (a layout row's like heart). */
    Xxs(8.dp),
    /** Markers that need a little more room to read (the download tray + arrow). */
    Xs(10.dp),
    /** A pill's leading glyph; text-line companions. */
    S(12.dp),
    M(14.dp),
    /** Utility glyphs in a bare icon button. */
    L(16.dp),
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
