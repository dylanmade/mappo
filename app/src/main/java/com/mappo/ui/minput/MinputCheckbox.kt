package com.mappo.ui.minput

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide

/**
 * Mappo's miniature checkbox — the library's boolean control for settings that read as part of
 * a SENTENCE, where [MinputSwitch] is the standalone on/off for a setting that reads as a row.
 *
 * Hand-drawn for the same reason the switch is: M3's `Checkbox` has a fixed 40dp touch box
 * around an 18dp mark, and uniform scaling can't bring the mark down to the mini text line
 * without taking the tap target with it. Behavior and semantics come from foundation's
 * `toggleable` (Checkbox role, halo-free like the rest of the library), and the conventional
 * state motion is reproduced: the box crossfades to the highlight plane and the tick pops in.
 * Press travel is the family's [minputInteractiveMotion].
 *
 * [label] makes the word beside the box part of the same target — the usual reading of a
 * checkbox, and the only sane way to hit a 12dp box with a thumb. Without one the box stands
 * alone (give the call site's row its own labelling then).
 */
@Composable
fun MinputCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    size: Dp = MinputCheckboxSize,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (checked) colors.primary else colors.surfaceContainerHighest,
        label = "checkboxFill",
    )
    val outline by animateColorAsState(
        // M3's unchecked box is a ring; the checked one fills and drops it.
        targetValue = if (checked) fill else colors.outline,
        label = "checkboxOutline",
    )
    val tick by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        label = "checkboxTick",
    )
    val shape = RoundedCornerShape(MinputCheckboxCorner)
    Row(
        modifier = modifier
            .minputInteractiveMotion(interaction)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        interactionSource = interaction,
                        // A box this small is under any sane ripple bound — the tick and the
                        // family press travel are the feedback.
                        indication = null,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = onCheckedChange,
                    )
                } else Modifier,
            )
            .then(if (enabled) Modifier else Modifier.alpha(0.55f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MinputCheckboxLabelGap),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(fill)
                .border(MinputBoxStroke, outline, shape)
                // The stroke rule: the tick's inset starts inside the outline.
                .minputStrokeInset(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Lucide.Check,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier
                    .size(size - (MinputCheckboxTickInset + MinputBoxStroke) * 2)
                    .graphicsLayer {
                        alpha = tick
                        scaleX = tick
                        scaleY = tick
                    },
            )
        }
        if (label != null) {
            Text(
                text = label,
                style = minputMiniTextStyle(),
                color = colors.onSurface,
                maxLines = 1,
            )
        }
    }
}

/** Box edge — the switch's height, so a checkbox and a switch sit on the same text line. */
val MinputCheckboxSize = MinputSwitchHeight

/** Air between the box and its label. */
val MinputCheckboxLabelGap = 5.dp

/** How far the tick sits inside the box. */
private val MinputCheckboxTickInset = 1.dp

/** Box rounding — a square with the corners knocked off, not a pill. */
private val MinputCheckboxCorner = 3.dp
