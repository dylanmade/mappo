package com.mappo.ui.screen.virtualset

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.AlignHorizontalRight
import androidx.compose.material.icons.filled.AlignVerticalBottom
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.AlignVerticalTop
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.HorizontalDistribute
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalDistribute
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mappo.R
import com.mappo.data.model.OverlayElement
import com.mappo.data.model.OverlayGesture
import com.mappo.data.model.RemapTarget
import com.mappo.data.model.displayLabel
import com.mappo.data.model.overlay.AppearanceLayer
import com.mappo.data.model.overlay.CornerRadii
import com.mappo.data.model.overlay.ElementAppearance
import com.mappo.data.model.overlay.GradientStop
import com.mappo.data.model.overlay.LayerKind
import com.mappo.data.model.overlay.LayerPaint
import com.mappo.data.model.overlay.StrokeAlign
import com.mappo.data.model.overlay.StrokeGradientMode
import com.mappo.data.model.overlay.StrokeStyle
import com.mappo.data.model.overlay.decodeElementAppearance
import com.mappo.data.model.overlay.defaultFillLayer
import com.mappo.data.model.overlay.defaultStrokeLayer
import com.mappo.data.model.overlay.encode
import com.mappo.data.model.overlay.nextLayerId
import com.mappo.data.model.targetFor
import com.mappo.data.model.withTarget
import com.mappo.service.overlay.element.OverlayScope
import com.mappo.ui.component.ColorPicker
import com.mappo.ui.component.GradientEditor
import com.mappo.ui.component.colorpicker.ColorPickerButton
import com.mappo.ui.minput.MinputButton
import com.mappo.ui.minput.MinputDraggableMenuEntry as MenuEntry
import com.mappo.ui.minput.MinputDraggableMenuTrailing as MenuTrailing
import com.mappo.ui.minput.MinputDraggableMenuWideWidthDp as WIDE_MENU_WIDTH_DP
import com.mappo.ui.minput.MinputIcon
import com.mappo.ui.minput.MinputIconSize
import com.mappo.ui.minput.MinputPercentSlider
import com.mappo.ui.minput.MinputSlider
import com.mappo.ui.minput.minputMiniTextStyle
import com.mappo.ui.screen.overlay.OverlayCommonCommands
import com.mappo.ui.screen.overlay.legacyAppearance

// The virtual button editor's EDIT fly-outs: Assign (commands) and Customize (appearance) — the
// rich per-button levels (pill pairs, sliders, colour pickers, the gradient editor) the context
// menu's Edit row opens beside the button, hosted by the minput draggable menu's fly-outs
// (ported from the retired OverlayLiveEditController; the rest of that menu moved to the bar, the
// toolbar row and the context menu 2026-11-11 — see VirtualButtonEditorActions.kt). Each builder is
// @Composable so a level's rows recompose live as edits commit.

// ── Assign / Customize: per-button commands + appearance ─────────────────────────────────────

/** The element, live — builders recompose as edits commit. */
@Composable
private fun liveElement(vm: VirtualButtonEditorViewModel, elementId: Long): OverlayElement? {
    val els by vm.elements.collectAsStateWithLifecycle()
    return els.firstOrNull { it.id == elementId }
}

private fun gestureLabel(g: OverlayGesture): String = when (g) {
    OverlayGesture.TAP -> "Tap"
    OverlayGesture.DOUBLE_TAP -> "Double-tap"
    OverlayGesture.HOLD -> "Hold"
}

private fun outputLabel(t: RemapTarget): String =
    if (t is RemapTarget.Unbound) "None" else t.displayLabel()

/**
 * Assign: one row per commanded gesture — [press-type pill] ▸ [output pill], the remap screen's
 * input→output row — plus "Add command". [draft] keeps freshly-added (unbound) rows visible.
 */
@Composable
internal fun assignEntries(vm: VirtualButtonEditorViewModel, elementId: Long): List<MenuEntry> {
    val el = liveElement(vm, elementId) ?: return emptyList()
    val draft = remember(elementId) { mutableStateListOf<OverlayGesture>() }
    val rows = OverlayGesture.entries.filter { g -> el.targetFor(g) !is RemapTarget.Unbound || g in draft }
    val addable = OverlayGesture.entries.filter { it !in rows }
    return buildList {
        rows.forEach { g -> add(MenuEntry.Custom("assign-$g") { openSub -> AssignmentRow(vm, el, g, draft, openSub) }) }
        if (rows.isNotEmpty()) add(MenuEntry.Divider)
        add(
            MenuEntry.Item(
                "Add command",
                leadingIcon = Icons.Default.Add,
                enabled = addable.isNotEmpty(),
                submenu = {
                    addable.map { g -> MenuEntry.Item(gestureLabel(g), closeToParentOnly = true, onClick = { draft.add(g) }) }
                },
            ),
        )
    }
}

@Composable
private fun AssignmentRow(
    vm: VirtualButtonEditorViewModel,
    el: OverlayElement,
    gesture: OverlayGesture,
    draft: MutableList<OverlayGesture>,
    openSub: (String, @Composable () -> List<MenuEntry>) -> Unit,
) {
    val target = el.targetFor(gesture)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        MinputButton(
            text = gestureLabel(gesture),
            onClick = { openSub("gesture-$gesture") { gesturePickEntries(vm, el.id, gesture, draft) } },
        )
        // The remap editor's input→output flow marker.
        MinputIcon(
            R.drawable.lucide_play_filled,
            contentDescription = null,
            size = MinputIconSize.Xxs,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        MinputButton(
            text = outputLabel(target),
            onClick = { openSub("output-$gesture") { outputPickEntries(vm, el.id, gesture, draft) } },
            filled = target !is RemapTarget.Unbound,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Move this row's command to a different press type (occupied gestures are disabled). */
@Composable
private fun gesturePickEntries(
    vm: VirtualButtonEditorViewModel,
    elementId: Long,
    from: OverlayGesture,
    draft: MutableList<OverlayGesture>,
): List<MenuEntry> {
    val el = liveElement(vm, elementId) ?: return emptyList()
    val target = el.targetFor(from)
    return OverlayGesture.entries.map { g ->
        val occupied = g != from && (el.targetFor(g) !is RemapTarget.Unbound || g in draft)
        MenuEntry.Item(
            gestureLabel(g),
            selected = g == from,
            enabled = !occupied,
            closeToParentOnly = true,
            onClick = {
                if (g != from) {
                    draft.remove(from)
                    if (target is RemapTarget.Unbound) draft.add(g)
                    else vm.virtualButtonEditor.update(el.withTarget(from, RemapTarget.Unbound).withTarget(g, target))
                }
            },
        )
    }
}

/** Output picker: None + the common-command palette (the full picker lands with the binding migration). */
@Composable
private fun outputPickEntries(
    vm: VirtualButtonEditorViewModel,
    elementId: Long,
    gesture: OverlayGesture,
    draft: MutableList<OverlayGesture>,
): List<MenuEntry> {
    val el = liveElement(vm, elementId) ?: return emptyList()
    val current = el.targetFor(gesture)
    return buildList {
        add(
            MenuEntry.Item(
                "None",
                selected = current is RemapTarget.Unbound,
                closeToParentOnly = true,
                onClick = {
                    // Keep the row visible as a draft so "None" doesn't make it vanish.
                    if (gesture !in draft) draft.add(gesture)
                    vm.virtualButtonEditor.update(el.withTarget(gesture, RemapTarget.Unbound))
                },
            ),
        )
        OverlayCommonCommands.forEach { code ->
            val t = RemapTarget.fromCode(code)
            add(
                MenuEntry.Item(
                    code,
                    selected = current == t,
                    closeToParentOnly = true,
                    onClick = {
                        draft.remove(gesture) // bound now — the row persists on its own
                        vm.virtualButtonEditor.update(el.withTarget(gesture, t))
                    },
                ),
            )
        }
    }
}

/** Customize: Global (element-wide appearance) on top, then the layer stack (top layer first). */
@Composable
internal fun customizeEntries(vm: VirtualButtonEditorViewModel, elementId: Long): List<MenuEntry> {
    val el = liveElement(vm, elementId) ?: return emptyList()
    val defaultFill = MaterialTheme.colorScheme.secondaryContainer
    val appearance = decodeElementAppearance(el.appearanceJson) ?: legacyAppearance(el, defaultFill)
    fun commit(a: ElementAppearance) = vm.virtualButtonEditor.update(el.copy(appearanceJson = a.encode()))
    return buildList {
        add(
            MenuEntry.Item(
                "Global",
                leadingIcon = Icons.Default.Tune,
                submenuWidthDp = WIDE_MENU_WIDTH_DP,
                submenu = { globalAppearanceEntries(vm, elementId) },
            ),
        )
        add(MenuEntry.Divider)
        appearance.layers.asReversed().forEach { layer ->
            add(
                MenuEntry.Item(
                    layerLabel(appearance.layers, layer),
                    leadingIcon = if (layer.kind == LayerKind.FILL) Icons.Default.FormatColorFill else Icons.Default.BorderStyle,
                    submenuWidthDp = WIDE_MENU_WIDTH_DP,
                    submenu = { layerEntries(vm, elementId, layer.id) },
                ),
            )
        }
        add(MenuEntry.Divider)
        add(
            MenuEntry.Item(
                "Add fill", leadingIcon = Icons.Default.Add, keepOpen = true,
                onClick = { commit(appearance.copy(layers = appearance.layers + defaultFillLayer(nextLayerId(appearance.layers), defaultFill))) },
            ),
        )
        add(
            MenuEntry.Item(
                "Add stroke", leadingIcon = Icons.Default.Add, keepOpen = true,
                onClick = {
                    // White: the most common authoring move is a highlight (see defaultStrokeLayer).
                    commit(appearance.copy(layers = appearance.layers + defaultStrokeLayer(nextLayerId(appearance.layers), Color.White)))
                },
            ),
        )
    }
}

/** "Fill" / "Stroke", numbered within kind when there's more than one (bottom-up order). */
private fun layerLabel(layers: List<AppearanceLayer>, layer: AppearanceLayer): String {
    val sameKind = layers.filter { it.kind == layer.kind }
    val kind = if (layer.kind == LayerKind.FILL) "Fill" else "Stroke"
    return if (sameKind.size > 1) "$kind ${sameKind.indexOfFirst { it.id == layer.id } + 1}" else kind
}

/** Global appearance: element opacity, corner radii, text colour. */
@Composable
private fun globalAppearanceEntries(vm: VirtualButtonEditorViewModel, elementId: Long): List<MenuEntry> {
    val el = liveElement(vm, elementId) ?: return emptyList()
    val defaultFill = MaterialTheme.colorScheme.secondaryContainer
    val defaultText = MaterialTheme.colorScheme.onSecondaryContainer
    val appearance = decodeElementAppearance(el.appearanceJson) ?: legacyAppearance(el, defaultFill)
    fun commit(a: ElementAppearance) = vm.virtualButtonEditor.update(el.copy(appearanceJson = a.encode()))
    var perCorner by remember(elementId) { mutableStateOf(false) }
    return buildList {
        add(
            MenuEntry.Custom("g-opacity") { _ ->
                MinputPercentSlider("Opacity", el.opacity, valueRange = 0.2f..1f, onChange = { vm.virtualButtonEditor.update(el.copy(opacity = it)) })
            },
        )
        add(
            MenuEntry.Custom("g-corner") { _ ->
                MinputPercentSlider("Corner radius", appearance.corners.average, onChange = { commit(appearance.copy(corners = CornerRadii.uniform(it))) })
            },
        )
        add(MenuEntry.Item("Per-corner radii", trailing = MenuTrailing.Check(perCorner) { perCorner = it }))
        if (perCorner) {
            val c = appearance.corners
            add(MenuEntry.Custom("g-tl") { _ -> MinputPercentSlider("Top left", c.topLeft, onChange = { commit(appearance.copy(corners = c.copy(topLeft = it))) }) })
            add(MenuEntry.Custom("g-tr") { _ -> MinputPercentSlider("Top right", c.topRight, onChange = { commit(appearance.copy(corners = c.copy(topRight = it))) }) })
            add(MenuEntry.Custom("g-bl") { _ -> MinputPercentSlider("Bottom left", c.bottomLeft, onChange = { commit(appearance.copy(corners = c.copy(bottomLeft = it))) }) })
            add(MenuEntry.Custom("g-br") { _ -> MinputPercentSlider("Bottom right", c.bottomRight, onChange = { commit(appearance.copy(corners = c.copy(bottomRight = it))) }) })
        }
        add(MenuEntry.Divider)
        add(MenuEntry.Custom("g-text-color") { _ -> MenuTextColorRow(vm, el, defaultText) })
    }
}

@Composable
private fun MenuTextColorRow(vm: VirtualButtonEditorViewModel, el: OverlayElement, defaultText: Color) {
    var picking by remember(el.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Text color",
                style = minputMiniTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (el.contentColorArgb != null) {
                MinputButton("Reset", onClick = { vm.virtualButtonEditor.update(el.copy(contentColorArgb = null)) })
                Spacer(Modifier.width(8.dp))
            }
            ColorPickerButton(
                color = el.contentColorArgb?.let { Color(it) } ?: defaultText,
                onClick = { picking = !picking },
                size = 24.dp,
            )
        }
        if (picking) {
            // Inline — a picker in a menu level keeps the level as its one surface.
            ColorPicker(
                color = el.contentColorArgb?.let { Color(it) } ?: defaultText,
                onChange = { c -> vm.virtualButtonEditor.update(el.copy(contentColorArgb = c.copy(alpha = 1f).toArgb())) },
                onClearOverride = { vm.virtualButtonEditor.update(el.copy(contentColorArgb = null)) },
                pickerKey = "menu-text-${el.id}",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One layer's full control set: paint, opacity, stroke geometry, ordering, delete. */
@Composable
private fun layerEntries(vm: VirtualButtonEditorViewModel, elementId: Long, layerId: Long): List<MenuEntry> {
    val el = liveElement(vm, elementId) ?: return emptyList()
    val defaultFill = MaterialTheme.colorScheme.secondaryContainer
    val appearance = decodeElementAppearance(el.appearanceJson) ?: legacyAppearance(el, defaultFill)
    val layer = appearance.layers.firstOrNull { it.id == layerId } ?: return emptyList()
    val index = appearance.layers.indexOfFirst { it.id == layerId }
    fun commit(a: ElementAppearance) = vm.virtualButtonEditor.update(el.copy(appearanceJson = a.encode()))
    fun updateLayer(updated: AppearanceLayer) =
        commit(appearance.copy(layers = appearance.layers.map { if (it.id == layerId) updated else it }))
    fun swapWith(other: Int) = commit(
        appearance.copy(
            layers = appearance.layers.toMutableList().also { l ->
                val tmp = l[index]; l[index] = l[other]; l[other] = tmp
            },
        ),
    )
    val isGradient = layer.paint is LayerPaint.Gradient

    fun <T> pick(label: String, value: String, options: List<Pair<T, String>>, current: T, set: (T) -> Unit) =
        MenuEntry.Item(
            label,
            trailing = MenuTrailing.Value(value),
            submenu = {
                options.map { (v, l) -> MenuEntry.Item(l, selected = current == v, closeToParentOnly = true, onClick = { set(v) }) }
            },
        )

    return buildList {
        add(
            MenuEntry.Item(
                "Paint",
                trailing = MenuTrailing.Value(if (isGradient) "Gradient" else "Solid"),
                submenu = {
                    listOf(
                        MenuEntry.Item("Solid", selected = !isGradient, closeToParentOnly = true, onClick = {
                            (layer.paint as? LayerPaint.Gradient)?.let { g ->
                                updateLayer(layer.copy(paint = LayerPaint.Solid(g.stops.firstOrNull()?.argb ?: Color.White.toArgb())))
                            }
                        }),
                        MenuEntry.Item("Gradient", selected = isGradient, closeToParentOnly = true, onClick = {
                            (layer.paint as? LayerPaint.Solid)?.let { s ->
                                // Seed: the same colour fading out — visibly a gradient immediately.
                                updateLayer(
                                    layer.copy(
                                        paint = LayerPaint.Gradient(
                                            stops = listOf(
                                                GradientStop(position = 0f, argb = s.argb, opacity = 1f),
                                                GradientStop(position = 1f, argb = s.argb, opacity = 0f),
                                            ),
                                        ),
                                    ),
                                )
                            }
                        }),
                    )
                },
            ),
        )
        when (val paint = layer.paint) {
            is LayerPaint.Solid -> add(MenuEntry.Custom("layer-color") { _ -> MenuLayerColorRow(layer, paint, ::updateLayer) })
            is LayerPaint.Gradient -> add(
                MenuEntry.Custom("layer-gradient") { _ ->
                    GradientEditor(
                        gradient = paint,
                        onChange = { updateLayer(layer.copy(paint = it)) },
                        editorKey = layer.id,
                        // An across-stroke gradient's direction IS the stroke — no angle to set.
                        showAngle = !(layer.kind == LayerKind.STROKE && layer.strokeGradientMode == StrokeGradientMode.ACROSS),
                    )
                },
            )
        }
        add(
            MenuEntry.Custom("layer-opacity") { _ ->
                MinputPercentSlider("Layer opacity", layer.opacity, onChange = { updateLayer(layer.copy(opacity = it)) })
            },
        )
        if (layer.kind == LayerKind.STROKE) {
            add(
                MenuEntry.Custom("layer-width") { _ ->
                    MinputSlider(
                        label = "Width",
                        value = layer.strokeWidthDp,
                        onChange = { updateLayer(layer.copy(strokeWidthDp = it)) },
                        valueRange = 0.5f..24f,
                        step = 0.5f,
                        unitLabel = "dp",
                    )
                },
            )
            val aligns = listOf(StrokeAlign.INSIDE to "Inside", StrokeAlign.CENTER to "Center", StrokeAlign.OUTSIDE to "Outside")
            add(pick("Alignment", aligns.first { it.first == layer.strokeAlign }.second, aligns, layer.strokeAlign) { updateLayer(layer.copy(strokeAlign = it)) })
            val styles = listOf(StrokeStyle.SOLID to "Solid", StrokeStyle.DASHED to "Dashed", StrokeStyle.DOTTED to "Dotted")
            add(pick("Style", styles.first { it.first == layer.strokeStyle }.second, styles, layer.strokeStyle) { updateLayer(layer.copy(strokeStyle = it)) })
            if (isGradient) {
                val modes = listOf(StrokeGradientMode.LINEAR to "Linear", StrokeGradientMode.ACROSS to "Across stroke")
                add(pick("Gradient", modes.first { it.first == layer.strokeGradientMode }.second, modes, layer.strokeGradientMode) { updateLayer(layer.copy(strokeGradientMode = it)) })
            }
            add(
                MenuEntry.Custom("layer-dx") { _ ->
                    MinputSlider(
                        label = "Offset X",
                        value = layer.offsetXDp,
                        onChange = { updateLayer(layer.copy(offsetXDp = it)) },
                        valueRange = -24f..24f,
                        step = 0.5f,
                        unitLabel = "dp",
                    )
                },
            )
            add(
                MenuEntry.Custom("layer-dy") { _ ->
                    MinputSlider(
                        label = "Offset Y",
                        value = layer.offsetYDp,
                        onChange = { updateLayer(layer.copy(offsetYDp = it)) },
                        valueRange = -24f..24f,
                        step = 0.5f,
                        unitLabel = "dp",
                    )
                },
            )
        }
        add(MenuEntry.Divider)
        add(MenuEntry.Item("Move up", enabled = index < appearance.layers.lastIndex, keepOpen = true, onClick = { swapWith(index + 1) }))
        add(MenuEntry.Item("Move down", enabled = index > 0, keepOpen = true, onClick = { swapWith(index - 1) }))
        add(MenuEntry.Item("Delete layer", leadingIcon = Icons.Default.Delete, closeToParentOnly = true, onClick = {
            commit(appearance.copy(layers = appearance.layers.filter { it.id != layerId }))
        }))
    }
}

@Composable
private fun MenuLayerColorRow(layer: AppearanceLayer, paint: LayerPaint.Solid, onChange: (AppearanceLayer) -> Unit) {
    var picking by remember(layer.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Color",
                style = minputMiniTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            ColorPickerButton(color = Color(paint.argb), onClick = { picking = !picking }, size = 24.dp)
        }
        if (picking) {
            ColorPicker(
                color = Color(paint.argb),
                onChange = { c -> onChange(layer.copy(paint = LayerPaint.Solid(c.copy(alpha = 1f).toArgb()))) },
                onClearOverride = null,
                pickerKey = "menu-layer-${layer.id}",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
