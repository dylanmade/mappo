package com.mappo.ui.screen.virtualset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mappo.data.model.OverlayElement
import com.mappo.data.settings.OverlaySettings
import com.mappo.service.overlay.element.OverlayEditor
import com.mappo.service.overlay.element.VirtualEditorSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What Align / Space operate relative to. */
enum class AlignTarget(val label: String) { Selection("Selection"), Canvas("Canvas") }

/** The align / distribute operations (the Align menu's rows). */
enum class AlignOp { LEFT, CENTER_X, RIGHT, TOP, CENTER_Y, BOTTOM, STACK_V, STACK_H, DIST_X, DIST_Y }

/**
 * The virtual set editor's own state — selection, clipboard, the editor-local toggles — and the
 * edits that aren't plain gestures (align, copy / paste, delete, nudge). The buttons themselves
 * live in [OverlayEditor] (shared with the run-mode overlay); this is everything around them.
 *
 * Ported from the retired `OverlayLiveEditController` (2026-10-15), minus its window plumbing.
 */
@HiltViewModel
class VirtualSetEditorViewModel @Inject constructor(
    val overlayEditor: OverlayEditor,
    val overlaySettings: OverlaySettings,
    val session: VirtualEditorSession,
) : ViewModel() {

    val elements: StateFlow<List<OverlayElement>> = overlayEditor.elements

    // Multi-select: tapping buttons accumulates them; dragging any member moves the whole set.
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    // Copied elements (a snapshot; OverlayElement is immutable). Paste new clones these; Paste
    // style applies the first one's appearance to the selection.
    private val clipboard = mutableListOf<OverlayElement>()
    private val _clipboardHasContent = MutableStateFlow(false)
    val clipboardHasContent: StateFlow<Boolean> = _clipboardHasContent.asStateFlow()

    // Editor-local toggles surfaced under Options. The grid is on by default and auto-sizes to the
    // editable bounds (see EditCanvas.gridCell).
    val showGrid = MutableStateFlow(true)
    val alignTo = MutableStateFlow(AlignTarget.Selection)
    /** Core menu orientation: false = vertical panel, true = horizontal icon bar. */
    val menuHorizontal = MutableStateFlow(false)
    val positionerOpen = MutableStateFlow(false)

    /** The button whose config drawer is open, if any. */
    val configElementId = MutableStateFlow<Long?>(null)

    init {
        // A different set / layer is a different canvas — nothing carries over.
        viewModelScope.launch {
            overlayEditor.editingScope.collect {
                _selectedIds.value = emptySet()
                configElementId.value = null
            }
        }
    }

    fun select(ids: Set<Long>) {
        _selectedIds.value = ids
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /**
     * Tap on a button (no drag) builds a multi-selection by accumulation: an unselected button is
     * added, the SOLE selection opens its config, one of several selected is removed.
     */
    fun tapElement(id: Long) {
        val current = _selectedIds.value
        when {
            id !in current -> _selectedIds.value = current + id
            current.size == 1 -> configElementId.value = id
            else -> _selectedIds.value = current - id
        }
    }

    fun addElement() {
        overlayEditor.pushUndoSnapshot()
        overlayEditor.addDefaultElement()
    }

    /** Delete every selected button and clear the selection. */
    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        overlayEditor.pushUndoSnapshot()
        ids.forEach { overlayEditor.delete(it) }
        _selectedIds.value = emptySet()
    }

    fun deleteElement(id: Long) {
        overlayEditor.pushUndoSnapshot()
        overlayEditor.delete(id)
        _selectedIds.value = _selectedIds.value - id
    }

    /** Commit a drag / resize: ONE undo entry, ONE batch write (no partial-commit flash). */
    fun commitRects(rects: List<OverlayEditor.ElementRect>) {
        if (rects.isEmpty()) return
        overlayEditor.pushUndoSnapshot()
        overlayEditor.moveResizeAll(rects)
    }

    // ── Positioner nudges ───────────────────────────────────────────────────────────────────
    // A press anchors on the selection's positions at press time and applies the CUMULATIVE delta
    // each step — so a held repeat moves steadily (not racing the async commit by re-reading
    // elements), and the whole press is one undo entry.
    private var nudgeAnchor: List<OverlayElement>? = null
    private var nudgeAccumX = 0f
    private var nudgeAccumY = 0f

    fun beginNudge() {
        val ids = _selectedIds.value
        val els = elements.value.filter { it.id in ids }
        if (els.isEmpty()) {
            nudgeAnchor = null
            return
        }
        overlayEditor.pushUndoSnapshot()
        nudgeAnchor = els
        nudgeAccumX = 0f
        nudgeAccumY = 0f
    }

    /** One nudge step of ([dxPx], [dyPx]) on a [canvasW]×[canvasH] canvas. */
    fun stepNudge(dxPx: Float, dyPx: Float, canvasW: Float, canvasH: Float) {
        val anchor = nudgeAnchor ?: return
        if (canvasW <= 0f || canvasH <= 0f) return
        nudgeAccumX += dxPx
        nudgeAccumY += dyPx
        overlayEditor.moveResizeAll(
            anchor.map { el ->
                OverlayEditor.ElementRect(
                    id = el.id,
                    x = el.x + nudgeAccumX / canvasW,
                    y = el.y + nudgeAccumY / canvasH,
                    width = el.width,
                    height = el.height,
                ) // moveResizeAll clamps to the editable bounds
            },
        )
    }

    fun endNudge() {
        nudgeAnchor = null
    }

    // ── Align / distribute ──────────────────────────────────────────────────────────────────

    /**
     * Align or distribute the selection relative to [target] (the selection's bounding box or the
     * whole canvas). All in normalized space; [OverlayEditor.moveResizeAll] clamps + commits once.
     */
    fun applyAlign(op: AlignOp, target: AlignTarget) {
        val ids = _selectedIds.value
        val els = elements.value.filter { it.id in ids }
        if (els.isEmpty()) return
        overlayEditor.pushUndoSnapshot()
        val refL: Float; val refT: Float; val refR: Float; val refB: Float
        when (target) {
            AlignTarget.Canvas -> { refL = 0f; refT = 0f; refR = 1f; refB = 1f }
            AlignTarget.Selection -> {
                refL = els.minOf { it.x }; refT = els.minOf { it.y }
                refR = els.maxOf { it.x + it.width }; refB = els.maxOf { it.y + it.height }
            }
        }
        fun rect(el: OverlayElement, x: Float = el.x, y: Float = el.y) =
            OverlayEditor.ElementRect(el.id, x, y, el.width, el.height)
        val rects = when (op) {
            AlignOp.LEFT -> els.map { rect(it, x = refL) }
            AlignOp.RIGHT -> els.map { rect(it, x = refR - it.width) }
            AlignOp.CENTER_X -> els.map { rect(it, x = (refL + refR) / 2f - it.width / 2f) }
            AlignOp.TOP -> els.map { rect(it, y = refT) }
            AlignOp.BOTTOM -> els.map { rect(it, y = refB - it.height) }
            AlignOp.CENTER_Y -> els.map { rect(it, y = (refT + refB) / 2f - it.height / 2f) }
            // Stack = pack edge-to-edge along one axis (no gap, no overlap), anchored at the
            // selection's current top-left and aligned on the cross axis. Independent of [target].
            AlignOp.STACK_V -> {
                val x0 = els.minOf { it.x }; var cy = els.minOf { it.y }
                els.sortedBy { it.y }.map { el -> rect(el, x = x0, y = cy).also { cy += el.height } }
            }
            AlignOp.STACK_H -> {
                val y0 = els.minOf { it.y }; var cx = els.minOf { it.x }
                els.sortedBy { it.x }.map { el -> rect(el, x = cx, y = y0).also { cx += el.width } }
            }
            AlignOp.DIST_X -> distribute(els, horizontal = true, target, refL, refR) { el, v -> rect(el, x = v) }
            AlignOp.DIST_Y -> distribute(els, horizontal = false, target, refT, refB) { el, v -> rect(el, y = v) }
        }
        overlayEditor.moveResizeAll(rects)
    }

    /**
     * Distribute [els] with equal gaps along one axis. Span = the canvas edges for
     * [AlignTarget.Canvas], else the selection's outer edges (which then stay put).
     */
    private fun distribute(
        els: List<OverlayElement>,
        horizontal: Boolean,
        target: AlignTarget,
        refStart: Float,
        refEnd: Float,
        place: (OverlayElement, Float) -> OverlayEditor.ElementRect,
    ): List<OverlayEditor.ElementRect> {
        val pos = { e: OverlayElement -> if (horizontal) e.x else e.y }
        val len = { e: OverlayElement -> if (horizontal) e.width else e.height }
        val sorted = els.sortedBy { pos(it) }
        if (sorted.size < 2) return els.map { place(it, pos(it)) }
        val spanStart = if (target == AlignTarget.Canvas) refStart else pos(sorted.first())
        val spanEnd = if (target == AlignTarget.Canvas) refEnd else pos(sorted.last()) + len(sorted.last())
        val totalLen = sorted.sumOf { len(it).toDouble() }.toFloat()
        val gap = (spanEnd - spanStart - totalLen) / (sorted.size - 1)
        var cursor = spanStart
        return sorted.map { el -> place(el, cursor).also { cursor += len(el) + gap } }
    }

    // ── Copy / paste ────────────────────────────────────────────────────────────────────────

    /** Snapshot the selected elements onto the clipboard (full button incl. appearance + commands). */
    fun copySelection() {
        val ids = _selectedIds.value
        val copied = elements.value.filter { it.id in ids }
        if (copied.isEmpty()) return
        clipboard.clear()
        clipboard.addAll(copied)
        _clipboardHasContent.value = true
    }

    /**
     * Paste new → clone the clipboard into the current scope (offset), then select the clones.
     * Paste style → apply the first copied element's appearance to the selection.
     */
    fun paste(styleOnly: Boolean) {
        if (clipboard.isEmpty()) return
        if (!styleOnly) {
            overlayEditor.pushUndoSnapshot()
            overlayEditor.duplicate(clipboard.toList()) { ids -> _selectedIds.value = ids.toSet() }
        } else {
            val ids = _selectedIds.value
            val targets = elements.value.filter { it.id in ids }
            if (targets.isEmpty()) return
            overlayEditor.pushUndoSnapshot()
            val style = clipboard.first()
            targets.forEach { el ->
                overlayEditor.update(
                    el.copy(
                        shape = style.shape,
                        opacity = style.opacity,
                        fillColorArgb = style.fillColorArgb,
                        contentColorArgb = style.contentColorArgb,
                        appearanceJson = style.appearanceJson,
                    ),
                )
            }
        }
    }
}
