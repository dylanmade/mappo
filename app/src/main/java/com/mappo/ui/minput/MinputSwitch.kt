package com.mappo.ui.minput

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp

/**
 * Mappo's miniature switch — restyles [androidx.compose.material3.Switch] at bar scale.
 * Hand-drawn rather than wrapped because the need is a NON-UNIFORM shrink: M3's switch has
 * fixed 52×32 track metrics, and `scaledLayout` can only scale both axes together —
 * squeezing it to a text-line height leaves a stubby track. This track runs proportionally
 * wider ([MinputSwitchWidth] × [MinputSwitchHeight], height matched to the mini text line
 * so an overline + switch stack measures like the bar's text stacks).
 *
 * Behavior/semantics come from foundation's `toggleable` (Switch role, halo-free like the
 * rest of the library); the conventional state motion is reproduced — the thumb slides and
 * the track/thumb colors crossfade on the standard short spec, M3's coloration mapping
 * (checked: primary track + onPrimary thumb; unchecked: outlined surfaceContainerHighest
 * track + outline thumb). Press travel is the family's [minputInteractiveMotion].
 */
@Composable
fun MinputSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = MinputSwitchWidth,
    height: Dp = MinputSwitchHeight,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.primary else colors.surfaceContainerHighest,
        label = "switchTrack",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) colors.onPrimary else colors.outline,
        label = "switchThumb",
    )
    val outlineAlpha by animateColorAsState(
        // M3's unchecked track wears an outline ring that fades out when checked.
        targetValue = if (checked) trackColor else colors.outline,
        label = "switchOutline",
    )
    val thumbSize = height - MinputSwitchThumbInset * 2
    val thumbTravel by animateDpAsState(
        targetValue = if (checked) width - thumbSize - MinputSwitchThumbInset else MinputSwitchThumbInset,
        label = "switchThumbTravel",
    )
    Box(
        modifier = modifier
            .minputInteractiveMotion(interaction)
            .size(width = width, height = height)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .border(MinputBoxStroke, outlineAlpha, RoundedCornerShape(50))
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        interactionSource = interaction,
                        // The thumb is smaller than any sane ripple bound — state feedback
                        // is the thumb slide + the family press travel instead.
                        indication = null,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                } else Modifier,
            )
            .then(if (enabled) Modifier else Modifier.alpha(0.55f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = thumbTravel)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}
