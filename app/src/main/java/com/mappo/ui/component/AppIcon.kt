package com.mappo.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The launcher icon of [packageName] as a [Painter], loaded off the UI thread; null while
 * loading, for a null package, or for an unknown/uninstalled package (callers show their own
 * fallback glyph). Icons render UNTINTED — pass `Color.Unspecified` wherever a tint parameter
 * sits between this painter and the pixels.
 */
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

@Composable
fun rememberAppIconPainter(packageName: String?): Painter? {
    val context = LocalContext.current.applicationContext
    val painter by produceState<Painter?>(initialValue = null, packageName) {
        value = packageName?.let { pkg ->
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapPainter(
                        context.packageManager.getApplicationIcon(pkg).toBitmap().asImageBitmap(),
                    )
                }.getOrNull()
            }
        }
    }
    return painter
}
