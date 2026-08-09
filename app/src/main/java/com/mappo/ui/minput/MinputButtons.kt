package com.mappo.ui.minput

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow

/**
 * Mappo's hand-rolled miniature pill button, in the shared box treatment. [filled] (a
 * primary/commit action) keeps its emphasis through the stronger text color only. [elevated]
 * uses the topmost button plane for buttons sitting on a box/card background. Disabled =
 * dimmed + inert.
 *
 * [leadingIcon] renders a small glyph before the label. [leadingIconTint] defaults to
 * Unspecified because the primary use is hardware button prompts (Kenney glyphs carry fixed
 * colors that must not re-tint); pass a theme role for tintable concept icons.
 */
@Composable
fun MinputPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    elevated: Boolean = false,
    leadingIcon: Painter? = null,
    leadingIconTint: Color = Color.Unspecified,
) {
    val content = if (filled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant
    val container = if (elevated) MinputElevatedContainer else minputBoxContainer()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        border = minputBevelBorder(container, MinputPillHeight / 2),
        modifier = modifier
            .minputInteractiveMotion(interaction)
            .height(MinputPillHeight)
            .then(
                if (enabled) {
                    Modifier.clip(RoundedCornerShape(50)).clickable(
                        interactionSource = interaction,
                        indication = minputIndication(),
                        onClick = onClick,
                    )
                } else Modifier.alpha(0.55f),
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .height(MinputPillHeight)
                .padding(horizontal = MinputPillContentPadding),
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(MinputPillIconSize),
                    tint = leadingIconTint,
                )
                Spacer(Modifier.width(MinputGlyphLabelGap))
            }
            Text(
                text = text,
                style = minputMiniTextStyle(),
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Mappo's hand-rolled miniature icon button (cogs etc.) — ripple-clipped circle, no 48dp halo. */
@Composable
fun MinputIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .minputInteractiveMotion(interaction)
            .size(MinputIconButtonSize)
            .clip(CircleShape)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = minputIndication(),
                        onClick = onClick,
                    )
                } else Modifier.alpha(0.45f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(MinputIconButtonIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
