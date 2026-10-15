package com.mappo.ui.screen.virtualset

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.mappo.data.model.OverlayElement
import com.mappo.service.overlay.element.OverlayEditor
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The virtual editor's canvas in px: the DISPLAY ([displaySizePx] — buttons are display fractions,
 * so the editor lays them out at full size and scales the whole stage to fit Mappo's screen) and
 * the editable bounds inside it — the whole display, or with "1:1 screen" on the centred square of
 * side min(w, h). Buttons clamp to [bounds]; [OverlayEditor] clamps persisted geometry the same.
 *
 * Ported from the retired `OverlayLiveEditController`, whose window positions were this math.
 */
internal class EditCanvas(
    val width: Float,
    val height: Float,
    private val density: Float,
    square: Boolean,
) {
    val bounds: Rect = if (square) {
        val side = minOf(width, height)
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        Rect(left, top, left + side, top + side)
    } else {
        Rect(0f, 0f, width, height)
    }

    val snapThresholdPx: Float = SNAP_THRESHOLD_DP * density

    fun rectOf(el: OverlayElement): Rect =
        Rect(el.x * width, el.y * height, (el.x + el.width) * width, (el.y + el.height) * height)

    fun toElementRect(id: Long, r: Rect): OverlayEditor.ElementRect =
        OverlayEditor.ElementRect(id, r.left / width, r.top / height, r.width / width, r.height / height)

    /**
     * Cell size of the editor grid: the count per axis is chosen so cells are as close to square
     * as possible AND tile [bounds] exactly, off a [GRID_TARGET_CELL_DP] target. The drawn guide
     * and snapping both read this, so they always coincide.
     */
    fun gridCell(): Pair<Float, Float> {
        val target = GRID_TARGET_CELL_DP * density
        val cols = maxOf(1, (bounds.width / target).roundToInt())
        val rows = maxOf(1, (bounds.height / target).roundToInt())
        return (bounds.width / cols) to (bounds.height / rows)
    }

    /**
     * Snap a [w]×[h] rect's top-left ([x], [y]) to the nearest grid line or sibling edge/centre
     * within [snapThresholdPx]. [others] are the snap targets (the moving group excluded).
     */
    fun snap(x: Float, y: Float, w: Float, h: Float, others: List<Rect>): Offset {
        val (gridX, gridY) = gridCell()
        val b = bounds
        val xs = mutableListOf(b.left + ((x - b.left) / gridX).roundToInt() * gridX)
        val ys = mutableListOf(b.top + ((y - b.top) / gridY).roundToInt() * gridY)
        others.forEach { o ->
            xs += listOf(o.left, o.right, o.left - w, o.right - w, o.center.x - w / 2f)
            ys += listOf(o.top, o.bottom, o.top - h, o.bottom - h, o.center.y - h / 2f)
        }
        return Offset(
            clamp(snapValue(x, xs), w, b.left, b.right),
            clamp(snapValue(y, ys), h, b.top, b.bottom),
        )
    }

    private fun snapValue(value: Float, candidates: List<Float>): Float {
        var best = value
        var bestDist = snapThresholdPx + 1f
        candidates.forEach { c ->
            val d = abs(value - c)
            if (d <= snapThresholdPx && d < bestDist) {
                best = c
                bestDist = d
            }
        }
        return best
    }

    companion object {
        /** [value] clamped so an [extent]-long span stays inside [lo, hi]; a span bigger than
         *  the bounds may overflow past an edge. */
        fun clamp(value: Float, extent: Float, lo: Float, hi: Float): Float =
            value.coerceIn(minOf(lo, hi - extent), maxOf(lo, hi - extent))

        // Target editor-grid cell; the real cell rounds to tile the bounds exactly.
        const val GRID_TARGET_CELL_DP = 56f
        const val SNAP_THRESHOLD_DP = com.mappo.data.settings.OverlaySettings.SNAP_THRESHOLD_DP
    }
}

/** The display's full size in px — the space the run-mode overlay (and so every button) lives in. */
internal fun displaySizePx(context: Context): Size {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val b = wm.maximumWindowMetrics.bounds
        Size(b.width().toFloat(), b.height().toFloat())
    } else {
        val p = Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealSize(p)
        Size(p.x.toFloat(), p.y.toFloat())
    }
}

/** The union bounding box of [rects], or null when empty. */
internal fun unionOf(rects: Collection<Rect>): Rect? {
    if (rects.isEmpty()) return null
    return Rect(rects.minOf { it.left }, rects.minOf { it.top }, rects.maxOf { it.right }, rects.maxOf { it.bottom })
}
