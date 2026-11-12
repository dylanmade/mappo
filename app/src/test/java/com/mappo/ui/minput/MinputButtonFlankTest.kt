package com.mappo.ui.minput

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Redo2
import com.composables.icons.lucide.Undo2
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **A labelled button's flanks are the same either side** (2026-11-12): from the pill's edge to
 * the leading glyph's ink, and from the label's end to the pill's other edge, is the stroke plus
 * the variant's content padding — and a labelled [MinputClusterButton] at a cluster's end
 * measures the same as a [MinputButton] beside it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1280dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MinputButtonFlankTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val size = MinputSize.Large
    private val flank = MinputBoxStroke + size.contentPadding
    private val glyphRun = size.labelIconSize.dp + MinputGlyphLabelGap

    private fun assertDp(expected: Dp, actual: Dp, what: String) =
        assertEquals(what, expected.value, actual.value, 0.6f)

    @Test
    fun labelledButton_flanksAreSymmetric() {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    Box(Modifier.padding(40.dp)) {
                        MinputButton(text = "New", leadingIcon = Lucide.Plus, size = size, onClick = {}, modifier = Modifier.testTag("b"))
                    }
                }
            }
        }
        val button = composeRule.onNodeWithTag("b").getUnclippedBoundsInRoot()
        val label = composeRule.onNodeWithText("New", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertDp(flank + glyphRun, label.left - button.left, "start flank + glyph + gap")
        assertDp(flank, button.right - label.right, "end flank")
    }

    @Test
    fun labelledCluster_endFlanksMatchAButton() {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    Row(Modifier.padding(40.dp)) {
                        MinputButtonCluster(size = size, modifier = Modifier.testTag("c")) {
                            MinputClusterButton(Lucide.Undo2, "Undo", onClick = {}, size = size, text = "Undo")
                            MinputClusterDivider(size = size)
                            MinputClusterButton(Lucide.Redo2, "Redo", onClick = {}, size = size, text = "Redo")
                        }
                    }
                }
            }
        }
        val cluster = composeRule.onNodeWithTag("c").getUnclippedBoundsInRoot()
        val undo = composeRule.onNodeWithText("Undo", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val redo = composeRule.onNodeWithText("Redo", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertDp(flank + glyphRun, undo.left - cluster.left, "start flank + glyph + gap")
        assertDp(flank, cluster.right - redo.right, "end flank")
        assertDp(size.height, cluster.bottom - cluster.top, "cluster height")
    }
}
