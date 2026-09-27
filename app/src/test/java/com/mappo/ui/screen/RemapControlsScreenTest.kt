package com.mappo.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.requestFocus
import com.mappo.ui.screen.remap.ControllerImageTestTag
import com.mappo.data.settings.TileReveal
import com.mappo.ui.screen.remap.ControlsBodyTestTag
import com.mappo.ui.screen.remap.LocalTileReveal
import com.mappo.ui.screen.remap.GroupOutlineEndInset
import com.mappo.ui.screen.remap.GroupOutlineInset
import com.mappo.ui.screen.remap.groupBackingTestTag
import com.mappo.ui.screen.remap.RemapSimpleGroup
import com.mappo.data.model.steam.ActionLayer
import com.mappo.data.model.steam.ActionLayerGraph
import com.mappo.data.model.steam.ActionSet
import com.mappo.data.model.steam.ActionSetGraph
import com.mappo.data.model.steam.Activator
import com.mappo.data.model.steam.ActivatorGraph
import com.mappo.data.model.steam.ActivatorType
import com.mappo.data.model.steam.Binding
import com.mappo.data.model.steam.BindingGroup
import com.mappo.data.model.steam.BindingGroupGraph
import com.mappo.data.model.steam.BindingMode
import com.mappo.data.model.steam.BindingOutput
import com.mappo.data.model.steam.BindingOutputType
import com.mappo.data.model.steam.ControllerConfig
import com.mappo.data.model.steam.ControllerProfile
import com.mappo.data.model.steam.ControllerType
import com.mappo.data.model.steam.GroupInput
import com.mappo.data.model.steam.GroupInputGraph
import com.mappo.data.model.steam.InputSource
import com.mappo.data.model.steam.PresetEntry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose tests for the brick 1.3 master-detail Remap Controls screen.
 * Robolectric per `project_compose_ui_test_blocker` — UI tests can't run on the AYN Thor.
 */
@RunWith(RobolectricTestRunner::class)
// Landscape-handheld viewport: the screen is a wide app bar + an expanded nav rail + the
// detail pane, which needs far more than Robolectric's default ~320dp width. This matches the
// real device (a wide landscape screen) so the rail, fly-out, and actions stay hit-testable.
@Config(sdk = [33], qualifiers = "w1280dp-h800dp")
class RemapControlsScreenTest {

    @get:Rule val composeRule = createComposeRule()



    @Test
    fun simpleView_restingRows_nameTheMode_notTheHardware() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = sampleConfig(),
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // sampleConfig populates only the face buttons, so every other source resolves to no
        // group at all — which is device default, and now SAYS so. It used to print a hardcoded
        // physical name per sub-input ("A Button", "L-Stick Click"), which looked like an
        // assignment, corresponded to no binding the advanced view could show, and was the
        // visible half of layouts seeding nothing (Dylan, 2026-09-21).
        composeRule.onAllNodesWithText("(Device default)", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty().let {
                assert(it) { "a source with no group should say it is at the device default" }
            }
        for (fake in listOf("A Button", "D-Pad Up", "L-Stick Click", "L-Stick Move")) {
            composeRule.onAllNodesWithText(fake, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    /**
     * A group box is a controller focus stop. Beyond navigation, that is what tells the right
     * stick the cursor is in the view rather than the chrome (see `StickScrollArbiter`), and
     * what the camera and the edit-mode seating both follow, so it is pinned on its own.
     */
    @Test
    fun simpleView_groupBoxesTakeControllerFocus() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = sampleConfig(),
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("simple-group:FACE").requestFocus()
        composeRule.onNodeWithTag("simple-group:FACE").assertIsFocused()
    }

    @Test
    fun simpleView_sticksSummarizeMovementAsOneRow() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = seedShapedConfig(),
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // ONE movement row per stick, and it names the stick's MODE — movement IS the mode, so
        // there is no binding to name and the mode's own word is the honest label.
        composeRule.onAllNodesWithText("Joystick", useUnmergedTree = true).assertCountEquals(2)
        // The click rows show their seeded self-mappings beside it.
        composeRule.onNodeWithText("L3", substring = true, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("R3", substring = true, useUnmergedTree = true).assertExists()
        // And the four cardinal rows stay out of the box.
        composeRule.onAllNodesWithText("L-Stick Up", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("R-Stick Left", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun holdingDpadBox_morphsIntoGroupEditor() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = sampleConfig(),
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        openAdvanced("DPAD")
        composeRule.waitForIdle()

        // The view zooms into the scene, with the camera on the group that was held.
        composeRule.onNodeWithTag("group-editor").assertExists()
        inOpenCard(hasContentDescription("Close")).assertCountEquals(1)
    }



    @Test
    fun displaysBoundLabel_forConfiguredBinding() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = sampleConfig(boundButtonA = BindingOutput.KeyPress("ENTER")),
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeRule.onNodeWithText("ENTER", useUnmergedTree = true).assertExists()
    }

    @Test
    fun simpleView_showsUserLabel_insteadOfAssignment() {
        setScreenLocal(sampleConfig(boundButtonA = BindingOutput.KeyPress("ENTER"), buttonALabel = "Jump"))

        composeRule.onNodeWithText("Jump", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("ENTER", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun simpleView_showsAlternatePressAssignment_inline() {
        setScreenLocal(
            sampleConfig(boundButtonA = BindingOutput.KeyPress("ENTER"), extraButtonAInput = true),
        )

        // 2026-09-11: a row renders EVERY assigned press type inline (Press then Long here),
        // replacing the "+N" badge that only counted them.
        composeRule.onNodeWithText("ENTER", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("SPACE", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("\u200A+\u200A1", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun simpleView_alternateOnlyRow_showsAssignmentNotRestingLabel() {
        setScreenLocal(sampleConfig(extraButtonBInput = true))

        // button_b's standard press is UNBOUND and its long press is bound: assignments close
        // up rank (no empty leading slot), and the row stops showing its resting hardware name.
        composeRule.onNodeWithText("Q", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("B Button", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun simpleView_showsAssignment_evenWhenGroupModeIsDeviceDefault() {
        setScreenLocal(
            sampleConfig(
                boundButtonA = BindingOutput.KeyPress("ENTER"),
                faceMode = BindingMode.DEVICE_DEFAULT,
            ),
        )

        // Regression (fixed 2026-09-11): the row label used to early-return the input's PHYSICAL
        // name whenever the group's mode was DEVICE_DEFAULT, so a command assigned from the
        // advanced table — which never consults the mode — showed as "A Button". A row with an
        // assignment shows the assignment; the mode only decides the RESTING label.
        composeRule.onNodeWithText("ENTER", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("A Button", useUnmergedTree = true).assertCountEquals(0)
    }


    /**
     * The scroll extent follows the content when a row grows.
     *
     * Dylan suspected this on 2026-09-24 ("the scrollbar still seems to think there is not yet
     * additional scrollable space") after adding a tile that landed past the window's edge. It
     * holds, so the stale-looking bar was the absent SCROLL, not a stale extent — see
     * [addingACommand_seatsTheCursorOnIt_notBackWhereYouCameIn], which is what wasn't moving.
     * Kept because it pins the half that works.
     */
    @Test
    fun aWiderRow_widensWhatTheBodyCanScroll() {
        val live = androidx.compose.runtime.mutableStateOf(seedShapedConfig())
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(420.dp, 500.dp)) {
                    RemapControlsScreen(
                        config = live.value,
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.onNodeWithTag("simple-group:FACE").performClick()
        composeRule.waitForIdle()
        fun maxScroll(): Float = composeRule
            .onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
            .maxValue()

        val before = maxScroll()
        live.value = live.value.withTwoCommands(InputSource.BUTTON_DIAMOND, "button_a", 900L)
        composeRule.waitForIdle()

        val after = maxScroll()
        assert(after > before) {
            "The face row grew by two commands and the body still scrolls $after (was $before)"
        }
    }

    /**
     * **The resting view is arranged around the CONTROLLER, and the scroll around the CONTENT.**
     *
     * Two claims that have to hold together, because either one alone is easy and wrong:
     *
     *  - the picture of the device sits dead centre in the window, whatever the two flanks
     *    measure — it is what the whole view is an arrangement of, and it used to drift
     *    whenever one side's rows were longer than the other's;
     *  - and the scroll range still ends where the content does. The first fix for the centring
     *    widened both side columns to the wider of the two, which centres the controller by
     *    padding the short side with a slab of nothing — and that nothing was scrollable, so the
     *    bar advertised content off to one side and scrolling there found blank plate (Dylan,
     *    2026-09-25: "not at all acceptable or tenable").
     *
     * The grid is padded by the SHORTFALL against the window instead, which puts the
     * controller-centred position at that end of the range: with a short right flank the resting
     * view is already as far right as the scroller goes, so there is nothing to scroll into.
     * That is what the last two assertions say.
     */
    @Test
    fun restingView_centresTheController_withoutInventingScrollSpace() {
        composeRule.setContent {
            MaterialTheme {
                // Small enough that the grid overflows, with the left flank carrying three
                // commands on every row and the right flank one — the lopsided case.
                Surface(modifier = androidx.compose.ui.Modifier.size(300.dp, 400.dp)) {
                    RemapControlsScreen(
                        config = seedShapedConfig()
                            .withPressStack(InputSource.DPAD, 7000L)
                            .withPressStack(InputSource.LEFT_TRIGGER, 8000L)
                            .withPressStack(InputSource.LEFT_JOYSTICK, 9000L),
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val body = composeRule
            .onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
            .fetchSemanticsNode()
        val range = body.config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
        val viewportLeft = body.positionInRoot.x
        val viewportRight = viewportLeft + body.size.width

        // The premise: this grid really is wider than the window, and really does hang off the
        // left. Without both, everything below passes for the wrong reason.
        assert(range.maxValue() > 0f) { "fixture does not overflow — nothing to scroll" }
        val leftFlank = composeRule
            .onNodeWithTag("simple-group:DPAD", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.x
        assert(leftFlank < viewportLeft) {
            "fixture's left flank is not off-screen (at $leftFlank, window starts $viewportLeft)"
        }

        // boundsInRoot, not the semantics size: the stage measures its contents once and DRAWS
        // them through a scaling layer, so the node's own width is the zoomed one.
        val image = composeRule
            .onNodeWithTag(ControllerImageTestTag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val drift = image.center.x - (viewportLeft + viewportRight) / 2
        assert(kotlin.math.abs(drift) <= 2f) {
            "the controller sits ${"%.1f".format(drift)}px off the middle of the window"
        }

        assert(range.value() == range.maxValue()) {
            "the resting view can still scroll toward the short flank: at ${range.value()} of " +
                "${range.maxValue()}, so there is blank grid over there to scroll into"
        }
        // And the end of the range is the end of the CONTENT, give or take the grid's own margin.
        val rightFlank = composeRule
            .onNodeWithTag("simple-group:FACE", useUnmergedTree = true)
            .fetchSemanticsNode()
        val contentRight = rightFlank.positionInRoot.x + rightFlank.size.width
        val slack = viewportRight - contentRight
        assert(slack <= GridEdgeSlack) {
            "at the end of the scroll the rightmost box stops ${"%.1f".format(slack)}px short " +
                "of the window — that gap is padding the user can scroll into"
        }
    }

    /**
     * **The right stick moves the view's own scroller** — Mappo's one universal control (Dylan,
     * 2026-09-25: "right stick = scrolls any container with a scrollbar"). This is the WIRING:
     * the stage's body joins the arbitration and the winner is actually driven.
     *
     * Only the cursor-is-in-the-view case is reachable here — Compose seats the cursor on the
     * first focusable when the window takes focus and it cannot be persuaded out of the view
     * from a test. Which scroller wins in the other arrangements, the no-cursor one included,
     * is pinned on the rule itself in `StickScrollArbiterTest`.
     */
    @Test
    fun rightStick_movesTheViewsScroller() {
        composeRule.mainClock.autoAdvance = false
        val stick = androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
        composeRule.setContent {
            MaterialTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.mappo.ui.component.LocalRightStick provides stick,
                ) {
                    Surface(modifier = androidx.compose.ui.Modifier.size(300.dp, 400.dp)) {
                        RemapControlsScreen(
                            config = seedShapedConfig()
                                .withPressStack(InputSource.DPAD, 7000L)
                                .withPressStack(InputSource.LEFT_TRIGGER, 8000L)
                                .withPressStack(InputSource.LEFT_JOYSTICK, 9000L),
                            onOpenInputEditor = { _, _, _ -> },
                            onBack = {},
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        repeat(8) { composeRule.mainClock.advanceTimeByFrame() }

        fun scroll(): Float = composeRule
            .onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
            .value()

        val before = scroll()
        assert(before > 0f) { "fixture is already at the near end — nothing to scroll back to" }
        // Pushed left; the resting view sits at the far end, so this has somewhere to go.
        stick.value = androidx.compose.ui.geometry.Offset(-1f, 0f)
        repeat(30) { composeRule.mainClock.advanceTimeByFrame() }

        assert(scroll() < before) {
            "the right stick did not move the resting view (still at $before)"
        }
    }

    /**
     * **Where the cursor goes after making a command.** On the command, wherever the row's sort
     * order puts it — not back on the first tile of the group edit mode was entered from.
     *
     * Seating by GROUP was all the stage could do, so every add and every move returned the
     * cursor to the entry group: open edit mode on the right trigger, add a command on the
     * button pad, and you were looking at the right trigger again (Dylan, 2026-09-24). Nothing
     * then scrolled to the new tile either, because the cursor — which is what the body's
     * bring-into-view follows — had never gone near it.
     *
     * Creating a command leaves for the full-screen picker and comes back, which is why the
     * claim is made by binding id and held saveably rather than as a cell position.
     */
    @Test
    fun addingACommand_seatsTheCursorOnIt_notBackWhereYouCameIn() {
        val live = androidx.compose.runtime.mutableStateOf(seedShapedConfig())
        composeRule.setContent {
            MaterialTheme {
                // EVERY group tiled, which is what makes acting on a row in a group other than
                // the one edit mode was entered from reachable at all (see [TileReveal]).
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalTileReveal provides TileReveal.ALL_GROUPS,
                ) {
                    Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                        RemapControlsScreen(
                            config = live.value,
                            onOpenInputEditor = { _, _, _ -> },
                            // Stand in for the repository: make the command, hand back its id.
                            // The picker it would open next is not part of what this asserts.
                            onAddRowCommand = { _, inputKey, _, onReady ->
                                live.value = live.value.withTwoCommands(
                                    InputSource.BUTTON_DIAMOND, inputKey, 900L,
                                )
                                onReady(950L)
                            },
                            onBack = {},
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        // Enter edit mode on the SHOULDER, so "the group you came in on" is somewhere else
        // entirely from the row being added to.
        composeRule.onNodeWithTag("simple-group:LEFT_SHOULDER").performClick()
        composeRule.waitForIdle()

        // The face row's "+" — slot 1, after the seeded command — and the verb that makes one.
        composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_a:1").performClick()
        composeRule.waitForIdle()
        clickMenuItem("New")
        composeRule.waitForIdle()

        // Slot 0: where the new command's press type sorts it — NOT the "+" that was clicked,
        // and not the shoulder. Only following the command's own id lands here.
        composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_a:0", useUnmergedTree = true)
            .assertIsFocused()
    }


    /**
     * Click a verb in a tile's action menu.
     *
     * [MinputMenuRow] puts the click on its Row and does not merge its label into it, so the
     * node carrying the text and the node carrying the action are two different nodes — which
     * is why the obvious `onNodeWithText(verb).performClick()` finds a node and does nothing.
     */

    /**
     * **A tile arriving re-frames the view the way opening a group does** (Dylan, 2026-09-24).
     *
     * A group's box is as wide as its tiles and a column as wide as its widest box, so one new
     * tile can land past the window's edge — and nothing went to it, because only entering edit
     * mode ever framed a group. Clearing one was worse than useless: the grid lost a tile's
     * width, the scroller clamped, and the whole view snapped sideways in a single frame.
     *
     * The travel is driven by an animation, so this drives the clock by hand and lets it land.
     */
    @Test
    fun aTileArriving_bringsItsWholeGroupIntoView() {
        composeRule.mainClock.autoAdvance = false
        val live = androidx.compose.runtime.mutableStateOf(
            seedShapedConfig().withTwoCommands(InputSource.BUTTON_DIAMOND, "button_a", 900L),
        )
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(420.dp, 500.dp)) {
                    RemapControlsScreen(
                        config = live.value,
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }
        fun settle() {
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(1_200L)
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        // Edit from the far LEFT, then look at the left edge, so the face group is off to the
        // right with nothing drawing the view toward it.
        composeRule.onNodeWithTag("simple-group:LEFT_SHOULDER").performClick()
        settle()
        val scrollBy = composeRule.onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.ScrollBy].action
        composeRule.runOnUiThread { scrollBy?.invoke(-4000f, 0f) }
        settle()

        fun faceVisible(): Pair<Float, Int> {
            val node = composeRule.onNodeWithTag("simple-group:FACE", useUnmergedTree = true)
                .fetchSemanticsNode()
            return node.boundsInRoot.width to node.size.width
        }
        val (before, _) = faceVisible()

        // A third command on the face row — one more tile, and the "+" pushed out behind it.
        live.value = seedShapedConfig()
            .withThreeCommands(InputSource.BUTTON_DIAMOND, "button_a", 900L)
        settle()

        val (after, own) = faceVisible()
        assert(after >= own - 1f) {
            "The face group grew a tile and the view never went to it: " +
                "%.1f of %d visible (was %.1f)".format(after, own, before)
        }
    }


    /**
     * **A lift SPENDS the press that caused it** (Dylan, 2026-09-26).
     *
     * Under "release to place", holding the activate button until a tile lifts and then letting
     * go over its own slot puts it back and ends the move. It did — and then the next direction
     * pressed lifted the tile all over again, because the tile was still holding the timestamp
     * of the press that had lifted it, and coyote time reads exactly that. Doing the same thing
     * with a detour (steer away, steer back, release) never showed it: stepping the drop target
     * moves FOCUS, and losing focus disarms the tile.
     *
     * Asserted through the button rather than through the state: once the move is over, an
     * activate press must open the tile's menu. While a move is live it commits instead, so the
     * menu appearing is the whole claim.
     */
    @Test
    fun releasingALiftedTileOnItsOwnSlot_endsTheGesture_soSteeringAfterwardDoesNotReliftIt() {
        composeRule.mainClock.autoAdvance = false
        setScreenLocal(seedShapedConfig().withTwoCommands(InputSource.BUTTON_DIAMOND, "button_a", 900L))
        fun settle() {
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(1_200L)
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("simple-group:FACE").performClick()
        settle()

        // Key input goes to whatever holds focus, so seat the cursor deliberately: this has to
        // be a tile that HOLDS something, because an empty slot never lifts.
        val tile = "cell:FACE:BUTTON_DIAMOND:button_a:0"
        composeRule.onNodeWithTag(tile).requestFocus()
        settle()
        composeRule.onNodeWithTag(tile).assertIsFocused()

        // Hold until it lifts — the lift is a delayed effect, so the clock has to run for it.
        composeRule.onNodeWithTag(tile).performKeyInput { keyDown(Key.ButtonA) }
        settle()
        // Let go without having gone anywhere: placed back where it started, move over.
        composeRule.onNodeWithTag(tile).performKeyInput { keyUp(Key.ButtonA) }
        settle()

        // Now steer. Nothing here may pick the tile up again.
        composeRule.onNodeWithTag(tile).performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        settle()

        // An activate press with no move in flight opens the focused tile's menu. While a move
        // IS in flight it commits instead and no menu appears, which is what the stale lifting
        // press used to cause.
        composeRule.onNodeWithTag(tile).performKeyInput {
            keyDown(Key.ButtonA)
            keyUp(Key.ButtonA)
        }
        settle()

        composeRule.onAllNodesWithText("Clear", useUnmergedTree = true).assertCountEquals(1)
    }

    /**
     * **Clearing a tile must not snap the view across.**
     *
     * Losing a tile narrows its column, which drags everything the column carries — and when the
     * content ends up narrower than the scroll position, the scroller clamps and the whole view
     * jumps by a tile's width in one frame. Dylan saw exactly that on 2026-09-24: "an abrupt
     * shift to the right, to the tune of what appears to be the full distance of the removed
     * tile".
     *
     * A frame-by-frame measure, because the fault is a single frame. The travel is ~260ms, so
     * even its fastest frame moves a group a few pixels; a snap moves it by a whole tile at once.
     */
    @Test
    fun clearingATile_doesNotSnapTheView() {
        composeRule.mainClock.autoAdvance = false
        val live = androidx.compose.runtime.mutableStateOf(
            seedShapedConfig().withTwoCommands(InputSource.LEFT_TRIGGER, "full_pull", 900L),
        )
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(420.dp, 500.dp)) {
                    RemapControlsScreen(
                        config = live.value,
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithTag("simple-group:LEFT_SHOULDER").performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_200L)
        composeRule.waitForIdle()

        // PART of the way right — the case Dylan hit ("a left column tile scrolled mostly
        // offscreen"). Pinned to the far end it cannot show: the content narrows by a tile, the
        // scroller clamps by exactly that, and the two cancel. It is the positions in between
        // where nothing absorbs the change and the grid slides.
        val scrollBy = composeRule.onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.ScrollBy].action
        composeRule.runOnUiThread { scrollBy?.invoke(200f, 0f) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()

        // Everything EXCEPT the group that lost the tile. That box genuinely gets one tile
        // narrower, shedding it from its outer end — that is the change, not a jump. What must
        // not move is the rest of the grid, which had nothing to do with it.
        val tracked = listOf("DPAD", "FACE", "RIGHT_SHOULDER", "RIGHT_STICK")
        // positionInRoot, not boundsInRoot: the latter is clipped, so a group off the window's
        // edge reads as a flat 0 and hides the very motion being measured.
        fun sample() = tracked.associateWith {
            composeRule.onNodeWithTag("simple-group:$it", useUnmergedTree = true)
                .fetchSemanticsNode().positionInRoot.x
        }

        val frames = mutableListOf(sample())
        // The trigger row loses a command: the left column narrows by a whole tile.
        live.value = seedShapedConfig()
        composeRule.waitForIdle()
        repeat(45) {
            composeRule.mainClock.advanceTimeBy(8)
            composeRule.waitForIdle()
            frames += sample()
        }

        tracked.forEach { group ->
            val path = frames.map { it.getValue(group) }
            val printable = path.map { "%.0f".format(it) }
            // THE SNAP: the frame the tile goes away on. Nothing may move on it — the travel
            // that follows is allowed to take the view anywhere, but it has to start from where
            // the eye last saw things. Without the hold this frame alone was a whole tile wide.
            val onTheFrame = kotlin.math.abs(path[1] - path[0])
            assert(onTheFrame < 4f) {
                "$group jumped ${"%.1f".format(onTheFrame)}px on the frame the tile was cleared " +
                    "(path: $printable)"
            }
            // And the travel out of it goes one way: a hold that is undone before the animation
            // picks it up reads as a lurch and a crawl back, which scores here as backtracking.
            val net = path.last() - path.first()
            val forward = if (net >= 0f) 1f else -1f
            val backtrack = path.zipWithNext { a, b -> (b - a) * forward }
                .filter { it < 0f }
                .sumOf { -it.toDouble() }
            assert(backtrack <= 1.0) {
                "$group backtracked ${"%.1f".format(backtrack)}px re-framing after a clear " +
                    "(path: $printable)"
            }
        }
        composeRule.mainClock.autoAdvance = true
    }


    /**
     * **Adding a command frames its WHOLE group — the "+" tile at the end included.**
     *
     * Focus, left to itself, scrolls the minimum that reveals the tile it just landed on, which
     * stops dead at the new command and leaves the "+" behind it off screen. That minimal scroll
     * was ALL that happened on an add, because the travel is started by comparing a layout
     * against the one before it and an add has no "before": creating a command leaves for the
     * full-screen output picker, so the screen is torn down and rebuilt around the answer. The
     * group is now named explicitly when a command lands in it (Dylan, 2026-09-25: "the camera
     * and scroll migrate to the very edge of that group, including the empty tiles").
     */
    @Test
    fun addingACommand_framesTheWholeGroup_notJustTheNewTile() {
        composeRule.mainClock.autoAdvance = false
        // The left shoulder starts WIDE, and the change below narrows it as the face row grows —
        // two boxes changing at once, which is the shape of every move: one row loses a command
        // and another gains it. Only naming the group the command LANDED IN can tell those apart;
        // taking the first box that changed takes the shoulder, which the view has no business
        // travelling to.
        val live = androidx.compose.runtime.mutableStateOf(
            seedShapedConfig().withTwoCommands(InputSource.LEFT_TRIGGER, "full_pull", 800L),
        )
        composeRule.setContent {
            MaterialTheme {
                // EVERY group tiled: the command lands in a group other than the one edit mode
                // was entered from, which only that reveal makes reachable (see [TileReveal]).
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalTileReveal provides TileReveal.ALL_GROUPS,
                ) {
                    Surface(modifier = androidx.compose.ui.Modifier.size(420.dp, 500.dp)) {
                        RemapControlsScreen(
                            config = live.value,
                            onOpenInputEditor = { _, _, _ -> },
                            onAddRowCommand = { _, inputKey, _, onReady ->
                                live.value = seedShapedConfig()
                                    .withTwoCommands(InputSource.BUTTON_DIAMOND, inputKey, 900L)
                                onReady(950L)
                            },
                            onBack = {},
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        fun settle() {
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(1_200L)
            composeRule.waitForIdle()
        }
        settle()
        // Edit from the far left, and look there, so the face group is off to the right.
        composeRule.onNodeWithTag("simple-group:LEFT_SHOULDER").performClick()
        settle()
        val scrollBy = composeRule.onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.ScrollBy].action
        composeRule.runOnUiThread { scrollBy?.invoke(-4000f, 0f) }
        settle()

        // The face row's "+", and the verb that makes a command on it. Through its semantics
        // action, not a tap: the tile is scrolled off the window, which is the whole point, and a
        // tap needs coordinates inside it.
        activateTile("cell:FACE:BUTTON_DIAMOND:button_a:1")
        settle()
        clickMenuItem("New")
        settle()

        val node = composeRule.onNodeWithTag("simple-group:FACE", useUnmergedTree = true)
            .fetchSemanticsNode()
        // boundsInRoot is CLIPPED, so this is how much of the box the window actually shows.
        assert(node.boundsInRoot.width >= node.size.width - 1f) {
            "Added a command and the face group is only %.1f of %d px on screen".format(
                node.boundsInRoot.width, node.size.width,
            )
        }
        // And the "+" pushed out behind it is reachable, which is the edge that was being missed.
        composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_a:2", useUnmergedTree = true)
            .assertExists()
        // Framing the OUTERMOST group goes the whole way to the end of the scroll. A group's box
        // stops short of the grid's own margin, so landing on the box's edge left ~6dp of blank
        // margin unscrolled — close enough to look landed, but the scroller still reported more
        // to come and the edge fade and chevron stayed lit over it (Dylan, 2026-09-25).
        val range = composeRule.onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
        assert(range.value() >= range.maxValue() - 0.5f) {
            "Panned to the outermost group and stopped %.1f short of the end (%.1f of %.1f)"
                .format(range.maxValue() - range.value(), range.value(), range.maxValue())
        }
        composeRule.mainClock.autoAdvance = true
    }


    /**
     * Activate a tile through its semantics action rather than a tap — a tap needs coordinates
     * inside the window, and some of these tests deliberately act on a tile scrolled out of it.
     * The click sits on a node INSIDE the tagged cell, so the tag alone doesn't carry it.
     */
    private fun activateTile(tag: String) {
        composeRule.onNode(
            androidx.compose.ui.test.hasClickAction() and hasAnyAncestor(hasTestTag(tag)),
            useUnmergedTree = true,
        ).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun clickMenuItem(label: String) {
        composeRule.onNode(
            androidx.compose.ui.test.hasClickAction() and
                androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasText(label)),
            useUnmergedTree = true,
        ).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun setScreenLocal(config: ControllerConfig) {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = config,
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    // ── Scope fly-out: action sets ──────────────

    @Test
    fun tabs_listEverySet_andSelectingInvokesCallback() {
        var selectedSetId: Long? = null
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = twoSetConfig(
                            setAButtonA = BindingOutput.KeyPress("ENTER"),
                            setBButtonA = BindingOutput.KeyPress("SPACE"),
                        ),
                        viewingActionSetId = 1L,
                        onSelectActionSet = { selectedSetId = it },
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Every set renders as a top-bar tab; tapping one selects it.
        composeRule.onNodeWithText("Menu", ignoreCase = true, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Menu", ignoreCase = true, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assert(selectedSetId == 2L) {
            "Expected onSelectActionSet(2L), got $selectedSetId"
        }
    }

    @Test
    fun bindingRowReflectsViewingSet_notDefaultSet() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = twoSetConfig(
                            setAButtonA = BindingOutput.KeyPress("ENTER"),
                            setBButtonA = BindingOutput.KeyPress("SPACE"),
                        ),
                        // Default per twoSetConfig is set 1 (ENTER); viewing 2 (SPACE).
                        viewingActionSetId = 2L,
                        onSelectActionSet = {},
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeRule.onNodeWithText("SPACE", useUnmergedTree = true).assertExists()
        // The default set's binding is NOT shown — the editor follows the viewing pointer.
        composeRule.onAllNodesWithText("ENTER", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun nullViewingActionSetId_fallsBackToDefaultSet() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = twoSetConfig(
                            setAButtonA = BindingOutput.KeyPress("ENTER"),
                            setBButtonA = BindingOutput.KeyPress("SPACE"),
                        ),
                        viewingActionSetId = null,
                        onSelectActionSet = {},
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Default set (set 1) wins → ENTER, not SPACE.
        composeRule.onNodeWithText("ENTER", useUnmergedTree = true).assertExists()
    }

    // ── Action-set row (rehomed from the top-bar tabs, 2026-08-13) ───────

    /**
     * **The bar's three clusters never overlap** (2026-09-26). The centre cluster is centred in
     * the BAR, which a Box of three alignments also does — and which lets a long layout name run
     * straight under it, because nothing measures the two against each other. The bar's own
     * layout gives each flank only the room beside the centre (see `BarSlots`), so a name too
     * long for its side ellipsizes instead.
     */
    @Test
    fun topBar_clustersNeverOverlap_evenWithALongLayoutName() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(420.dp, 500.dp)) {
                    RemapControlsScreen(
                        config = twoSetConfig(
                            setAButtonA = BindingOutput.Unbound,
                            setBButtonA = BindingOutput.Unbound,
                        ),
                        viewingActionSetId = 1L,
                        layoutName = "A deliberately overlong layout name for a narrow bar",
                        onSelectActionSet = {},
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        fun bounds(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val identity = bounds("bar:identity")
        val editors = bounds("bar:editors")
        // The cluster's end is its kebab now — the "+" action segment retired 2026-09-27.
        val sets = bounds("bar:sets-menu")

        assert(identity.right <= sets.left) {
            "the identity widget ran under the action sets: $identity vs $sets"
        }
        assert(editors.left >= sets.right) {
            "the editor switch ran under the action sets: $editors vs $sets"
        }
    }

    /**
     * **The editor switch** (Dylan, 2026-09-26) — this screen IS the physical-buttons editor, so
     * its half is the live one, and picking the other half opens the virtual-buttons (overlay)
     * editor. It replaced the "Edit overlay" button.
     */
    @Test
    fun topBar_editorSwitch_offersBothEditors_andOpensTheVirtualOne() {
        var opened = 0
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = seedShapedConfig(),
                        onOpenInputEditor = { _, _, _ -> },
                        onEditOverlay = { opened++ },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Glyphs, no words — the semantics are the descriptions.
        composeRule.onNodeWithContentDescription("Physical buttons editor", useUnmergedTree = true)
            .assertExists()
        composeRule.onAllNodesWithText("Edit overlay").assertCountEquals(0)
        // The click lives on the SEGMENT; the glyph inside it carries the description (the same
        // split MinputMenuRow has — see clickMenuItem).
        composeRule.onNode(
            androidx.compose.ui.test.hasClickAction() and
                androidx.compose.ui.test.hasAnyDescendant(
                    hasContentDescription("Virtual buttons editor"),
                ),
            useUnmergedTree = true,
        ).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assert(opened == 1) { "the virtual editor should have been opened once, got $opened" }
    }

    @Test
    fun topBar_viewingLayout_showsPreviewOverline_noBack() {
        // No Back arrow in any state (2026-08-27) — the identity widget (the layouts drawer's
        // summon) leads. The Layout settings and Activate pills are retired (options = Start
        // key; activation = drawer cards); a non-active layout is marked by its OVERLINE, which
        // reads "PREVIEWING LAYOUT" over the layout's name (2026-09-26 — it was a "(Preview)"
        // suffix on the name itself while the identity was one pill). The add-set affordance is
        // the set row's "+" segment.
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = twoSetConfig(
                            setAButtonA = BindingOutput.Unbound,
                            setBButtonA = BindingOutput.Unbound,
                        ),
                        viewingActionSetId = 1L,
                        onSelectActionSet = {},
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        isActiveLayout = false,
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeRule.onAllNodesWithContentDescription("Back").assertCountEquals(0)
        // The identity widget (the drawer summon since 2026-08-27, ArrowLeftRight pill
        // deleted; the two-line app-icon + overline + name stack is back as of 2026-09-26).
        composeRule.onNodeWithTag("bar:identity").assertExists()
        composeRule.onNodeWithText("PREVIEWING LAYOUT", useUnmergedTree = true).assertExists()
        // No layout name in this setup — the name line falls back to "Layout".
        composeRule.onNodeWithText("Layout", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("Activate layout").assertCountEquals(0)
        composeRule.onAllNodesWithText("Layout settings").assertCountEquals(0)
        // Adding a set is the sets KEBAB's menu now, not a "+" segment closing the group
        // (2026-09-27): a group button is single-choice, and a verb in it reads as a peer.
        composeRule.onAllNodesWithContentDescription("Add action set").assertCountEquals(0)
        composeRule.onNodeWithTag("bar:sets-menu").assertExists()
        composeRule.onNodeWithTag("bar:sets-menu").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("New layout set", useUnmergedTree = true).assertExists()
    }

    @Test
    fun topBar_activeLayout_noAutoDetect_noActivate() {
        // The home state: the viewed layout IS active, so the Activate pill is absent.
        // (Auto-detect used to hold the bar's trailing corner; it moved to the Mappo drawer
        // 2026-08-30 — the bar must NOT carry it any more.)
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = twoSetConfig(
                            setAButtonA = BindingOutput.Unbound,
                            setBButtonA = BindingOutput.Unbound,
                        ),
                        viewingActionSetId = 1L,
                        onSelectActionSet = {},
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        isActiveLayout = true,
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeRule.onAllNodesWithText("AUTO").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Back").assertCountEquals(0)
        composeRule.onAllNodesWithText("Activate layout").assertCountEquals(0)
        composeRule.onNodeWithTag("bar:identity").assertExists()
        composeRule.onAllNodesWithText("Layout settings").assertCountEquals(0)
        // The home state's overline says the layout on screen is the ACTIVE one.
        composeRule.onNodeWithText("ACTIVE LAYOUT", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("PREVIEWING LAYOUT", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onNodeWithText("Layout", useUnmergedTree = true).assertExists()
    }

    @Test
    fun changeButton_opensLayoutsDrawer_withSectionsAndCards() {
        // The change button slides in the layouts drawer: the permanent "New layout"
        // card, then a card per layout of the viewed application. Headerless since
        // 2026-08-30 — the INSTALLED/COMMUNITY overlines retired in favour of a
        // per-card download marker.
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = sampleConfig(),
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        layouts = kotlinx.collections.immutable.persistentListOf(
                            com.mappo.data.model.Layout(id = 1L, name = "Alpha layout"),
                            com.mappo.data.model.Layout(id = 2L, name = "Beta layout"),
                        ),
                        activeLayoutId = 1L,
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("bar:identity").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("INSTALLED LAYOUTS", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("COMMUNITY LAYOUTS", useUnmergedTree = true)
            .assertCountEquals(0)
        // The create-layout affordance leads the list (2026-08-30), on a proper
        // icon + label rather than the old "+ New layout" text glyph.
        composeRule.onNodeWithText("New layout", useUnmergedTree = true).assertExists()
        // Every listed layout is on-device, so each card carries the installed marker.
        composeRule.onAllNodesWithContentDescription("Installed").assertCountEquals(2)
        composeRule.onNodeWithText("Alpha layout", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Beta layout", useUnmergedTree = true).assertExists()
    }

    @Test
    fun applicationsButton_entersApplicationsMode_withAppCards() {
        // The layouts drawer's full-width Applications button (2026-08-27: the retired
        // right-side applications drawer folded into the layouts drawer) radiates the
        // pane into applications mode: a card per detected app under the same category
        // headers. Picking an app transitions back to layouts mode scoped to it.
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = sampleConfig(),
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        layouts = kotlinx.collections.immutable.persistentListOf(
                            com.mappo.data.model.Layout(
                                id = 1L,
                                name = "Alpha layout",
                                packageName = "com.example.alpha",
                            ),
                        ),
                        activeLayoutId = 1L,
                        installedApps = listOf(
                            com.mappo.data.repository.InstalledAppsRepository.InstalledApp(
                                packageName = "com.example.alpha",
                                label = "Alpha Game",
                            ),
                            com.mappo.data.repository.InstalledAppsRepository.InstalledApp(
                                packageName = "com.example.beta",
                                label = "Beta Game",
                            ),
                        ),
                        appBindings = mapOf("com.example.alpha" to 1L),
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // The layouts drawer first — the Applications button lives inside it, wearing
        // the viewed application's identity ("Alpha Game": the active layout's app).
        composeRule.onNodeWithTag("bar:identity").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Alpha Game", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        // Applications mode: cards are name-only (2026-08-26: the active-layout
        // subtitle retired). "Alpha Game" appears twice — its app card AND the
        // Applications button itself, which still shows the viewed application.
        composeRule.onAllNodesWithText("Alpha Game", useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithText("Beta Game", useUnmergedTree = true).assertExists()

        // Picking the unbound app repoints the VIEWING context (no activation) and
        // transitions back to layouts mode: the content plane swaps to the no-layout
        // state with the two route tiles.
        composeRule.onNodeWithText("Beta Game", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No layout assigned for Beta Game", useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithText("Create a layout", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Browse layouts", useUnmergedTree = true).assertExists()
    }

    @Test
    fun bindingRow_showsResolvedSetTitle_forChangePresetBinding() {
        // Brick 4.5: a button bound to CHANGE_PRESET should display "Switch to: <set title>"
        // — the row-preview side of the context-aware displayLabel(config).
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = twoSetConfig(
                            setAButtonA = BindingOutput.ControllerAction("CHANGE_PRESET", listOf("2")),
                            setBButtonA = BindingOutput.Unbound,
                        ),
                        viewingActionSetId = 1L,
                        onSelectActionSet = {},
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Switch to: Menu").assertIsDisplayed()
    }

    @Test
    fun setRow_listsAllSets_notJustViewing() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = twoSetConfig(
                            setAButtonA = BindingOutput.Unbound,
                            setBButtonA = BindingOutput.Unbound,
                        ),
                        viewingActionSetId = 2L,  // viewing set B
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Gameplay", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Menu", useUnmergedTree = true).assertExists()
    }

    @Test
    fun setRow_selectingSet_selectsSetAndDropsLayer() {
        var selectedSetId: Long? = null
        var selectedLayerId: Long? = -1L  // sentinel; null is a meaningful value
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = twoSetConfigWithLayers(
                            setALayers = listOf(10L to "ScopeA"),
                            setBLayers = emptyList(),
                        ),
                        viewingActionSetId = 1L,
                        viewingLayerId = 10L,
                        onSelectActionSet = { selectedSetId = it },
                        onSelectLayer = { selectedLayerId = it },
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Menu", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assert(selectedSetId == 2L) { "Expected onSelectActionSet(2L), got $selectedSetId" }
        assert(selectedLayerId == null) {
            "Expected the set pick to drop to base with onSelectLayer(null), got $selectedLayerId"
        }
    }

    @Test
    fun setRow_doesNotListLayers() {
        // Layers deliberately lost their tab surface in the 2026-08-13 top-bar rework; they
        // return with the set-management cog. The set row lists sets only.
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = singleSetConfigWithLayers(
                            layers = listOf(10L to "Scope", 11L to "Vehicle"),
                        ),
                        viewingActionSetId = 1L,
                        viewingLayerId = null,
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeRule.onAllNodesWithText("Scope", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Vehicle", useUnmergedTree = true).assertCountEquals(0)
    }

    // ── Overlay editing mode (Brick 5.5.c) ────────────────────────────────────

    @Test
    fun overlayMode_ghostRow_showsBaseBindingWhenLayerHasNoOverride() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = configWithLayerOverride(
                            baseButtonA = BindingOutput.KeyPress("ENTER"),
                            layerId = 10L,
                            layerTitle = "Scope",
                            layerOverrideButtonA = null,  // ghost row
                        ),
                        viewingActionSetId = 1L,
                        viewingLayerId = 10L,
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // The base set's binding text shows through as ghost (Robolectric can't probe
        // alpha cleanly; we verify the text is *present* and there's no override icon).
        composeRule.onNodeWithText("ENTER", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("Override actions").assertCountEquals(0)
    }

    /**
     * The zoomed scene's framing (2026-09-17): opening a group puts ITS table on its own side of
     * the screen with the centre column — the controller — beside it, and leaves the groups
     * around it a pan away rather than gone. Bounds, not existence: the first cut of the camera
     * placed the scene correctly and then drew it half a viewport off, which every
     * existence-based assertion in this file happily passed.
     */
    @Test
    fun zoomScene_framesTheOpenedGroup_withTheControllerBeside() {
        setScreenLocal(seedShapedConfig())
        val viewport = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

        openAdvanced("FACE")
        composeRule.waitForIdle()
        val face = composeRule.onNodeWithTag("zoom-card:FACE", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        // A right-hand group clamps flush to the right edge, and leaves the left third of the
        // screen to the centre column.
        assert(face.right >= viewport.right - 20f) { "face card should reach the right edge: $face" }
        assert(face.left > viewport.width * 0.2f) { "face card should leave room for the controller: $face" }

        inOpenCard(hasContentDescription("Close")).onFirst().performClick()
        composeRule.waitForIdle()
        openAdvanced("DPAD")
        composeRule.waitForIdle()
        val dpad = composeRule.onNodeWithTag("zoom-card:DPAD", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        // And a left-hand group frames the other way about.
        assert(dpad.left <= viewport.left + 20f) { "dpad card should reach the left edge: $dpad" }
        assert(dpad.right < viewport.width * 0.8f) { "dpad card should leave room for the controller: $dpad" }
    }

    /**
     * The left flank's tables are MIRRORED (Dylan, 2026-09-17): glyph column at the card's right
     * edge, the row's commands running outward to the left, so a card and the basic-view box it
     * grew out of have the same shape. Asserted on the CELLS rather than the glyphs — the slot
     * order is the thing that flips, and it's what the move-preview arithmetic keys off.
     */
    @Test
    fun zoomScene_mirrorsTheLeftFlanksSlots() {
        setScreenLocal(
            seedShapedConfig()
                .withTwoCommands(InputSource.DPAD, "dpad_up", idBase = 500L)
                .withTwoCommands(InputSource.BUTTON_DIAMOND, "button_y", idBase = 600L),
        )

        openAdvanced("DPAD")
        composeRule.waitForIdle()
        val first = composeRule.onNodeWithTag("cell:DPAD:DPAD:dpad_up:0", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithTag("cell:DPAD:DPAD:dpad_up:1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assert(second.left < first.left) {
            "slot 1 should sit LEFT of slot 0 on a mirrored table: $first / $second"
        }

        inOpenCard(hasContentDescription("Close")).onFirst().performClick()
        composeRule.waitForIdle()
        openAdvanced("FACE")
        composeRule.waitForIdle()
        val faceFirst = composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_y:0", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val faceSecond = composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_y:1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assert(faceSecond.left > faceFirst.left) {
            "the right flank keeps its normal order: $faceFirst / $faceSecond"
        }
    }

    /**
     * A LEFT-flank card's body sits at its RIGHT edge (Dylan, 2026-09-20).
     *
     * The card reads toward its glyph column, which mirroring pins to the right; left-aligning
     * the body left the gap on the side the eye starts from and the content floating away from
     * the controller it belongs to.
     */
    @Test
    fun zoomScene_rightAlignsTheLeftFlanksBody() {
        setScreenLocal(seedShapedConfig())

        openAdvanced("DPAD")
        composeRule.waitForIdle()
        val card = composeRule.onNodeWithTag("zoom-card:DPAD", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val tile = composeRule.onNodeWithTag("cell:DPAD:DPAD:dpad_up:0", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assert(tile.left > card.center.x) {
            "a mirrored card's body should hug its right edge: card $card, tile $tile"
        }
    }

    /**
     * A tile's press-type glyph is POSITIONED, not packed (Dylan, 2026-09-21).
     *
     * It used to lead a Row, which made it part of the tile's flex: the command name centred in
     * whatever the glyph left over, so the same command sat at a different x depending on which
     * press type it fired on. Asserted as "the name is centred in its own tile" on both a
     * glyphless Regular Press tile and the Long Press tile beside it.
     */
    @Test
    fun groupEditor_pressGlyph_doesNotShiftTheCommandName() {
        setScreenLocal(
            seedShapedConfig().withTwoCommands(InputSource.BUTTON_DIAMOND, "button_y", idBase = 600L),
        )

        openAdvanced("FACE")
        composeRule.waitForIdle()
        // Slot 0 is the Regular Press (no glyph), slot 1 the Long Press — the auto-sort's order.
        val plain = composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_y:0", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val glyphed = composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_y:1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        // By bounds, not by uniqueness: the basic-view box under the card still holds a node
        // saying the same thing, faded out behind its own table.
        fun textIn(name: String, tile: androidx.compose.ui.geometry.Rect) =
            composeRule.onAllNodesWithText(name, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .map { it.boundsInRoot }
                .first { tile.contains(it.center) }
        val plainText = textIn("ENTER", plain)
        val glyphedText = textIn("SPACE", glyphed)

        // Each name sits at the SAME offset from its own tile's centre, press glyph or not. (The
        // offset itself isn't zero: the name is the second half of a centred glyph + name pair.)
        val plainOffset = plainText.center.x - plain.center.x
        val glyphedOffset = glyphedText.center.x - glyphed.center.x
        assert(kotlin.math.abs(plainOffset - glyphedOffset) < 2f) {
            "the press glyph shifted the name: plain $plainOffset vs glyphed $glyphedOffset " +
                "(tiles $plain / $glyphed, text $plainText / $glyphedText)"
        }
        // And the glyph itself is pinned to that tile's start edge rather than riding the text.
        val pressGlyph = composeRule.onNodeWithContentDescription("Long Press", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assert(pressGlyph.center.x < glyphed.center.x) {
            "the press glyph leads the tile: tile $glyphed, glyph $pressGlyph"
        }
    }

    /**
     * **A utility card mirrors the flank it joined** (Dylan, 2026-09-26).
     *
     * This replaced a centre-card test: the utility group used to straddle the scene's centre
     * line, Select's commands running left out of it and Start's right. Now Select is a LEFT
     * column group, so its card reads like every other one on that side — glyph at the card's
     * right edge, commands running outward to the left.
     */
    @Test
    fun zoomScene_utilityCard_mirrorsItsFlank() {
        setScreenLocal(seedShapedConfig())

        openAdvanced("LEFT_UTILITY")
        composeRule.waitForIdle()
        val card = composeRule.onNodeWithTag("zoom-card:LEFT_UTILITY", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        // Slot 0 is the command nearest the glyph, slot 1 the "+" beyond it — and on a mirrored
        // card the indices climb LEFTWARD.
        val first = composeRule.onNodeWithTag("cell:LEFT_UTILITY:SWITCH_SELECT:click:0", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithTag("cell:LEFT_UTILITY:SWITCH_SELECT:click:1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assert(second.right <= first.left + 1f) {
            "the row should run outward to the LEFT: first $first, second $second (card $card)"
        }
    }

    /**
     * **Every group box sits on a rectangle that runs off its own side of the screen** (Dylan,
     * 2026-09-26) — which replaced the single plate under the whole grid.
     *
     * Measured both ways round: the rectangle's outer edge is past the window (its UNCLIPPED
     * position, since `boundsInRoot` clips to what is visible), and its inner edge is the box's
     * own, so the box sits at the end of its rectangle rather than somewhere along it.
     */
    @Test
    fun simpleView_eachGroupSitsOnARectangleRunningOffItsOwnSide() {
        setScreenLocal(seedShapedConfig())
        val viewport = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

        // LEFT column: the rectangle starts off the left edge and ends with the box.
        val leftBox = node("simple-group:DPAD").boundsInRoot
        val leftBacking = node(groupBackingTestTag(RemapSimpleGroup.DPAD))
        val leftStart = leftBacking.positionInRoot.x
        assert(leftStart < viewport.left) {
            "the left column's backing should start off screen: $leftStart vs ${viewport.left}"
        }
        val leftEnd = leftStart + leftBacking.size.width
        val innerLine = with(composeRule.density) { GroupOutlineInset.toPx() }
        assert(kotlin.math.abs((leftBox.right - leftEnd) - innerLine) < 1.5f) {
            "it should end at its box's edge LINE: ${leftBox.right - leftEnd} vs $innerLine"
        }

        // RIGHT column: the mirror image.
        val rightBox = node("simple-group:FACE").boundsInRoot
        val rightBacking = node(groupBackingTestTag(RemapSimpleGroup.FACE))
        val rightStart = rightBacking.positionInRoot.x
        assert(rightStart + rightBacking.size.width > viewport.right) {
            "the right column's backing should run off the right edge: " +
                "${rightStart + rightBacking.size.width} vs ${viewport.right}"
        }
        assert(rightStart > rightBox.left) {
            "it should start inboard of its box's edge, at the line: $rightStart vs ${rightBox.left}"
        }

        // **The rectangle IS the line's panel** (Dylan, 2026-09-26): it ends where the line ends
        // in both directions — inset by the line's visible extent along the edge, and stopping at
        // the line's inner face across it, rather than running on to the box's own edge.
        val endInset = with(composeRule.density) { GroupOutlineEndInset.toPx() }
        val innerInset = with(composeRule.density) { GroupOutlineInset.toPx() }
        val backingBounds = composeRule.onNodeWithTag(
            groupBackingTestTag(RemapSimpleGroup.FACE),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        assert(kotlin.math.abs((backingBounds.top - rightBox.top) - endInset) < 1.5f) {
            "top inset should match the line: ${backingBounds.top - rightBox.top} vs $endInset"
        }
        assert(kotlin.math.abs((rightBox.bottom - backingBounds.bottom) - endInset) < 1.5f) {
            "bottom inset should match the line: ${rightBox.bottom - backingBounds.bottom} vs $endInset"
        }
        assert(kotlin.math.abs((backingBounds.left - rightBox.left) - innerInset) < 1.5f) {
            "a right-column rectangle stops at its line, not its box edge: " +
                "${backingBounds.left - rightBox.left} vs $innerInset"
        }
    }

    /**
     * **A 4:3 screen opens a group with nothing left to scroll** (Dylan, 2026-09-27).
     *
     * The controller's column is sized from the grid's HEIGHT, which says nothing about how much
     * width the flanks need — so the two together can outgrow the window and light the body's fade
     * and chevron over a view with nothing more to show. Going immersive surfaced it: the system
     * bars' height came back to the grid, 40% of it went into the column, and a 4:3 screen that
     * used to fit started overflowing by a few dp.
     *
     * The column gives that ground back now (`spanOf`'s squeeze). Asserted on the EDIT end, where
     * the numbers are real: a tile's width is a fixed dp, so this measures the actual grid rather
     * than Robolectric's idea of how wide a word is.
     */
    @Test
    fun aFourThreeScreen_opensAGroupWithoutOverflowing() {
        composeRule.setContent {
            MaterialTheme {
                // 4:3, and the size class this regressed on: big enough that the flanks and the
                // picture both fit at the height the bars used to leave, small enough that they
                // do not at the full height.
                Surface(modifier = androidx.compose.ui.Modifier.size(632.dp, 474.dp)) {
                    RemapControlsScreen(
                        config = seedShapedConfig(),
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        fun range() = composeRule.onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]

        assert(range().maxValue() == 0f) { "the resting view already overflows: ${range().maxValue()}" }

        composeRule.onNodeWithTag("simple-group:DPAD").performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_200L)
        composeRule.waitForIdle()

        assert(range().maxValue() == 0f) {
            "opening a group overflowed by ${range().maxValue()}px — the fade and chevron would " +
                "be up over a view with nothing more to show"
        }
    }

    /**
     * **Tapping a group's PANEL opens it** (Dylan, 2026-09-27): "I can't actually tap the
     * background rectangles to open an input group, and I would expect to be able to".
     *
     * A box is only as wide as its own text, so most of the rectangle it sits on — a surface that
     * plainly reads as part of the group — did nothing. The panel carries the box's two gestures
     * now. Tapped OUTSIDE the box's own bounds, so it is the panel's hit area under test and not
     * the box's.
     */
    @Test
    fun simpleView_tappingAGroupsPanel_opensThatGroup() {
        setScreenLocal(seedShapedConfig())
        val box = composeRule.onNodeWithTag("simple-group:DPAD", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        // The left column's panel runs off the LEFT edge, so anywhere left of the box at the
        // box's own height is panel and nothing else.
        composeRule.onNodeWithTag(groupBackingTestTag(RemapSimpleGroup.DPAD), useUnmergedTree = true)
            .performTouchInput {
                val spot = androidx.compose.ui.geometry.Offset(2f, height / 2f)
                down(spot)
                up()
            }
        composeRule.waitForIdle()

        // Edit mode: the d-pad's rows are command TILES now, addressable by cell tag.
        composeRule.onNodeWithTag("cell:DPAD:DPAD:dpad_up:0", useUnmergedTree = true).assertExists()
        // And the tap landed off the box, which is what makes this the panel's doing.
        assert(box.left > 2f) { "the box should not reach the screen edge: $box" }
    }

    /**
     * **Each utility group sits at the bottom of its OWN column** (Dylan, 2026-09-26).
     *
     * Select and Start used to be one card in the centre column, seated between the two stick
     * boxes where the hardware puts them. They are now the utility group of each side, in a
     * fourth band under the sticks, and the centre column carries nothing but the controller —
     * which is what this pins: same side as its own stick, below it, and clear of the picture.
     */
    @Test
    fun simpleView_seatsEachUtilityBoxUnderItsOwnColumn() {
        setScreenLocal(seedShapedConfig())
        fun boundsOf(group: String) =
            composeRule.onNodeWithTag("simple-group:$group", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot

        val controller = composeRule.onNodeWithTag(ControllerImageTestTag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val leftUtility = boundsOf("LEFT_UTILITY")
        val rightUtility = boundsOf("RIGHT_UTILITY")
        val leftStick = boundsOf("LEFT_STICK")
        val rightStick = boundsOf("RIGHT_STICK")

        // Below the sticks — the fourth band — rather than level with them.
        assert(leftUtility.top >= leftStick.bottom) {
            "the left utility box belongs under the left stick: $leftUtility vs $leftStick"
        }
        assert(rightUtility.top >= rightStick.bottom) {
            "the right utility box belongs under the right stick: $rightUtility vs $rightStick"
        }
        // Each on its own flank, with the controller between them.
        assert(leftUtility.right <= controller.center.x) {
            "the left utility box should be left of the controller: $leftUtility vs $controller"
        }
        assert(rightUtility.left >= controller.center.x) {
            "the right utility box should be right of the controller: $rightUtility vs $controller"
        }
        // The left column is right-aligned to itself and the right column left-aligned, so each
        // utility box lines up with the stick above it rather than floating in its column.
        assert(kotlin.math.abs(leftUtility.right - leftStick.right) < 2f) {
            "the left column's inner edges should agree: $leftUtility vs $leftStick"
        }
        assert(kotlin.math.abs(rightUtility.left - rightStick.left) < 2f) {
            "the right column's inner edges should agree: $rightUtility vs $rightStick"
        }
    }

    /**
     * Every box ANCHORS TOWARD THE CONTROLLER (Dylan, 2026-09-17). The first grid handed all its
     * spare height to the middle band, which pinned the shoulder row to the top of the screen
     * and the stick row to the bottom — "flung to the far edges". The bands are packed and the
     * whole matrix is centred now, so the cluster reads as one object around the controller.
     */
    @Test
    fun simpleView_clustersTheBandsAroundTheController() {
        setScreenLocal(seedShapedConfig())
        val viewport = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        fun boundsOf(group: String) =
            composeRule.onNodeWithTag("simple-group:$group", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot

        val shoulder = boundsOf("LEFT_SHOULDER")
        val dpad = boundsOf("DPAD")
        val stick = boundsOf("LEFT_STICK")

        // Stacked in order, and each band's neighbour is a gutter away rather than a screen away.
        assert(shoulder.bottom <= dpad.top) { "bands out of order: $shoulder / $dpad" }
        assert(dpad.bottom <= stick.top) { "bands out of order: $dpad / $stick" }
        assert(dpad.top - shoulder.bottom < viewport.height * 0.2f) {
            "the shoulder band is stranded above the d-pad: $shoulder / $dpad"
        }
        assert(stick.top - dpad.bottom < viewport.height * 0.2f) {
            "the stick band is stranded below the d-pad: $dpad / $stick"
        }
        // And the cluster sits in the middle of the plate: the air above it matches the air below.
        val above = shoulder.top - viewport.top
        val below = viewport.bottom - stick.bottom
        assert(kotlin.math.abs(above - below) < viewport.height * 0.1f) {
            "the matrix should be centred: ${'$'}above above, ${'$'}below below"
        }
    }

    @Test
    fun overlayMode_overriddenRow_showsLayerBindingAndOverflowMenu() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = configWithLayerOverride(
                            baseButtonA = BindingOutput.KeyPress("ENTER"),
                            layerId = 10L,
                            layerTitle = "Scope",
                            layerOverrideButtonA = BindingOutput.MouseButton("MOUSE_LEFT"),
                        ),
                        viewingActionSetId = 1L,
                        viewingLayerId = 10L,
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Override visible in the simple view; base hidden for that row.
        composeRule.onNodeWithText("MOUSE_LEFT", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("ENTER", useUnmergedTree = true).assertCountEquals(0)
        // The override affordance now lives on the cell itself: the advanced view is a table,
        // and button_a's Press cell carries the layer menu.
        openAdvanced("FACE")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("group-editor-table:FACE")
            .performScrollToNode(hasTestTag("cell:FACE:BUTTON_DIAMOND:button_a:0"))
        composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_a:0").assertIsDisplayed()
    }

    @Test
    fun overlayMode_clearOverride_invokesCallback_withLayerSourceAndKey() {
        var args: Triple<Long, com.mappo.data.model.steam.InputSource, String>? = null
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = configWithLayerOverride(
                            baseButtonA = BindingOutput.KeyPress("ENTER"),
                            layerId = 42L,
                            layerTitle = "Scope",
                            layerOverrideButtonA = BindingOutput.MouseButton("MOUSE_LEFT"),
                        ),
                        viewingActionSetId = 1L,
                        viewingLayerId = 42L,
                        onClearLayerOverride = { layerId, src, key -> args = Triple(layerId, src, key) },
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        openAdvanced("FACE")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("group-editor-table:FACE")
            .performScrollToNode(hasTestTag("cell:FACE:BUTTON_DIAMOND:button_a:0"))
        composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_a:0").performClick()
        // Driven through semantics rather than performClick: menu rows live in a Popup, and
        // popup bounds come back NEGATED under Robolectric (a menu anchored at x=56 reports
        // x=-56), so a coordinate-based click can miss depending on where the menu sits.
        // The semantics action tests the same handler without the bogus hit-testing.
        composeRule.onNodeWithText("Clear override")
            .performSemanticsAction(SemanticsActions.OnClick)

        assert(args == Triple(42L, com.mappo.data.model.steam.InputSource.BUTTON_DIAMOND, "button_a")) {
            "Expected callback (42, BUTTON_DIAMOND, button_a); got $args"
        }
    }

    @Test
    fun groupEditor_hasNoOverridesFilter() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = sampleConfig(),
                        viewingActionSetId = 1L,
                        viewingLayerId = null,
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }
        openAdvanced("FACE")
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Only overrides").assertCountEquals(0)
        composeRule.onAllNodesWithText("Show all").assertCountEquals(0)
    }


    /**
     * Helper for overlay-mode tests. Builds a single-set config with one layer that
     * optionally carries a `button_a` override (when [layerOverrideButtonA] is non-null).
     * Mirrors `sampleConfig`'s base shape (four face buttons on BUTTON_DIAMOND).
     */
    private fun configWithLayerOverride(
        baseButtonA: BindingOutput,
        layerId: Long,
        layerTitle: String,
        layerOverrideButtonA: BindingOutput?,
    ): ControllerConfig {
        val base = sampleConfig(boundButtonA = baseButtonA)
        val baseSet = base.actionSets.first()

        val layerPresetEntries = if (layerOverrideButtonA != null) {
            val overlayActivator = Activator(
                id = 5000L, groupInputId = 6000L, type = ActivatorType.FULL_PRESS, orderIndex = 0,
            )
            val overlayBinding = layerOverrideButtonA.toEntity().let { (t, args) ->
                Binding(id = 7000L, activatorId = overlayActivator.id, outputType = t, args = args, orderIndex = 0)
            }
            val overlayInput = GroupInputGraph(
                input = GroupInput(id = 6000L, bindingGroupId = 4000L, inputKey = "button_a", orderIndex = 0),
                activators = listOf(ActivatorGraph(overlayActivator, listOf(overlayBinding))),
            )
            val overlayGroup = BindingGroupGraph(
                group = BindingGroup(
                    id = 4000L, actionSetId = null, actionLayerId = layerId,
                    name = "face_overlay", mode = BindingMode.BUTTON_PAD,
                ),
                inputs = listOf(overlayInput),
            )
            listOf(PresetEntry(InputSource.BUTTON_DIAMOND, "active", overlayGroup))
        } else emptyList()

        val layer = ActionLayerGraph(
            layer = ActionLayer(
                id = layerId,
                parentActionSetId = baseSet.actionSet.id,
                name = layerTitle.lowercase(),
                title = layerTitle,
            ),
            bindingGroups = emptyList(),
            preset = layerPresetEntries,
        )

        return base.copy(actionSets = listOf(baseSet.copy(layers = listOf(layer))))
    }

    /** Single-set config with [layers] attached (id+title pairs, in order). */
    private fun singleSetConfigWithLayers(
        layers: List<Pair<Long, String>>,
    ): ControllerConfig {
        val base = sampleConfig()
        val setWithLayers = base.actionSets.first().copy(
            layers = layers.mapIndexed { idx, (id, title) ->
                ActionLayerGraph(
                    layer = ActionLayer(
                        id = id,
                        parentActionSetId = base.actionSets.first().actionSet.id,
                        name = title.lowercase(),
                        title = title,
                        orderIndex = idx,
                    ),
                    bindingGroups = emptyList(),
                )
            },
        )
        return base.copy(actionSets = listOf(setWithLayers))
    }

    /** Two-set config where each set carries its own layer list. */
    private fun twoSetConfigWithLayers(
        setALayers: List<Pair<Long, String>>,
        setBLayers: List<Pair<Long, String>>,
    ): ControllerConfig {
        val base = twoSetConfig(
            setAButtonA = BindingOutput.Unbound,
            setBButtonA = BindingOutput.Unbound,
        )
        fun attach(graph: ActionSetGraph, layers: List<Pair<Long, String>>) = graph.copy(
            layers = layers.mapIndexed { idx, (id, title) ->
                ActionLayerGraph(
                    layer = ActionLayer(
                        id = id,
                        parentActionSetId = graph.actionSet.id,
                        name = title.lowercase(),
                        title = title,
                        orderIndex = idx,
                    ),
                    bindingGroups = emptyList(),
                )
            },
        )
        return base.copy(
            actionSets = listOf(
                attach(base.actionSets[0], setALayers),
                attach(base.actionSets[1], setBLayers),
            ),
        )
    }

    /**
     * Builds a two-action-set config. Set 1 is the starting set (first in order)
     * ("Gameplay", button_a → [setAButtonA]); Set 2 is "Menu" with button_a → [setBButtonA].
     */
    private fun twoSetConfig(
        setAButtonA: BindingOutput,
        setBButtonA: BindingOutput,
    ): ControllerConfig {
        fun buildFaceGroup(actionSetId: Long, bindingGroupId: Long, baseId: Long, output: BindingOutput): BindingGroupGraph {
            val activator = Activator(
                id = baseId, groupInputId = baseId + 1, type = ActivatorType.FULL_PRESS, orderIndex = 0,
            )
            val binding = output.toEntity().let { (t, args) ->
                Binding(id = baseId + 100, activatorId = activator.id, outputType = t, args = args, orderIndex = 0)
            }
            val buttonAInput = GroupInputGraph(
                input = GroupInput(id = baseId + 1, bindingGroupId = bindingGroupId, inputKey = "button_a", orderIndex = 0),
                activators = listOf(ActivatorGraph(activator, listOf(binding))),
            )
            return BindingGroupGraph(
                group = BindingGroup(id = bindingGroupId, actionSetId = actionSetId, name = "face_buttons", mode = BindingMode.BUTTON_PAD),
                inputs = listOf(buttonAInput),
            )
        }
        val setA = ActionSetGraph(
            actionSet = ActionSet(id = 1L, controllerProfileId = 1L, name = "gameplay", title = "Gameplay"),
            layers = emptyList(),
            preset = listOf(PresetEntry(InputSource.BUTTON_DIAMOND, "active", buildFaceGroup(1L, 1L, 100L, setAButtonA))),
        )
        val setB = ActionSetGraph(
            actionSet = ActionSet(id = 2L, controllerProfileId = 1L, name = "menu", title = "Menu"),
            layers = emptyList(),
            preset = listOf(PresetEntry(InputSource.BUTTON_DIAMOND, "active", buildFaceGroup(2L, 2L, 200L, setBButtonA))),
        )
        return ControllerConfig(
            controllerProfile = ControllerProfile(
                id = 1L, layoutId = 1L,
                controllerType = ControllerType.GENERIC_ANDROID, name = "Default",
            ),
            actionSets = listOf(setA, setB),
        )
    }

    /** Builds a minimal ControllerConfig matching the seed shape, with optional override for BUTTON_A. */


    /**
     * The advanced view's two write affordances — the mode pill and an empty cell's "New" —
     * both depend on the SAME lookup: the viewed set's active preset entry for the group's
     * source. When that resolves to null the header silently degrades to a dead "DEFAULT"
     * label and every tile is disabled, which is a regression shape Dylan hit on device
     * (2026-09-12) and one nothing in the suite covered. These three pin it.
     */
    @Test
    fun groupEditor_everyGroup_offersAnEnabledModePill() {
        setScreenLocal(seedShapedConfig())
        // The utility groups are exempt by design (Dylan, 2026-09-21): Start and Select have no
        // mode to pick — theirs follows from whether they are bound — so their card states its
        // name instead.
        val utility = setOf(RemapSimpleGroup.LEFT_UTILITY, RemapSimpleGroup.RIGHT_UTILITY)
        for (group in RemapSimpleGroup.entries - utility) {
            openAdvanced(group.name)
            composeRule.waitForIdle()
            val pills = inOpenCard(modePillMatcher).fetchSemanticsNodes().size
            assert(pills == 1) {
                "${group.name}: expected one enabled mode pill, found $pills — the header fell " +
                    "back to its dead \"DEFAULT\" label, so the group's preset didn't resolve."
            }
            inOpenCard(hasContentDescription("Close")).onFirst().performClick()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun groupEditor_modePill_picksMode() {
        var picked: Pair<Long, BindingMode>? = null
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = seedShapedConfig(),
                        onSetBindingGroupMode = { id, mode -> picked = id to mode },
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }
        openAdvanced("FACE")
        composeRule.waitForIdle()
        inOpenCard(modePillMatcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        // Menu rows live in a Popup, whose bounds come back negated under Robolectric — drive
        // the row through semantics rather than a coordinate click.
        composeRule.onNodeWithText("None").performSemanticsAction(SemanticsActions.OnClick)
        assert(picked != null) { "Picking a mode did not reach onSetBindingGroupMode" }
    }

    @Test
    fun groupEditor_plusTile_newCommand_addsToTheRow() {
        var added: Triple<Long, String, ActivatorType>? = null
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = seedShapedConfig(),
                        onAddRowCommand = { g, k, t, _ -> added = Triple(g, k, t) },
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }
        openAdvanced("FACE")
        composeRule.waitForIdle()
        // A fresh layout seeds one command per row (its own self-mapping), so the row's "+"
        // is SLOT 1 — slot 0 holds a real command and offers Edit, not New. A new command
        // starts as a Regular Press and is retyped from the same menu.
        composeRule.onNodeWithTag("group-editor-table:FACE")
            .performScrollToNode(hasTestTag("cell:FACE:BUTTON_DIAMOND:button_a:1"))
        composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_a:1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("New").performSemanticsAction(SemanticsActions.OnClick)
        assert(added == Triple(1L, "button_a", ActivatorType.FULL_PRESS)) {
            "Expected New to add (1, button_a, FULL_PRESS); got $added"
        }
    }

    /**
     * A screen with a reveal scope chosen for it — the experiment's two behaviours (see
     * [TileReveal]) are otherwise indistinguishable from the outside.
     */
    private fun setScreenRevealing(reveal: TileReveal, config: ControllerConfig = seedShapedConfig()) {
        composeRule.setContent {
            MaterialTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalTileReveal provides reveal) {
                    Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                        RemapControlsScreen(
                            config = config,
                            onOpenInputEditor = { _, _, _ -> },
                            onBack = {},
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * EDIT MODE is view-WIDE (Dylan, 2026-09-22): the mode itself belongs to the whole view, so a
     * command can be carried from any group to any other. On [TileReveal.ALL_GROUPS] that shows
     * as every group's rows tiling at once. And it does not travel — the advanced card is not
     * opened.
     */
    @Test
    fun simpleView_selectingAGroup_tilesEveryGroupsRows_withoutZooming() {
        setScreenRevealing(TileReveal.ALL_GROUPS)
        composeRule.onNodeWithTag("simple-group:FACE").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_a:0").assertExists()
        // A group the user did NOT select, tiled all the same.
        composeRule.onNodeWithTag("cell:DPAD:DPAD:dpad_up:0").assertExists()
        // Every row ends in its "+", exactly as the table's rows do.
        composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_a:1").assertExists()
        composeRule.onAllNodesWithTag("group-editor").assertCountEquals(0)
    }

    /**
     * **Revealing ONE group at a time** (Dylan, 2026-09-27, the default while the experiment
     * runs): "entering edit mode via an input group only reveals the tiles for that input group".
     *
     * Everything else about the mode is unchanged — the group opened is fully tiled, "+" and all,
     * and the view has not travelled anywhere.
     */
    @Test
    fun editMode_revealingOneGroup_tilesThatGroupAlone() {
        setScreenRevealing(TileReveal.FOCUSED_GROUP)
        composeRule.onNodeWithTag("simple-group:FACE").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_a:0").assertExists()
        composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_a:1").assertExists()
        // Every OTHER group is still the resting view.
        composeRule.onAllNodesWithTag("cell:DPAD:DPAD:dpad_up:0").assertCountEquals(0)
        composeRule.onAllNodesWithTag("cell:LEFT_UTILITY:SWITCH_SELECT:click:0").assertCountEquals(0)
        composeRule.onAllNodesWithTag("group-editor").assertCountEquals(0)
    }

    /**
     * **Moving to another group reveals it and collapses the one left behind.**
     *
     * A collapsed group keeps its BOX — that is what the cursor walks onto, and what a finger
     * taps — so the mode stays navigable with only one group's tiles on screen. Driven here by
     * the box's own click, which is the same path the d-pad takes (both seat the cursor in the
     * group and then open it).
     */
    @Test
    fun editMode_movingToAnotherGroup_revealsItAndCollapsesTheLast() {
        setScreenRevealing(TileReveal.FOCUSED_GROUP)
        composeRule.onNodeWithTag("simple-group:FACE").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_a:0").assertExists()

        composeRule.onNodeWithTag("simple-group:DPAD").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("cell:DPAD:DPAD:dpad_up:0").assertExists()
        composeRule.onAllNodesWithTag("cell:FACE:BUTTON_DIAMOND:button_a:0").assertCountEquals(0)
    }

    /**
     * **A tile in flight reveals everything** (Dylan, 2026-09-27: "if a tile has been grabbed,
     * all of the tiles should become visible") — and when it lands, the group it landed in is
     * the one left open.
     *
     * The tile is lifted by the controller path, which is the one that can be driven from here:
     * hold the activate button until the lift ripens.
     */
    @Test
    fun editMode_liftingATile_revealsEveryGroup_thenCollapsesAroundWhereItLands() {
        composeRule.mainClock.autoAdvance = false
        setScreenRevealing(
            TileReveal.FOCUSED_GROUP,
            seedShapedConfig().withTwoCommands(InputSource.BUTTON_DIAMOND, "button_a", 900L),
        )
        fun settle() {
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(1_200L)
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        settle()
        composeRule.onNodeWithTag("simple-group:FACE").performClick()
        settle()
        val tile = "cell:FACE:BUTTON_DIAMOND:button_a:0"
        composeRule.onNodeWithTag(tile).requestFocus()
        settle()
        composeRule.onAllNodesWithTag("cell:DPAD:DPAD:dpad_up:0").assertCountEquals(0)

        // Hold until it lifts — the lift is a delayed effect, so the clock has to run for it.
        composeRule.onNodeWithTag(tile).performKeyInput { keyDown(Key.ButtonA) }
        settle()
        composeRule.onNodeWithTag("cell:DPAD:DPAD:dpad_up:0").assertExists()

        // Put it straight back down. The group it landed in stays open; the rest collapse again.
        composeRule.onNodeWithTag(tile).performKeyInput { keyUp(Key.ButtonA) }
        settle()
        composeRule.onNodeWithTag(tile).assertExists()
        composeRule.onAllNodesWithTag("cell:DPAD:DPAD:dpad_up:0").assertCountEquals(0)
        composeRule.mainClock.autoAdvance = true
    }

    /**
     * **The morph's two ends must be the real thing.**
     *
     * Entering edit mode now travels (Dylan, 2026-09-24): the labels widen into their tiles and
     * the buttons fade in behind them. Mid-travel the tiles are inert ghosts, so this walks the
     * clock through the middle — where nothing should be focusable or tagged — and on to the end,
     * where the real tiles must have taken over.
     */
    @Test
    fun selectingAGroup_morphsIntoTiles_ratherThanCutting() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = seedShapedConfig(),
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithTag("simple-group:FACE").performClick()

        // Mid-travel: the resting label is still on screen and no tile has been built yet.
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.onAllNodesWithTag("cell:FACE:BUTTON_DIAMOND:button_a:0").assertCountEquals(0)

        // Landed: the real tiles are there.
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_a:0").assertExists()
        composeRule.mainClock.autoAdvance = true
    }

    /**
     * **The morph must not shake.**
     *
     * The first implementation corrected the view's position from the frame before it, while the
     * scroller clamped against the width that correction was producing — a loop that chased its
     * own tail, and which Dylan saw immediately as the content "shaking horizontally a little
     * bit" on the way in and out. Since the whole point is a motion nobody can watch in a unit
     * test, this measures it instead: every group's on-screen x is sampled frame by frame across
     * the travel, and each one has to move in ONE direction throughout. A reversal is jitter.
     *
     * The window is deliberately narrow enough that the tiles overrun it, which is the case that
     * jittered: the scroller has a range, so the camera correction actually does something.
     */
    @Test
    fun enteringEditMode_movesEveryGroupInOneDirection_withoutShaking() =
        assertMorphDoesNotShake(TileReveal.FOCUSED_GROUP) { back ->
            listOf(
                "in" to { composeRule.onNodeWithTag("simple-group:FACE").performClick() },
                // And out again — the leg that drifted, and so the one worth watching.
                "out" to back,
            )
        }

    /** The same, with every group revealing at once: every column changes width instead of one,
     *  so the grid's own re-centring has the most to do. */
    @Test
    fun enteringEditMode_withEveryGroupRevealed_doesNotShake() =
        assertMorphDoesNotShake(TileReveal.ALL_GROUPS) { back ->
            listOf(
                "in" to { composeRule.onNodeWithTag("simple-group:FACE").performClick() },
                "out" to back,
            )
        }

    /**
     * **The travel that only revealing one group at a time has** (2026-09-27): a SWAP, one group
     * opening as another closes, and across the two columns so both change width at once — the
     * left giving up its wide box while the right takes one on.
     *
     * It is the case the two ends cannot describe between them: the box growing widens through
     * act one of the morph and the one collapsing gives its width up in act two, so an
     * interpolation of the two endpoint grids disagrees with the boxes in the middle. Hence the
     * column floor in the stage's layout — and hence this, which is what would catch its absence.
     */
    @Test
    fun switchingGroups_movesEveryGroupInOneDirection_withoutShaking() =
        assertMorphDoesNotShake(
            reveal = TileReveal.FOCUSED_GROUP,
            // Two fat groups in the SAME column, which is the shape that needs the floor: the
            // column is as wide as whichever of them is open, so it leaves and arrives at the very
            // same width while the two boxes cross in the middle at four fifths of theirs. And fat
            // enough that the grid overruns the window — a grid that fits is padded around the
            // controller's middle and pins every box to it, so nothing moves to measure.
            config = seedShapedConfig()
                .withPressStack(InputSource.DPAD, 7000L)
                .withPressStack(InputSource.LEFT_TRIGGER, 8000L),
        ) { _ ->
            listOf(
                "in" to { composeRule.onNodeWithTag("simple-group:DPAD").performClick() },
                "across" to { composeRule.onNodeWithTag("simple-group:LEFT_SHOULDER").performClick() },
            )
        }

    private fun assertMorphDoesNotShake(
        reveal: TileReveal,
        config: ControllerConfig = seedShapedConfig(),
        legs: (back: () -> Unit) -> List<Pair<String, () -> Unit>>,
    ) {
        composeRule.mainClock.autoAdvance = false
        var back: (() -> Unit)? = null
        composeRule.setContent {
            val dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
                ?.onBackPressedDispatcher
            back = { dispatcher?.onBackPressed() }
            MaterialTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalTileReveal provides reveal) {
                    Surface(modifier = androidx.compose.ui.Modifier.size(560.dp, 500.dp)) {
                        RemapControlsScreen(
                            config = config,
                            onOpenInputEditor = { _, _, _ -> },
                            onBack = {},
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(600)

        val tracked = listOf("DPAD", "FACE", "LEFT_SHOULDER", "RIGHT_STICK")
        fun sample() = tracked.associateWith {
            composeRule.onNodeWithTag("simple-group:$it", useUnmergedTree = true)
                // positionInRoot, not boundsInRoot: the latter is CLIPPED to its parents, so a
                // group scrolled past the window's edge reads as a flat 0 and hides the motion
                // being measured.
                .fetchSemanticsNode().positionInRoot.x
        }

        fun travel(begin: () -> Unit): List<Map<String, Float>> {
            val frames = mutableListOf(sample())
            begin()
            // Every frame of the travel, not a sample of it: a shake lives between frames.
            repeat(40) {
                composeRule.mainClock.advanceTimeBy(8)
                frames += sample()
            }
            return frames
        }

        val walked = legs { composeRule.runOnUiThread { back?.invoke() } }
            .map { (name, begin) -> name to travel(begin) }

        for ((leg, frames) in walked) tracked.forEach { group ->
            val path = frames.map { it.getValue(group) }
            val net = path.last() - path.first()
            val forward = if (net >= 0f) 1f else -1f
            // How far the group travelled AGAINST its own overall direction. A monotonic travel
            // scores zero; a wobble superimposed on one scores a couple of pixels per frame,
            // which is exactly what "shaking a little bit" looks like from the outside.
            val backtrack = path.zipWithNext { a, b -> (b - a) * forward }
                .filter { it < 0f }
                .sumOf { -it.toDouble() }
            // One pixel of give for rounding; a wobble costs several per frame.
            val allowed = 1.0
            assert(backtrack <= allowed) {
                "$group shook on the way $leg (backtracked %.1f of %.1f): "
                    .format(backtrack, net) + path.joinToString { "%.1f".format(it) }
            }
        }
        composeRule.mainClock.autoAdvance = true
    }

    /**
     * **Leaving edit mode from a scrolled view must land where it started.**
     *
     * Dylan, 2026-09-24: scroll right in edit mode, leave it, and the whole body came to rest
     * offset to the right by exactly what had been scrolled — a band of empty space down the
     * left. The travel holds the view still by shifting the grid against a scroll it captured
     * when the travel began, but the scroller CLAMPS its own value as the content narrows, so
     * the two disagreed by however far it had been dragged.
     */
    @Test
    fun leavingEditMode_whileScrolled_returnsToTheRestingPosition() {
        // Back is the only way out of edit mode, so the test needs the dispatcher the
        // BackHandler is registered against.
        var back: (() -> Unit)? = null
        composeRule.setContent {
            val dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
                ?.onBackPressedDispatcher
            back = { dispatcher?.onBackPressed() }
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(560.dp, 500.dp)) {
                    RemapControlsScreen(
                        config = seedShapedConfig(),
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }
        val tracked = listOf("DPAD", "FACE", "LEFT_SHOULDER", "RIGHT_STICK", "LEFT_UTILITY")
        fun sample() = tracked.associateWith {
            composeRule.onNodeWithTag("simple-group:$it", useUnmergedTree = true)
                .fetchSemanticsNode().positionInRoot.x
        }

        val resting = sample()
        composeRule.onNodeWithTag("simple-group:FACE").performClick()
        composeRule.waitForIdle()

        // Drag the body to the right, the way a finger would.
        val body = composeRule.onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
            .fetchSemanticsNode()
        val scrollBy = body.config[SemanticsActions.ScrollBy].action
        composeRule.runOnUiThread { scrollBy?.invoke(50f, 0f) }
        composeRule.waitForIdle()

        composeRule.runOnUiThread { back?.invoke() }
        composeRule.waitForIdle()

        val after = sample()
        tracked.forEach { group ->
            val drift = after.getValue(group) - resting.getValue(group)
            assert(kotlin.math.abs(drift) <= 1f) {
                "$group came back %.1f from where it started".format(drift)
            }
        }
    }

    /**
     * **Entering edit mode goes to the group you opened, not to wherever you were last.**
     *
     * Dylan, 2026-09-24: "I might've been scrolled all the way to the right while working on
     * button pad assignments the last time I was in edit mode, but then when I open the left
     * trigger input group sometime later, the window scrolls all the way to the right because
     * that's where I was last. Very unintuitive." The travel had no target of its own — it
     * simply held whatever the scroll happened to be.
     *
     * `boundsInRoot` is CLIPPED, which makes it the measurement here: a group scrolled off the
     * side of the window loses that much of its visible width.
     *
     * **This pins the invariant, it is not a regression test.** No fixture I could build made
     * the old rule leave the opened group cut off — the real case depends on a layout's own
     * widths against a particular window — so it guards the rule from here rather than proving
     * the bug gone.
     */
    @Test
    fun enteringEditMode_bringsTheOpenedGroupIntoView() {
        composeRule.setContent {
            MaterialTheme {
                // Narrow enough, and with rows full enough, that the RESTING view already
                // overruns it — so there is somewhere to be scrolled away from to begin with.
                // 250dp rather than the 300 it was: at 300 the controller's column now gives up
                // enough of its own width to land the whole grid inside the window (2026-09-27's
                // squeeze) and there would be nothing to scroll. The shoulder this opens still
                // holds one command per row, so its edit-mode box fits the window — which the
                // last assertion needs.
                Surface(modifier = androidx.compose.ui.Modifier.size(250.dp, 500.dp)) {
                    RemapControlsScreen(
                        config = seedShapedConfig()
                            .withTwoCommands(InputSource.BUTTON_DIAMOND, "button_a", 900L)
                            .withTwoCommands(InputSource.DPAD, "dpad_up", 910L),
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }
        fun leftShoulderX() = composeRule
            .onNodeWithTag("simple-group:LEFT_SHOULDER", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.x

        val scrollBy = composeRule.onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.ScrollBy].action
        // From one end to the other. The resting view opens with the CONTROLLER centred, so it
        // starts mid-range now — measuring from there would leave only half the travel and the
        // premise below would be judging a scroll half as long as the one available.
        composeRule.runOnUiThread { scrollBy?.invoke(-4000f, 0f) }
        composeRule.waitForIdle()
        val before = leftShoulderX()
        composeRule.runOnUiThread { scrollBy?.invoke(4000f, 0f) }
        composeRule.waitForIdle()
        // The premise: the view really is scrolled away from the group about to be opened.
        assert(leftShoulderX() < before - 10f) {
            "the body did not scroll, so this proves nothing (%.1f -> %.1f)"
                .format(before, leftShoulderX())
        }

        // Now open a group on the far LEFT.
        composeRule.onNodeWithTag("simple-group:LEFT_SHOULDER").performClick()
        composeRule.waitForIdle()

        val node = composeRule
            .onNodeWithTag("simple-group:LEFT_SHOULDER", useUnmergedTree = true)
            .fetchSemanticsNode()
        // Clipped width vs the node's own: anything less and part of the group the user just
        // opened is off the side of the window.
        assert(node.boundsInRoot.width >= node.size.width - 1f) {
            "The group just opened is still cut off: %.1f of %d visible"
                .format(node.boundsInRoot.width, node.size.width)
        }
    }

    /**
     * **Dylan's sequence, exactly**: work on one group, scroll away, leave, then open a
     * different group — which must be the one you end up looking at.
     *
     * The travel's plan outlived its travel. It was cleared on a later layout pass, and there
     * need not BE one: the hand-off usually scrolls to where the scroller already is, nothing is
     * invalidated, no measure follows. So every session after the first reused the previous
     * one's captured scroll target, and you were returned to wherever you last were rather than
     * taken to what you just opened (2026-09-24).
     */
    @Test
    fun openingASecondGroup_goesToThatGroup_notBackToTheLastOne() {
        var back: (() -> Unit)? = null
        composeRule.setContent {
            val dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
                ?.onBackPressedDispatcher
            back = { dispatcher?.onBackPressed() }
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(420.dp, 500.dp)) {
                    RemapControlsScreen(
                        config = seedShapedConfig()
                            .withTwoCommands(InputSource.BUTTON_DIAMOND, "button_a", 900L)
                            .withTwoCommands(InputSource.DPAD, "dpad_up", 910L),
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }
        fun visibleWidthOf(group: String): Pair<Float, Int> {
            val node = composeRule.onNodeWithTag("simple-group:$group", useUnmergedTree = true)
                .fetchSemanticsNode()
            return node.boundsInRoot.width to node.size.width
        }

        // A session on the RIGHT, dragged as far right as it will go.
        composeRule.onNodeWithTag("simple-group:FACE").performClick()
        composeRule.waitForIdle()
        val scrollBy = composeRule.onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.ScrollBy].action
        composeRule.runOnUiThread { scrollBy?.invoke(4000f, 0f) }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { back?.invoke() }
        composeRule.waitForIdle()

        // Now a group on the far LEFT. It is the one being opened, so it is the one to show.
        composeRule.onNodeWithTag("simple-group:LEFT_SHOULDER").performClick()
        composeRule.waitForIdle()

        val (visible, own) = visibleWidthOf("LEFT_SHOULDER")
        assert(visible >= own - 1f) {
            "Opened the left shoulder and got left looking elsewhere: " +
                "%.1f of %d visible".format(visible, own)
        }
    }

    /**
     * A row tile is the table's tile: same menu, same verbs. If it weren't, edit mode would be a
     * second implementation of the same control wearing the same face.
     */
    @Test
    fun editMode_tile_offersTheSameMenuAsTheTable() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = seedShapedConfig(),
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.onNodeWithTag("simple-group:FACE").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_a:0").performClick()
        composeRule.waitForIdle()

        listOf("Edit", "Label", "Type", "Move", "Clear").forEach { verb ->
            composeRule.onNodeWithText(verb).assertExists()
        }
    }

    /** The mode pill carries no test tag; its click LABEL is the stable handle. */
    private val modePillMatcher = androidx.compose.ui.test.SemanticsMatcher("mode pill") { node ->
        node.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Change input mode"
    }

    /**
     * Scope a matcher to the card the camera is ON.
     *
     * Every card in the zoomed scene carries the full header from 2026-09-21 (Dylan: the scene
     * is a canvas the user roams, so "the card the camera is on" stopped meaning "the card being
     * worked on"). "The Close button" is therefore seven nodes, and a test that means the open
     * one has to say so — the open card is the one tagged `group-editor`.
     */
    /**
     * Open a group's ADVANCED card.
     *
     * A HOLD on its basic-view box since 2026-09-22: an ordinary tap now means "edit this
     * group's rows in place" (see RemapSimpleView's edit mode), and holding is what still
     * travels to the separate view.
     */
    private fun openAdvanced(group: String) {
        composeRule.onNodeWithTag("simple-group:$group").performTouchInput { longClick() }
        composeRule.waitForIdle()
    }

    private fun inOpenCard(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        composeRule.onAllNodes(matcher and hasAnyAncestor(hasTestTag("group-editor")), useUnmergedTree = true)

    /**
     * Put two bound commands — a Regular and a Long press — on one row of [source], for tests
     * that need a row holding more than its "+" tile.
     */
    private fun ControllerConfig.withTwoCommands(
        source: InputSource,
        inputKey: String,
        idBase: Long,
    ): ControllerConfig = copy(
        actionSets = actionSets.map { set ->
            set.copy(
                preset = set.preset.map { entry ->
                    if (entry.inputSource != source) return@map entry
                    entry.copy(
                        group = entry.group.copy(
                            inputs = entry.group.inputs.map { input ->
                                if (input.input.inputKey != inputKey) return@map input
                                input.copy(
                                    activators = listOf(
                                        ActivatorType.FULL_PRESS to "ENTER",
                                        ActivatorType.LONG_PRESS to "SPACE",
                                    ).mapIndexed { index, (type, key) ->
                                        val activator = Activator(
                                            id = idBase + index,
                                            groupInputId = input.input.id,
                                            type = type,
                                            orderIndex = index,
                                        )
                                        val (outputType, args) = BindingOutput.KeyPress(key).toEntity()
                                        ActivatorGraph(
                                            activator,
                                            listOf(
                                                Binding(
                                                    id = idBase + 50L + index,
                                                    activatorId = activator.id,
                                                    outputType = outputType,
                                                    args = args,
                                                ),
                                            ),
                                        )
                                    },
                                )
                            },
                        ),
                    )
                },
            )
        },
    )

    /**
     * A config shaped like `seedDefaultConfig` leaves one: every default-seeded source present
     * with an ACTIVE preset entry, all in their seeded modes, no commands bound. [sampleConfig]
     * deliberately populates only BUTTON_DIAMOND, so it can't catch a per-source preset miss.
     */

    /** As [withTwoCommands], with a third command so a row can be seen to GAIN a tile. */
    private fun ControllerConfig.withThreeCommands(
        source: InputSource,
        inputKey: String,
        idBase: Long,
    ): ControllerConfig = withTwoCommands(source, inputKey, idBase).copy(
        actionSets = withTwoCommands(source, inputKey, idBase).actionSets.map { set ->
            set.copy(
                preset = set.preset.map { entry ->
                    if (entry.inputSource != source) return@map entry
                    entry.copy(
                        group = entry.group.copy(
                            inputs = entry.group.inputs.map { input ->
                                if (input.input.inputKey != inputKey) return@map input
                                val activator = Activator(
                                    id = idBase + 2,
                                    groupInputId = input.input.id,
                                    type = ActivatorType.DOUBLE_PRESS,
                                    orderIndex = 2,
                                )
                                val (outputType, args) = BindingOutput.KeyPress("ESCAPE").toEntity()
                                input.copy(
                                    activators = input.activators + ActivatorGraph(
                                        activator,
                                        listOf(
                                            Binding(
                                                id = idBase + 52L,
                                                activatorId = activator.id,
                                                outputType = outputType,
                                                args = args,
                                            ),
                                        ),
                                    ),
                                )
                            },
                        ),
                    )
                },
            )
        },
    )

    /** The grid's own margin — boxes stop this far short of its edge, so an outermost box being
     *  this far from the window is the layout, not scrollable padding. */
    private val GridEdgeSlack = 20f

    /** Stack three press types onto every row of [source], which is how a group box is made
     *  measurably wider than its opposite number under Robolectric — text measures at ~zero
     *  width here, but each command's press and device glyphs are real dp. */
    private fun ControllerConfig.withPressStack(
        source: InputSource,
        idBase: Long,
    ): ControllerConfig = copy(
        actionSets = actionSets.map { set ->
            set.copy(
                preset = set.preset.map { entry ->
                    if (entry.inputSource != source) return@map entry
                    entry.copy(
                        group = entry.group.copy(
                            inputs = entry.group.inputs.mapIndexed { position, input ->
                                input.copy(
                                    activators = listOf(
                                        ActivatorType.FULL_PRESS to "ENTER",
                                        ActivatorType.LONG_PRESS to "SPACE",
                                        ActivatorType.DOUBLE_PRESS to "TAB",
                                    ).mapIndexed { index, (type, key) ->
                                        val activator = Activator(
                                            id = idBase + position * 10 + index,
                                            groupInputId = input.input.id,
                                            type = type,
                                            orderIndex = index,
                                        )
                                        val (outputType, args) = BindingOutput.KeyPress(key).toEntity()
                                        ActivatorGraph(
                                            activator,
                                            listOf(
                                                Binding(
                                                    id = idBase + 5000L + position * 10 + index,
                                                    activatorId = activator.id,
                                                    outputType = outputType,
                                                    args = args,
                                                ),
                                            ),
                                        )
                                    },
                                )
                            },
                        ),
                    )
                },
            )
        },
    )

    private fun seedShapedConfig(): ControllerConfig {
        var nextId = 1L
        fun entry(
            source: InputSource,
            keys: List<String>,
            mode: BindingMode,
            defaults: Map<String, String> = emptyMap(),
        ): PresetEntry {
            val groupId = nextId++
            return PresetEntry(
                source, "active",
                BindingGroupGraph(
                    group = BindingGroup(
                        id = groupId, actionSetId = 1L, name = source.name.lowercase(), mode = mode,
                    ),
                    inputs = keys.mapIndexed { index, key ->
                        val inputId = nextId++
                        val activator = Activator(
                            id = inputId + 500L, groupInputId = inputId,
                            type = ActivatorType.FULL_PRESS, orderIndex = 0,
                        )
                        val (type, args) = defaults[key]
                            ?.let { BindingOutput.XInputButton(it) as BindingOutput }
                            .let { it ?: BindingOutput.Unbound }
                            .toEntity()
                        GroupInputGraph(
                            input = GroupInput(
                                id = inputId, bindingGroupId = groupId, inputKey = key, orderIndex = index,
                            ),
                            activators = listOf(
                                ActivatorGraph(
                                    activator,
                                    listOf(
                                        Binding(
                                            id = inputId + 900L, activatorId = activator.id,
                                            outputType = type, args = args, orderIndex = 0,
                                        ),
                                    ),
                                ),
                            ),
                        )
                    },
                ),
            )
        }
        return ControllerConfig(
            controllerProfile = ControllerProfile(
                id = 1L, layoutId = 1L,
                controllerType = ControllerType.GENERIC_ANDROID, name = "Test layout",
            ),
            actionSets = listOf(
                ActionSetGraph(
                    actionSet = ActionSet(id = 1L, controllerProfileId = 1L, name = "default", title = "Default"),
                    layers = emptyList(),
                    preset = listOf(
                        entry(
                            InputSource.BUTTON_DIAMOND,
                            listOf("button_a", "button_b", "button_x", "button_y"),
                            BindingMode.BUTTON_PAD,
                            mapOf(
                                "button_a" to "BUTTON_A", "button_b" to "BUTTON_B",
                                "button_x" to "BUTTON_X", "button_y" to "BUTTON_Y",
                            ),
                        ),
                        entry(
                            InputSource.DPAD,
                            listOf("dpad_up", "dpad_down", "dpad_left", "dpad_right"),
                            BindingMode.DPAD,
                            mapOf(
                                "dpad_up" to "DPAD_UP", "dpad_down" to "DPAD_DOWN",
                                "dpad_left" to "DPAD_LEFT", "dpad_right" to "DPAD_RIGHT",
                            ),
                        ),
                        entry(InputSource.LEFT_BUMPER, listOf("click"), BindingMode.SINGLE_BUTTON, mapOf("click" to "BUTTON_L1")),
                        entry(InputSource.RIGHT_BUMPER, listOf("click"), BindingMode.SINGLE_BUTTON, mapOf("click" to "BUTTON_R1")),
                        entry(InputSource.LEFT_TRIGGER, listOf("full_pull", "soft_pull"), BindingMode.SINGLE_BUTTON, mapOf("full_pull" to "AXIS_L2")),
                        entry(InputSource.RIGHT_TRIGGER, listOf("full_pull", "soft_pull"), BindingMode.SINGLE_BUTTON, mapOf("full_pull" to "AXIS_R2")),
                        entry(InputSource.LEFT_JOYSTICK, listOf("click", "outer_ring"), BindingMode.JOYSTICK_MOVE, mapOf("click" to "BUTTON_THUMBL")),
                        entry(InputSource.RIGHT_JOYSTICK, listOf("click", "outer_ring"), BindingMode.JOYSTICK_MOVE, mapOf("click" to "BUTTON_THUMBR")),
                        entry(InputSource.SWITCH_START, listOf("click"), BindingMode.SINGLE_BUTTON, mapOf("click" to "BUTTON_START")),
                        entry(InputSource.SWITCH_SELECT, listOf("click"), BindingMode.SINGLE_BUTTON, mapOf("click" to "BUTTON_SELECT")),
                        entry(InputSource.GYRO, emptyList(), BindingMode.DEVICE_DEFAULT),
                    ),
                ),
            ),
        )
    }


    @Test
    fun noLayoutViewed_showsNoLayoutState_notAPhantomLayout() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = null,
                        viewedLayoutId = null,
                        layoutsLoaded = true,
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // A fresh install used to fall through to the controls view and render a phantom
        // layout called "Layout" — interactive group boxes over no binding graph at all.
        composeRule.onNodeWithText("No layout selected").assertExists()
        composeRule.onAllNodesWithTag("simple-group:FACE").assertCountEquals(0)
        composeRule.onAllNodesWithText("Layout").assertCountEquals(0)
    }

    @Test
    fun noLayoutViewed_beforeLayoutsLoad_doesNotShowNoLayoutState() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = null,
                        viewedLayoutId = null,
                        // The cold-start shape: nothing resolved yet. An empty layout list
                        // means "still loading" here, not "there are none".
                        layoutsLoaded = false,
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeRule.onAllNodesWithText("No layout selected").assertCountEquals(0)
    }

    private fun sampleConfig(
        boundButtonA: BindingOutput = BindingOutput.Unbound,
        buttonALabel: String? = null,
        extraButtonAInput: Boolean = false,
        extraButtonBInput: Boolean = false,
        faceMode: BindingMode = BindingMode.BUTTON_PAD,
    ): ControllerConfig {
        val activator = Activator(id = 100L, groupInputId = 10L, type = ActivatorType.FULL_PRESS, orderIndex = 0)
        val binding = boundButtonA.toEntity().let { (type, args) ->
            Binding(id = 1000L, activatorId = activator.id, outputType = type, args = args, orderIndex = 0, label = buttonALabel)
        }
        val activatorGraphs = buildList {
            add(ActivatorGraph(activator, listOf(binding)))
            if (extraButtonAInput) {
                val extra = Activator(id = 101L, groupInputId = 10L, type = ActivatorType.LONG_PRESS, orderIndex = 1)
                val extraBinding = BindingOutput.KeyPress("SPACE").toEntity().let { (type, args) ->
                    Binding(id = 1001L, activatorId = extra.id, outputType = type, args = args, orderIndex = 0)
                }
                add(ActivatorGraph(extra, listOf(extraBinding)))
            }
        }
        val buttonAInput = GroupInputGraph(
            input = GroupInput(id = 10L, bindingGroupId = 1L, inputKey = "button_a", orderIndex = 0),
            activators = activatorGraphs,
        )
        fun unboundInput(id: Long, key: String, order: Int, extraInput: Boolean = false): GroupInputGraph {
            val act = Activator(id = id + 100L, groupInputId = id, type = ActivatorType.FULL_PRESS)
            val b = Binding(id = id + 1000L, activatorId = act.id, outputType = BindingOutputType.UNBOUND, args = "")
            val activators = buildList {
                add(ActivatorGraph(act, listOf(b)))
                if (extraInput) {
                    val extra = Activator(id = id + 110L, groupInputId = id, type = ActivatorType.LONG_PRESS, orderIndex = 1)
                    val extraBinding = BindingOutput.KeyPress("Q").toEntity().let { (type, args) ->
                        Binding(id = id + 1010L, activatorId = extra.id, outputType = type, args = args, orderIndex = 0)
                    }
                    add(ActivatorGraph(extra, listOf(extraBinding)))
                }
            }
            return GroupInputGraph(
                input = GroupInput(id = id, bindingGroupId = 1L, inputKey = key, orderIndex = order),
                activators = activators,
            )
        }
        val faceGroup = BindingGroupGraph(
            group = BindingGroup(id = 1L, actionSetId = 1L, name = "face_buttons", mode = faceMode),
            inputs = listOf(
                buttonAInput,
                unboundInput(11L, "button_b", 1, extraInput = extraButtonBInput),
                unboundInput(12L, "button_x", 2),
                unboundInput(13L, "button_y", 3),
            ),
        )
        val faceEntry = PresetEntry(InputSource.BUTTON_DIAMOND, "active", faceGroup)
        // Minimal: only BUTTON_DIAMOND populated. Other sections will show with Unbound rows.
        val actionSet = ActionSetGraph(
            actionSet = ActionSet(id = 1L, controllerProfileId = 1L, name = "default", title = "Default"),
            layers = emptyList(),
            preset = listOf(faceEntry),
        )
        return ControllerConfig(
            controllerProfile = ControllerProfile(
                // Distinct from the "Default" set title so app-bar subtitle text doesn't
                // collide with the set row in onNodeWithText lookups.
                id = 1L, layoutId = 1L,
                controllerType = ControllerType.GENERIC_ANDROID, name = "Test layout",
            ),
            actionSets = listOf(actionSet),
        )
    }
}
