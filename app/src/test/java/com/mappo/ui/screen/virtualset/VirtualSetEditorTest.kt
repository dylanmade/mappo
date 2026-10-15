package com.mappo.ui.screen.virtualset

import androidx.compose.foundation.layout.Box
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
import com.mappo.service.overlay.element.OverlayEditor
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

/**
 * The virtual editor's canvas (2026-10-15): one gesture layer over a stage laid out at the
 * display's size and scaled to fit — a tap selects, a drag moves and commits ONCE.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1280dp-h800dp")
class VirtualSetEditorTest {

    @get:Rule val composeRule = createComposeRule()

    private val elements = MutableStateFlow(
        listOf(OverlayElement(id = 1L, layoutId = 1L, x = 0.4f, y = 0.4f, width = 0.2f, height = 0.2f)),
    )
    private lateinit var editor: OverlayEditor
    private lateinit var vm: VirtualSetEditorViewModel

    @Before
    fun setUp() {
        editor = mockk(relaxed = true) {
            every { this@mockk.elements } returns this@VirtualSetEditorTest.elements
            every { editingScope } returns MutableStateFlow<OverlayScope?>(OverlayScope.Set(10L))
            every { availableScopes } returns MutableStateFlow(emptyList())
            every { canUndo } returns MutableStateFlow(false)
            every { canRedo } returns MutableStateFlow(false)
        }
        val settings = mockk<OverlaySettings>(relaxed = true) {
            every { snapEnabled } returns MutableStateFlow(false)
            every { squareEditArea } returns MutableStateFlow(false)
        }
        vm = VirtualSetEditorViewModel(editor, settings, mockk(relaxed = true))
        composeRule.setContent {
            MaterialTheme {
                // Mappo's compact 1:1 screen: the stage letterboxes inside it.
                Box(Modifier.size(600.dp, 600.dp)) {
                    VirtualSetEditor(backdrop = null, vm = vm)
                }
            }
        }
    }

    /** Regression (2026-10-15): a fly-out's close dropped its content and its placement threw on
     *  the empty frame — "Add new" crashed the app every time. */
    @Test
    fun addNew_closesItsFlyout_withoutCrashing() {
        composeRule.onNodeWithText("Add").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Add new").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        verify(exactly = 1) { editor.addDefaultElement() }
        composeRule.onAllNodesWithText("Add new").assertCountEquals(0)
    }

    @Test
    fun tappingAButton_selectsIt() {
        // Positions are fractions of the stage — the same fractions the button is stored in.
        composeRule.onNodeWithTag(VIRTUAL_STAGE_TAG).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        composeRule.waitForIdle()
        assertEquals(setOf(1L), vm.selectedIds.value)
    }

    @Test
    fun tappingEmptySpace_clearsTheSelection() {
        vm.select(setOf(1L))
        // Bottom-right: clear of the button and of the core menu (docked at the left edge).
        composeRule.onNodeWithTag(VIRTUAL_STAGE_TAG).performTouchInput { click(Offset(width * 0.9f, height * 0.9f)) }
        composeRule.waitForIdle()
        assertEquals(emptySet<Long>(), vm.selectedIds.value)
    }

    @Test
    fun draggingAButton_commitsItsNewPosition_once() {
        val rects = slot<List<OverlayEditor.ElementRect>>()
        every { editor.moveResizeAll(capture(rects)) } returns Unit
        composeRule.onNodeWithTag(VIRTUAL_STAGE_TAG).performTouchInput {
            swipe(Offset(width * 0.5f, height * 0.5f), Offset(width * 0.6f, height * 0.5f), durationMillis = 300)
        }
        composeRule.waitForIdle()
        verify(exactly = 1) { editor.pushUndoSnapshot() }
        verify(exactly = 1) { editor.moveResizeAll(any()) }
        val r = rects.captured.single()
        assertEquals(1L, r.id)
        assertEquals(0.5f, r.x, 0.01f)
        assertEquals(0.4f, r.y, 0.01f)
    }
}
