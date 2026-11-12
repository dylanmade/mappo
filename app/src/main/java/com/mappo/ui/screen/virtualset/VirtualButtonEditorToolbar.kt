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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Component
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Redo2
import com.composables.icons.lucide.SquarePlus
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
 * **The virtual button editor's toolbar row** (2026-11-11) — the hierarchy bar's SECOND row while
 * this editor is on screen, holding the canvas-wide verbs that used to sit on the draggable menu:
 *
 *  - **New** — its own button, opening a menu: a new button, or one from a template;
 *  - **Undo | Redo** — one shared container ([MinputButtonCluster]), a divider between them,
 *    each a glyph + its label.
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
    var newOpen by remember { mutableStateOf(false) }
    val size = ToolbarSize

    val content: @Composable () -> Unit = {
        Box {
            MinputButton(
                text = "New".takeUnless { vertical },
                leadingIcon = Lucide.Plus,
                contentDescription = "New",
                highlighted = newOpen,
                size = size,
                onClick = { newOpen = !newOpen },
                modifier = Modifier.testTag(TOOLBAR_NEW_TAG),
            )
            MinputActionMenu(
                expanded = newOpen,
                onDismissRequest = { newOpen = false },
                actions = listOf(
                    MinputAction("New button", Lucide.SquarePlus, onClick = { vm.addElement() }),
                    MinputAction("From template", Lucide.Component, onClick = { menuGap("Add template") }),
                ),
                placement = menuPlacement,
                caret = true,
            )
        }
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

/** One variant above the hierarchy bar's compact one (Standard read too small, 2026-11-12). */
private val ToolbarSize = MinputSize.Large

internal const val TOOLBAR_NEW_TAG = "virtual-editor:new"
internal const val TOOLBAR_UNDO_TAG = "virtual-editor:undo"
internal const val TOOLBAR_REDO_TAG = "virtual-editor:redo"
