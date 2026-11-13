package com.mappo.ui.screen.virtualset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.AlignStartVertical
import com.composables.icons.lucide.ClipboardPaste
import com.composables.icons.lucide.Component
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Redo2
import com.composables.icons.lucide.SquarePlus
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Undo2
import com.mappo.ui.minput.MinputAction
import com.mappo.ui.minput.MinputActionMenu
import com.mappo.ui.minput.MinputButton
import com.mappo.ui.minput.MinputButtonCluster
import com.mappo.ui.minput.MinputClusterButton
import com.mappo.ui.minput.MinputClusterDivider
import com.mappo.ui.minput.MinputMenuPlacement
import com.mappo.ui.minput.MinputSegmentGap
import com.mappo.ui.minput.MinputSize

/**
 * **The virtual button editor's toolbar row** (2026-11-11; every command since 2026-11-13) — the
 * hierarchy bar's SECOND row while this editor is on screen. In order:
 *
 *  - **New** — menu: a new button, or one from a template;
 *  - **Edit** — menu: Assign / Customize, for the one selected button;
 *  - **Copy**;
 *  - **Paste** — menu: Paste new / Paste style;
 *  - **Align** — menu: the align, stack and space verbs;
 *  - **Undo | Redo** — one shared container ([MinputButtonCluster]), a divider between them;
 *  - **Delete**.
 *
 * The verbs act on the selection and are disabled without one (Paste needs only a clipboard).
 *
 * Always [MinputSize.Large] — one variant up from the compact bar's — whichever the bar is in.
 * [vertical] is the side-pinned bar's form: a column, every button collapsed to its glyph. Menus open
 * [menuPlacement] — away from the edge the bar is pinned to, as the bar's own do.
 *
 * Mounted by the controls screen beside the bar (it is ViewModel-backed, so the host supplies it).
 */
@Composable
fun VirtualButtonEditorToolbar(
    vertical: Boolean,
    menuPlacement: MinputMenuPlacement,
    modifier: Modifier = Modifier,
) {
    VirtualButtonEditorToolbar(hiltViewModel<VirtualButtonEditorViewModel>(), vertical, menuPlacement, modifier)
}

@Composable
internal fun VirtualButtonEditorToolbar(
    vm: VirtualButtonEditorViewModel,
    vertical: Boolean,
    menuPlacement: MinputMenuPlacement,
    modifier: Modifier = Modifier,
) {
    val editor = vm.virtualButtonEditor
    val canUndo by editor.canUndo.collectAsStateWithLifecycle()
    val canRedo by editor.canRedo.collectAsStateWithLifecycle()
    val selected by vm.selectedIds.collectAsStateWithLifecycle()
    val hasClipboard by vm.clipboardHasContent.collectAsStateWithLifecycle()
    val hasSel = selected.isNotEmpty()
    val align = alignMenu(vm)
    val size = ToolbarSize

    val content: @Composable () -> Unit = {
        ToolbarMenuButton(
            "New", Lucide.Plus, TOOLBAR_NEW_TAG, vertical, menuPlacement,
            actions = listOf(
                MinputAction("New button", Lucide.SquarePlus, onClick = { vm.addElement() }),
                MinputAction("From template", Lucide.Component, onClick = { menuGap("Add template") }),
            ),
        )
        ToolbarMenuButton(
            "Edit", Lucide.Pencil, TOOLBAR_EDIT_TAG, vertical, menuPlacement,
            enabled = selected.size == 1,
            actions = editActions(vm),
        )
        ToolbarButton("Copy", Lucide.Copy, TOOLBAR_COPY_TAG, vertical, enabled = hasSel, onClick = { vm.copySelection() })
        ToolbarMenuButton(
            "Paste", Lucide.ClipboardPaste, TOOLBAR_PASTE_TAG, vertical, menuPlacement,
            enabled = hasClipboard,
            actions = pasteActions(vm, hasSel),
        )
        ToolbarMenuButton(
            "Align", Lucide.AlignStartVertical, TOOLBAR_ALIGN_TAG, vertical, menuPlacement,
            enabled = hasSel,
            actions = align.actions,
            actionGroups = align.groups,
        )
        MinputButtonCluster(size = size, vertical = vertical) {
            MinputClusterButton(
                icon = Lucide.Undo2,
                text = "Undo".takeUnless { vertical },
                contentDescription = "Undo",
                enabled = canUndo,
                size = size,
                onClick = { editor.undo() },
                modifier = Modifier.testTag(TOOLBAR_UNDO_TAG),
            )
            MinputClusterDivider(size = size, vertical = vertical)
            MinputClusterButton(
                icon = Lucide.Redo2,
                text = "Redo".takeUnless { vertical },
                contentDescription = "Redo",
                enabled = canRedo,
                size = size,
                onClick = { editor.redo() },
                modifier = Modifier.testTag(TOOLBAR_REDO_TAG),
            )
        }
        ToolbarButton("Delete", Lucide.Trash2, TOOLBAR_DELETE_TAG, vertical, enabled = hasSel, onClick = { vm.deleteSelected() })
    }
    // Like the bar it hangs under, the row FLOATS over the canvas: a touch in its gaps is its own.
    val blockTouches = Modifier.pointerInput(Unit) {}
    if (vertical) {
        Column(
            modifier = modifier.then(blockTouches),
            verticalArrangement = Arrangement.spacedBy(MinputSegmentGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    } else {
        Row(
            modifier = modifier.then(blockTouches),
            horizontalArrangement = Arrangement.spacedBy(MinputSegmentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

/** A toolbar verb: glyph + label, the label dropped when the row is [vertical]. */
@Composable
private fun ToolbarButton(
    label: String,
    icon: ImageVector,
    tag: String,
    vertical: Boolean,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    MinputButton(
        text = label.takeUnless { vertical },
        leadingIcon = icon,
        contentDescription = label,
        enabled = enabled,
        highlighted = highlighted,
        size = ToolbarSize,
        onClick = onClick,
        modifier = Modifier.testTag(tag),
    )
}

/** A toolbar button that opens a menu of [actions] (then the divider-parted [actionGroups]); lit
 *  while its menu is up. */
@Composable
private fun ToolbarMenuButton(
    label: String,
    icon: ImageVector,
    tag: String,
    vertical: Boolean,
    menuPlacement: MinputMenuPlacement,
    actions: List<MinputAction>,
    enabled: Boolean = true,
    actionGroups: List<List<MinputAction>> = emptyList(),
) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolbarButton(label, icon, tag, vertical, enabled = enabled, highlighted = open, onClick = { open = !open })
        MinputActionMenu(
            // The button can go disabled under an open menu (the selection its rows act on is gone).
            expanded = open && enabled,
            onDismissRequest = { open = false },
            actions = actions,
            actionGroups = actionGroups,
            placement = menuPlacement,
            caret = true,
        )
    }
}

/** One variant above the hierarchy bar's compact one (Standard read too small, 2026-11-12). */
private val ToolbarSize = MinputSize.Large

internal const val TOOLBAR_NEW_TAG = "virtual-editor:new"
internal const val TOOLBAR_EDIT_TAG = "virtual-editor:edit"
internal const val TOOLBAR_COPY_TAG = "virtual-editor:copy"
internal const val TOOLBAR_PASTE_TAG = "virtual-editor:paste"
internal const val TOOLBAR_ALIGN_TAG = "virtual-editor:align"
internal const val TOOLBAR_UNDO_TAG = "virtual-editor:undo"
internal const val TOOLBAR_REDO_TAG = "virtual-editor:redo"
internal const val TOOLBAR_DELETE_TAG = "virtual-editor:delete"
