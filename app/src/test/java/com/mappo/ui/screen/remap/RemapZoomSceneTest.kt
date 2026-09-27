package com.mappo.ui.screen.remap

import androidx.compose.ui.unit.dp
import com.mappo.data.model.steam.Activator
import com.mappo.data.model.steam.ActivatorGraph
import com.mappo.data.model.steam.ActivatorType
import com.mappo.data.model.steam.Binding
import com.mappo.data.model.steam.BindingOutput
import com.mappo.data.model.steam.BindingOutputType
import com.mappo.data.model.steam.GroupInput
import com.mappo.data.model.steam.GroupInputGraph
import com.mappo.data.model.steam.InputSource
import kotlin.math.abs
import org.junit.Test

/**
 * The zoomed scene's navigation map (2026-09-17).
 *
 * These pin the route a CARRIED command travels, which Dylan specified in hardware terms: up and
 * down walk one flank of the controller, left and right cross it to the group opposite. Spatial
 * focus search handles a free-roaming cursor; a move deserves a route you can learn, so it gets
 * this explicit map and these tests.
 *
 * 2026-09-26: the utility buttons used to be ONE group in the centre column, reached by going
 * right out of the left stick and left out of the right one. They are a group per side now, at
 * the bottom of their own column, so nothing sits between the flanks any more.
 */
class RemapZoomSceneTest {

    private companion object {
        const val Slots = 6
    }

    /**
     * Every row in these tests holds five commands plus its trailing "+" — six tiles, the width
     * the fixed press-type columns used to have, so the routes these pin are the same ones.
     * Rows are variable-length stacks now (2026-09-20), which is why the steppers ASK.
     */
    private val slots: (RemapSimpleGroup, SimpleRowSpec) -> Int = { _, _ -> Slots }

    private fun cell(group: RemapSimpleGroup, row: Int, slot: Int) =
        CellKey(group, group.rows[row], slot)

    @Test
    fun steppingInsideAGroup_staysInIt() {
        val from = cell(RemapSimpleGroup.FACE, row = 0, slot = 0)
        val down = stepCellAcrossGroups(from, dRow = 1, dCol = 0, slots = slots)
        assert(down == cell(RemapSimpleGroup.FACE, row = 1, slot = 0)) { "got $down" }
        val right = stepCellAcrossGroups(from, dRow = 0, dCol = 1, slots = slots)
        assert(right == cell(RemapSimpleGroup.FACE, row = 0, slot = 1)) { "got $right" }
    }

    @Test
    fun steppingOffTheBottom_entersTheGroupBelow_atItsTopRow() {
        val last = RemapSimpleGroup.DPAD.rows.lastIndex
        val from = cell(RemapSimpleGroup.DPAD, row = last, slot = 2)
        val next = stepCellAcrossGroups(from, dRow = 1, dCol = 0, slots = slots)
        // Down the left flank: d-pad → left stick, arriving on its first row, same column.
        assert(next == cell(RemapSimpleGroup.LEFT_STICK, row = 0, slot = 2)) { "got $next" }
    }

    @Test
    fun steppingOffTheTop_entersTheGroupAbove_atItsBottomRow() {
        val from = cell(RemapSimpleGroup.FACE, row = 0, slot = 0)
        val next = stepCellAcrossGroups(from, dRow = -1, dCol = 0, slots = slots)
        val shoulderLast = RemapSimpleGroup.RIGHT_SHOULDER.rows.lastIndex
        assert(next == cell(RemapSimpleGroup.RIGHT_SHOULDER, row = shoulderLast, slot = 0)) {
            "got $next"
        }
    }

    @Test
    fun steppingOffTheLastColumn_crossesToTheOppositeFlank_atItsFirstColumn() {
        // The LEFT flank reads outward from a glyph on its right, so its slot 0 is the tile
        // nearest the controller — the one you step right OUT of.
        val from = cell(RemapSimpleGroup.DPAD, row = 1, slot = 0)
        val next = stepCellAcrossGroups(from, dRow = 0, dCol = 1, slots = slots)
        assert(next == cell(RemapSimpleGroup.FACE, row = 1, slot = 0)) { "got $next" }
    }

    /**
     * **A step is a SCREEN direction, whichever way the row's slots happen to run.**
     *
     * A mirrored row's indices climb leftward, so this used to invert on the whole left flank:
     * pressing right carried a tile left, and pressing left did nothing at all because slot 0
     * was already the end of the row (Dylan, 2026-09-23). Ordinary focus navigation was always
     * fine — Compose's spatial search reads real positions — which is exactly why it only ever
     * showed up while carrying a tile.
     */
    @Test
    fun steppingLeftOnAMirroredRow_movesLeftOnScreen() {
        val from = cell(RemapSimpleGroup.DPAD, row = 0, slot = 0)
        // LEFT, away from the controller: outward along the row, so UP an index.
        val left = stepCellAcrossGroups(from, dRow = 0, dCol = -1, slots = slots)
        assert(left == cell(RemapSimpleGroup.DPAD, row = 0, slot = 1)) { "got $left" }
        // And RIGHT off slot 0 leaves the group entirely rather than walking the row backwards.
        val right = stepCellAcrossGroups(from, dRow = 0, dCol = 1, slots = slots)
        assert(right?.group == RemapSimpleGroup.FACE) { "got $right" }
    }

    @Test
    fun steppingIntoAMirroredGroup_landsOnTheTileNearestTheEdgeYouCameFrom() {
        // Travelling LEFT out of the face buttons enters the d-pad from its right-hand side,
        // which on a mirrored row is slot 0.
        val from = cell(RemapSimpleGroup.FACE, row = 1, slot = 0)
        val next = stepCellAcrossGroups(from, dRow = 0, dCol = -1, slots = slots)
        assert(next == cell(RemapSimpleGroup.DPAD, row = 1, slot = 0)) { "got $next" }
    }

    /**
     * **A utility group mirrors with the flank it joined** (Dylan, 2026-09-26).
     *
     * Select and Start used to share one centre-column group that mirrored per ROW — Select's
     * slots climbing leftward out of the card's centre line, Start's rightward. Each is on a
     * flank now, so each follows its own column's rule, whole.
     */
    @Test
    fun aUtilityGroupMirrorsWithItsFlank() {
        val select = RemapSimpleGroup.LEFT_UTILITY.rows.single()
        val start = RemapSimpleGroup.RIGHT_UTILITY.rows.single()
        assert(select.source == InputSource.SWITCH_SELECT) { "got $select" }
        assert(start.source == InputSource.SWITCH_START) { "got $start" }
        assert(RemapSimpleGroup.LEFT_UTILITY.slotsRunLeftward(select)) {
            "the left column's rows read leftward"
        }
        assert(!RemapSimpleGroup.RIGHT_UTILITY.slotsRunLeftward(start)) {
            "the right column's rows read rightward"
        }

        // Outward along a mirrored row is LEFT on screen, which is UP an index.
        val outward = stepCellAcrossGroups(
            CellKey(RemapSimpleGroup.LEFT_UTILITY, select, 0),
            dRow = 0,
            dCol = -1,
            slots = slots,
        )
        assert(outward == CellKey(RemapSimpleGroup.LEFT_UTILITY, select, 1)) { "got $outward" }
    }

    @Test
    fun eachUtilityGroupSitsUnderItsOwnStick() {
        // Down the left flank, past the stick, into the left utility group.
        val downLeft = stepCellAcrossGroups(
            cell(RemapSimpleGroup.LEFT_STICK, row = 0, slot = 0),
            dRow = 1,
            dCol = 0,
            slots = slots,
        )
        assert(downLeft?.group == RemapSimpleGroup.LEFT_UTILITY) { "got $downLeft" }
        val downRight = stepCellAcrossGroups(
            cell(RemapSimpleGroup.RIGHT_STICK, row = 0, slot = 0),
            dRow = 1,
            dCol = 0,
            slots = slots,
        )
        assert(downRight?.group == RemapSimpleGroup.RIGHT_UTILITY) { "got $downRight" }

        // And the two are each other's crossing, the way every band's pair is. Slot 0 is the
        // left group's inboard tile (its row reads leftward), so right is off the group.
        val across = stepCellAcrossGroups(
            cell(RemapSimpleGroup.LEFT_UTILITY, row = 0, slot = 0),
            dRow = 0,
            dCol = 1,
            slots = slots,
        )
        assert(across?.group == RemapSimpleGroup.RIGHT_UTILITY) { "got $across" }
        val back = stepCellAcrossGroups(
            cell(RemapSimpleGroup.RIGHT_UTILITY, row = 0, slot = 0),
            dRow = 0,
            dCol = -1,
            slots = slots,
        )
        assert(back?.group == RemapSimpleGroup.LEFT_UTILITY) { "got $back" }
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
        val topLeft = cell(RemapSimpleGroup.LEFT_SHOULDER, row = 0, slot = 0)
        assert(stepCellAcrossGroups(topLeft, dRow = -1, dCol = 0, slots = slots) == null)
        // Outboard of the left flank is its LAST slot — the row reads leftward from its glyph.
        val outboard = cell(RemapSimpleGroup.LEFT_SHOULDER, row = 0, slot = Slots - 1)
        assert(stepCellAcrossGroups(outboard, dRow = 0, dCol = -1, slots = slots) == null)
        val bottom = RemapSimpleGroup.LEFT_UTILITY
        val bottomLeft = cell(bottom, row = bottom.rows.lastIndex, slot = 0)
        assert(stepCellAcrossGroups(bottomLeft, dRow = 1, dCol = 0, slots = slots) == null)
    }

    @Test
    fun theStandaloneEditorsStepper_neverLeavesItsGroup() {
        // Slot 0 is the d-pad's inboard edge, and right is off the group entirely.
        val from = cell(RemapSimpleGroup.DPAD, row = 1, slot = 0)
        val next = stepCellWithinGroup(from, dRow = 0, dCol = 1, slots = slots)
        assert(next == from) { "an edge step should stay put, got $next" }
    }

    @Test
    fun aCellKnowsItsGroup_soRepeatedSubInputKeysStayDistinct() {
        // "click" names a row in the utility groups AND in both sticks; the group is what tells
        // them apart, in move state and in test tags alike.
        val onUtility = cell(RemapSimpleGroup.RIGHT_UTILITY, row = 0, slot = 0)
        val onStick = cell(RemapSimpleGroup.LEFT_STICK, row = 0, slot = 0)
        assert(onUtility.inputKey == onStick.inputKey) { "the keys should be the colliding pair" }
        assert(onUtility != onStick)
        assert(cellTestTag(onUtility) != cellTestTag(onStick))
    }

    @Test
    fun twoRowsOfOneGroupSharingASubInputKeyStayDistinct() {
        // A shoulder is a trigger AND a bumper — two SOURCES in one group, one table. Cells like
        // these were the same object until the row spec became the identity (2026-09-17). Start
        // and Select were the original pair; they are separate groups since 2026-09-26, so the
        // case is pinned on a group that still holds two sources.
        val group = RemapSimpleGroup.LEFT_SHOULDER
        val trigger = cell(group, row = 0, slot = 0)
        val bumper = cell(group, row = 1, slot = 0)
        assert(trigger.source != bumper.source) { "got $trigger and $bumper" }
        assert(trigger != bumper) { "got $trigger and $bumper" }
        assert(cellTestTag(trigger) != cellTestTag(bumper))
        // And the d-pad can actually walk between them.
        assert(stepCellAcrossGroups(trigger, dRow = 1, dCol = 0, slots = slots) == bumper)
    }

    @Test
    fun aTileLabelThatRepeatsTheCommandsOwnNameIsNotASecondLine() {
        // The label editor's placeholder IS the command's name, so typing it back means "no
        // label" (Dylan, 2026-09-19) — spacing and case included.
        assert(tileLabelFor(null, "Escape") == null)
        assert(tileLabelFor("", "Escape") == null)
        assert(tileLabelFor("  ", "Escape") == null)
        assert(tileLabelFor("Escape", "Escape") == null)
        assert(tileLabelFor(" escape ", "Escape") == null)
        assert(tileLabelFor("Menu", "Escape") == "Menu")
    }

    @Test
    fun aCellNamesItselfAfterEveryOutputItFires() {
        val escape = BindingOutput.KeyPress("ESCAPE")
        val a = BindingOutput.XInputButton("BUTTON_A")
        // Initials are the device prefix the name carries; a cycling command joins with a plus.
        assert(commandsText(listOf(escape), null, initials = false) == "ESCAPE") {
            commandsText(listOf(escape), null, initials = false)
        }
        assert(commandsText(listOf(escape), null, initials = true) == "KB: ESCAPE") {
            commandsText(listOf(escape), null, initials = true)
        }
        assert(commandsText(listOf(escape, a), null, initials = false) == "ESCAPE + A") {
            commandsText(listOf(escape, a), null, initials = false)
        }
        // An unbound slot contributes no name at all.
        assert(commandsText(listOf(BindingOutput.Unbound), null, initials = true) == "")
    }

    /**
     * One command, one reading — the basic row and the advanced tile resolve through the same
     * [commandDisplay] (Dylan, 2026-09-20). The basic view showed no device glyph at all and
     * resolved its own text, so the two views disagreed about the same binding.
     */
    /**
     * A row is a STACK now (Dylan, 2026-09-20): every command on the input, auto-sorted into the
     * press-type order the table's columns used to run in, with several of one type allowed.
     */
    @Test
    fun aRowsCommandsAreSortedByPressType_andSeveralMayShareOne() {
        fun command(id: Long, type: ActivatorType, key: String, order: Int = 0) =
            ActivatorGraph(
                Activator(id = id, groupInputId = 1L, type = type, orderIndex = order),
                listOf(
                    Binding(
                        id = id * 10,
                        activatorId = id,
                        outputType = BindingOutputType.KEY_PRESS,
                        args = key,
                    ),
                ),
            )

        val row = GroupInputGraph(
            input = GroupInput(id = 1L, bindingGroupId = 1L, inputKey = "button_a"),
            activators = listOf(
                command(3L, ActivatorType.RELEASE_PRESS, "UP"),
                command(1L, ActivatorType.LONG_PRESS, "LONG"),
                command(2L, ActivatorType.FULL_PRESS, "PRESS"),
                // An UNBOUND command is a cancelled "New", not something to show.
                ActivatorGraph(
                    Activator(id = 4L, groupInputId = 1L, type = ActivatorType.DOUBLE_PRESS),
                    listOf(Binding(id = 40L, activatorId = 4L, outputType = BindingOutputType.UNBOUND)),
                ),
            ),
        )
        val sorted = row.rowCommands(CommandOrder.PRESS_TYPE).map {
            BindingOutput.fromEntity(it.binding.outputType, it.binding.args)
        }
        assert(sorted.map { (it as BindingOutput.KeyPress).keyCode } == listOf("PRESS", "LONG", "UP")) {
            "got $sorted"
        }

        // TWO commands on one press type — the thing the fixed columns could never express.
        val doubled = row.copy(
            activators = listOf(
                ActivatorGraph(
                    Activator(id = 5L, groupInputId = 1L, type = ActivatorType.LONG_PRESS),
                    listOf(
                        Binding(id = 50L, activatorId = 5L, outputType = BindingOutputType.KEY_PRESS, args = "ONE", orderIndex = 0),
                        Binding(id = 51L, activatorId = 5L, outputType = BindingOutputType.KEY_PRESS, args = "TWO", orderIndex = 1),
                    ),
                ),
            ),
        ).rowCommands(CommandOrder.PRESS_TYPE)
        assert(doubled.map { (it.output as BindingOutput.KeyPress).keyCode } == listOf("ONE", "TWO")) {
            "got $doubled"
        }
        // And the row shows one more tile than it holds commands: the trailing "+".
        assert(rowSlotCount(doubled.size) == 3)
    }

    /**
     * Rows are different LENGTHS now, so a step between them clamps to what each actually holds
     * rather than to a fixed column count.
     */
    @Test
    fun steppingOntoAShorterRow_landsOnItsLastTile() {
        // The d-pad's first row has five tiles, the second only two.
        val counts: (RemapSimpleGroup, SimpleRowSpec) -> Int = { group, spec ->
            if (spec == group.rows[0]) 5 else 2
        }
        val from = cell(RemapSimpleGroup.DPAD, row = 0, slot = 4)
        val down = stepCellAcrossGroups(from, dRow = 1, dCol = 0, slots = counts)
        assert(down == cell(RemapSimpleGroup.DPAD, row = 1, slot = 1)) { "got $down" }

        // And a step off the SHORT row's inboard end still leaves the table for the neighbour.
        val onward = stepCellAcrossGroups(
            cell(RemapSimpleGroup.DPAD, row = 1, slot = 0),
            dRow = 0,
            dCol = 1,
            slots = counts,
        )
        assert(onward?.group == RemapSimpleGroup.FACE) { "got $onward" }
        assert(onward?.slot == 0) { "crossing a flank starts at the first tile, got $onward" }
    }

    @Test
    fun aCommandReadsTheSameWayInBothViews() {
        val outputs = listOf(BindingOutput.KeyPress("ESCAPE"))
        fun binding(label: String? = null, icon: Boolean = true, initials: Boolean = true) =
            Binding(
                activatorId = 1L,
                outputType = BindingOutputType.KEY_PRESS,
                args = "ESCAPE",
                label = label,
                showDeviceIcon = icon,
                showDeviceInitials = initials,
            )

        val plain = commandDisplay(binding(), outputs, null)
        assert(plain.glyph == outputs.single()) { "the glyph is the command's own output" }
        assert(plain.lineGlyph == outputs.single()) { "an unnamed command's row glyphs it" }
        assert(plain.text == "KB: ESCAPE") { plain.text }
        assert(plain.label == null) { "no user label: ${plain.label}" }
        // The basic view has ONE line for the tile's two: the label when there is one.
        assert(plain.line == "KB: ESCAPE") { plain.line }

        val labelled = commandDisplay(binding(label = "Menu"), outputs, null)
        assert(labelled.label == "Menu") { "${labelled.label}" }
        assert(labelled.text == "KB: ESCAPE") { labelled.text }
        assert(labelled.line == "Menu") { labelled.line }
        // A NAMED command's row goes bare (Dylan, 2026-09-20): the label replaces the output's
        // name, so the device info qualifying that name has nothing left to qualify. The tile
        // still glyphs its own output line, which the label sits above.
        assert(labelled.lineGlyph == null) { "${labelled.lineGlyph}" }
        assert(labelled.glyph == outputs.single()) { "the tile's output line keeps it" }

        // Both switches are per COMMAND, and they reach the basic row as well as the tile.
        val bare = commandDisplay(binding(icon = false, initials = false), outputs, null)
        assert(bare.glyph == null) { "icons off: ${bare.glyph}" }
        assert(bare.line == "ESCAPE") { bare.line }
    }

    /**
     * The zoomed controller sits where the basic grid puts it: level with the middle band, above
     * the sticks, below the shoulders.
     *
     * Dylan, 2026-09-18 — it used to be centred over the top TWO bands together, most of a band
     * higher than the basic view has it, so zooming into the button pad carried the face buttons
     * off the top of the screen. The camera parks on the card, so wherever the image is relative
     * to that card is what the user sees.
     */
    @Test
    fun theZoomedControllerStaysLevelWithTheGroupsItSitsBetween() {
        val scene = sceneGeometry(viewportW = 800.dp, viewportH = 480.dp, controllerAspect = 0.62f)
        fun centreY(rect: SceneRect) = (rect.y + rect.height / 2).value
        val controller = centreY(scene.controller)
        val dpad = centreY(scene.cards.getValue(RemapSimpleGroup.DPAD))
        val face = centreY(scene.cards.getValue(RemapSimpleGroup.FACE))
        assert(abs(dpad - controller) < 1f) { "d-pad at $dpad, controller at $controller" }
        assert(abs(face - controller) < 1f) { "face at $face, controller at $controller" }

        val shoulder = centreY(scene.cards.getValue(RemapSimpleGroup.RIGHT_SHOULDER))
        val stick = centreY(scene.cards.getValue(RemapSimpleGroup.RIGHT_STICK))
        assert(shoulder < controller) { "shoulders should sit above the controller, got $shoulder" }
        assert(stick > controller) { "sticks should sit below the controller, got $stick" }

        // And the whole scene still contains it — the camera can't travel past the scene's edge,
        // so anything hanging off the bottom would simply be unreachable.
        assert(scene.controller.y.value >= 0f) { "controller starts at ${scene.controller.y}" }
        val bottom = (scene.controller.y + scene.controller.height).value
        assert(bottom <= scene.height.value) { "controller ends at $bottom, scene is ${scene.height}" }
    }
}
