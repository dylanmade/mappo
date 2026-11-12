package com.mappo.ui.screen.virtualset

import com.mappo.data.model.OverlayElement
import com.mappo.data.settings.OverlaySettings
import com.mappo.service.overlay.element.VirtualButtonEditor
import com.mappo.service.overlay.element.OverlayScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The virtual button editor's non-gesture edits (ported from the retired overlay-window editor). */
@OptIn(ExperimentalCoroutinesApi::class)
class VirtualButtonEditorViewModelTest {

    private val elements = MutableStateFlow(
        listOf(
            OverlayElement(id = 1L, layoutId = 1L, x = 0.1f, y = 0.1f, width = 0.1f, height = 0.1f),
            OverlayElement(id = 2L, layoutId = 1L, x = 0.5f, y = 0.3f, width = 0.2f, height = 0.1f),
        ),
    )
    private val scope = MutableStateFlow<OverlayScope?>(OverlayScope.Set(10L))
    private lateinit var editor: VirtualButtonEditor
    private lateinit var vm: VirtualButtonEditorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        editor = mockk(relaxed = true) {
            every { this@mockk.elements } returns this@VirtualButtonEditorViewModelTest.elements
            every { editingScope } returns scope
        }
        vm = VirtualButtonEditorViewModel(editor, mockk<OverlaySettings>(relaxed = true), mockk(relaxed = true))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun tap_selectsThatButtonAlone_butKeepsASelectionItIsPartOf() {
        vm.tapElement(1L)
        assertEquals(setOf(1L), vm.selectedIds.value)
        vm.tapElement(2L)
        assertEquals(setOf(2L), vm.selectedIds.value)
        vm.select(setOf(1L, 2L))
        vm.tapElement(1L)
        assertEquals(setOf(1L, 2L), vm.selectedIds.value)
    }

    @Test
    fun aDifferentScope_clearsTheSelectionAndConfig() {
        vm.tapElement(1L)
        vm.configElementId.value = 1L
        scope.value = OverlayScope.Set(11L)
        assertEquals(emptySet<Long>(), vm.selectedIds.value)
        assertNull(vm.configElementId.value)
    }

    @Test
    fun alignLeftToSelection_movesEveryMemberToTheLeftmostEdge_inOneWrite() {
        vm.select(setOf(1L, 2L))
        val rects = slot<List<VirtualButtonEditor.ElementRect>>()
        every { editor.moveResizeAll(capture(rects)) } returns Unit

        vm.applyAlign(AlignOp.LEFT, AlignTarget.Selection)

        verify(exactly = 1) { editor.pushUndoSnapshot() }
        assertEquals(listOf(0.1f, 0.1f), rects.captured.map { it.x })
        assertEquals(listOf(0.1f, 0.3f), rects.captured.map { it.y })
    }

    @Test
    fun deleteSelected_deletesEach_andClearsTheSelection() {
        vm.select(setOf(1L, 2L))
        vm.deleteSelected()
        verify { editor.delete(1L) }
        verify { editor.delete(2L) }
        assertEquals(emptySet<Long>(), vm.selectedIds.value)
    }
}
