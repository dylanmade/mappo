package com.mappo.ui.screen.virtualset

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.mappo.data.model.OverlayElement
import com.mappo.data.settings.OverlaySettings
import com.mappo.service.overlay.element.VirtualButtonEditor
import com.mappo.service.overlay.element.OverlayScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.mappo.ui.minput.MinputMenuPlacement

/**
 * The virtual button editor's canvas: one gesture layer over a stage laid out at the display's
 * size and scaled to fit. Since 2026-11-11 a tap selects and opens the context menu, a HOLD (or
 * the menu's Move) lifts and the drag commits ONCE, a plain drag is a marquee; New / Undo / Redo
 * live on the toolbar row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1280dp-h800dp")
// The context menu's caret outline is a path — LEGACY graphics can't hit-test its rows.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VirtualButtonEditorScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val elements = MutableStateFlow(
        listOf(OverlayElement(id = 1L, layoutId = 1L, x = 0.4f, y = 0.4f, width = 0.2f, height = 0.2f)),
    )
    private lateinit var editor: VirtualButtonEditor
    private lateinit var vm: VirtualButtonEditorViewModel

    @Before
    fun setUp() {
        editor = mockk(relaxed = true) {
            every { this@mockk.elements } returns this@VirtualButtonEditorScreenTest.elements
            every { editingScope } returns MutableStateFlow<OverlayScope?>(OverlayScope.Set(10L))
            every { availableScopes } returns MutableStateFlow(emptyList())
            every { canUndo } returns MutableStateFlow(true)
            every { canRedo } returns MutableStateFlow(false)
        }
        val settings = mockk<OverlaySettings>(relaxed = true) {
            every { snapEnabled } returns MutableStateFlow(false)
            every { squareEditArea } returns MutableStateFlow(false)
        }
        vm = VirtualButtonEditorViewModel(editor, settings, mockk(relaxed = true))
        composeRule.setContent {
            MaterialTheme {
                Column {
                    VirtualButtonEditorToolbar(vm, vertical = false, menuPlacement = MinputMenuPlacement.Below)
                    // Mappo's compact 1:1 screen: the stage letterboxes inside it.
                    Box(Modifier.size(600.dp, 600.dp)) {
                        VirtualButtonEditorScreen(backdrop = null, vm = vm)
                    }
                }
            }
        }
    }

    private fun tapTheButton() {
        // Positions are fractions of the stage — the same fractions the button is stored in.
        composeRule.onNodeWithTag(VIRTUAL_STAGE_TAG).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        composeRule.waitForIdle()
    }

    @Test
    fun toolbar_new_addsAButton_andUndoUndoes() {
        composeRule.onNodeWithTag(TOOLBAR_NEW_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("New button").performClick()
        composeRule.waitForIdle()
        verify(exactly = 1) { editor.addDefaultElement() }
        composeRule.onNodeWithTag(TOOLBAR_UNDO_TAG).performClick()
        verify(exactly = 1) { editor.undo() }
    }

    @Test
    fun tappingAButton_selectsIt_andOpensItsContextMenu() {
        tapTheButton()
        assertEquals(setOf(1L), vm.selectedIds.value)
        listOf("Edit", "Copy", "Paste", "Align", "Templatize", "Move", "Resize", "Delete").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }
    }

    @Test
    fun contextMenu_delete_deletesTheSelection() {
        tapTheButton()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()
        verify(exactly = 1) { editor.delete(1L) }
        assertEquals(emptySet<Long>(), vm.selectedIds.value)
    }

    /** Regression (2026-10-15, re-homed): a fly-out's close dropped its content and its placement
     *  threw on the empty frame. Edit's fly-outs are what is left of that menu. */
    @Test
    fun contextMenu_edit_opensTheAssignFlyout_andAnOutsidePressClosesIt() {
        tapTheButton()
        composeRule.onNodeWithText("Edit").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Assign").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Add command").assertExists()
        composeRule.onNodeWithTag(VIRTUAL_STAGE_TAG).performTouchInput { click(Offset(width * 0.9f, height * 0.9f)) }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Add command").assertCountEquals(0)
    }

    @Test
    fun tappingEmptySpace_clearsTheSelection() {
        vm.select(setOf(1L))
        composeRule.onNodeWithTag(VIRTUAL_STAGE_TAG).performTouchInput { click(Offset(width * 0.9f, height * 0.9f)) }
        composeRule.waitForIdle()
        assertEquals(emptySet<Long>(), vm.selectedIds.value)
    }

    @Test
    fun aPlainDragOnAButton_isAMarquee_notAMove() {
        composeRule.onNodeWithTag(VIRTUAL_STAGE_TAG).performTouchInput {
            swipe(Offset(width * 0.5f, height * 0.5f), Offset(width * 0.7f, height * 0.7f), durationMillis = 200)
        }
        composeRule.waitForIdle()
        verify(exactly = 0) { editor.moveResizeAll(any()) }
        // The box covered the button: selected, and its menu is up.
        assertEquals(setOf(1L), vm.selectedIds.value)
        composeRule.onNodeWithText("Copy").assertExists()
    }

    @Test
    fun holdingAButton_liftsIt_andTheDragCommitsItsNewPosition_once() {
        val rects = slot<List<VirtualButtonEditor.ElementRect>>()
        every { editor.moveResizeAll(capture(rects)) } returns Unit
        composeRule.onNodeWithTag(VIRTUAL_STAGE_TAG).performTouchInput {
            down(Offset(width * 0.5f, height * 0.5f))
            advanceEventTime(1_000)
            repeat(10) { moveBy(Offset(width * 0.01f, 0f)) }
            up()
        }
        composeRule.waitForIdle()
        assertMovedOnce(rects)
    }

    @Test
    fun contextMenu_move_armsTheSelection_andTheNextDragCarriesIt() {
        val rects = slot<List<VirtualButtonEditor.ElementRect>>()
        every { editor.moveResizeAll(capture(rects)) } returns Unit
        tapTheButton()
        composeRule.onNodeWithText("Move").performClick()
        composeRule.waitForIdle()
        // From empty space: the lifted selection follows the drag wherever it starts.
        composeRule.onNodeWithTag(VIRTUAL_STAGE_TAG).performTouchInput {
            swipe(Offset(width * 0.1f, height * 0.9f), Offset(width * 0.2f, height * 0.9f), durationMillis = 300)
        }
        composeRule.waitForIdle()
        assertMovedOnce(rects)
    }

    @Test
    fun resizeHandles_takeDragsOnlyAfterTheMenusResize() {
        val rects = slot<List<VirtualButtonEditor.ElementRect>>()
        every { editor.moveResizeAll(capture(rects)) } returns Unit
        tapTheButton()
        composeRule.onNodeWithText("Resize").performClick()
        composeRule.waitForIdle()
        // The bottom-right handle, dragged outward.
        composeRule.onNodeWithTag(VIRTUAL_STAGE_TAG).performTouchInput {
            swipe(Offset(width * 0.6f, height * 0.6f), Offset(width * 0.7f, height * 0.7f), durationMillis = 300)
        }
        composeRule.waitForIdle()
        verify(exactly = 1) { editor.moveResizeAll(any()) }
        val r = rects.captured.single()
        assertEquals(0.4f, r.x, 0.01f)
        assertEquals(0.3f, r.width, 0.01f)
        assertEquals(0.3f, r.height, 0.01f)
    }

    private fun assertMovedOnce(rects: io.mockk.CapturingSlot<List<VirtualButtonEditor.ElementRect>>) {
        verify(exactly = 1) { editor.pushUndoSnapshot() }
        verify(exactly = 1) { editor.moveResizeAll(any()) }
        val r = rects.captured.single()
        assertEquals(1L, r.id)
        assertEquals(0.5f, r.x, 0.01f)
        assertEquals(0.4f, r.y, 0.01f)
    }
}
