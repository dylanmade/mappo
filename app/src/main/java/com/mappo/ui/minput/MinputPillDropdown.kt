package com.mappo.ui.minput

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Mappo's pill-style dropdown picker: the current option's (optional) glyph + label on a
 * beveled pill, opening a menu of options with a check on the current one. The generic
 * behind the remap screen's mode/strip pickers.
 *
 * @param optionLabel label for an option (pill + menu rows).
 * @param optionIcon menu-row leading glyph per option; null lambda result = no glyph.
 * @param pillIcon glyph shown ON the pill — the caller decides (e.g. current option's
 *   glyph, a fixed identity icon, or null for none); the pill derives nothing itself.
 * @param overline renders the pill label in the overline treatment (uppercase, tracked out).
 * @param elevated topmost-plane fill for pills sitting on a box/card background.
 * @param fixedWidth pins the pill to a static footprint instead of flexing to the label.
 */
@Composable
fun <T> MinputPillDropdown(
    current: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    optionIcon: (@Composable (T) -> ImageVector?)? = null,
    pillIcon: ImageVector? = null,
    overline: Boolean = false,
    elevated: Boolean = false,
    fixedWidth: Dp? = null,
    onClickLabel: String? = null,
    /** The variant on the library's control scale — see [MinputSize]. */
    size: MinputSize = MinputSize.Standard,
) {
    var open by remember { mutableStateOf(false) }
    val container = if (elevated) MinputElevatedContainer else minputBoxContainer()
    val interaction = remember { MutableInteractionSource() }
    Box {
        // Shared box treatment — pill-style dropdown button, no trailing arrow.
        Surface(
            shape = RoundedCornerShape(50),
            color = container,
            border = minputBevelBorder(container, size.corner),
            modifier = modifier
                .minputInteractiveMotion(interaction)
                .heightIn(min = size.height)
                .then(
                    if (fixedWidth != null) Modifier.width(fixedWidth)
                    else Modifier.widthIn(min = MinputPillMinWidth),
                )
                .then(
                    if (enabled) {
                        Modifier.clip(RoundedCornerShape(50)).clickable(
                            interactionSource = interaction,
                            indication = minputIndication(),
                            onClickLabel = onClickLabel,
                        ) { open = true }
                    } else Modifier.alpha(0.6f),
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                // The stroke rule: padding starts inside the bevel ring.
                // Symmetric: the glyph is ink-measured ([MinputIcon]), so it brings no margin
                // that a fixed-width pill's centring would expose.
                modifier = Modifier.minputStrokeInset().padding(horizontal = size.contentPadding),
            ) {
                if (pillIcon != null) {
                    MinputIcon(
                        pillIcon,
                        contentDescription = null,
                        size = size.iconSize,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(MinputGlyphLabelGap))
                }
                Text(
                    text = optionLabel(current).let { if (overline) it.uppercase() else it },
                    style = if (overline) minputOverlineTextStyle() else minputMiniTextStyle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = MinputPillLabelMaxWidth),
                )
            }
        }
        MinputDropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            current = current,
            options = options,
            optionLabel = optionLabel,
            onPick = onPick,
            optionIcon = optionIcon,
        )
    }
}
