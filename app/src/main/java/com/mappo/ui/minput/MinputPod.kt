package com.mappo.ui.minput

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The minput POD: a pill-shaped plate that a small cluster of controls rides on, wearing the
 * `surfaceContainer` plane plus the family bevel's top/bottom edge highlights.
 *
 * Born 2026-08-29 with the remap controls view's transparent top bar — with no strip to sit
 * on, each cluster needed to carry its own ground — and formalized here because the pattern
 * generalizes: wherever chrome must float over content rather than band across it, a pod
 * gives a group of controls one plate, one silhouette, and one shared inset.
 *
 * **Plane:** the pod is the plane the bars themselves used to be, so its contents sit one
 * step ABOVE it — pass `elevated = true` to the [MinputPillButton]s (and friends) inside,
 * the same fill [MinputGroupButton]'s segments already wear. A default (surface-1) button on
 * a pod all but vanishes into it.
 *
 * Height rests at [MinputPodHeight] — a pill control plus [MinputPodPadding] above and below
 * — but is a FLOOR, not a cap: taller content (a two-line stack) grows the plate and its
 * pill silhouette follows. (The bevel's corner fade is still measured from the resting
 * height; on a much taller pod the highlight runs a touch short of the arc.)
 *
 * Space pods with [MinputPodGap]; controls WITHIN one are spaced by [MinputPodItemGap] via
 * the default [horizontalArrangement].
 *
 * @param color the plate's fill — theme-derived by default, overrideable per view like every
 *   minput coloration.
 */
@Composable
fun MinputPod(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(MinputPodItemGap),
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color,
        border = minputBevelBorder(color, MinputPodHeight / 2),
        modifier = modifier.heightIn(min = MinputPodHeight),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = horizontalArrangement,
            modifier = Modifier.padding(MinputPodPadding),
            content = content,
        )
    }
}
