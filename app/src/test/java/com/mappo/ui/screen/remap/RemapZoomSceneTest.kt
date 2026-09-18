package com.mappo.ui.screen.remap

import com.mappo.data.model.steam.ActivatorType
import org.junit.Test

/**
 * The zoomed scene's navigation map (2026-09-17).
 *
 * These pin the route a CARRIED command travels, which Dylan specified in hardware terms: up and
 * down walk one flank of the controller, left and right cross it, and the utility group sits
 * between the two sticks — right out of the left stick, left out of the right one. Spatial focus
 * search handles a free-roaming cursor; a move deserves a route you can learn, so it gets this
 * explicit map and these tests.
 */
class RemapZoomSceneTest {

    private fun cell(group: RemapSimpleGroup, row: Int, column: Int) =
        CellKey(group, group.rows[row], pressTypeColumns[column])

    @Test
    fun steppingInsideAGroup_staysInIt() {
        val from = cell(RemapSimpleGroup.FACE, row = 0, column = 0)
        val down = stepCellAcrossGroups(from, dRow = 1, dCol = 0)
        assert(down == cell(RemapSimpleGroup.FACE, row = 1, column = 0)) { "got $down" }
        val right = stepCellAcrossGroups(from, dRow = 0, dCol = 1)
        assert(right == cell(RemapSimpleGroup.FACE, row = 0, column = 1)) { "got $right" }
    }

    @Test
    fun steppingOffTheBottom_entersTheGroupBelow_atItsTopRow() {
        val last = RemapSimpleGroup.DPAD.rows.lastIndex
        val from = cell(RemapSimpleGroup.DPAD, row = last, column = 2)
        val next = stepCellAcrossGroups(from, dRow = 1, dCol = 0)
        // Down the left flank: d-pad → left stick, arriving on its first row, same column.
        assert(next == cell(RemapSimpleGroup.LEFT_STICK, row = 0, column = 2)) { "got $next" }
    }

    @Test
    fun steppingOffTheTop_entersTheGroupAbove_atItsBottomRow() {
        val from = cell(RemapSimpleGroup.FACE, row = 0, column = 0)
        val next = stepCellAcrossGroups(from, dRow = -1, dCol = 0)
        val shoulderLast = RemapSimpleGroup.RIGHT_SHOULDER.rows.lastIndex
        assert(next == cell(RemapSimpleGroup.RIGHT_SHOULDER, row = shoulderLast, column = 0)) {
            "got $next"
        }
    }

    @Test
    fun steppingOffTheLastColumn_crossesToTheOppositeFlank_atItsFirstColumn() {
        val lastColumn = pressTypeColumns.lastIndex
        val from = cell(RemapSimpleGroup.DPAD, row = 1, column = lastColumn)
        val next = stepCellAcrossGroups(from, dRow = 0, dCol = 1)
        assert(next == cell(RemapSimpleGroup.FACE, row = 1, column = 0)) { "got $next" }
    }

    @Test
    fun theUtilityGroupSitsBetweenTheSticks() {
        val lastColumn = pressTypeColumns.lastIndex
        val outOfLeftStick = stepCellAcrossGroups(
            cell(RemapSimpleGroup.LEFT_STICK, row = 0, column = lastColumn),
            dRow = 0,
            dCol = 1,
        )
        assert(outOfLeftStick?.group == RemapSimpleGroup.UTILITY) { "got $outOfLeftStick" }

        val outOfRightStick = stepCellAcrossGroups(
            cell(RemapSimpleGroup.RIGHT_STICK, row = 0, column = 0),
            dRow = 0,
            dCol = -1,
        )
        assert(outOfRightStick?.group == RemapSimpleGroup.UTILITY) { "got $outOfRightStick" }

        // And out the far side of utility, on to the other stick.
        val onward = stepCellAcrossGroups(
            cell(RemapSimpleGroup.UTILITY, row = 0, column = lastColumn),
            dRow = 0,
            dCol = 1,
        )
        assert(onward?.group == RemapSimpleGroup.RIGHT_STICK) { "got $onward" }
    }

    @Test
    fun aRowWithFewerRows_clampsWhenCrossedInto() {
        // Utility has two rows (Start, Select), a stick one (its click): stepping right out of
        // utility's BOTTOM row must land on a row the stick actually has.
        val last = RemapSimpleGroup.UTILITY.rows.lastIndex
        val from = cell(RemapSimpleGroup.UTILITY, row = last, column = pressTypeColumns.lastIndex)
        val next = stepCellAcrossGroups(from, dRow = 0, dCol = 1)
        assert(next?.group == RemapSimpleGroup.RIGHT_STICK) { "got $next" }
        assert(next!!.inputKey in RemapSimpleGroup.RIGHT_STICK.rows.map { it.subInputKey }) { "got $next" }
    }

    @Test
    fun aStickEditorOffersOnlyItsClick() {
        // Stick MOVEMENT is the source mode's business, not a command you assign (Dylan,
        // 2026-09-17) — the four cardinal rows left the editor with it.
        listOf(RemapSimpleGroup.LEFT_STICK, RemapSimpleGroup.RIGHT_STICK).forEach { stick ->
            assert(stick.rows.map { it.subInputKey } == listOf("click")) { "$stick: ${stick.rows}" }
        }
    }

    @Test
    fun theEdgesOfTheSceneGoNowhere() {
        // Nothing above the shoulders, nothing below the sticks, nothing outboard of a flank.
        val topLeft = cell(RemapSimpleGroup.LEFT_SHOULDER, row = 0, column = 0)
        assert(stepCellAcrossGroups(topLeft, dRow = -1, dCol = 0) == null)
        assert(stepCellAcrossGroups(topLeft, dRow = 0, dCol = -1) == null)
        val bottomLeft = cell(RemapSimpleGroup.LEFT_STICK, row = RemapSimpleGroup.LEFT_STICK.rows.lastIndex, column = 0)
        assert(stepCellAcrossGroups(bottomLeft, dRow = 1, dCol = 0) == null)
    }

    @Test
    fun theStandaloneEditorsStepper_neverLeavesItsGroup() {
        val lastColumn = pressTypeColumns.lastIndex
        val from = cell(RemapSimpleGroup.DPAD, row = 1, column = lastColumn)
        val next = stepCellWithinGroup(from, dRow = 0, dCol = 1)
        assert(next == from) { "an edge step should stay put, got $next" }
    }

    @Test
    fun aCellKnowsItsGroup_soRepeatedSubInputKeysStayDistinct() {
        // "click" names a row in the utility group AND in both sticks; the group is what tells
        // them apart, in move state and in test tags alike.
        val onUtility = cell(RemapSimpleGroup.UTILITY, row = 0, column = 0)
        val onStick = cell(RemapSimpleGroup.LEFT_STICK, row = 0, column = 0)
        assert(onUtility.inputKey == onStick.inputKey) { "the keys should be the colliding pair" }
        assert(onUtility != onStick)
        assert(cellTestTag(onUtility) != cellTestTag(onStick))
    }

    @Test
    fun twoRowsOfOneGroupSharingASubInputKeyStayDistinct() {
        // Start and Select are both a "click" — different SOURCES, one group, one table. Their
        // cells were the same object until the row spec became the identity (2026-09-17).
        val start = cell(RemapSimpleGroup.UTILITY, row = 0, column = 0)
        val select = cell(RemapSimpleGroup.UTILITY, row = 1, column = 0)
        assert(start.inputKey == select.inputKey) { "the keys should be the colliding pair" }
        assert(start != select) { "got $start and $select" }
        assert(cellTestTag(start) != cellTestTag(select))
        // And the d-pad can actually walk between them.
        assert(stepCellAcrossGroups(start, dRow = 1, dCol = 0) == select)
    }
}
