package com.mappo.ui.screen.virtualset

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.AlignCenterHorizontal
import com.composables.icons.lucide.AlignCenterVertical
import com.composables.icons.lucide.AlignEndHorizontal
import com.composables.icons.lucide.AlignEndVertical
import com.composables.icons.lucide.AlignHorizontalSpaceAround
import com.composables.icons.lucide.AlignStartHorizontal
import com.composables.icons.lucide.AlignStartVertical
import com.composables.icons.lucide.AlignVerticalSpaceAround
import com.composables.icons.lucide.BoxSelect
import com.composables.icons.lucide.ClipboardPaste
import com.composables.icons.lucide.Columns2
import com.composables.icons.lucide.Command
import com.composables.icons.lucide.Component
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Crosshair
import com.composables.icons.lucide.Frame
import com.composables.icons.lucide.Grid3x3
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Layers2
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Magnet
import com.composables.icons.lucide.Move
import com.composables.icons.lucide.Paintbrush
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Rows2
import com.composables.icons.lucide.Scaling
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.Trash2
import com.mappo.service.overlay.element.OverlayScope
import com.mappo.ui.minput.MinputAction
import com.mappo.ui.minput.MinputActionMenu
import com.mappo.ui.minput.MinputDropdownMenu

// Where the virtual button editor's commands live since 2026-11-11, when its draggable core menu
// was retired (it is minput's `MinputDraggableMenu` now):
//
//  - the CONTEXT MENU of a tapped button / a marquee'd selection — [virtualButtonContextActions]:
//    Edit, Copy, Paste, Align, Templatize, Move, Resize, Delete;
//  - the hierarchy bar's menu (the hamburger), while this is the editor on screen —
//    [virtualButtonEditorBarActions]: Options, Layers;
//  - the toolbar row under the bar — New, Undo | Redo (VirtualButtonEditorToolbar.kt).
//
// All of them are minput popup menus; only Edit's Assign / Customize levels (embedded sliders,
// pickers, the gradient editor) still open as in-composition fly-outs beside the button.

/** Log-only placeholder for menu actions whose real behaviour isn't built yet (templates). */
internal fun menuGap(name: String) {
    Log.i("VirtualButtonEditor", "editor menu action not implemented yet: $name")
}

/** Which of Edit's fly-outs to open beside the button. */
internal enum class EditFlyout { Assign, Customize }

/**
 * The context menu of the current selection — shown when a button is tapped, or a marquee is
 * released over some. The physical editor's tile menu, for virtual buttons: verbs act on the
 * whole selection; Edit needs exactly one button.
 *
 * [onEdit] opens one of the Edit fly-outs on the sole selected button; [onMove] lifts the
 * selection (the next drag carries it); [onResize] shows the resize handles.
 */
@Composable
internal fun virtualButtonContextActions(
    vm: VirtualButtonEditorViewModel,
    onEdit: (EditFlyout, elementId: Long) -> Unit,
    onMove: () -> Unit,
    onResize: () -> Unit,
): List<MinputAction> {
    val sel by vm.selectedIds.collectAsStateWithLifecycle()
    val hasClipboard by vm.clipboardHasContent.collectAsStateWithLifecycle()
    val alignTo by vm.alignTo.collectAsStateWithLifecycle()
    val soleId = sel.singleOrNull()
    val hasSel = sel.isNotEmpty()

    val edit = MinputAction(
        label = "Edit",
        icon = Lucide.Pencil,
        enabled = soleId != null,
        submenu = { sub ->
            MinputActionMenu(
                expanded = sub.expanded,
                onDismissRequest = sub.onDismissRequest,
                actions = listOf(
                    // Commands + appearance: all per-button controls.
                    MinputAction("Assign", Lucide.Command, onClick = { soleId?.let { onEdit(EditFlyout.Assign, it) } }),
                    MinputAction("Customize", Lucide.Palette, onClick = { soleId?.let { onEdit(EditFlyout.Customize, it) } }),
                ),
            )
        },
    )
    val copy = MinputAction("Copy", Lucide.Copy, enabled = hasSel, onClick = { vm.copySelection() })
    val paste = MinputAction(
        label = "Paste",
        icon = Lucide.ClipboardPaste,
        enabled = hasClipboard,
        submenu = { sub ->
            MinputActionMenu(
                expanded = sub.expanded,
                onDismissRequest = sub.onDismissRequest,
                actions = listOf(
                    MinputAction("Paste new", Lucide.ClipboardPaste, onClick = { vm.paste(styleOnly = false) }),
                    MinputAction("Paste style", Lucide.Paintbrush, enabled = hasSel, onClick = { vm.paste(styleOnly = true) }),
                ),
            )
        },
    )
    // Align / space. "Align to canvas" picks the reference — off: the selection's own box (needs
    // two or more), on: the canvas (one is enough) — and the rows enable off it.
    val toCanvas = alignTo == AlignTarget.Canvas
    val canAlign = if (toCanvas) hasSel else sel.size >= 2
    fun alignOp(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, op: AlignOp) =
        MinputAction(label, icon, enabled = canAlign, onClick = { vm.applyAlign(op, alignTo) })
    val align = MinputAction(
        label = "Align",
        icon = Lucide.AlignStartVertical,
        submenu = { sub ->
            MinputActionMenu(
                expanded = sub.expanded,
                onDismissRequest = sub.onDismissRequest,
                actions = listOf(
                    // A toggle: flips in place, the menu staying up.
                    MinputAction(
                        "Align to canvas",
                        Lucide.Crosshair,
                        checked = toCanvas,
                        onClick = { vm.alignTo.value = if (toCanvas) AlignTarget.Selection else AlignTarget.Canvas },
                    ),
                ),
                actionGroups = listOf(
                    listOf(
                        alignOp("Align left", Lucide.AlignStartVertical, AlignOp.LEFT),
                        alignOp("Align vertically", Lucide.AlignCenterVertical, AlignOp.CENTER_X),
                        alignOp("Align right", Lucide.AlignEndVertical, AlignOp.RIGHT),
                        alignOp("Align top", Lucide.AlignStartHorizontal, AlignOp.TOP),
                        alignOp("Align horizontally", Lucide.AlignCenterHorizontal, AlignOp.CENTER_Y),
                        alignOp("Align bottom", Lucide.AlignEndHorizontal, AlignOp.BOTTOM),
                    ),
                    listOf(
                        alignOp("Stack vertically", Lucide.Rows2, AlignOp.STACK_V),
                        alignOp("Stack horizontally", Lucide.Columns2, AlignOp.STACK_H),
                    ),
                    listOf(
                        alignOp("Space vertically", Lucide.AlignVerticalSpaceAround, AlignOp.DIST_Y),
                        alignOp("Space horizontally", Lucide.AlignHorizontalSpaceAround, AlignOp.DIST_X),
                    ),
                ),
            )
        },
    )
    val templatize = MinputAction(
        label = "Templatize",
        icon = Lucide.Component,
        submenu = { sub ->
            MinputActionMenu(
                expanded = sub.expanded,
                onDismissRequest = sub.onDismissRequest,
                actions = listOf(
                    MinputAction("Whole overlay", Lucide.Frame, onClick = { menuGap("Templatize whole overlay") }),
                    MinputAction("Selection", Lucide.BoxSelect, enabled = hasSel, onClick = { menuGap("Templatize selection") }),
                ),
            )
        },
    )
    val move = MinputAction("Move", Lucide.Move, enabled = hasSel, onClick = onMove)
    val resize = MinputAction("Resize", Lucide.Scaling, enabled = hasSel, onClick = onResize)
    val delete = MinputAction("Delete", Lucide.Trash2, enabled = hasSel, destructive = true, onClick = { vm.deleteSelected() })
    return listOf(edit, copy, paste, align, templatize, move, resize, delete)
}

/**
 * The virtual button editor's rows in the hierarchy bar's menu — listed there only while it is
 * the editor on screen. The host calls this (it is ViewModel-backed) and hands the rows to the bar.
 */
@Composable
fun virtualButtonEditorBarActions(): List<MinputAction> =
    virtualButtonEditorBarActions(hiltViewModel<VirtualButtonEditorViewModel>())

@Composable
internal fun virtualButtonEditorBarActions(vm: VirtualButtonEditorViewModel): List<MinputAction> {
    val settings = vm.overlaySettings
    val editor = vm.virtualButtonEditor
    val snap by settings.snapEnabled.collectAsStateWithLifecycle()
    val grid by vm.showGrid.collectAsStateWithLifecycle()
    val square by settings.squareEditArea.collectAsStateWithLifecycle()
    val positionerShown by vm.positionerOpen.collectAsStateWithLifecycle()
    val scopes by editor.availableScopes.collectAsStateWithLifecycle()
    val current by editor.editingScope.collectAsStateWithLifecycle()

    // Toggles: each flips in place, the menu staying up.
    val options = MinputAction(
        label = "Options",
        icon = Lucide.SlidersHorizontal,
        submenu = { sub ->
            MinputActionMenu(
                expanded = sub.expanded,
                onDismissRequest = sub.onDismissRequest,
                actions = listOf(
                    MinputAction("Snapping", Lucide.Magnet, checked = snap, onClick = { settings.setSnapEnabled(!snap) }),
                    MinputAction("Show grid", Lucide.Grid3x3, checked = grid, onClick = { vm.showGrid.value = !grid }),
                    MinputAction("1:1 screen", Lucide.Square, checked = square, onClick = { settings.setSquareEditArea(!square) }),
                ),
                actionGroups = listOf(
                    listOf(
                        MinputAction("Positioner", Lucide.Move, checked = positionerShown, onClick = { vm.positionerOpen.value = !positionerShown }),
                    ),
                ),
            )
        },
    )

    // The viewed set's LAYERS. (The set itself is the bar's Buttons crumb; its layers were only
    // ever reachable from the retired core menu's scope row, so they come along here.)
    val setId = when (val s = current) {
        is OverlayScope.Set -> s.actionSetId
        is OverlayScope.Layer -> s.parentActionSetId
        null -> null
    }
    val layerChoices = scopes.filter { opt ->
        when (val s = opt.scope) {
            is OverlayScope.Set -> s.actionSetId == setId
            is OverlayScope.Layer -> s.parentActionSetId == setId
        }
    }
    val layers = MinputAction(
        label = "Layers",
        icon = Lucide.Layers,
        enabled = setId != null,
        submenu = { sub ->
            MinputDropdownMenu(
                expanded = sub.expanded,
                onDismissRequest = sub.onDismissRequest,
                current = layerChoices.firstOrNull { it.scope == current },
                options = layerChoices,
                optionLabel = { opt -> if (opt?.isLayer == true) opt.label else "Base buttons" },
                onPick = { opt -> opt?.let { editor.setScope(it.scope) } },
                optionIcon = { opt -> if (opt?.isLayer == true) Lucide.Layers2 else Lucide.Square },
                actionGroups = listOf(
                    listOf(
                        MinputAction("Add layer", Lucide.Plus, onClick = { setId?.let { id -> editor.addLayer(id) { editor.setScope(it) } } }),
                    ),
                ),
            )
        },
    )
    return listOf(options, layers)
}
