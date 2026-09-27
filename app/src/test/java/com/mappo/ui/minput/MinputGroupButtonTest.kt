package com.mappo.ui.minput

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **The library's rounded-end optical rule** ([minputRoundEndBias]).
 *
 * A fully-rounded end carries its visual mass inboard of its geometric edge, so content centred on
 * the geometric centre crowds that arc while the squarer end keeps a fat flank. Dylan spotted it on
 * the controls bar's editor switch (2026-09-26) and asked for it to be enforced in the library
 * rather than per call site — it had only ever been applied by hand, to the group button's trailing
 * "+" segment.
 *
 * Asserted as a DIRECTION, not a number: each end segment's glyph must sit away from its own pill
 * arc, and the pair must be mirror images. The exact allowance is a tuning constant.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MinputGroupButtonTest {

    @get:Rule val composeRule = createComposeRule()

    private enum class Editor { PHYSICAL, VIRTUAL }

    @Test
    fun iconOnlySegments_seatTheirGlyphAwayFromTheirOwnPillArc() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.size(200.dp, 60.dp)) {
                    MinputGroupButton(
                        options = Editor.entries.toList(),
                        selected = Editor.PHYSICAL,
                        onSelect = {},
                        optionLabel = { "" },
                        optionIcon = {
                            if (it == Editor.PHYSICAL) {
                                Icons.Filled.SportsEsports
                            } else Icons.Filled.Layers
                        },
                        optionDescription = { it.name },
                        modifier = Modifier.testTag("switch"),
                    )
                }
            }
        }

        fun glyphCentre(name: String) = composeRule
            .onNode(hasContentDescription(name), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center.x
        fun segmentCentre(name: String) = composeRule
            .onNode(
                androidx.compose.ui.test.hasClickAction() and
                    androidx.compose.ui.test.hasAnyDescendant(hasContentDescription(name)),
                useUnmergedTree = true,
            )
            .fetchSemanticsNode().boundsInRoot.center.x

        // The FIRST segment is rounded at its start and square at its end, so its glyph sits to
        // the END of centre — off the arc. The LAST segment is the mirror image.
        val firstOffset = glyphCentre("PHYSICAL") - segmentCentre("PHYSICAL")
        val lastOffset = glyphCentre("VIRTUAL") - segmentCentre("VIRTUAL")
        assert(firstOffset > 0f) {
            "the first segment's glyph should lean away from its rounded start, got $firstOffset"
        }
        assert(lastOffset < 0f) {
            "the last segment's glyph should lean away from its rounded end, got $lastOffset"
        }
        // Mirror images, within the pixel rounding a 0.33dp inner-corner allowance quantizes to
        // (this runs at density 1, where a third of a dp is a third of a pixel).
        assert(kotlin.math.abs(firstOffset + lastOffset) < 2f) {
            "the two ends should mirror each other, got $firstOffset and $lastOffset"
        }
        // And the shift comes out of the segment's WIDTH, not the glyph's room: each is a square
        // of pill height plus its own arc's allowance.
        val group = composeRule.onNodeWithTag("switch", useUnmergedTree = true)
            .fetchSemanticsNode().size.width
        val pillPx = with(composeRule.density) { MinputPillHeight.roundToPx() }
        assert(group > pillPx * 2) { "icon-only segments should be wider than bare squares: $group" }
    }

    /** The rule itself: proportional to how round the end is, and nothing at all for a square one. */
    @Test
    fun theBias_scalesWithTheCornerAndVanishesOnASquareEnd() {
        val height = 24.dp
        assert(minputRoundEndBias(corner = 0.dp, height = height) == 0.dp)
        assert(minputRoundEndBias(corner = height / 2, height = height) == MinputRoundEndBias)
        // Past a pill it saturates rather than growing.
        assert(minputRoundEndBias(corner = height, height = height) == MinputRoundEndBias)
        // Half-round, half the allowance.
        assert(minputRoundEndBias(corner = height / 4, height = height) == MinputRoundEndBias / 2)
        // A shape rounded alike at both ends is symmetric, so nothing shifts — a circle's glyph
        // stays centred.
        val pill = minputRoundEndBias(corner = height / 2, height = height)
        assert(pill == minputRoundEndBias(corner = height / 2, height = height))
        assert(minputRoundEndWidth(height, height / 2, height / 2) == height + pill * 2)
    }

    /** Guards against the test above passing on an empty screen. */
    @Test
    fun theSwitchRenders() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.size(200.dp, 60.dp)) {
                    MinputGroupButton(
                        options = Editor.entries.toList(),
                        selected = Editor.PHYSICAL,
                        onSelect = {},
                        optionLabel = { "" },
                        optionIcon = { Icons.Filled.Layers },
                        optionDescription = { it.name },
                    )
                }
            }
        }
        composeRule.onRoot().fetchSemanticsNode()
        composeRule.onNode(hasContentDescription("PHYSICAL"), useUnmergedTree = true)
            .fetchSemanticsNode()
    }
}
