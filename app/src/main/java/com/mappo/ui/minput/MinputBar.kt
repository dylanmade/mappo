package com.mappo.ui.minput

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * **A screen-edge bar: a physical strip, lit on the edge that faces the content** (Dylan,
 * 2026-09-26).
 *
 * The app's two fixed bars — the remap controls top bar and the home frame's bottom bar — are
 * this component, so they cannot drift apart again. Anatomy: a [MinputBarHeight] strip filled
 * with the bar plane (`surfaceContainer`, one step above the screen's own
 * `surfaceContainerLowest`), a single bevel highlight along its CONTENT-facing edge
 * ([minputBevelEdge]), and the family's [MinputBarEdgePadding] inset for whatever rides it.
 *
 * The lit edge is the whole idea: with one surface above the content plane and one lit rim, the
 * two bars read as the top and bottom faces of a device's front panel rather than as two
 * rectangles of slightly different grey. It is the [minputBevelBorder] treatment every raised
 * minput element wears, reduced to the one edge a flat strip actually catches light on.
 *
 * History worth keeping: from 2026-08-29 to 2026-09-26 both bars were TRANSPARENT, each cluster
 * riding its own [MinputPod]. That is retired — pods on the bars are gone with it, the buttons
 * sit directly on the strip, and a bar is a surface again. Don't re-pod one bar alone: a pod
 * wears a plane BELOW `surfaceContainer`, so one on a filled strip reads as a hole punched in
 * it, and the two bars must stay siblings.
 *
 * Content is aligned by the caller (`Modifier.align(Alignment.CenterStart)` and friends) so a
 * centred cluster is centred in the BAR rather than in the slack between its neighbours.
 */
@Composable
fun MinputBar(
    edge: MinputEdge,
    modifier: Modifier = Modifier,
    height: Dp = MinputBarHeight,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(color)
            // After the fill, so the line lands on top of it rather than under.
            .minputBevelEdge(minputBevelHighlight(color), edge)
            .padding(horizontal = MinputBarEdgePadding),
        content = content,
    )
}
