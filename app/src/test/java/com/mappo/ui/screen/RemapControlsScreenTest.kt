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
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.requestFocus
import com.mappo.ui.screen.remap.ControlsBodyTestTag
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
     * A group box is a controller focus stop — which is also the gate on the right stick
     * scrolling its rows (`LocalStickScroll`, published from the box's own focus state). If the
     * box stops taking focus, the stick silently stops working, so this pins the gate rather
     * than the scrolling (Robolectric measures text at ~zero width, so nothing here overflows
     * to scroll in the first place).
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

    /**
     * The centre column's box is sized by the COLUMN, not by its content, so its rows have to
     * be centred in it — a Box hands its children a zero minimum width, and without an explicit
     * centre the cluster sat against the box's left edge (Dylan, 2026-09-19).
     */
    @Test
    fun simpleView_centresTheUtilityBoxesRowsInItsColumn() {
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

        val box = composeRule.onNodeWithTag("simple-group:UTILITY").fetchSemanticsNode().boundsInRoot
        // Their seeded self-mappings, which is what a fresh layout actually shows.
        val start = composeRule.onNodeWithText("Start / Menu", substring = true, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val select = composeRule.onNodeWithText("Select / View", substring = true, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        // The two halves meet at the box's centre line, so their own span centres on it.
        val clusterCentre = (minOf(start.left, select.left) + maxOf(start.right, select.right)) / 2f
        val drift = kotlin.math.abs(clusterCentre - box.center.x)
        assert(drift < box.width / 8f) {
            "utility rows drift ${'$'}drift from the box centre (box=${'$'}box, start=${'$'}start, select=${'$'}select)"
        }
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

    @Test
    fun topBar_viewingLayout_showsPreviewOverline_noBack() {
        // 2026-08-27 bar: no Back arrow in any state — the change button (the layouts
        // drawer's summon) leads. The Layout settings and Activate pills are retired
        // (options = Start key; activation = drawer cards); a non-active layout is
        // marked by the "(Preview)" overline suffix instead. The add-set affordance
        // is the set row's "+" segment.
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
        // The identity button (the drawer summon since 2026-08-27, ArrowLeftRight
        // pill deleted; a plain pill carrying the LAYOUT name since the 2026-08-29 bar
        // redesign collapsed the two-line stack — the application rides the icon).
        composeRule.onNodeWithTag("bar:identity").assertExists()
        // No layout name in this setup — the label falls back to "Layout".
        composeRule.onNodeWithText("Layout (Preview)", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("Activate layout").assertCountEquals(0)
        composeRule.onAllNodesWithText("Layout settings").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Add action set").assertCountEquals(1)
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
        // The home state's identity label carries no "(Preview)" suffix.
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
     * The CENTRE card splits at its centre line, like the centre BOX does (Dylan, 2026-09-20):
     * the utility glyphs meet in the middle and their commands radiate outward.
     */
    @Test
    fun zoomScene_centreCardsRowsRadiateFromItsCentreLine() {
        setScreenLocal(seedShapedConfig())

        openAdvanced("UTILITY")
        composeRule.waitForIdle()
        val card = composeRule.onNodeWithTag("zoom-card:UTILITY", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val select = composeRule.onNodeWithTag("cell:UTILITY:SWITCH_SELECT:click:0", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val start = composeRule.onNodeWithTag("cell:UTILITY:SWITCH_START:click:0", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assert(select.right <= card.center.x) {
            "Select's commands run LEFT out of the centre: card $card, select $select"
        }
        assert(start.left >= card.center.x) {
            "Start's commands run RIGHT out of the centre: card $card, start $start"
        }
        // Both on one line, as in the box: the glyphs are neighbours, not stacked rows.
        assert(kotlin.math.abs(select.top - start.top) < 1f) {
            "the two halves should sit level: $select / $start"
        }
    }

    /**
     * The basic view is a 3 × 3 grid (Dylan, 2026-09-17), and the point of rebuilding it that way
     * was this: the utility box belongs in the stick BAND, between the two stick boxes, not at
     * the bottom of the plate below them — which is where three independently-laid-out columns
     * had left it.
     */
    @Test
    fun simpleView_seatsTheUtilityBoxBetweenTheStickBoxes() {
        setScreenLocal(seedShapedConfig())
        fun boundsOf(group: String) =
            composeRule.onNodeWithTag("simple-group:$group", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot

        val utility = boundsOf("UTILITY")
        val leftStick = boundsOf("LEFT_STICK")
        val rightStick = boundsOf("RIGHT_STICK")

        assert(utility.left > leftStick.right) { "utility should sit right of the left stick: $utility" }
        assert(utility.right < rightStick.left) { "utility should sit left of the right stick: $utility" }
        // Level with them: its centre inside the band the two stick boxes span.
        val bandTop = minOf(leftStick.top, rightStick.top)
        val bandBottom = maxOf(leftStick.bottom, rightStick.bottom)
        assert(utility.center.y in bandTop..bandBottom) {
            "utility should be level with the sticks, not below them: $utility vs $bandTop..$bandBottom"
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
        // UTILITY is exempt by design (Dylan, 2026-09-21): Start and Select have no mode to
        // pick — theirs follows from whether they are bound — so their card states its name.
        for (group in RemapSimpleGroup.entries - RemapSimpleGroup.UTILITY) {
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
     * EDIT MODE is view-WIDE (Dylan, 2026-09-22): selecting one group tiles EVERY group's rows,
     * so the whole controller stays legible and a command can be carried from any group to any
     * other. And it does not travel — the advanced card is not opened.
     */
    @Test
    fun simpleView_selectingAGroup_tilesEveryGroupsRows_withoutZooming() {
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

        composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_a:0").assertExists()
        // A group the user did NOT select, tiled all the same.
        composeRule.onNodeWithTag("cell:DPAD:DPAD:dpad_up:0").assertExists()
        // Every row ends in its "+", exactly as the table's rows do.
        composeRule.onNodeWithTag("cell:FACE:BUTTON_DIAMOND:button_a:1").assertExists()
        composeRule.onAllNodesWithTag("group-editor").assertCountEquals(0)
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
    fun enteringEditMode_movesEveryGroupInOneDirection_withoutShaking() {
        composeRule.mainClock.autoAdvance = false
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

        val legs = mapOf(
            "in" to travel { composeRule.onNodeWithTag("simple-group:FACE").performClick() },
            // And out again — the leg that drifted, and so the one worth watching.
            "out" to travel { composeRule.runOnUiThread { back?.invoke() } },
        )

        for ((leg, frames) in legs) tracked.forEach { group ->
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
        val tracked = listOf("DPAD", "FACE", "LEFT_SHOULDER", "RIGHT_STICK", "UTILITY")
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
                Surface(modifier = androidx.compose.ui.Modifier.size(300.dp, 500.dp)) {
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

        // Hard to the right, as far as the body will go.
        val before = leftShoulderX()
        val scrollBy = composeRule.onNodeWithTag(ControlsBodyTestTag, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.ScrollBy].action
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
