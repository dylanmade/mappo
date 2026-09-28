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

    /**
     * **HOLDING a group opens its own action menu** (Dylan, 2026-09-27) — the gesture that used to
     * open the advanced view, now that there is no advanced view to open.
     *
     * It carries the two things that view's card header carried and edit mode had nowhere to put:
     * the group's mode settings, and resetting the group to the layout's defaults. Same
     * `MinputActionMenu` a tile wears, named after the GROUP.
     */
    @Test
    fun holdingAGroup_opensItsOwnMenu_withSettingsAndReset() {
        var reset: Long? = null
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                    RemapControlsScreen(
                        config = seedShapedConfig(),
                        onResetBindingGroups = { reset = it.firstOrNull() },
                        onOpenInputEditor = { _, _, _ -> },
                        onBack = {},
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("simple-group:DPAD").performTouchInput { longClick() }
        composeRule.waitForIdle()
        // Named after the group, not after its mode: the thing being held is the group.
        composeRule.onNodeWithText("Directional Pad settings", useUnmergedTree = true).assertExists()
        // Holding a group does NOT enter edit mode — its rows are still rows.
        composeRule.onAllNodesWithTag("cell:DPAD:DPAD:dpad_up:0").assertCountEquals(0)

        clickMenuItem("Reset Directional Pad to default")
        assert(reset != null) { "Reset should have reached the callback, got $reset" }
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

        live.value = live.value.withTwoCommands(InputSource.BUTTON_DIAMOND, "button_a", 900L)
        composeRule.waitForIdle()

        // Every tile of the wider row can be reached: scrolled to the end, its outer end is on
        // screen. (The range need not GROW — the reach may already have held room past the group
        // that the new tiles simply fill; see [StageCamera].)
        assert(maxScroll() > 0f) { "the fixture should overflow the window" }
        val body = composeRule.onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
        val scrollBy = body.fetchSemanticsNode().config[SemanticsActions.ScrollBy].action
        composeRule.runOnUiThread { scrollBy?.invoke(100_000f, 0f) }
        composeRule.waitForIdle()
        val window = body.fetchSemanticsNode().let { it.positionInRoot.x + it.size.width }
        val faceEnd = composeRule.onNodeWithTag("simple-group:FACE", useUnmergedTree = true)
            .fetchSemanticsNode().let { it.positionInRoot.x + it.size.width }
        assert(faceEnd <= window + 1f) {
            "The face row grew by two commands and its end is ${faceEnd - window}px past the " +
                "furthest the body scrolls"
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
     * controller-centred position at that end of the range. **The view mode's rule only** (Dylan,
     * 2026-09-28: "In the physical controls' view mode, the controller genuinely should be centered
     * by default even if the labels of input rows begin to exit the screen width"); edit mode builds
     * its layout around its camera instead.
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
     * **The bar centres the SETS SWITCH, not the switch plus its kebab** (Dylan, 2026-09-27).
     *
     * The two used to be one Row measured into the centre slot, so what sat in the middle of the
     * screen was the pair and the switch itself was half a kebab to the left of it. The switch is
     * the control the eye lines up on — and the one that lines up with the controller image below —
     * so the kebab hangs off its end instead (`BarSlots`' `centreTrailing`).
     */
    @Test
    fun topBar_centresTheSetsSwitch_withItsKebabHungOffTheEnd() {
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
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    )
                }
            }
        }

        fun bounds(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val bar = bounds("bar:identity").let { composeRule.onRoot().fetchSemanticsNode().boundsInRoot }
        val switch = bounds("bar:sets")
        val kebab = bounds("bar:sets-menu")

        assert(kotlin.math.abs(switch.center.x - bar.center.x) <= 1.5f) {
            "the sets switch should be centred in the bar: ${switch.center.x} vs ${bar.center.x}"
        }
        assert(kebab.left >= switch.right) {
            "the kebab should hang off the switch's end: $kebab vs $switch"
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
     * **The left flank's rows are MIRRORED** (Dylan, 2026-09-17): the input glyph sits at the
     * group's INNER edge and the row's tiles run outward from it, so the two flanks read as each
     * other's reflection around the controller between them.
     *
     * Asserted on the TILES rather than the glyph, because the slot order is the thing that flips
     * and it is what the move-preview arithmetic keys off ([slotStepFor]). It was the zoomed
     * card's rule first; edit mode inherited it, and the cards are gone (2026-09-27).
     */
    @Test
    fun editMode_theLeftFlanksSlotsRunOutward_theRightFlanksInward() {
        setScreenRevealing(
            TileReveal.ALL_GROUPS,
            seedShapedConfig()
                .withTwoCommands(InputSource.DPAD, "dpad_up", idBase = 500L)
                .withTwoCommands(InputSource.BUTTON_DIAMOND, "button_y", idBase = 600L),
        )
        composeRule.onNodeWithTag("simple-group:DPAD").performClick()
        composeRule.waitForIdle()

        fun bounds(tag: String) = composeRule
            .onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val first = bounds("cell:DPAD:DPAD:dpad_up:0")
        val second = bounds("cell:DPAD:DPAD:dpad_up:1")
        assert(second.left < first.left) {
            "slot 1 should sit LEFT of slot 0 on the left flank: $first / $second"
        }
        // And a utility group reads like every other group on its side (Dylan, 2026-09-26): it
        // used to straddle the centre, Select running left and Start right.
        val utilityFirst = bounds("cell:LEFT_UTILITY:SWITCH_SELECT:click:0")
        val utilitySecond = bounds("cell:LEFT_UTILITY:SWITCH_SELECT:click:1")
        assert(utilitySecond.right <= utilityFirst.left + 1f) {
            "a left utility row should run outward too: $utilityFirst / $utilitySecond"
        }

        val faceFirst = bounds("cell:FACE:BUTTON_DIAMOND:button_y:0")
        val faceSecond = bounds("cell:FACE:BUTTON_DIAMOND:button_y:1")
        assert(faceSecond.left > faceFirst.left) {
            "the right flank keeps its normal order: $faceFirst / $faceSecond"
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
        // The override affordance lives on the TILE: opening the group in edit mode turns
        // button_a's row into tiles, and its first tile carries the layer menu.
        composeRule.onNodeWithTag("simple-group:FACE").performClick()
        composeRule.waitForIdle()
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

        composeRule.onNodeWithTag("simple-group:FACE").performClick()
        composeRule.waitForIdle()
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
    fun theEditor_hasNoOverridesFilter() {
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
        composeRule.onNodeWithTag("simple-group:FACE").performClick()
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


    /**
     * A row's trailing "+" tile creates a command on that row, in the edit mode that is now the
     * only editor. A fresh layout seeds one command per row (its own self-mapping), so the "+" is
     * SLOT 1 — slot 0 holds a real command and offers Edit, not New.
     */
    @Test
    fun editMode_plusTile_newCommand_addsToTheRow() {
        var added: Triple<Long, String, ActivatorType>? = null
        composeRule.setContent {
            MaterialTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalTileReveal provides TileReveal.ALL_GROUPS,
                ) {
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
        }
        composeRule.onNodeWithTag("simple-group:FACE").performClick()
        composeRule.waitForIdle()
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
     * as every group's rows tiling at once.
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
    }

    /**
     * **Revealing ONE group at a time** (Dylan, 2026-09-27, the default while the experiment
     * runs): "entering edit mode via an input group only reveals the tiles for that input group".
     *
     * Everything else about the mode is unchanged — the group opened is fully tiled, "+" and all.
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
     * **The morph plays, but nothing waits for it** (Dylan, 2026-09-27: "focus does not land on an
     * activated input group's tile until the animation completes, which is not great. The focus
     * should land immediately and the tile should be immediately interactive").
     *
     * A tile on its way in used to be an inert ghost, so for 260ms the cursor sat on a box that had
     * already stepped aside and a tap had nothing to hit. The tiles of a group being opened are the
     * real thing from the first frame now ([EditPhase.ARRIVING]) — they simply wear the chrome as
     * far as it has faded in, and the label travelling above them belongs to the row.
     *
     * So this checks both halves at a few frames in, nowhere near the end of the travel: the tile
     * holds the cursor and answers a click, AND it is still growing, which is the animation.
     */
    @Test
    fun selectingAGroup_morphsIntoTiles_thatAreUsableFromTheFirstFrame() {
        composeRule.mainClock.autoAdvance = false
        setScreenRevealing(TileReveal.FOCUSED_GROUP)
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithTag("simple-group:FACE").performClick()

        // A handful of frames — the morph runs for EditMorphMillis, and this is a fraction of it.
        repeat(4) { composeRule.mainClock.advanceTimeByFrame() }
        // The group's first row — which for the face buttons is Y, the top of the diamond.
        val tile = "cell:FACE:BUTTON_DIAMOND:button_y:0"
        composeRule.onNodeWithTag(tile, useUnmergedTree = true).assertIsFocused()
        composeRule.onNode(
            androidx.compose.ui.test.hasClickAction() and hasAnyAncestor(hasTestTag(tile)),
            useUnmergedTree = true,
        ).assertExists()
        val midway = composeRule.onNodeWithTag(tile, useUnmergedTree = true)
            .fetchSemanticsNode().size.width

        // Landed.
        composeRule.mainClock.advanceTimeBy(600)
        val arrived = composeRule.onNodeWithTag(tile, useUnmergedTree = true)
            .fetchSemanticsNode().size.width
        assert(midway < arrived) {
            "the tile was already its full ${arrived}px wide four frames in — the travel is gone"
        }
        composeRule.mainClock.autoAdvance = true
    }

    /**
     * **The grid moves for the WHOLE travel** (Dylan, 2026-09-27: opening a group and walking into
     * one "feel quite quick and almost jarring", where the camera on a cross-group move — the same
     * [EditMorphMillis] of `FastOutSlowInEasing`, untruncated — "feels great").
     *
     * The geometry used to finish inside the first 62% of the travel's OUTPUT, which on an eased
     * tween is about 120ms of motion ending at full speed: no deceleration at all, because the
     * curve is cut while it is still in its fast middle. Now every width, and with it the camera
     * that frames the group, is linear in the travel itself, so the two operations share one curve.
     *
     * Pinned at 150ms of 260: well past where the old ramp had already landed, and far enough from
     * the end that an eased curve is still visibly short of it.
     */
    @Test
    fun openingAGroup_keepsMovingForTheWholeTravel_ratherThanStoppingEarly() {
        composeRule.mainClock.autoAdvance = false
        setScreenRevealing(TileReveal.FOCUSED_GROUP)
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithTag("simple-group:FACE").performClick()

        val tile = "cell:FACE:BUTTON_DIAMOND:button_y:0"
        composeRule.mainClock.advanceTimeBy(150)
        val late = composeRule.onNodeWithTag(tile, useUnmergedTree = true).fetchSemanticsNode().size.width
        composeRule.mainClock.advanceTimeBy(600)
        val arrived = composeRule.onNodeWithTag(tile, useUnmergedTree = true).fetchSemanticsNode().size.width
        assert(late < arrived * 0.9f) {
            "the tile was ${late}px of its final ${arrived}px 150ms in — the travel is being cut short"
        }
        composeRule.mainClock.autoAdvance = true
    }

    /**
     * **A group's panel gives its outermost tiles the same air as the tiles between them** (Dylan,
     * 2026-09-27) — measured against the BACKING RECTANGLE, which is the visible edge, and which
     * sits inset from the box by the line's own end trim.
     *
     * The box wraps its rows exactly, so the padding around them is the only thing standing between
     * the top and bottom tiles and the end of the panel; a hand-picked figure was most of a gap
     * short of the one the rows keep between themselves. Asserted as a RELATIONSHIP, since the gap
     * is specified in device pixels and so is a different dp figure on every screen.
     */
    @Test
    fun editMode_aGroupsPanelGivesItsOutermostTilesTheSameAirAsTheOnesBetweenThem() {
        setScreenRevealing(TileReveal.FOCUSED_GROUP)
        composeRule.onNodeWithTag("simple-group:FACE").performClick()
        composeRule.waitForIdle()

        fun tile(key: String) = composeRule
            .onNodeWithTag("cell:FACE:BUTTON_DIAMOND:$key:0", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val rows = listOf("button_y", "button_x", "button_b", "button_a")
            .map { tile(it) }.sortedBy { it.top }
        val backing = composeRule
            .onNodeWithTag(groupBackingTestTag(RemapSimpleGroup.FACE), useUnmergedTree = true)
            .fetchSemanticsNode()
        val panelTop = backing.positionInRoot.y
        val panelBottom = panelTop + backing.size.height

        val between = rows[1].top - rows[0].bottom
        val above = rows.first().top - panelTop
        val below = panelBottom - rows.last().bottom
        assert(kotlin.math.abs(above - between) <= 1f) {
            "air above the top tile should be the row gap: $above vs $between"
        }
        assert(kotlin.math.abs(below - between) <= 1f) {
            "air below the bottom tile should be the row gap: $below vs $between"
        }
    }

    /**
     * **Walking into a group lands on the tile nearest where the cursor came from** (Dylan,
     * 2026-09-27) — not on that group's first tile, which is a jump across the whole box whenever
     * you arrive from below or from the far side of a row.
     *
     * The cursor enters from the DPAD's TOP row, and the shoulder group is directly above it, so
     * the nearest tile in it is the one on its BOTTOM row — the opposite end from where a seat used
     * to land. Driven by a tap on the box, which is the same path the d-pad takes (both seat the
     * cursor in the group and then open it).
     */
    @Test
    fun walkingIntoAGroup_seatsTheCursorOnTheTileNearestWhereItCameFrom() {
        setScreenRevealing(TileReveal.FOCUSED_GROUP)
        composeRule.onNodeWithTag("simple-group:DPAD").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cell:DPAD:DPAD:dpad_up:0").requestFocus()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("simple-group:LEFT_SHOULDER").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("cell:LEFT_SHOULDER:LEFT_BUMPER:click:0", useUnmergedTree = true)
            .assertIsFocused()
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
     * It is the case the two ends cannot describe between them. The column is as wide as whichever
     * of the two is open, so it leaves and arrives at the very same width — while the boxes
     * themselves cross in the middle at about HALF of it, since one is widening as the other
     * narrows. Taken as the live max alone the column would dip to that crossing and back, and a
     * column that dips is the whole grid moving one way and then the other. Hence the endpoint
     * floor in the stage's layout — and hence this, which is what would catch its absence.
     */
    @Test
    fun switchingGroups_movesEveryGroupInOneDirection_withoutShaking() =
        assertMorphDoesNotShake(
            reveal = TileReveal.FOCUSED_GROUP,
            // Two fat groups in the SAME column, which is the shape that needs the floor: the
            // column is as wide as whichever of them is open, so it leaves and arrives at the very
            // same width while the two boxes cross in the middle at half of theirs. And fat
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

    /**
     * **A carried command's write lands while every group is still revealed** (Dylan, 2026-09-27:
     * "the screen/camera movement is now instantaneous and has no animation/easing when moving a
     * tile to a new input group that adjusts the width/camera").
     *
     * The write comes back a frame or several after the carry ends, and the group it lands in
     * changes shape when it does. Framing that change is [ReframePlan]'s travel — the same one that
     * frames a group when a command is ADDED to it, and the one
     * `addingACommand_framesTheWholeGroup_notJustTheNewTile` pins. But it stands down for the length
     * of any reveal travel, on purpose: mid-morph every box is changing width and it would fight
     * the morph for the view. So if the reveal collapses the instant the tile is dropped, the write
     * arrives with nobody holding the view and its shape change is applied in a single frame.
     *
     * The reveal therefore waits for the command to be SEEN. This drives the write late on purpose
     * — which is what a repository round trip is — and checks the reveal is still open when it
     * lands, then closes around the group the command went to.
     */
    @Test
    fun carryingACommandIntoAnotherGroup_keepsEveryGroupRevealed_untilTheWriteLands() {
        composeRule.mainClock.autoAdvance = false
        var write: (() -> Unit)? = null
        val live = androidx.compose.runtime.mutableStateOf(
            seedShapedConfig().withPressStack(InputSource.DPAD, 7000L),
        )
        composeRule.setContent {
            MaterialTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalTileReveal provides TileReveal.FOCUSED_GROUP,
                ) {
                    Surface(modifier = androidx.compose.ui.Modifier.size(1200.dp, 1600.dp)) {
                        RemapControlsScreen(
                            config = live.value,
                            onOpenInputEditor = { _, _, _ -> },
                            // A repository round trip: the config changes LATER, not on this call.
                            onMoveRowCommand = { bindingId, _, toKey, _ ->
                                write = {
                                    live.value = live.value
                                        .withCommandMovedTo(bindingId, InputSource.LEFT_TRIGGER, toKey)
                                }
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
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        settle()
        composeRule.onNodeWithTag("simple-group:DPAD").performClick()
        settle()
        val tile = "cell:DPAD:DPAD:dpad_up:0"
        composeRule.onNodeWithTag(tile).requestFocus()
        settle()
        // Lift it, steer up into the shoulder group, and let go.
        composeRule.onNodeWithTag(tile).performKeyInput { keyDown(Key.ButtonA) }
        settle()
        composeRule.onNodeWithTag("cell:LEFT_SHOULDER:LEFT_TRIGGER:full_pull:0").assertExists()
        composeRule.onNodeWithTag(tile).performKeyInput {
            keyDown(Key.DirectionUp)
            keyUp(Key.DirectionUp)
        }
        settle()
        composeRule.onNodeWithTag(tile).performKeyInput { keyUp(Key.ButtonA) }
        // A full morph's worth of frames with the write still outstanding — long enough that a
        // reveal collapsing on its own would be over and done with.
        composeRule.mainClock.advanceTimeBy(320L)
        composeRule.waitForIdle()
        assert(write != null) { "the carry never committed — nothing to measure" }
        // Still open, so the write has something holding the view when it lands.
        composeRule.onNodeWithTag("cell:LEFT_SHOULDER:LEFT_TRIGGER:full_pull:0").assertExists()
        composeRule.onNodeWithTag(tile).assertExists()

        composeRule.runOnUiThread { write!!.invoke() }
        settle()

        // Landed: the reveal has closed around the group the command went to.
        composeRule.onNodeWithTag("cell:LEFT_SHOULDER:LEFT_TRIGGER:full_pull:0").assertExists()
        composeRule.onAllNodesWithTag(tile).assertCountEquals(0)
        composeRule.mainClock.autoAdvance = true
    }

    /** Move the command with this binding id onto [toSource]/[toKey], the way the repository does:
     *  it leaves the row it was on, and keeps its identity so the cursor can follow it. */
    private fun ControllerConfig.withCommandMovedTo(
        bindingId: Long,
        toSource: InputSource,
        toKey: String,
    ): ControllerConfig {
        var carried: ActivatorGraph? = null
        val stripped = actionSets.map { set ->
            set.copy(
                preset = set.preset.map { entry ->
                    entry.copy(
                        group = entry.group.copy(
                            inputs = entry.group.inputs.map { input ->
                                val (out, keep) = input.activators.partition { activator ->
                                    activator.bindings.any { it.id == bindingId }
                                }
                                if (out.isEmpty()) {
                                    input
                                } else {
                                    carried = out.first()
                                    input.copy(activators = keep)
                                }
                            },
                        ),
                    )
                },
            )
        }
        val moved = carried ?: return this
        return copy(
            actionSets = stripped.map { set ->
                set.copy(
                    preset = set.preset.map { entry ->
                        if (entry.inputSource != toSource) return@map entry
                        entry.copy(
                            group = entry.group.copy(
                                inputs = entry.group.inputs.map { input ->
                                    if (input.input.inputKey != toKey) {
                                        input
                                    } else {
                                        input.copy(
                                            activators = input.activators + moved.copy(
                                                activator = moved.activator.copy(
                                                    groupInputId = input.input.id,
                                                    orderIndex = input.activators.size,
                                                ),
                                            ),
                                        )
                                    }
                                },
                            ),
                        )
                    },
                )
            },
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

    /**
     * The screen as the pan tests need it. The WINDOW's shape picks the pan target (3 tiles, 2 on a
     * square window); the class's `w1280dp-h800dp` qualifier makes it three, which is what these
     * tests are written against. [aspect] stands in for the screen frame's canvas (see
     * `LocalScreenAspect`), which is what the compact 1:1 screen changes.
     */
    private fun setPanScreen(
        reveal: TileReveal = TileReveal.FOCUSED_GROUP,
        aspect: Float? = null,
        width: Int = 1200,
        height: Int = 1600,
        config: ControllerConfig = seedShapedConfig(),
    ) {
        composeRule.setContent {
            MaterialTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalTileReveal provides reveal,
                    com.mappo.ui.screen.home.LocalScreenAspect provides aspect,
                ) {
                    Surface(modifier = androidx.compose.ui.Modifier.size(width.dp, height.dp)) {
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
     * **Opening a group pans the view even when the group needs no framing at all** (Dylan,
     * 2026-09-27: groups "whose inputs all only have one assigned tile (and subsequently one empty
     * tile) ... don't shift the camera at all when activated").
     *
     * Two reasons it did nothing, and the pan-on-open designation answers both: framing is a
     * SCROLL, so a grid that fits the window has no pan to give; and a flank box widening on a
     * fitting grid eats its own leading pad, so the controller and everything past it stay exactly
     * put. With a floor of three tiles, a two-tile group is one tile short — and that shortfall is
     * what the view moves by, out of the pad on the far side.
     *
     * Measured on the CONTROLLER, which is the one element that says the picture moved rather than
     * that a box grew, and against the row's own tile PITCH, so "an additional tile's width" is
     * asserted as exactly that and not as a number.
     */
    @Test
    fun openingAGroup_pansByTheDesignatedRoom_evenWhenItNeedsNoFraming() {
        setPanScreen()
        fun controller() = composeRule
            .onNodeWithTag("controller-image", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.x
        fun settle() {
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(1200)
            composeRule.waitForIdle()
        }

        val rest = controller()
        // A LEFT-flank group: its rows run off the left edge, so looking at it moves the content
        // RIGHT.
        composeRule.onNodeWithTag("simple-group:LEFT_UTILITY").performClick()
        settle()
        val onLeft = controller()
        // One tile and its gap — the row's own pitch, taken from two adjacent tiles of the group
        // that is now open (its slots run leftward, being an end-anchored row).
        fun tileLeft(slot: Int) = composeRule
            .onNodeWithTag("cell:LEFT_UTILITY:SWITCH_SELECT:click:$slot", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.x
        val pitch = kotlin.math.abs(tileLeft(0) - tileLeft(1))
        assert(kotlin.math.abs((onLeft - rest) - pitch) <= 1f) {
            "a two-tile group should pan by one tile: ${onLeft - rest} vs $pitch"
        }

        // And the mirror image on the other flank, by the same amount the other way.
        composeRule.onNodeWithTag("simple-group:FACE").performClick()
        settle()
        val onRight = controller()
        assert(kotlin.math.abs((rest - onRight) - pitch) <= 1f) {
            "the right flank should pan by the same tile, the other way: ${rest - onRight} vs $pitch"
        }
    }

    /**
     * **Walking into a group in the other column pans to it even with every group revealed**
     * (Dylan, 2026-09-27: "we don't induce an input group pan at all if 'Show tiles for Every
     * Group' is configured and the user navigates from an input group in one column to an input
     * group in the other column").
     *
     * On [TileReveal.ALL_GROUPS] a hop changes NO group's shape, so there was no travel to run and
     * the view only moved as far as the focused tile's own bring-into-view dragged it. The reveal
     * now travels for the camera alone, and the pan follows the cursor's group.
     */
    @Test
    fun walkingIntoTheOtherColumn_pansToThatGroup_withEveryGroupRevealed() {
        setPanScreen(reveal = TileReveal.ALL_GROUPS)
        fun controller() = composeRule
            .onNodeWithTag("controller-image", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.x
        fun settle() {
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(1200)
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("simple-group:FACE").performClick()
        settle()
        val onRight = controller()
        // Every group is tiles, so there is no box left to tap: the cursor moves between groups by
        // landing on a TILE, which is what a d-pad hop across the grid does.
        composeRule.onNodeWithTag("cell:LEFT_UTILITY:SWITCH_SELECT:click:0", useUnmergedTree = true)
            .requestFocus()
        settle()
        assert(controller() > onRight) {
            "walking to the left flank should have panned back: $onRight -> ${controller()}"
        }
    }

    /**
     * **The target is shown even where the content runs out** (Dylan, 2026-09-28: with every group
     * revealed, walking into a right-column group "only pans to the right enough to show the assigned
     * input and its empty tile, instead of the 3 tile slots"; and rule 1 of the rebuild: "If the
     * group has fewer tiles than our target ... pan to the target number of tiles instead").
     *
     * On a grid that overruns the window, the room past the outermost group is part of the layout
     * built around the camera — on the focused group's own side, never the far one (see
     * [StageCamera]). From the group's glyph edge to the window's edge there is room for THREE tiles:
     * its own two-tile box plus one more tile's pitch.
     */
    @Test
    fun walkingIntoAGroupAtTheScrollEnd_stillShowsTheTargetRoom() {
        // The grid must genuinely OVERRUN the window: one fat column does it.
        setPanScreen(
            reveal = TileReveal.ALL_GROUPS,
            width = 900,
            config = seedShapedConfig().withPressStack(InputSource.DPAD, 7000L),
        )
        composeRule.onNodeWithTag("simple-group:DPAD").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_y:0", useUnmergedTree = true)
            .requestFocus()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1200)
        composeRule.waitForIdle()

        val window = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val box = composeRule.onNodeWithTag("simple-group:FACE", useUnmergedTree = true)
            .fetchSemanticsNode().let { it.positionInRoot.x to it.size.width }
        fun tileLeft(slot: Int) = composeRule
            .onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_y:$slot", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.x
        val pitch = kotlin.math.abs(tileLeft(1) - tileLeft(0))
        // A right-flank group's glyph is at its LEFT edge and its tiles run rightward.
        val room = window.right - box.first
        assert(room >= box.second + pitch - 1f) {
            "only ${room}px from the group's glyph to the window edge; three tiles need " +
                "${box.second + pitch} (its two-tile box ${box.second} plus one pitch $pitch)"
        }
    }

    /**
     * **Walking ALONG a column does not move the view** (Dylan, 2026-09-28) — which is the point of
     * the pan being a designation rather than a floor measured against each group's own box.
     *
     * Every group in a column has its glyph on the column's inner edge, so they all share the region
     * the setting designates, and the room the grid already gives that region is the COLUMN's width.
     * Measured against each box instead, a NARROW group in a wide column asked for a tile more than
     * the wide group beside it: "navigating to another left column input group that contains fewer
     * tiles actually extends that input group's camera pan even further than the original longest
     * input group row, almost like it's additive". Its mirror on the other flank was the view sitting
     * at the widest group's position and never coming back.
     */
    @Test
    fun walkingAlongAColumn_leavesTheViewWhereItIs() {
        // One FAT group in the left column and the narrow ones beside it — the shape the additive
        // pan showed up on.
        setPanScreen(
            reveal = TileReveal.ALL_GROUPS,
            config = seedShapedConfig().withPressStack(InputSource.DPAD, 7000L),
        )
        fun controller() = composeRule
            .onNodeWithTag("controller-image", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.x
        fun settle() {
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(1200)
            composeRule.waitForIdle()
        }
        fun focusTile(tag: String) {
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).requestFocus()
            settle()
        }

        composeRule.onNodeWithTag("simple-group:DPAD").performClick()
        settle()
        val onTheFatOne = controller()
        // ...and along to the two-tile groups in the SAME column.
        focusTile("cell:LEFT_UTILITY:SWITCH_SELECT:click:0")
        assert(controller() == onTheFatOne) {
            "walking to a narrower group in the same column moved the view: " +
                "$onTheFatOne -> ${controller()}"
        }
        focusTile("cell:LEFT_SHOULDER:LEFT_TRIGGER:full_pull:0")
        assert(controller() == onTheFatOne) {
            "and again: $onTheFatOne -> ${controller()}"
        }
    }

    /**
     * **The pan never flashes** (Dylan, 2026-09-28: "a strange jitter every time I open an input
     * group or navigate between input groups - almost like the input groups are flashing inward
     * towards the center column").
     *
     * Activating a group writes the new focus in the CLICK; the travel that answers it is reset by
     * an effect a frame later. Anything that recomputes a placement from the live focus therefore
     * gets one frame of "new group, old travel" — and for the pan-on-open designation that was the
     * whole grid jumping to its unbiased, controller-centred position and back (measured: 94px, one
     * tile). The pan is captured with the travel's other endpoints now, so a frame that has not
     * re-planned cannot move it at all.
     *
     * Asserted frame by frame on the CONTROLLER, which is the element that says the picture moved
     * rather than that a box grew: across a whole hop it may only ever travel ONE way.
     */
    @Test
    fun switchingGroups_neverFlashesTheViewBackToTheRestingPosition() {
        composeRule.mainClock.autoAdvance = false
        setPanScreen(
            // A fat group in the LEFT column: the grid overruns the window, so there is both a pan
            // to make and a scroll plan making it.
            config = seedShapedConfig().withPressStack(InputSource.DPAD, 7000L),
            width = 900,
        )
        composeRule.mainClock.advanceTimeBy(800)
        fun controller() = composeRule
            .onNodeWithTag("controller-image", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.x

        fun frames(count: Int): List<Float> = buildList {
            repeat(count) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
                add(controller())
            }
        }
        fun assertOneWay(label: String, series: List<Float>) {
            val net = series.last() - series.first()
            assert(net != 0f) { "$label should have panned at all: $series" }
            // **The first frame after the gesture has not moved.** The travel is reset to 0 before
            // it runs, so anything already at its destination on that frame is not travelling — it
            // is flashing. This is the assertion the flash was caught by; the monotonicity below
            // would miss a jump that then STAYS jumped.
            assert(kotlin.math.abs(series[1] - series[0]) <= 2f) {
                "$label jumped on its first frame: ${series[0]} -> ${series[1]}\n$series"
            }
            series.zipWithNext { a, b ->
                val step = b - a
                assert(step == 0f || (step > 0f) == (net > 0f)) {
                    "$label reversed: $a -> $b against a net of $net\n$series"
                }
            }
        }

        val rest = controller()
        composeRule.onNodeWithTag("simple-group:FACE").performClick()
        assertOneWay("opening a group", listOf(rest) + frames(24))
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()

        val opened = controller()
        composeRule.onNodeWithTag("simple-group:DPAD").performClick()
        assertOneWay("hopping to the other column", listOf(opened) + frames(24))
        composeRule.mainClock.autoAdvance = true
    }

    /**
     * **Opening a group shows the END of it** (Dylan, 2026-09-28) — its last tile and its "+", even
     * where that is further than the pan target. The target is only a floor for a narrow group.
     *
     * DPAD here holds three commands a row — four tiles with the "+", one past the three-tile
     * target — on a window narrow enough that the grid overruns it.
     */
    @Test
    fun openingAWideGroup_bringsItsWholeRowIntoView() {
        setPanScreen(
            width = 900,
            config = seedShapedConfig().withPressStack(InputSource.DPAD, 7000L),
        )
        composeRule.onNodeWithTag("simple-group:DPAD").performClick()
        settlePan()

        val window = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val boxLeft = composeRule.onNodeWithTag("simple-group:DPAD", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.x
        // A left-flank group's tiles run outward to the LEFT, so its far end is the box's left edge.
        assert(boxLeft >= window.left - 1f) {
            "the group's outer end is off screen at $boxLeft (window starts at ${window.left})"
        }
    }

    /**
     * **Crossing to the other column lands on the TARGET, not on the whole of the group crossed
     * into** (Dylan, 2026-09-28: opening a wide group on one side and walking to a wide group on the
     * other made "the resultant camera pan ... massive and jarring").
     *
     * Asserted as the invariant: from the group's glyph to the window's edge there is room for
     * exactly three tiles — its four-tile box less one pitch — and so its outer end is off screen.
     */
    @Test
    fun crossingColumns_fromAWideGroupToAWideGroup_landsOnTheTarget() =
        assertCrossingLandsOnTarget(aspect = null, targetTiles = 3, reveal = TileReveal.FOCUSED_GROUP)

    /**
     * **The compact 1:1 screen targets TWO tiles** (Dylan, 2026-09-28: "when the screen size is
     * reduced to 1:1, we're still panning to a 3 tile distance"). The compact screen is a square
     * drawn inside the full display, so the window never changes shape; the target has to come from
     * the frame's canvas.
     *
     * With every group revealed, so the far column is wide too: with it at rest the scroll range
     * runs out before a two-tile view is reachable, and the view stops at the end of the content
     * rather than scroll out into nothing — which is right, and not what this is about.
     */
    @Test
    fun crossingColumns_onTheSquareScreen_landsOnTheTwoTileTarget() =
        assertCrossingLandsOnTarget(aspect = 1f, targetTiles = 2, reveal = TileReveal.ALL_GROUPS)

    private fun assertCrossingLandsOnTarget(aspect: Float?, targetTiles: Int, reveal: TileReveal) {
        setPanScreen(
            aspect = aspect,
            reveal = reveal,
            // Narrow enough that the content genuinely overruns it: where everything fits, every
            // group is simply on screen and there is no target to land on.
            width = 700,
            config = seedShapedConfig()
                .withPressStack(InputSource.DPAD, 7000L)
                .withPressStack(InputSource.BUTTON_DIAMOND, 8000L),
        )
        composeRule.onNodeWithTag("simple-group:FACE").performClick()
        settlePan()
        if (reveal == TileReveal.ALL_GROUPS) {
            // Every group is tiles: the cursor crosses by landing on one, as the d-pad does.
            composeRule.onNodeWithTag("cell:DPAD:DPAD:dpad_up:0", useUnmergedTree = true).requestFocus()
        } else {
            // Walked into, as the d-pad does — the box may well be off screen, out of a tap's reach.
            composeRule.onNodeWithTag("simple-group:DPAD").requestFocus()
        }
        settlePan()

        val window = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val box = composeRule.onNodeWithTag("simple-group:DPAD", useUnmergedTree = true)
            .fetchSemanticsNode().let { it.positionInRoot.x to it.size.width }
        fun tileLeft(slot: Int) = composeRule
            .onNodeWithTag("cell:DPAD:DPAD:dpad_up:$slot", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.x
        val pitch = kotlin.math.abs(tileLeft(1) - tileLeft(0))
        val glyph = box.first + box.second
        val room = glyph - window.left
        // The box holds four tiles; the target is that less the tiles it leaves off screen.
        val target = box.second - pitch * (4 - targetTiles)
        // The region is framed to the grid's own margin, so up to that margin more is fine.
        assert(room >= target - 2f && room <= target + GridEdgeSlack) {
            "${room}px from the glyph to the window edge; the $targetTiles-tile target is $target"
        }
    }

    /**
     * **Walking along a column never pulls the view back in**, with one group revealed at a time
     * too (Dylan, 2026-09-28). Opening the wide group reaches to its end; walking to a narrow group
     * in the same column keeps that reach, even though the wide group has closed behind the cursor.
     *
     * Measured at the column's INNER edge — the glyph edge every group in the column shares — which
     * may only stay put or move further in.
     */
    @Test
    fun walkingAlongAColumn_oneGroupRevealed_keepsTheWideGroupsReach() {
        setPanScreen(
            width = 900,
            config = seedShapedConfig().withPressStack(InputSource.DPAD, 7000L),
        )
        fun innerEdge(group: String) = composeRule
            .onNodeWithTag("simple-group:$group", useUnmergedTree = true)
            .fetchSemanticsNode().let { it.positionInRoot.x + it.size.width }

        composeRule.onNodeWithTag("simple-group:DPAD").performClick()
        settlePan()
        val onTheWideOne = innerEdge("DPAD")
        composeRule.onNodeWithTag("simple-group:LEFT_UTILITY").performClick()
        settlePan()
        assert(innerEdge("LEFT_UTILITY") >= onTheWideOne - 1f) {
            "walking to a narrow group pulled the view back in: $onTheWideOne -> " +
                innerEdge("LEFT_UTILITY")
        }
    }

    private fun settlePan() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1200)
        composeRule.waitForIdle()
    }

    /**
     * **The camera runs the SAME travel as the tiles — starting, easing and landing with them**
     * (Dylan, 2026-09-28: "a strange consistent starting lag" on opening a group, a cross-column hop
     * whose "ease out has been cut short", and leaving edit mode where the camera "completes more
     * quickly and more abruptly" than the labels).
     *
     * All three were one bug: the camera interpolated a SCROLL and clamped it into the grid's live
     * scroll range, which is zero for as long as the grid still fits the window. Opening a group sat
     * the camera still for a third of the travel; a hop or an exit hit the range collapsing early and
     * stopped dead. Measured frame by frame, on the controller (the camera) against the opening or
     * closing box's width (the morph), in both reveal modes.
     */
    @Test
    fun theCamera_runsTheWholeMorph_oneGroupRevealed() = assertCameraFollowsTheMorph(TileReveal.FOCUSED_GROUP)

    @Test
    fun theCamera_runsTheWholeMorph_everyGroupRevealed() = assertCameraFollowsTheMorph(TileReveal.ALL_GROUPS)

    private fun assertCameraFollowsTheMorph(reveal: TileReveal) {
        var back: androidx.activity.OnBackPressedDispatcher? = null
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            back = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
                ?.onBackPressedDispatcher
            MaterialTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalTileReveal provides reveal) {
                    Surface(modifier = androidx.compose.ui.Modifier.size(900.dp, 1600.dp)) {
                        RemapControlsScreen(
                            // Both flanks fat, so the grid overruns the window mid-travel — the
                            // shape the clamp bit on.
                            config = seedShapedConfig()
                                .withPressStack(InputSource.DPAD, 7000L)
                                .withPressStack(InputSource.BUTTON_DIAMOND, 8000L),
                            onOpenInputEditor = { _, _, _ -> },
                            onBack = {},
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        fun camera() = composeRule.onNodeWithTag("controller-image", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.x
        fun width(group: String) = composeRule
            .onNodeWithTag("simple-group:$group", useUnmergedTree = true)
            .fetchSemanticsNode().size.width.toFloat()
        /** The frames on which [series] moved at all, first to last. */
        fun moving(series: List<Float>): IntRange? {
            val moves = series.zipWithNext().mapIndexedNotNull { index, (a, b) ->
                if (kotlin.math.abs(b - a) > 0.5f) index + 1 else null
            }
            return if (moves.isEmpty()) null else moves.first()..moves.last()
        }
        fun travel(label: String, morphing: String?, act: () -> Unit) {
            val cameras = mutableListOf(camera())
            val widths = mutableListOf(morphing?.let(::width) ?: 0f)
            act()
            repeat(30) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
                cameras += camera()
                widths += morphing?.let(::width) ?: 0f
            }
            val cam = moving(cameras)
            assert(cam != null) { "$label: the camera never moved\n$cameras" }
            // One way only: a camera that stalls and resumes is fine, one that turns back is not.
            val net = cameras.last() - cameras.first()
            cameras.zipWithNext { a, b ->
                assert(b == a || (b > a) == (net > 0f)) { "$label: the camera reversed\n$cameras" }
            }
            // No stall mid-flight either: once it starts, it moves on every frame until it lands,
            // bar the last pixel or two the ease-out rounds away.
            val flight = cameras.subList(cam!!.first, cam.last + 1)
            val still = flight.zipWithNext().count { (a, b) -> kotlin.math.abs(b - a) <= 0.5f }
            assert(still <= 2) { "$label: the camera stalled for $still frames mid-flight\n$cameras" }
            val morph = morphing?.let { moving(widths) }
            if (morph != null) {
                assert(cam.first <= morph.first + 1) {
                    "$label: the camera started on frame ${cam.first}, the morph on ${morph.first}" +
                        "\n$cameras\n$widths"
                }
                assert(cam.last >= morph.last - 2) {
                    "$label: the camera landed on frame ${cam.last}, the morph on ${morph.last}" +
                        "\n$cameras\n$widths"
                }
            }
            composeRule.mainClock.advanceTimeBy(800)
            composeRule.waitForIdle()
        }

        travel("opening a group", "FACE") {
            composeRule.onNodeWithTag("simple-group:FACE").performClick()
        }
        travel("hopping to the other column", "DPAD".takeIf { reveal == TileReveal.FOCUSED_GROUP }) {
            if (reveal == TileReveal.FOCUSED_GROUP) {
                composeRule.onNodeWithTag("simple-group:DPAD").performClick()
            } else {
                composeRule.onNodeWithTag("cell:DPAD:DPAD:dpad_up:0", useUnmergedTree = true)
                    .requestFocus()
            }
        }
        travel("leaving edit mode", "DPAD") {
            composeRule.runOnUiThread { back!!.onBackPressed() }
        }
        composeRule.mainClock.autoAdvance = true
    }

    /**
     * **Walking down a column HOLDS the view, on either side** (Dylan, 2026-09-28: the pan "will
     * also be maintained if the user navigates up and down to input groups still within the original
     * group's same column; this is important").
     *
     * Entered from a narrow group and walked onto a wide one, the camera does not move: the column's
     * inner edge — every group's glyph in it — stays exactly where it was on screen, so the wide
     * group shows the room the narrow one was framed with and is NOT dragged out to its full width.
     * Asserted as mirror images: a left-column group grows at the content's START, which is what
     * once made the left side alone reveal every wide group whole.
     */
    @Test
    fun walkingDownAColumnOntoAWideGroup_leftColumn_holdsTheView() =
        assertSameColumnHopHoldsTheView(from = "LEFT_SHOULDER", to = "DPAD", onLeft = true)

    @Test
    fun walkingDownAColumnOntoAWideGroup_rightColumn_holdsTheView() =
        assertSameColumnHopHoldsTheView(from = "RIGHT_SHOULDER", to = "FACE", onLeft = false)

    private fun assertSameColumnHopHoldsTheView(from: String, to: String, onLeft: Boolean) {
        setPanScreen(
            // Narrow enough that the content genuinely overruns it once the wide group is open.
            width = 700,
            config = seedShapedConfig()
                .withPressStack(InputSource.DPAD, 7000L)
                .withPressStack(InputSource.BUTTON_DIAMOND, 8000L),
        )
        val window = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        fun box(group: String) = composeRule.onNodeWithTag("simple-group:$group", useUnmergedTree = true)
            .fetchSemanticsNode().let { it.positionInRoot.x to it.size.width }
        /** The group's glyph — its INNER edge, the column's — in the window. */
        fun glyph(group: String) = box(group).let { (x, w) -> if (onLeft) x + w else x }
        composeRule.onNodeWithTag("simple-group:$from").performClick()
        settlePan()
        val before = glyph(from)
        // Walked into, as the d-pad does — the box may be off screen, out of a tap's reach.
        composeRule.onNodeWithTag("simple-group:$to").requestFocus()
        settlePan()
        relayout()

        assert(kotlin.math.abs(glyph(to) - before) <= 1f) {
            "$to: the column's inner edge moved from $before to ${glyph(to)} on a same-column hop"
        }
        val (x, w) = box(to)
        val wholeOnScreen = if (onLeft) x >= window.left - 1f else x + w <= window.right + 1f
        assert(!wholeOnScreen) { "$to was pulled out to its full width on a same-column hop" }
    }

    /**
     * **The group the cursor walked into survives the command picker** (Dylan, 2026-09-28: "when I
     * open one input group, then navigate to a different input group and add a brand new tile,
     * focus then returns to the initial input group that was used to enter edit mode").
     *
     * Adding a command goes out to the full-screen picker and back, which rebuilds the screen. The
     * group edit mode was ENTERED from was saved and the cursor's group was not, so with one group
     * revealed the view came back showing the entry group — and the group the command had just been
     * added to had no tiles for the cursor to land on. Emulated here as the rebuild itself.
     */
    @Test
    fun theCursorsGroup_survivesTheTripToTheCommandPicker() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(composeRule)
        restoration.setContent {
            MaterialTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalTileReveal provides TileReveal.FOCUSED_GROUP,
                ) {
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
        }
        settlePan()
        composeRule.onNodeWithTag("simple-group:DPAD").performClick()
        settlePan()
        // WALKED into, not tapped: a tap on a box re-enters edit mode on it, which was always
        // saved. The d-pad only moves the cursor, and the cursor was what got lost.
        composeRule.onNodeWithTag("simple-group:FACE").requestFocus()
        settlePan()
        composeRule.onAllNodesWithTag("cell:FACE:BUTTON_DIAMOND:button_a:0", useUnmergedTree = true)
            .assertCountEquals(1)

        restoration.emulateSavedInstanceStateRestore()
        settlePan()

        composeRule.onAllNodesWithTag("cell:FACE:BUTTON_DIAMOND:button_a:0", useUnmergedTree = true)
            .assertCountEquals(1)
        // Neither the group edit mode was entered from, nor the top-left box a fresh screen seats.
        composeRule.onAllNodesWithTag("cell:DPAD:DPAD:dpad_up:0", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("cell:LEFT_SHOULDER:LEFT_TRIGGER:full_pull:0", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    /** Resizes the live screen by a dp and back — see [relayout]. */
    private val nudge = androidx.compose.runtime.mutableStateOf(0)

    /**
     * **Lay the stage out again from its STATE**, as the next unrelated recomposition on the device
     * would. A travel that hands off to a scroll the scroller already has invalidates nothing, so the
     * last frame it drew can stay on screen looking right while the state behind it says otherwise
     * — and the device shows the truth the moment anything else re-lays the view out.
     */
    private fun relayout() {
        nudge.value = 1
        settlePan()
        nudge.value = 0
        settlePan()
    }

    /** A screen whose config the test can change under it — a paste or a clear, as it lands. */
    private fun setLiveScreen(
        live: androidx.compose.runtime.MutableState<ControllerConfig>,
        reveal: TileReveal = TileReveal.FOCUSED_GROUP,
        // LANDSCAPE, like the device: the controller's column is sized from the height, so a tall
        // surface overruns the window and pays the pan in scroll range, never exercising the DRAWN
        // pan that a grid which fits uses. Inside the class's w1280dp-h800dp window, or it is
        // clamped to it and [relayout]'s nudge changes nothing.
        width: Int = 1200,
        height: Int = 700,
    ) {
        composeRule.setContent {
            MaterialTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalTileReveal provides reveal) {
                    Surface(
                        modifier = androidx.compose.ui.Modifier.size((width + nudge.value).dp, height.dp),
                    ) {
                        RemapControlsScreen(
                            config = live.value,
                            onOpenInputEditor = { _, _, _ -> },
                            onBack = {},
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        settlePan()
    }

    private fun controllerX() = composeRule
        .onNodeWithTag("controller-image", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.x

    private fun utilityPitch(): Float {
        fun left(slot: Int) = composeRule
            .onNodeWithTag("cell:LEFT_UTILITY:SWITCH_SELECT:click:$slot", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.x
        return kotlin.math.abs(left(1) - left(0))
    }

    /**
     * **A pasted tile re-plans the pan for the group's NEW width** (Dylan, 2026-09-28: pasting into
     * a group whose rows each hold one command "pans a whole tile beyond the newly resultant empty
     * tile" — on a screen larger than 1:1 only).
     *
     * On a grid that fits, the pan is DRAWN, and it was only ever planned by a reveal or a collapse.
     * Opening the two-tile group drew it one tile out (three-tile target); the paste made the group
     * three tiles wide, which needs no drawn pan at all — but the one-tile pan stayed. Now the
     * re-frame re-plans it, so the controller comes back to where the resting grid centres it.
     */
    @Test
    fun pastingATile_rePlansThePan_forTheGroupsNewWidth_oneGroupRevealed() =
        assertPasteRePlansThePan(TileReveal.FOCUSED_GROUP)

    @Test
    fun pastingATile_rePlansThePan_forTheGroupsNewWidth_everyGroupRevealed() =
        assertPasteRePlansThePan(TileReveal.ALL_GROUPS)

    private fun assertPasteRePlansThePan(reveal: TileReveal) {
        val live = androidx.compose.runtime.mutableStateOf(seedShapedConfig())
        setLiveScreen(live, reveal)
        assertNothingToScroll("the fixture should fit the window, so the pan is DRAWN")
        val rest = controllerX()
        composeRule.onNodeWithTag("simple-group:LEFT_UTILITY").performClick()
        settlePan()
        val pitch = utilityPitch()
        assert(kotlin.math.abs(controllerX() - rest - pitch) <= 1f) {
            "opening the two-tile group should draw the view one tile out: ${controllerX() - rest} vs $pitch"
        }

        live.value = seedShapedConfig().withTwoCommands(InputSource.SWITCH_SELECT, "click", 800L)
        settlePan()
        relayout()
        assert(kotlin.math.abs(controllerX() - rest) <= 1f) {
            "a three-tile group needs no drawn pan, but the view sits ${controllerX() - rest}px out"
        }
    }

    /**
     * **A cleared tile never leaves the view narrower than the target** (Dylan, 2026-09-28:
     * removing a tile "truncates the view so it's now narrower than it ever was upon first
     * opening").
     *
     * The mirror of the paste: a three-tile group opened with no drawn pan, then cleared back to two
     * tiles, has to be drawn one tile out — exactly as if the two-tile group had been opened fresh.
     */
    @Test
    fun clearingATile_neverLeavesTheViewShortOfTheTarget_oneGroupRevealed() =
        assertClearKeepsTheTarget(TileReveal.FOCUSED_GROUP)

    @Test
    fun clearingATile_neverLeavesTheViewShortOfTheTarget_everyGroupRevealed() =
        assertClearKeepsTheTarget(TileReveal.ALL_GROUPS)

    private fun assertClearKeepsTheTarget(reveal: TileReveal) {
        val live = androidx.compose.runtime.mutableStateOf(
            seedShapedConfig().withTwoCommands(InputSource.SWITCH_SELECT, "click", 800L),
        )
        setLiveScreen(live, reveal)
        assertNothingToScroll("the fixture should fit the window, so the pan is DRAWN")
        val rest = controllerX()
        composeRule.onNodeWithTag("simple-group:LEFT_UTILITY").performClick()
        settlePan()

        live.value = seedShapedConfig()
        settlePan()
        relayout()
        val pitch = utilityPitch()
        assert(kotlin.math.abs(controllerX() - rest - pitch) <= 1f) {
            "the two-tile group should be drawn one tile out, as when opened: " +
                "${controllerX() - rest} vs $pitch"
        }
    }

    /**
     * **Content that fits never scrolls** (Dylan, 2026-09-28: a group with two commands made "a
     * scroll appear along with a chevron + fade on the opposite screen edge", as if there were
     * content to scroll to in the other column).
     *
     * One half of the grid wider than half the window used to have its other half padded out to
     * half a window regardless, to keep the controller dead centre — making the grid wider than the
     * window with nothing in the extra. Asserted as the invariant: every group shown whole means
     * everything is on screen, so there must be nothing to scroll.
     */
    @Test
    fun aLopsidedGridThatFits_hasNothingToScroll() {
        val live = androidx.compose.runtime.mutableStateOf(
            seedShapedConfig().withPressStack(InputSource.DPAD, 7000L),
        )
        setLiveScreen(live, width = 900, height = 1600)
        composeRule.onNodeWithTag("simple-group:DPAD").performClick()
        settlePan()

        val window = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val everyGroupOnScreen = listOf(
            "LEFT_SHOULDER", "DPAD", "LEFT_STICK", "LEFT_UTILITY",
            "RIGHT_SHOULDER", "FACE", "RIGHT_STICK", "RIGHT_UTILITY",
        ).all { group ->
            composeRule.onNodeWithTag("simple-group:$group", useUnmergedTree = true)
                .fetchSemanticsNode().let {
                    it.positionInRoot.x >= window.left - 1f &&
                        it.positionInRoot.x + it.size.width <= window.right + 1f
                }
        }
        assert(everyGroupOnScreen) { "the fixture should fit the window" }
        assertNothingToScroll("everything is on screen, yet the body can scroll")
    }

    private fun assertNothingToScroll(message: String) {
        val range = composeRule.onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
        assert(range.maxValue() == 0f) { "$message: range ${range.maxValue()}px" }
    }

    /**
     * **The scroll cues are never lit on a grid with nothing to scroll** (Dylan, 2026-09-28:
     * opening a group "causes the phantom scroll to appear on the opposite screen edge").
     *
     * The camera draws its travel as a shift even where the grid fits the window, and the fade,
     * chevron and bar read whatever shift they are handed as scroll — so a travel lit them on the
     * side it pointed, and a pixel of rounding left over at its end kept them lit. Every value the
     * cues are handed is recorded, frame by frame, through an open, a hop across the grid and a hop
     * back; with no scroll range, every one of them must be nothing.
     */
    @Test
    fun theScrollCues_stayDark_onAGridThatFits_oneGroupRevealed_640x360() =
        assertCuesStayDark(TileReveal.FOCUSED_GROUP, 640, 360)

    @Test
    fun theScrollCues_stayDark_onAGridThatFits_everyGroupRevealed_640x360() =
        assertCuesStayDark(TileReveal.ALL_GROUPS, 640, 360)

    @Test
    fun theScrollCues_stayDark_onAGridThatFits_oneGroupRevealed_700x380() =
        assertCuesStayDark(TileReveal.FOCUSED_GROUP, 700, 380)

    @Test
    fun theScrollCues_stayDark_onAGridThatFits_everyGroupRevealed_700x380() =
        assertCuesStayDark(TileReveal.ALL_GROUPS, 700, 380)

    @Test
    fun theScrollCues_stayDark_onAGridThatFits_oneGroupRevealed_800x420() =
        assertCuesStayDark(TileReveal.FOCUSED_GROUP, 800, 420)

    @Test
    fun theScrollCues_stayDark_onAGridThatFits_everyGroupRevealed_800x420() =
        assertCuesStayDark(TileReveal.ALL_GROUPS, 800, 420)

    @Test
    fun theScrollCues_stayDark_onAGridThatFits_oneGroupRevealed_1200x700() =
        assertCuesStayDark(TileReveal.FOCUSED_GROUP, 1200, 700)

    @Test
    fun theScrollCues_stayDark_onAGridThatFits_everyGroupRevealed_1200x700() =
        assertCuesStayDark(TileReveal.ALL_GROUPS, 1200, 700)

    private fun assertCuesStayDark(reveal: TileReveal, width: Int, height: Int) {
        val published = mutableListOf<Float>()
        composeRule.setContent {
            MaterialTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalTileReveal provides reveal,
                    com.mappo.ui.screen.remap.LocalBodyShiftProbe provides { published += it },
                ) {
                    Surface(modifier = androidx.compose.ui.Modifier.size(width.dp, height.dp)) {
                        RemapControlsScreen(
                            config = seedShapedConfig(),
                            onOpenInputEditor = { _, _, _ -> },
                            onBack = {},
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        settlePan()
        assertNothingToScroll("the fixture should fit the window")
        fun walkInto(group: String, cell: String) {
            if (reveal == TileReveal.FOCUSED_GROUP) {
                composeRule.onNodeWithTag("simple-group:$group").requestFocus()
            } else {
                composeRule.onNodeWithTag(cell, useUnmergedTree = true).requestFocus()
            }
            settlePan()
        }
        // The top row: on a short screen the bottom one can sit below the window, out of a tap.
        composeRule.onNodeWithTag("simple-group:LEFT_SHOULDER").performClick()
        settlePan()
        walkInto("FACE", "cell:FACE:BUTTON_DIAMOND:button_a:0")
        walkInto("LEFT_UTILITY", "cell:LEFT_UTILITY:SWITCH_SELECT:click:0")
        relayout()

        assertNothingToScroll("the travels should not have made any scroll range")
        val lit = published.filter { it != 0f }
        assert(lit.isEmpty()) {
            "the cues were handed ${lit.size} non-zero shifts with nothing to scroll: ${lit.take(12)}"
        }
    }

    /**
     * **In edit mode the far end of the scroll is the far column, never padding beyond it** (Dylan,
     * 2026-09-28: "What is this business about keeping the controller exactly centered anyway?
     * Aren't we panning to one side no matter what when editing an input group?").
     *
     * Centring the controller is the resting view's rule, and it pads an overflowing grid's short
     * side out to half a window. With one group revealed the grid is lopsided by design, so that
     * padding was scroll range past the far column with nothing in it — the phantom scroll, fade and
     * chevron "on the opposite screen edge". Scrolled all the way over, the far column has to reach
     * the window's edge.
     */
    @Test
    fun editMode_theFarEndOfTheScroll_isTheFarColumn() {
        val live = androidx.compose.runtime.mutableStateOf(
            seedShapedConfig().withPressStack(InputSource.DPAD, 7000L),
        )
        setLiveScreen(live, width = 520, height = 300)
        composeRule.onNodeWithTag("simple-group:DPAD").performClick()
        settlePan()
        val range = composeRule.onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
        assert(range.maxValue() > 0f) { "the fixture should overflow the window" }

        val scrollBy = composeRule.onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.ScrollBy].action
        composeRule.runOnUiThread { scrollBy?.invoke(10_000f, 0f) }
        settlePan()

        val window = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val farEdge = listOf("RIGHT_SHOULDER", "FACE", "RIGHT_STICK", "RIGHT_UTILITY").maxOf { group ->
            composeRule.onNodeWithTag("simple-group:$group", useUnmergedTree = true)
                .fetchSemanticsNode().let { it.positionInRoot.x + it.size.width }
        }
        assert(farEdge >= window.right - GridEdgeSlack) {
            "scrolled to the end, the far column stops ${window.right - farEdge}px short of the " +
                "window's edge — scroll range with nothing in it"
        }
    }

    /**
     * **The scroll range is the content, at every screen shape and every group** (Dylan,
     * 2026-09-28: phantom scroll whenever a group of single commands was focused, on a 4:3 screen and
     * in 1:1 alike — "your solution needs to be viable at any screen width up to at least 16:9 ...
     * empty scroll space ... serves no purpose anyway, and should be eliminated entirely").
     *
     * Asserted structurally rather than case by case: with each group focused in turn, whenever the
     * body can scroll at all, scrolling to the FAR end — the one away from the focused group — must
     * bring that side's outermost group to the window's edge. The near end may hold room past the
     * content: that is the pan's own reach, which rule 1 asks for past a narrow group and rule 2
     * keeps across a column (see [StageCamera]). At rest, both ends must be content.
     */
    @Test fun scrollRangeIsTheContent_16x9_oneGroup_default() =
        assertScrollRangeIsTheContent(TileReveal.FOCUSED_GROUP, 1200, 675, null, seedShapedConfig())

    @Test fun scrollRangeIsTheContent_16x9_oneGroup_lopsided() =
        assertScrollRangeIsTheContent(TileReveal.FOCUSED_GROUP, 1200, 675, null, seedShapedConfig().withPressStack(InputSource.DPAD, 7000L))

    @Test fun scrollRangeIsTheContent_16x9_everyGroup_default() =
        assertScrollRangeIsTheContent(TileReveal.ALL_GROUPS, 1200, 675, null, seedShapedConfig())

    @Test fun scrollRangeIsTheContent_16x9_everyGroup_lopsided() =
        assertScrollRangeIsTheContent(TileReveal.ALL_GROUPS, 1200, 675, null, seedShapedConfig().withPressStack(InputSource.DPAD, 7000L))

    @Test fun scrollRangeIsTheContent_16x10_oneGroup_default() =
        assertScrollRangeIsTheContent(TileReveal.FOCUSED_GROUP, 1120, 700, null, seedShapedConfig())

    @Test fun scrollRangeIsTheContent_16x10_oneGroup_lopsided() =
        assertScrollRangeIsTheContent(TileReveal.FOCUSED_GROUP, 1120, 700, null, seedShapedConfig().withPressStack(InputSource.DPAD, 7000L))

    @Test fun scrollRangeIsTheContent_16x10_everyGroup_default() =
        assertScrollRangeIsTheContent(TileReveal.ALL_GROUPS, 1120, 700, null, seedShapedConfig())

    @Test fun scrollRangeIsTheContent_16x10_everyGroup_lopsided() =
        assertScrollRangeIsTheContent(TileReveal.ALL_GROUPS, 1120, 700, null, seedShapedConfig().withPressStack(InputSource.DPAD, 7000L))

    @Test fun scrollRangeIsTheContent_4x3_oneGroup_default() =
        assertScrollRangeIsTheContent(TileReveal.FOCUSED_GROUP, 800, 600, null, seedShapedConfig())

    @Test fun scrollRangeIsTheContent_4x3_oneGroup_lopsided() =
        assertScrollRangeIsTheContent(TileReveal.FOCUSED_GROUP, 800, 600, null, seedShapedConfig().withPressStack(InputSource.DPAD, 7000L))

    @Test fun scrollRangeIsTheContent_4x3_everyGroup_default() =
        assertScrollRangeIsTheContent(TileReveal.ALL_GROUPS, 800, 600, null, seedShapedConfig())

    @Test fun scrollRangeIsTheContent_4x3_everyGroup_lopsided() =
        assertScrollRangeIsTheContent(TileReveal.ALL_GROUPS, 800, 600, null, seedShapedConfig().withPressStack(InputSource.DPAD, 7000L))

    @Test fun scrollRangeIsTheContent_4x3small_oneGroup_default() =
        assertScrollRangeIsTheContent(TileReveal.FOCUSED_GROUP, 640, 480, null, seedShapedConfig())

    @Test fun scrollRangeIsTheContent_4x3small_oneGroup_lopsided() =
        assertScrollRangeIsTheContent(TileReveal.FOCUSED_GROUP, 640, 480, null, seedShapedConfig().withPressStack(InputSource.DPAD, 7000L))

    @Test fun scrollRangeIsTheContent_4x3small_everyGroup_default() =
        assertScrollRangeIsTheContent(TileReveal.ALL_GROUPS, 640, 480, null, seedShapedConfig())

    @Test fun scrollRangeIsTheContent_4x3small_everyGroup_lopsided() =
        assertScrollRangeIsTheContent(TileReveal.ALL_GROUPS, 640, 480, null, seedShapedConfig().withPressStack(InputSource.DPAD, 7000L))

    @Test fun scrollRangeIsTheContent_1x1_oneGroup_default() =
        assertScrollRangeIsTheContent(TileReveal.FOCUSED_GROUP, 600, 600, 1f, seedShapedConfig())

    @Test fun scrollRangeIsTheContent_1x1_oneGroup_lopsided() =
        assertScrollRangeIsTheContent(TileReveal.FOCUSED_GROUP, 600, 600, 1f, seedShapedConfig().withPressStack(InputSource.DPAD, 7000L))

    @Test fun scrollRangeIsTheContent_1x1_everyGroup_default() =
        assertScrollRangeIsTheContent(TileReveal.ALL_GROUPS, 600, 600, 1f, seedShapedConfig())

    @Test fun scrollRangeIsTheContent_1x1_everyGroup_lopsided() =
        assertScrollRangeIsTheContent(TileReveal.ALL_GROUPS, 600, 600, 1f, seedShapedConfig().withPressStack(InputSource.DPAD, 7000L))

    @Test fun scrollRangeIsTheContent_1x1small_oneGroup_default() =
        assertScrollRangeIsTheContent(TileReveal.FOCUSED_GROUP, 480, 480, 1f, seedShapedConfig())

    @Test fun scrollRangeIsTheContent_1x1small_oneGroup_lopsided() =
        assertScrollRangeIsTheContent(TileReveal.FOCUSED_GROUP, 480, 480, 1f, seedShapedConfig().withPressStack(InputSource.DPAD, 7000L))

    @Test fun scrollRangeIsTheContent_1x1small_everyGroup_default() =
        assertScrollRangeIsTheContent(TileReveal.ALL_GROUPS, 480, 480, 1f, seedShapedConfig())

    @Test fun scrollRangeIsTheContent_1x1small_everyGroup_lopsided() =
        assertScrollRangeIsTheContent(TileReveal.ALL_GROUPS, 480, 480, 1f, seedShapedConfig().withPressStack(InputSource.DPAD, 7000L))

    @Test fun scrollRangeIsTheContent_narrow_oneGroup_default() =
        assertScrollRangeIsTheContent(TileReveal.FOCUSED_GROUP, 540, 300, null, seedShapedConfig())

    @Test fun scrollRangeIsTheContent_narrow_oneGroup_lopsided() =
        assertScrollRangeIsTheContent(TileReveal.FOCUSED_GROUP, 540, 300, null, seedShapedConfig().withPressStack(InputSource.DPAD, 7000L))

    @Test fun scrollRangeIsTheContent_narrow_everyGroup_default() =
        assertScrollRangeIsTheContent(TileReveal.ALL_GROUPS, 540, 300, null, seedShapedConfig())

    @Test fun scrollRangeIsTheContent_narrow_everyGroup_lopsided() =
        assertScrollRangeIsTheContent(TileReveal.ALL_GROUPS, 540, 300, null, seedShapedConfig().withPressStack(InputSource.DPAD, 7000L))

    private fun assertScrollRangeIsTheContent(
        reveal: TileReveal,
        width: Int,
        height: Int,
        aspect: Float?,
        config: ControllerConfig,
    ) {
        composeRule.setContent {
            MaterialTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalTileReveal provides reveal,
                    com.mappo.ui.screen.home.LocalScreenAspect provides aspect,
                ) {
                    Surface(modifier = androidx.compose.ui.Modifier.size(width.dp, height.dp)) {
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
        settlePan()
        val body = composeRule.onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
        fun range() = body.fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
        fun scrollBy(dx: Float) {
            val action = body.fetchSemanticsNode().config[SemanticsActions.ScrollBy].action
            composeRule.runOnUiThread { action?.invoke(dx, 0f) }
            settlePan()
        }
        fun edge(group: String, outer: Boolean) = composeRule
            .onNodeWithTag("simple-group:$group", useUnmergedTree = true)
            .fetchSemanticsNode()
            .let { if (outer) it.positionInRoot.x + it.size.width else it.positionInRoot.x }
        val left = listOf("LEFT_SHOULDER", "DPAD", "LEFT_STICK", "LEFT_UTILITY")
        val right = listOf("RIGHT_SHOULDER", "FACE", "RIGHT_STICK", "RIGHT_UTILITY")
        fun assertEndsAreContent(label: String, focusOnLeft: Boolean? = null) {
            if (range().maxValue() <= 0f) return
            val window = body.fetchSemanticsNode().let {
                it.positionInRoot.x to it.positionInRoot.x + it.size.width
            }
            if (focusOnLeft != true) {
                scrollBy(-100_000f)
                val leading = left.minOf { edge(it, outer = false) }
                assert(leading >= window.first - 1f && leading - window.first <= GridEdgeSlack) {
                    "$label: scrolled to the start, the leftmost group sits " +
                        "${leading - window.first}px from the window's edge"
                }
            }
            if (focusOnLeft != false) {
                scrollBy(100_000f)
                val trailing = right.maxOf { edge(it, outer = true) }
                assert(trailing <= window.second + 1f && window.second - trailing <= GridEdgeSlack) {
                    "$label: scrolled to the end, the rightmost group sits " +
                        "${window.second - trailing}px from the window's edge"
                }
            }
        }
        assertEndsAreContent("at rest")
        // The top row first: on a short screen the bottom one can sit below the window, out of a tap.
        composeRule.onNodeWithTag("simple-group:LEFT_SHOULDER").performClick()
        settlePan()
        relayout()
        assertEndsAreContent("LEFT_SHOULDER open", focusOnLeft = true)
        val cells = mapOf(
            "DPAD" to "cell:DPAD:DPAD:dpad_up:0",
            "LEFT_STICK" to "cell:LEFT_STICK:LEFT_JOYSTICK:click:0",
            "LEFT_UTILITY" to "cell:LEFT_UTILITY:SWITCH_SELECT:click:0",
            "RIGHT_SHOULDER" to "cell:RIGHT_SHOULDER:RIGHT_TRIGGER:full_pull:0",
            "FACE" to "cell:FACE:BUTTON_DIAMOND:button_a:0",
            "RIGHT_STICK" to "cell:RIGHT_STICK:RIGHT_JOYSTICK:click:0",
            "RIGHT_UTILITY" to "cell:RIGHT_UTILITY:SWITCH_START:click:0",
        )
        for ((group, cell) in cells) {
            if (reveal == TileReveal.FOCUSED_GROUP) {
                composeRule.onNodeWithTag("simple-group:$group").requestFocus()
            } else {
                composeRule.onNodeWithTag(cell, useUnmergedTree = true).requestFocus()
            }
            settlePan()
            relayout()
            assertEndsAreContent("$group focused", focusOnLeft = group in left)
        }
    }

    /**
     * **A wide group's reach is HELD down its column** (Dylan, 2026-09-28: "you've lost the feature
     * whereby the initial input group sets the screen width for groups in its same column until the
     * user navigates to the other column. Now, all of the input groups in selected group mode just
     * flex the screen width to the minimum content size").
     *
     * With one group revealed, walking from a wide group to a narrow one collapses the wide one's
     * tiles, and the column shrinks under the view. The view must not follow it: the column's inner
     * edge — every group's glyph — stays exactly where it was on screen, keeping the room the wide
     * group was opened with. Checked where the grid overruns the window and where it fits.
     */
    @Test
    fun aWideGroupsReach_isHeldDownItsColumn_whenTheGridOverflows() = assertReachHeldDownColumn(700)

    @Test
    fun aWideGroupsReach_isHeldDownItsColumn_whenTheGridFits() = assertReachHeldDownColumn(1200)

    private fun assertReachHeldDownColumn(width: Int) {
        val live = androidx.compose.runtime.mutableStateOf(
            seedShapedConfig().withPressStack(InputSource.DPAD, 7000L),
        )
        setLiveScreen(live, width = width, height = 700)
        fun innerEdge(group: String) = composeRule
            .onNodeWithTag("simple-group:$group", useUnmergedTree = true)
            .fetchSemanticsNode().let { it.positionInRoot.x + it.size.width }
        composeRule.onNodeWithTag("simple-group:DPAD").performClick()
        settlePan()
        val wide = innerEdge("DPAD")
        // Opening the wide group shows the whole of it.
        val window = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val dpadLeft = composeRule.onNodeWithTag("simple-group:DPAD", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.x
        assert(dpadLeft >= window.left - 1f) { "opening DPAD should show all of it: at $dpadLeft" }

        composeRule.onNodeWithTag("simple-group:LEFT_UTILITY").requestFocus()
        settlePan()
        relayout()
        assert(kotlin.math.abs(innerEdge("LEFT_UTILITY") - wide) <= 1f) {
            "walking to a narrow group in the same column moved the view: the column's inner edge " +
                "went from $wide to ${innerEdge("LEFT_UTILITY")}"
        }
        // And the far column has nothing past it to scroll to.
        val body = composeRule.onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
        val scrollBy = body.fetchSemanticsNode().config[SemanticsActions.ScrollBy].action
        composeRule.runOnUiThread { scrollBy?.invoke(100_000f, 0f) }
        settlePan()
        val far = listOf("RIGHT_SHOULDER", "FACE", "RIGHT_STICK", "RIGHT_UTILITY").maxOf { group ->
            composeRule.onNodeWithTag("simple-group:$group", useUnmergedTree = true)
                .fetchSemanticsNode().let { it.positionInRoot.x + it.size.width }
        }
        assert(far <= window.right + 1f) { "the far column runs ${far - window.right}px off screen" }
        val range = body.fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
        if (range.maxValue() > 0f) {
            assert(window.right - far <= GridEdgeSlack) {
                "scrolled to the far end, the far column stops ${window.right - far}px short — " +
                    "scroll range with nothing in it"
            }
        }
    }
}
