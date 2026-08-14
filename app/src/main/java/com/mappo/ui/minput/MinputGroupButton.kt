package com.mappo.ui.minput

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The minput group button: a row of single-choice segments — a deliberate mix of the M2 and
 * M3 group buttons. It BEHAVES like the classic connected segmented button
 * ([androidx.compose.material3.SingleChoiceSegmentedButtonRow] / M2's ToggleButtonBar —
 * plain single-choice, no width animation), but carries the M3 button group's distinct GAP
 * between segments. Group silhouette stays a pill: outer ends fully rounded, inner edges
 * perfectly FLAT (square corners) — the classic connected-segment profile.
 *
 * Selection reads through the surface system: unselected segments wear **surface 2**
 * ([MinputElevatedContainer]) with the family bevel — ordinary buttons on their plane —
 * and the selected segment wears the **highlight plane** ([minputHighlightContainer],
 * the lightest fill, reserved for selection) so it reads as the lit button in the row.
 * The well fill stays reserved for text inputs. Each segment hovers/presses with the pill
 * family's [minputInteractiveMotion] lift. Base behavior (single-choice semantics, ripple,
 * radio-button roles) comes from foundation's selectableGroup/selectable — the same base
 * the M3 component uses.
 *
 * Segments share the width equally; size the group via [modifier] (typically
 * `fillMaxWidth()`).
 *
 * @param trailingActionIcon optional ACTION segment closing the group — a deliberate break
 *   from single-choice convention: a narrow fixed-width segment (it takes the group's outer
 *   end rounding) that fires [onTrailingAction] instead of selecting. Born for the action-set
 *   row's "+" (add a set).
 */
@Composable
fun <T> MinputGroupButton(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingActionIcon: ImageVector? = null,
    trailingActionDescription: String? = null,
    onTrailingAction: () -> Unit = {},
) {
    val outerCorner = MinputPillHeight / 2
    Row(
        modifier = modifier
            .height(MinputPillHeight)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(GroupSegmentGap),
    ) {
        val lastRoundedIndex = if (trailingActionIcon != null) -1 else options.lastIndex
        options.forEachIndexed { i, option ->
            val shape = RoundedCornerShape(
                topStart = if (i == 0) outerCorner else GroupInnerCorner,
                bottomStart = if (i == 0) outerCorner else GroupInnerCorner,
                topEnd = if (i == lastRoundedIndex) outerCorner else GroupInnerCorner,
                bottomEnd = if (i == lastRoundedIndex) outerCorner else GroupInnerCorner,
            )
            val isSelected = option == selected
            val fill by animateColorAsState(
                targetValue = if (isSelected) minputHighlightContainer() else MinputElevatedContainer,
                label = "minputGroupSegmentFill",
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                label = "minputGroupSegmentContent",
            )
            val interaction = remember { MutableInteractionSource() }
            Surface(
                shape = shape,
                color = fill,
                border = minputBevelBorder(
                    fill,
                    cornerRadius = if (i == 0) outerCorner else GroupInnerCorner,
                    endCornerRadius = if (i == lastRoundedIndex) outerCorner else GroupInnerCorner,
                ),
                modifier = Modifier
                    .weight(1f)
                    .minputInteractiveMotion(interaction)
                    .fillMaxHeight()
                    .then(
                        if (enabled) {
                            Modifier.clip(shape).selectable(
                                selected = isSelected,
                                interactionSource = interaction,
                                indication = minputIndication(),
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            )
                        } else Modifier.alpha(0.55f),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = MinputPillContentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = optionLabel(option),
                        style = minputMiniTextStyle(),
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (trailingActionIcon != null) {
            // The action segment: fixed narrow width (never a selection peer), wearing the
            // unselected surface-2 treatment and the group's outer end rounding.
            val shape = RoundedCornerShape(
                topStart = GroupInnerCorner,
                bottomStart = GroupInnerCorner,
                topEnd = outerCorner,
                bottomEnd = outerCorner,
            )
            val interaction = remember { MutableInteractionSource() }
            Surface(
                shape = shape,
                color = MinputElevatedContainer,
                border = minputBevelBorder(
                    MinputElevatedContainer,
                    cornerRadius = GroupInnerCorner,
                    endCornerRadius = outerCorner,
                ),
                modifier = Modifier
                    .width(GroupActionSegmentWidth)
                    .minputInteractiveMotion(interaction)
                    .fillMaxHeight()
                    .then(
                        if (enabled) {
                            Modifier.clip(shape).clickable(
                                interactionSource = interaction,
                                indication = minputIndication(),
                                role = Role.Button,
                                onClick = onTrailingAction,
                            )
                        } else Modifier.alpha(0.55f),
                    ),
            ) {
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Icon(
                        trailingActionIcon,
                        contentDescription = trailingActionDescription,
                        modifier = Modifier.size(MinputPillIconSize),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** Gap between segments — the M3 button-group side of this component's M2/M3 mix. */
private val GroupSegmentGap = 4.dp

/** Segment inner corners: perfectly square (outer ends stay full pill) — the M2 side. */
private val GroupInnerCorner = 2.dp

/** Fixed width of the trailing ACTION segment — icon-only, deliberately narrower than the
 *  equal-weight selection segments so it reads as an appendix, not a peer. */
private val GroupActionSegmentWidth = 30.dp
