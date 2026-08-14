package com.mappo.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The launcher icon of [packageName] as a [Painter], loaded off the UI thread; null while
 * loading, for a null package, or for an unknown/uninstalled package (callers show their own
 * fallback glyph). Icons render UNTINTED — pass `Color.Unspecified` wherever a tint parameter
 * sits between this painter and the pixels.
 */
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
