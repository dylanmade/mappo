package com.mappo.ui.minput

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
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
 * perfectly FLAT (square corners) — the classic connected-segment layout.
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
 * Segments WRAP their label by default — a group of buttons is a row of buttons, and a
 * stretched segment reads as a banner (2026-08-29: the action-set row's "Default Map"
 * sprawled the full content width). Pass [equalWidths] for the stretched form: every
 * segment takes an equal share of whatever [modifier] sizes the group to (typically
 * `fillMaxWidth()`).
 *
 * Segments may be ICON-ONLY (2026-09-26): pass [optionIcon] and return a blank [optionLabel],
 * and a segment renders as a square glyph tile wearing the same selection treatment — the
 * form the controls bar's editor switcher takes, where two glyphs say "physical buttons" and
 * "virtual buttons" better than two words would. An icon BESIDE a label works too; the glyph
 * follows the segment's content color either way (a segment's icons are concept icons, never
 * fixed-color hardware art), and [optionDescription] carries the semantics a blank label
 * cannot.
 *
 * @param equalWidths stretch every segment to an equal share of the group's width (the
 *   pre-2026-08-29 behavior); default wraps each label.
 * @param container fill for the UNSELECTED segments (and the trailing action segment) —
 *   surface 2 by default, overrideable for a group riding a plane where that would vanish
 *   (the minput coloration rule: theme-derived, overrideable per view).
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
    optionIcon: ((T) -> ImageVector)? = null,
    optionDescription: ((T) -> String?)? = null,
    enabled: Boolean = true,
    equalWidths: Boolean = false,
    container: Color = MinputElevatedContainer,
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
            // Optical centring, per the library rule: each end is inset in proportion to how
            // round it is, so a segment with one pill end and one square one doesn't crowd its
            // arc (see [minputRoundEndBias]). An end segment of a group has exactly that shape.
            val startBias = minputRoundEndBias(
                corner = if (i == 0) outerCorner else GroupInnerCorner,
                height = MinputPillHeight,
            )
            val endBias = minputRoundEndBias(
                corner = if (i == lastRoundedIndex) outerCorner else GroupInnerCorner,
                height = MinputPillHeight,
            )
            val isSelected = option == selected
            val fill by animateColorAsState(
                targetValue = if (isSelected) minputHighlightContainer() else container,
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
                    .then(if (equalWidths) Modifier.weight(1f) else Modifier)
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
                val label = optionLabel(option)
                val icon = optionIcon?.invoke(option)
                val description = optionDescription?.invoke(option)
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        // An icon-only segment is a SQUARE of glyph room (a pill's height
                        // across) plus each end's round allowance, so a pair of them reads as two
                        // glyph tiles rather than two capsules — and the arcs get their space
                        // instead of taking it off the glyph.
                        .then(
                            if (label.isBlank() && icon != null) {
                                Modifier.width(
                                    minputRoundEndWidth(
                                        height = MinputPillHeight,
                                        startCorner = if (i == 0) outerCorner else GroupInnerCorner,
                                        endCorner = if (i == lastRoundedIndex) {
                                            outerCorner
                                        } else GroupInnerCorner,
                                    ),
                                )
                            } else Modifier,
                        )
                        .padding(
                            start = if (label.isBlank() && icon != null) {
                                startBias
                            } else MinputPillContentPadding + startBias,
                            end = if (label.isBlank() && icon != null) {
                                endBias
                            } else MinputPillContentPadding + endBias,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = description,
                            // An icon-only segment's glyph runs at the UTILITY scale rather than
                            // the pill one (Dylan, 2026-09-26): it is the segment's entire
                            // content, with no label to be measured against, and at the pill
                            // scale it read small and lost in its own tile. A glyph BESIDE a
                            // label keeps the pill scale, so the two still match in weight.
                            modifier = Modifier.size(
                                if (label.isBlank()) MinputIconButtonIconSize else MinputPillIconSize,
                            ),
                            tint = content,
                        )
                        if (label.isNotBlank()) Spacer(Modifier.width(MinputGlyphLabelGap))
                    }
                    if (label.isNotBlank()) {
                        Text(
                            text = label,
                            style = minputMiniTextStyle(),
                            color = content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // Wrap-width segments are otherwise unbounded (set titles are
                            // user-typed); cap them at the family's label width so one long
                            // name can't run the group off its bar.
                            modifier = Modifier.widthIn(max = MinputPillLabelMaxWidth),
                        )
                    }
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
                color = container,
                border = minputBevelBorder(
                    container,
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
                Box(
                    // The same optical rule as the segments above, which this is where it was
                    // first tuned (Dylan, 2026-08-29): a full pill arc at the end, a square
                    // start, so the glyph is pushed off the curve by half the end's allowance.
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(
                            start = minputRoundEndBias(GroupInnerCorner, MinputPillHeight),
                            end = minputRoundEndBias(outerCorner, MinputPillHeight),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
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

/** Fixed width of the trailing ACTION segment — icon-only, deliberately narrower than a
 *  LABELLED selection segment so it reads as an appendix, not a peer. (It comes out the same as
 *  an icon-only selection segment with one pill end, which is what it is.) */
private val GroupActionSegmentWidth = 26.dp

