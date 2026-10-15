package com.mappo.ui.component

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Renders a launcher-icon painter at the family's glyph sizing. Launcher bitmaps fill their
 * bounds edge-to-edge while Material vector glyphs sit inside ~2dp of built-in padding (per
 * 24dp of viewport), so a raw `Image` at the same [size] reads a smidge LARGER than sibling
 * vector icons — this insets the bitmap by the same fraction to optically match. Every app
 * icon sitting next to vector glyphs renders through this, never a bare `Image`.
 */
@Composable
fun AppIconImage(
    painter: Painter,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.size(size).padding(size * AppIconInsetFraction),
    )
}

/** Material vector glyphs keep 2/24 of their viewport as padding per side. */
private const val AppIconInsetFraction = 2f / 24f

/**
 * The launcher icon of [packageName] as a [Painter] — [rememberAppIconBitmap] wrapped. Null while
 * loading, for a null package, or for an unknown/uninstalled package (callers show their own
 * fallback glyph). Icons render UNTINTED — pass `Color.Unspecified` wherever a tint parameter
 * sits between this painter and the pixels.
 */
@Composable
fun rememberAppIconPainter(packageName: String?): Painter? {
    val bitmap = rememberAppIconBitmap(packageName) ?: return null
    return remember(bitmap) { BitmapPainter(bitmap) }
}

/** The launcher icon as a bitmap, for [com.mappo.ui.minput.MinputIcon]'s ink-cropping bitmap
 *  overload (a launcher icon sized beside glyphs). Null while loading or when there is none.
 *  Served from [AppIconCache]: an icon already decoded (or [AppIconCache.prewarm]ed) is there
 *  from the first frame, with no load and no pop-in. */
@Composable
fun rememberAppIconBitmap(packageName: String?): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState(initialValue = AppIconCache.peek(packageName), packageName) {
        value = AppIconCache.peek(packageName) ?: packageName?.let { AppIconCache.load(context, it) }
    }
    return bitmap
}

/**
 * **Every launcher icon the UI draws, decoded once** (2026-10-15). Until now each row of the
 * Application crumb's list went to PackageManager for its icon as it composed — every time the
 * panel opened, and again for each row scrolled back into view — so the list always opened on
 * fallback glyphs that then popped into icons. Now an icon is decoded once per process, at
 * [DecodeSizeDp] (a launcher drawable's intrinsic size is several times anything drawn here),
 * and [prewarm] fills the cache in the background as soon as the app list is known, so the
 * panel opens on icons that are already there.
 *
 * Icons are keyed by package alone: an app that updates its icon mid-session shows the old one
 * until the process restarts.
 */
object AppIconCache {
    /** Decode edge — above the largest app icon drawn (the bar's 20dp, the ink-cropped crumb
     *  glyph scaled from its ~2/3 opaque core), so cropping and scaling down stay crisp. */
    private const val DecodeSizeDp = 48

    private val cache = object : LruCache<String, ImageBitmap>(CacheBytes) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /** The cached icon, or null if it hasn't been decoded. Cheap — safe in composition. */
    fun peek(packageName: String?): ImageBitmap? = packageName?.let { cache.get(it) }

    /** The icon of [packageName], decoded off the main thread on a miss; null when it has none. */
    suspend fun load(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val px = (DecodeSizeDp * context.resources.displayMetrics.density).roundToInt()
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(px, px)
                    .asImageBitmap()
            }.getOrNull()?.also { cache.put(packageName, it) }
        }
    }

    /** Decodes every uncached icon of [packageNames], in order, in the background — call it as
     *  soon as the list a picker will show is known, its first rows first. */
    suspend fun prewarm(context: Context, packageNames: List<String>) {
        for (pkg in packageNames) load(context.applicationContext, pkg)
    }

    /** 16 MB: ~170 icons at 48dp on a density-2 screen (96px squared). */
    private const val CacheBytes = 16 * 1024 * 1024
}
