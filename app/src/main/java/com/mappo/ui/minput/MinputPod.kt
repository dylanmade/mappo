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
import androidx.compose.ui.unit.Dp

/**
 * The minput POD: a pill-shaped plate that a small cluster of controls rides on, wearing the
 * `surface` plane plus the family bevel's top/bottom edge highlights.
 *
 * Born 2026-08-29 with the remap controls view's transparent top bar — with no strip to sit
 * on, each cluster needed to carry its own ground — and formalized here because the pattern
 * generalizes: wherever chrome must float over content rather than band across it, a pod
 * gives a group of controls one plate, one silhouette, and one shared inset.
 *
 * **Plane:** a pod floats one step above the view behind it (which drops to
 * `surfaceContainerLowest` under a pod-based screen), and its contents sit one step above the
 * pod in turn. At the default `surface` fill that means the contents take the DEFAULT
 * surface-1 box treatment — plain [MinputButton]s, no variant. Give a pod a higher [color]
 * and its contents step up with it (`elevated = true`, the fill [MinputGroupButton]'s
 * segments wear by default).
 *
 * The fill was `surfaceContainer` — the plane the bars themselves used to be — until
 * 2026-08-30, when the whole controls view shifted a step darker and the pods came with it.
 *
 * Height rests at [MinputPodHeight] — a pill control plus [MinputPodPadding] above and below
 * — but is a FLOOR, not a cap: taller content (a two-line stack, a whole card list) grows
 * the plate.
 *
 * **[corner] is an absolute radius, never a percentage** (2026-08-30): at the resting height
 * the default is exactly half of it, so a bar-scale pod is a true pill — but a TALL pod then
 * stays a rounded rectangle instead of stretching into a vertical capsule, which is what a
 * percentage corner did to the layouts drawer's list plate. Pass a tighter radius to relate a
 * plate to the cards riding it.
 *
 * Space pods with [MinputPodGap]; controls WITHIN one are spaced by [MinputPodItemGap] via
 * the default [horizontalArrangement].
 *
 * @param color the plate's fill — theme-derived by default, overrideable per view like every
 *   minput coloration.
 * @param corner the plate's corner radius; the default is a pill at the resting height.
 */
@Composable
fun MinputPod(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    corner: Dp = MinputPodHeight / 2,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(MinputPodItemGap),
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(corner),
        color = color,
        border = minputBevelBorder(color, corner),
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
